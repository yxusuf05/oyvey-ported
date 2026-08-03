/**
 * Scene assembly and per-frame uniform updates.
 *
 * Everything visual hangs off one number. `setDescent` samples the theme's palette ladder
 * and pushes the result into the world shader, the actor shaders, the fog and the grade
 * pass, so "rainbow and sunshine" and "the descent" are the same renderer with different
 * uniforms — which is exactly why the transition can be continuous instead of a cut.
 */

import {
  BoxGeometry,
  ClampToEdgeWrapping,
  Color,
  DataTexture,
  FogExp2,
  Group,
  LinearFilter,
  Mesh,
  MeshBasicMaterial,
  NearestFilter,
  PerspectiveCamera,
  RGBAFormat,
  RedFormat,
  Scene,
  ShaderMaterial,
  SRGBColorSpace,
  Texture,
  UnsignedByteType,
  Vector2,
  Vector3,
  Vector4,
  WebGLRenderer,
} from 'three';
import {
  CEILING_HEIGHT,
  TILE_SIZE,
  levelGrid,
  packLightTexture,
  packWallTexture,
  propagateLight,
  samplePalette,
  tileToWorld,
  getTheme,
  type Level,
  type PaletteStop,
  type ThemeSpec,
} from '@game/shared/levelgen';
import { EntityFlags, EntityKind, type ChalkMarkState, type WorldItemState } from '@game/shared/protocol';
import { getItemSpec } from '@game/shared/content';
import { clamp01, lerp, smoothstep } from '@game/shared/math';
import { animateRig, buildBlindOne, buildPlayerAvatar, createActorMaterial, type ActorRig } from './actors';
import { buildChunks, buildFixtureGeometry } from './mesher';
import { Composer, type GradeParams, type VolumetricParams } from './postfx';
import { makeNoiseTexture, makeSurfaceTextures, type SurfaceTextures } from './textures';
import { MAX_DYNAMIC_LIGHTS, createWorldMaterial } from './worldMaterial';

export interface DynamicLight {
  position: Vector3;
  direction: Vector3;
  color: Color;
  range: number;
  cosInner: number;
  cosOuter: number;
  /** Shadow march steps; 0 disables shadowing for this light. */
  shadowSteps: number;
}

export interface RenderActor {
  id: number;
  kind: number;
  flags: number;
  x: number;
  z: number;
  yaw: number;
  aiState: number;
  name?: string;
}

export interface RendererQuality {
  resolutionScale: number;
  bloom: number;
  volumetric: boolean;
  grain: number;
  fov: number;
}

interface ActorInstance {
  rig: ActorRig;
  kind: number;
  phase: number;
  lastX: number;
  lastZ: number;
  seen: boolean;
}

const FIXTURE_VERTEX = /* glsl */ `
  attribute float aRoom;
  varying float vRoom;
  varying vec3 vWorld;
  void main() {
    vRoom = aRoom;
    vec4 world = modelMatrix * vec4(position, 1.0);
    vWorld = world.xyz;
    gl_Position = projectionMatrix * viewMatrix * world;
  }
`;

const FIXTURE_FRAGMENT = /* glsl */ `
  precision highp float;
  uniform sampler2D uRoomTex;
  uniform vec3 uColor;
  uniform float uIntensity;
  uniform float uTime;
  uniform float uFlicker;
  uniform vec3 uFogColor;
  uniform float uFogDensity;
  uniform vec3 uCamPos;
  varying float vRoom;
  varying vec3 vWorld;

  void main() {
    vec4 room = texture2D(uRoomTex, vec2((vRoom + 0.5) / 256.0, 0.5));
    float dark = room.g;
    // Each room flickers on its own phase; a floor where every tube stutters in unison
    // reads as a shader bug rather than as failing hardware.
    float flick = 1.0 - uFlicker * 0.75 * step(0.55, fract(uTime * 6.7 + vRoom * 2.7));
    vec3 color = uColor * uIntensity * (1.0 - dark) * flick;
    float dist = length(vWorld - uCamPos);
    float fogFactor = exp(-pow(dist * uFogDensity, 2.0));
    gl_FragColor = vec4(mix(uFogColor, color, clamp(fogFactor, 0.0, 1.0)), 1.0);
  }
`;

/** Fallback marker colours for items that are not light sources. */
const ITEM_COLOURS: Record<string, number> = {
  almondWater: 0xe8e2c4,
  medkit: 0xd24a4a,
  flashlight: 0xb8b28e,
};

export class WorldRenderer {
  readonly renderer: WebGLRenderer;
  readonly scene = new Scene();
  readonly camera: PerspectiveCamera;

  private composer!: Composer;
  private level: Level | null = null;
  private theme: ThemeSpec = getTheme('level0');
  private palette: PaletteStop;

  private worldMaterial: ShaderMaterial | null = null;
  private fixtureMaterial: ShaderMaterial | null = null;
  private actorMaterials: ShaderMaterial[] = [];
  private objectiveMaterials: MeshBasicMaterial[] = [];

  private lightGridTexture: DataTexture | null = null;
  private lightGridData: Uint8Array = new Uint8Array(0);
  private wallMaskTexture: DataTexture | null = null;
  private roomTexture: DataTexture | null = null;
  private roomData = new Uint8Array(256 * 4);
  private noiseTexture: Texture;
  private surfaces: SurfaceTextures | null = null;

  private chunkGroup = new Group();
  private actorGroup = new Group();
  private objectiveGroup = new Group();
  private worldItemGroup = new Group();
  private markGroup = new Group();
  private actors = new Map<number, ActorInstance>();
  private objectiveMeshes = new Map<number, Mesh>();
  /** Shared per colour: a floor of forty glowsticks is still one material. */
  private worldItemMaterials = new Map<number, MeshBasicMaterial>();
  private markMaterial: MeshBasicMaterial | null = null;

  private darkRooms = new Set<number>();
  private lights: DynamicLight[] = [];

  private descent = 0;
  private sanity = 1;
  private scare = 0;
  private time = 0;
  private quality: RendererQuality;

  constructor(canvas: HTMLCanvasElement, quality: RendererQuality, preserveDrawingBuffer = false) {
    this.quality = quality;
    this.renderer = new WebGLRenderer({
      canvas,
      antialias: false,
      powerPreference: 'high-performance',
      stencil: false,
      // Only for the test harness: without it the back buffer is undefined the moment the
      // frame is handed to the compositor, and reading it back returns nothing.
      preserveDrawingBuffer,
    });
    this.renderer.outputColorSpace = SRGBColorSpace;
    this.renderer.setClearColor(0x000000, 1);

    this.camera = new PerspectiveCamera(quality.fov, 1, 0.05, 220);
    this.scene.add(this.chunkGroup, this.actorGroup, this.objectiveGroup, this.worldItemGroup, this.markGroup);
    this.noiseTexture = makeNoiseTexture(1337);
    this.palette = samplePalette(this.theme, 0);
    this.scene.fog = new FogExp2(this.palette.fog, this.palette.fogDensity);
  }

  // ---------------------------------------------------------------------------
  // Level loading
  // ---------------------------------------------------------------------------

  loadLevel(level: Level): void {
    this.disposeLevel();
    this.level = level;
    this.theme = getTheme(level.themeId);
    this.darkRooms.clear();

    const grid = levelGrid(level);

    this.lightGridData = packLightTexture(propagateLight(grid, level.fixtures));
    this.lightGridTexture = makeRedTexture(this.lightGridData, level.width, level.height, LinearFilter);
    this.wallMaskTexture = makeRedTexture(packWallTexture(grid), level.width, level.height, NearestFilter);

    this.roomData = new Uint8Array(256 * 4);
    this.roomTexture = new DataTexture(this.roomData, 256, 1, RGBAFormat, UnsignedByteType);
    this.roomTexture.magFilter = NearestFilter;
    this.roomTexture.minFilter = NearestFilter;
    this.roomTexture.wrapS = ClampToEdgeWrapping;
    this.roomTexture.wrapT = ClampToEdgeWrapping;
    this.roomTexture.needsUpdate = true;

    const anisotropy = Math.min(8, this.renderer.capabilities.getMaxAnisotropy());
    this.surfaces = makeSurfaceTextures(level.layoutHash, anisotropy);

    this.worldMaterial = createWorldMaterial({
      wall: this.surfaces.wall,
      carpet: this.surfaces.carpet,
      ceiling: this.surfaces.ceiling,
      lightGrid: this.lightGridTexture,
      wallMask: this.wallMaskTexture,
      roomRot: this.roomTexture,
      gridWidth: level.width,
      gridHeight: level.height,
      tileSize: TILE_SIZE,
    });

    for (const chunk of buildChunks(level)) {
      const mesh = new Mesh(chunk.geometry, this.worldMaterial);
      mesh.matrixAutoUpdate = false;
      this.chunkGroup.add(mesh);
    }

    const fixtures = buildFixtureGeometry(level);
    this.fixtureMaterial = new ShaderMaterial({
      vertexShader: FIXTURE_VERTEX,
      fragmentShader: FIXTURE_FRAGMENT,
      uniforms: {
        uRoomTex: { value: this.roomTexture },
        uColor: { value: new Color(this.palette.lightColor) },
        uIntensity: { value: 2.4 },
        uTime: { value: 0 },
        uFlicker: { value: 0 },
        uFogColor: { value: new Color(this.palette.fog) },
        uFogDensity: { value: this.palette.fogDensity },
        uCamPos: { value: new Vector3() },
      },
    });
    const fixtureMesh = new Mesh(fixtures.geometry, this.fixtureMaterial);
    fixtureMesh.frustumCulled = false;
    this.chunkGroup.add(fixtureMesh);

    this.buildObjectives(level);
    this.setupComposer();
    this.setDescent(0);
  }

  private buildObjectives(level: Level): void {
    // Objectives are unlit and emissive on purpose. In a level that goes pitch black, a
    // fuse you cannot find is not a challenge, it is a dead run.
    const makeMarker = (color: number, size: [number, number, number], height: number): Mesh => {
      const material = new MeshBasicMaterial({ color, fog: true, toneMapped: false });
      this.objectiveMaterials.push(material);
      const mesh = new Mesh(new BoxGeometry(size[0], size[1], size[2]), material);
      mesh.position.y = height;
      return mesh;
    };

    for (const placement of level.objectives) {
      const world = tileToWorld(level, placement.x, placement.y);
      const group = new Group();
      group.position.set(world.x, 0, world.z);

      if (placement.kind === 'fuse') {
        const body = makeMarker(0xffd24a, [0.22, 0.42, 0.22], 0.28);
        group.add(body);
        const base = new Mesh(new BoxGeometry(0.4, 0.06, 0.4), new MeshBasicMaterial({ color: 0x3a2f16, fog: true }));
        base.position.y = 0.03;
        group.add(base);
      } else if (placement.kind === 'generator') {
        const cabinet = new Mesh(
          new BoxGeometry(1.1, 1.5, 0.7),
          new MeshBasicMaterial({ color: 0x2b2f33, fog: true }),
        );
        cabinet.position.y = 0.75;
        group.add(cabinet);
        const panel = makeMarker(0x8a2b1e, [0.5, 0.28, 0.05], 1.1);
        panel.position.z = 0.38;
        group.add(panel);
      } else {
        const frame = new Mesh(
          new BoxGeometry(1.6, 2.4, 0.25),
          new MeshBasicMaterial({ color: 0x1b1d1f, fog: true }),
        );
        frame.position.y = 1.2;
        group.add(frame);
        const sign = makeMarker(0x7a2b2b, [1.0, 0.22, 0.08], 2.25);
        sign.position.z = 0.18;
        group.add(sign);
      }

      this.objectiveGroup.add(group);
      this.objectiveMeshes.set(placement.id, group as unknown as Mesh);
    }
  }

  private setupComposer(): void {
    if (!this.level || !this.wallMaskTexture) return;
    this.composer?.dispose();
    this.composer = new Composer(
      this.renderer,
      this.noiseTexture,
      this.wallMaskTexture,
      new Vector2(this.level.width, this.level.height),
      TILE_SIZE,
    );
    this.resize();
  }

  // ---------------------------------------------------------------------------
  // Descent-driven look
  // ---------------------------------------------------------------------------

  setDescent(descent: number): void {
    this.descent = clamp01(descent);
    this.palette = samplePalette(this.theme, this.descent);
    const p = this.palette;

    const fog = this.scene.fog as FogExp2;
    fog.color.setHex(p.fog);
    fog.density = p.fogDensity;

    if (this.worldMaterial) {
      const u = this.worldMaterial.uniforms;
      (u.uWallA.value as Color).setHex(p.wallA);
      (u.uWallB.value as Color).setHex(p.wallB);
      (u.uCarpetCol.value as Color).setHex(p.carpet);
      (u.uCeilCol.value as Color).setHex(p.ceiling);
      (u.uTrimCol.value as Color).setHex(p.trim);
      (u.uAmbient.value as Color).setHex(p.ambient);
      (u.uStaticLightCol.value as Color).setHex(p.lightColor);
      u.uStaticLightIntensity.value = p.lightIntensity;
      u.uRainbow.value = 1 - p.rot;
      u.uRot.value = p.rot;
      u.uSaturation.value = p.saturation;
      (u.uFogColor.value as Color).setHex(p.fog);
      u.uFogDensity.value = p.fogDensity;
      // Flicker ramps in through the middle of the descent and stops once the tubes are
      // simply dead. It is also the main source of flashing, so it is the first thing the
      // accessibility toggle silences.
      u.uFlicker.value = smoothstep(0.22, 0.55, this.descent) * (1 - smoothstep(0.8, 0.98, this.descent));
    }

    if (this.fixtureMaterial) {
      const u = this.fixtureMaterial.uniforms;
      (u.uColor.value as Color).setHex(p.lightColor);
      u.uIntensity.value = 1.2 + p.lightIntensity * 1.6;
      (u.uFogColor.value as Color).setHex(p.fog);
      u.uFogDensity.value = p.fogDensity;
      u.uFlicker.value = this.worldMaterial ? this.worldMaterial.uniforms.uFlicker.value : 0;
    }

    for (const material of this.actorMaterials) {
      const u = material.uniforms;
      (u.uAmbient.value as Color).setHex(p.ambient);
      (u.uStaticLightCol.value as Color).setHex(p.lightColor);
      u.uStaticLightIntensity.value = p.lightIntensity;
      (u.uFogColor.value as Color).setHex(p.fog);
      u.uFogDensity.value = p.fogDensity;
    }
  }

  /** Disables the flicker used to signal decay, for photosensitivity. */
  setFlashReduction(reduce: boolean): void {
    if (reduce && this.worldMaterial) this.worldMaterial.uniforms.uFlicker.value = 0;
    if (reduce && this.fixtureMaterial) this.fixtureMaterial.uniforms.uFlicker.value = 0;
  }

  setSanity(sanity01: number): void {
    this.sanity = clamp01(sanity01);
  }

  /** Impulse in [0, 1]; decays over the following moments. */
  pulseScare(intensity: number): void {
    this.scare = Math.max(this.scare, clamp01(intensity));
  }

  applyLightsOut(rooms: number[]): void {
    if (!this.level) return;
    for (const room of rooms) {
      this.darkRooms.add(room);
      if (room < 256) this.roomData[room * 4 + 1] = 255;
    }
    if (this.roomTexture) this.roomTexture.needsUpdate = true;
    // Only the affected regions change, but the propagation is a few milliseconds over the
    // whole grid, so recomputing wholesale is simpler and still imperceptible.
    const field = propagateLight(levelGrid(this.level), this.level.fixtures, this.darkRooms);
    if (this.lightGridTexture) {
      this.lightGridData.set(packLightTexture(field));
      this.lightGridTexture.needsUpdate = true;
    }
  }

  applyThemeShift(rooms: number[], stage: number): void {
    const value = Math.min(255, Math.round((stage / 3) * 255));
    for (const room of rooms) {
      if (room < 256) this.roomData[room * 4] = Math.max(this.roomData[room * 4], value);
    }
    if (this.roomTexture) this.roomTexture.needsUpdate = true;
  }

  applySeal(doorX: number, doorZ: number): void {
    // A sealed doorway gets a slab dropped into it. The geometry is tiny and pre-built at
    // the moment it is needed, never re-meshed from the tile grid mid-run.
    const slab = new Mesh(
      new BoxGeometry(TILE_SIZE, CEILING_HEIGHT, TILE_SIZE),
      new MeshBasicMaterial({ color: 0x121110, fog: true }),
    );
    slab.position.set(doorX, CEILING_HEIGHT / 2, doorZ);
    this.chunkGroup.add(slab);
  }

  // ---------------------------------------------------------------------------
  // Actors and lights
  // ---------------------------------------------------------------------------

  private actorMaterialFor(kind: number): ShaderMaterial {
    if (!this.level || !this.lightGridTexture || !this.wallMaskTexture) throw new Error('level not loaded');
    const isMonster = kind !== EntityKind.Player;
    const material = createActorMaterial({
      lightGrid: this.lightGridTexture,
      wallMask: this.wallMaskTexture,
      gridWidth: this.level.width,
      gridHeight: this.level.height,
      tileSize: TILE_SIZE,
      baseColor: isMonster ? 0xb9b3a4 : 0x7d8ea3,
      rimColor: isMonster ? 0xd8cfc0 : 0x9fb4cc,
      rimStrength: isMonster ? 0.16 : 0.1,
    });
    this.actorMaterials.push(material);
    return material;
  }

  updateActors(actors: RenderActor[], dt: number): void {
    for (const instance of this.actors.values()) instance.seen = false;

    for (const actor of actors) {
      // The local player's own body is never drawn; the camera is inside it.
      if ((actor.flags & EntityFlags.Local) !== 0) continue;

      let instance = this.actors.get(actor.id);
      if (!instance) {
        const material = this.actorMaterialFor(actor.kind);
        const rig = actor.kind === EntityKind.Player ? buildPlayerAvatar(material) : buildBlindOne(material);
        this.actorGroup.add(rig.group);
        instance = { rig, kind: actor.kind, phase: 0, lastX: actor.x, lastZ: actor.z, seen: true };
        this.actors.set(actor.id, instance);
      }
      instance.seen = true;

      const dx = actor.x - instance.lastX;
      const dz = actor.z - instance.lastZ;
      const travelled = Math.sqrt(dx * dx + dz * dz);
      const speed = dt > 0 ? travelled / dt : 0;
      instance.phase += travelled * 2.6;
      instance.lastX = actor.x;
      instance.lastZ = actor.z;

      instance.rig.group.position.set(actor.x, 0, actor.z);
      // Simulation yaw has forward at (sin, -cos); three's Y rotation runs the other way,
      // so negating is the whole conversion — for players and monsters alike.
      instance.rig.group.rotation.y = -actor.yaw;
      instance.rig.group.visible = (actor.flags & EntityFlags.Alive) !== 0;
      animateRig(instance.rig, instance.phase, speed, actor.aiState, (actor.flags & EntityFlags.Crouching) !== 0);
    }

    for (const [id, instance] of this.actors) {
      if (instance.seen) continue;
      this.actorGroup.remove(instance.rig.group);
      this.actors.delete(id);
    }
  }

  setDynamicLights(lights: DynamicLight[]): void {
    this.lights = lights.slice(0, MAX_DYNAMIC_LIGHTS);
    for (const material of [this.worldMaterial, ...this.actorMaterials]) {
      if (!material) continue;
      const u = material.uniforms;
      u.uLightCount.value = this.lights.length;
      const positions = u.uLightPos.value as Vector3[];
      const directions = u.uLightDir.value as Vector3[];
      const colors = u.uLightCol.value as Vector3[];
      const params = u.uLightParam.value as Vector4[];
      this.lights.forEach((light, i) => {
        positions[i].copy(light.position);
        directions[i].copy(light.direction);
        colors[i].set(light.color.r, light.color.g, light.color.b);
        params[i].set(light.range, light.cosInner, light.cosOuter, light.shadowSteps);
      });
    }
  }

  /**
   * Rebuilds the props lying on the floor. Called only when the set actually changes, which
   * is what buys them out of the twenty-times-a-second snapshot in the first place.
   */
  setWorldItems(items: WorldItemState[]): void {
    for (const child of [...this.worldItemGroup.children]) {
      this.worldItemGroup.remove(child);
      if (child instanceof Mesh) child.geometry.dispose();
    }

    for (const world of items) {
      const spec = getItemSpec(world.item);
      if (!spec) continue;

      // Unlit and emissive, like the objectives: a level that goes pitch black must not
      // swallow the medkit you dropped two rooms back.
      const colour =
        world.lit && spec.lightColor
          ? new Color(spec.lightColor[0], spec.lightColor[1], spec.lightColor[2]).getHex()
          : ITEM_COLOURS[spec.id] ?? 0xbfb69a;
      const material = this.worldItemMaterials.get(colour) ?? new MeshBasicMaterial({
        color: colour,
        fog: true,
        toneMapped: false,
      });
      this.worldItemMaterials.set(colour, material);

      const mesh = new Mesh(new BoxGeometry(0.22, 0.1, 0.22), material);
      mesh.position.set(world.x, 0.08, world.z);
      // A deterministic tilt from the id, so a floor of dropped items does not read as a
      // grid of identical cubes. Decoration only — nothing here reaches the simulation.
      mesh.rotation.y = (world.id % 16) * 0.39;
      this.worldItemGroup.add(mesh);
    }
  }

  /**
   * Draws the chalk. Marks are append-only for a whole run, so this rebuilds from the full
   * list rather than diffing — a few hundred quads is nothing, and there is no lifecycle to
   * get wrong.
   */
  setMarks(marks: ChalkMarkState[]): void {
    for (const child of [...this.markGroup.children]) {
      this.markGroup.remove(child);
      if (child instanceof Mesh) child.geometry.dispose();
    }
    if (marks.length === 0) return;

    this.markMaterial ??= new MeshBasicMaterial({ color: 0xd8d2c0, fog: true, toneMapped: false });

    for (const mark of marks) {
      const mesh = new Mesh(new BoxGeometry(0.34, 0.34, 0.02), this.markMaterial);
      mesh.position.set(mark.x, 1.35, mark.z);
      mesh.rotation.y = mark.yaw;
      // A deterministic tilt from the id so a corridor of marks looks scrawled by hand
      // rather than printed. Decoration only.
      mesh.rotation.z = ((mark.id % 7) - 3) * 0.12;
      this.markGroup.add(mesh);
    }
  }

  setObjectiveVisible(id: number, visible: boolean): void {
    const mesh = this.objectiveMeshes.get(id);
    if (mesh) mesh.visible = visible;
  }

  setExitPowered(id: number, powered: boolean): void {
    const group = this.objectiveMeshes.get(id);
    if (!group) return;
    group.traverse((child) => {
      if (child instanceof Mesh && child.material instanceof MeshBasicMaterial) {
        if (child.material.color.getHex() === 0x7a2b2b || child.material.color.getHex() === 0x3fd07a) {
          child.material.color.setHex(powered ? 0x3fd07a : 0x7a2b2b);
        }
      }
    });
  }

  // ---------------------------------------------------------------------------
  // Frame
  // ---------------------------------------------------------------------------

  setQuality(quality: RendererQuality): void {
    this.quality = quality;
    this.camera.fov = quality.fov;
    this.camera.updateProjectionMatrix();
    this.resize();
  }

  resize(): void {
    const canvas = this.renderer.domElement;
    const width = Math.max(1, canvas.clientWidth || canvas.width);
    const height = Math.max(1, canvas.clientHeight || canvas.height);
    const scale = Math.min(2, Math.max(0.4, this.quality.resolutionScale));
    const pixelRatio = Math.min(window.devicePixelRatio ?? 1, 2) * scale;

    this.renderer.setPixelRatio(1);
    this.renderer.setSize(width, height, false);
    this.camera.aspect = width / height;
    this.camera.updateProjectionMatrix();
    this.composer?.setSize(width * pixelRatio, height * pixelRatio);
  }

  render(dt: number): void {
    if (!this.composer || !this.worldMaterial) return;
    this.time += dt;

    // Scares fade out over roughly a third of a second; anything longer stops reading as a
    // shock and starts reading as a broken frame.
    this.scare = Math.max(0, this.scare - dt * 3.2);

    const camPos = this.camera.position;
    (this.worldMaterial.uniforms.uCamPos.value as Vector3).copy(camPos);
    this.worldMaterial.uniforms.uTime.value = this.time;
    if (this.fixtureMaterial) {
      (this.fixtureMaterial.uniforms.uCamPos.value as Vector3).copy(camPos);
      this.fixtureMaterial.uniforms.uTime.value = this.time;
    }
    for (const material of this.actorMaterials) {
      (material.uniforms.uCamPos.value as Vector3).copy(camPos);
    }

    const p = this.palette;
    const grade: GradeParams = {
      exposure: lerp(0.5, 1.35, this.descent),
      bloom: p.bloom * this.quality.bloom,
      vignette: p.vignette,
      grain: p.grain * this.quality.grain,
      aberration: p.aberration,
      sanity: this.sanity,
      scare: this.scare,
      vhs: smoothstep(0.75, 1, this.descent),
    };

    const flashlight = this.lights[0];
    const volumetric: VolumetricParams = {
      enabled: this.quality.volumetric && flashlight !== undefined,
      position: flashlight?.position ?? new Vector3(),
      direction: flashlight?.direction ?? new Vector3(0, 0, -1),
      color: flashlight?.color ?? new Color(0xffffff),
      range: flashlight?.range ?? 1,
      cosInner: flashlight?.cosInner ?? 1,
      cosOuter: flashlight?.cosOuter ?? 1,
      // Thicker air as the level rots, so the beam gets more visible as it gets less useful.
      // The scale is small because the raymarch accumulates over the step length: sixteen
      // steps across twenty-odd metres sum to several units before this multiplier.
      strength: lerp(0.009, 0.028, this.descent),
    };

    this.composer.renderScene(this.scene, this.camera);
    this.composer.present(this.camera, this.time, grade, volumetric);
  }

  /**
   * Luminance statistics of the frame just presented, for the headless smoke test.
   *
   * CI asserts liveness rather than pixel equality: software rendering drifts between
   * Chromium versions, so a screenshot comparison would be permanently flaky, while "the
   * frame has a real range of brightness" catches black screens, failed shader compiles
   * and NaN geometry — which is nearly every way this can actually break.
   */
  readPixelStats(): { mean: number; buckets: number; samples: number } {
    const gl = this.renderer.getContext();
    const canvas = this.renderer.domElement;
    const width = canvas.width;
    const height = canvas.height;
    if (width === 0 || height === 0) return { mean: 0, buckets: 0, samples: 0 };

    const pixels = new Uint8Array(width * height * 4);
    gl.readPixels(0, 0, width, height, gl.RGBA, gl.UNSIGNED_BYTE, pixels);

    const histogram = new Uint32Array(32);
    let total = 0;
    let samples = 0;
    // Every fourth pixel is plenty for a histogram and keeps this cheap under SwiftShader.
    for (let i = 0; i < pixels.length; i += 16) {
      const luma = (pixels[i] * 0.2126 + pixels[i + 1] * 0.7152 + pixels[i + 2] * 0.0722) / 255;
      total += luma;
      histogram[Math.min(31, Math.floor(luma * 32))]++;
      samples++;
    }

    let buckets = 0;
    for (const count of histogram) if (count > samples * 0.0005) buckets++;
    return { mean: samples > 0 ? total / samples : 0, buckets, samples };
  }

  get drawCalls(): number {
    return this.composer?.sceneDrawCalls ?? 0;
  }

  get triangles(): number {
    return this.composer?.sceneTriangles ?? 0;
  }

  private disposeLevel(): void {
    for (const child of [...this.chunkGroup.children]) {
      this.chunkGroup.remove(child);
      if (child instanceof Mesh) child.geometry.dispose();
    }
    for (const child of [...this.objectiveGroup.children]) this.objectiveGroup.remove(child);
    for (const child of [...this.worldItemGroup.children]) {
      this.worldItemGroup.remove(child);
      if (child instanceof Mesh) child.geometry.dispose();
    }
    for (const child of [...this.markGroup.children]) {
      this.markGroup.remove(child);
      if (child instanceof Mesh) child.geometry.dispose();
    }
    for (const instance of this.actors.values()) this.actorGroup.remove(instance.rig.group);
    this.actors.clear();
    this.objectiveMeshes.clear();

    this.worldMaterial?.dispose();
    this.fixtureMaterial?.dispose();
    for (const material of this.actorMaterials) material.dispose();
    for (const material of this.objectiveMaterials) material.dispose();
    for (const material of this.worldItemMaterials.values()) material.dispose();
    this.worldItemMaterials.clear();
    this.markMaterial?.dispose();
    this.markMaterial = null;
    this.actorMaterials = [];
    this.objectiveMaterials = [];
    this.worldMaterial = null;
    this.fixtureMaterial = null;

    this.lightGridTexture?.dispose();
    this.wallMaskTexture?.dispose();
    this.roomTexture?.dispose();
    this.surfaces?.wall.dispose();
    this.surfaces?.carpet.dispose();
    this.surfaces?.ceiling.dispose();
  }

  dispose(): void {
    this.disposeLevel();
    this.composer?.dispose();
    this.renderer.dispose();
  }
}

function makeRedTexture(
  data: Uint8Array,
  width: number,
  height: number,
  filter: typeof LinearFilter | typeof NearestFilter,
): DataTexture {
  const texture = new DataTexture(data, width, height, RedFormat, UnsignedByteType);
  texture.magFilter = filter;
  texture.minFilter = filter;
  texture.wrapS = ClampToEdgeWrapping;
  texture.wrapT = ClampToEdgeWrapping;
  texture.needsUpdate = true;
  return texture;
}
