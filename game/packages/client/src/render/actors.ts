/**
 * Actors: remote players and monsters, built from primitives and animated by hand.
 *
 * With no art pipeline, the Blind One has to work through silhouette and proportion
 * alone — far too tall, limbs far too long, no face at all. It is lit by the same light
 * grid and the same flashlight cone as the world, which matters: the moment your beam
 * finds it is supposed to be the moment you see it, not a second later.
 */

import {
  BoxGeometry,
  CapsuleGeometry,
  Color,
  Group,
  Mesh,
  ShaderMaterial,
  Texture,
  Vector2,
  Vector3,
  Vector4,
} from 'three';
import { AiState } from '@game/shared/protocol';
import { MAX_DYNAMIC_LIGHTS } from './worldMaterial';

const ACTOR_VERTEX = /* glsl */ `
  varying vec3 vWorld;
  varying vec3 vNormal;
  varying vec3 vViewDir;
  void main() {
    vec4 world = modelMatrix * vec4(position, 1.0);
    vWorld = world.xyz;
    vNormal = normalize(mat3(modelMatrix) * normal);
    vViewDir = normalize(cameraPosition - world.xyz);
    gl_Position = projectionMatrix * viewMatrix * world;
  }
`;

const ACTOR_FRAGMENT = /* glsl */ `
  #define MAX_LIGHTS ${MAX_DYNAMIC_LIGHTS}
  precision highp float;

  uniform sampler2D uLightGrid;
  uniform sampler2D uWallMask;
  uniform vec2 uGridSize;
  uniform float uTileSize;

  uniform vec3 uBaseColor;
  uniform vec3 uRimColor;
  uniform float uRimStrength;
  uniform vec3 uAmbient;
  uniform vec3 uStaticLightCol;
  uniform float uStaticLightIntensity;

  uniform vec3 uFogColor;
  uniform float uFogDensity;
  uniform vec3 uCamPos;

  uniform int uLightCount;
  uniform vec3 uLightPos[MAX_LIGHTS];
  uniform vec3 uLightDir[MAX_LIGHTS];
  uniform vec3 uLightCol[MAX_LIGHTS];
  uniform vec4 uLightParam[MAX_LIGHTS];

  varying vec3 vWorld;
  varying vec3 vNormal;
  varying vec3 vViewDir;

  float lightVisibility(vec3 from, vec3 to, float steps) {
    vec2 a = from.xz / uTileSize + uGridSize * 0.5;
    vec2 b = to.xz / uTileSize + uGridSize * 0.5;
    vec2 delta = b - a;
    float n = max(steps, 1.0);
    for (int i = 1; i < 32; i++) {
      if (float(i) >= n) break;
      vec2 p = a + delta * (float(i) / n);
      if (texture2D(uWallMask, p / uGridSize).r > 0.5) return 0.0;
    }
    return 1.0;
  }

  void main() {
    vec2 gridUv = (vWorld.xz / uTileSize + uGridSize * 0.5) / uGridSize;
    float grid = texture2D(uLightGrid, gridUv).r;
    vec3 lighting = uStaticLightCol * grid * uStaticLightIntensity * (0.6 + 0.4 * max(vNormal.y, 0.0));

    for (int i = 0; i < MAX_LIGHTS; i++) {
      if (i >= uLightCount) break;
      vec3 toLight = uLightPos[i] - vWorld;
      float dist = length(toLight);
      float range = uLightParam[i].x;
      if (dist > range) continue;
      vec3 L = toLight / max(dist, 0.0001);
      float ndotl = max(dot(vNormal, L), 0.0);
      if (ndotl <= 0.0) continue;
      float attenuation = 1.0 - dist / range;
      attenuation *= attenuation;
      float cone = 1.0;
      if (uLightParam[i].y < 0.999) {
        cone = smoothstep(uLightParam[i].z, uLightParam[i].y, dot(-L, normalize(uLightDir[i])));
        if (cone <= 0.0) continue;
      }
      float shadow = lightVisibility(vWorld + vNormal * 0.2, uLightPos[i], uLightParam[i].w);
      lighting += uLightCol[i] * ndotl * attenuation * cone * shadow;
    }

    vec3 color = uBaseColor * (lighting + uAmbient);

    // A faint rim keeps the silhouette readable in near-darkness without ever making the
    // creature comfortable to look at.
    float rim = pow(1.0 - max(dot(normalize(vNormal), normalize(vViewDir)), 0.0), 2.5);
    color += uRimColor * rim * uRimStrength;

    float dist = length(vWorld - uCamPos);
    float fogFactor = exp(-pow(dist * uFogDensity, 2.0));
    color = mix(uFogColor, color, clamp(fogFactor, 0.0, 1.0));

    gl_FragColor = vec4(color, 1.0);
  }
`;

export interface ActorMaterialOptions {
  lightGrid: Texture;
  wallMask: Texture;
  gridWidth: number;
  gridHeight: number;
  tileSize: number;
  baseColor: number;
  rimColor: number;
  rimStrength: number;
}

export function createActorMaterial(options: ActorMaterialOptions): ShaderMaterial {
  return new ShaderMaterial({
    vertexShader: ACTOR_VERTEX,
    fragmentShader: ACTOR_FRAGMENT,
    uniforms: {
      uLightGrid: { value: options.lightGrid },
      uWallMask: { value: options.wallMask },
      uGridSize: { value: new Vector2(options.gridWidth, options.gridHeight) },
      uTileSize: { value: options.tileSize },
      uBaseColor: { value: new Color(options.baseColor) },
      uRimColor: { value: new Color(options.rimColor) },
      uRimStrength: { value: options.rimStrength },
      uAmbient: { value: new Color(0x16140f) },
      uStaticLightCol: { value: new Color(0xfff3d0) },
      uStaticLightIntensity: { value: 1 },
      uFogColor: { value: new Color(0xfff0cf) },
      uFogDensity: { value: 0.012 },
      uCamPos: { value: new Vector3() },
      uLightCount: { value: 0 },
      uLightPos: { value: Array.from({ length: MAX_DYNAMIC_LIGHTS }, () => new Vector3()) },
      uLightDir: { value: Array.from({ length: MAX_DYNAMIC_LIGHTS }, () => new Vector3(0, 0, -1)) },
      uLightCol: { value: Array.from({ length: MAX_DYNAMIC_LIGHTS }, () => new Vector3()) },
      uLightParam: { value: Array.from({ length: MAX_DYNAMIC_LIGHTS }, () => new Vector4(1, 1, 1, 0)) },
    },
  });
}

/** Limb references kept so the actor can be animated without a skeleton. */
export interface ActorRig {
  group: Group;
  leftArm: Mesh;
  rightArm: Mesh;
  leftLeg: Mesh;
  rightLeg: Mesh;
  head: Mesh;
  torso: Mesh;
}

function limb(width: number, height: number, depth: number, material: ShaderMaterial, pivotAtTop: boolean): Mesh {
  const geometry = new BoxGeometry(width, height, depth);
  // Translate so the mesh rotates around its shoulder/hip rather than its centre.
  geometry.translate(0, pivotAtTop ? -height / 2 : 0, 0);
  return new Mesh(geometry, material);
}

/**
 * The Blind One. Two and a half metres, hunched, with arms that reach past its knees. It
 * has no eyes because it does not need them, and that absence is the entire tell.
 */
export function buildBlindOne(material: ShaderMaterial): ActorRig {
  const group = new Group();

  const torso = new Mesh(new CapsuleGeometry(0.22, 0.85, 4, 10), material);
  torso.position.y = 1.45;
  group.add(torso);

  const head = new Mesh(new BoxGeometry(0.24, 0.42, 0.26), material);
  head.position.y = 2.12;
  // Tilted forward and down, listening rather than looking.
  head.rotation.x = 0.42;
  group.add(head);

  const leftArm = limb(0.11, 1.05, 0.11, material, true);
  leftArm.position.set(-0.28, 1.86, 0);
  group.add(leftArm);

  const rightArm = limb(0.11, 1.05, 0.11, material, true);
  rightArm.position.set(0.28, 1.86, 0);
  group.add(rightArm);

  const leftLeg = limb(0.13, 1.0, 0.13, material, true);
  leftLeg.position.set(-0.13, 1.0, 0);
  group.add(leftLeg);

  const rightLeg = limb(0.13, 1.0, 0.13, material, true);
  rightLeg.position.set(0.13, 1.0, 0);
  group.add(rightLeg);

  return { group, leftArm, rightArm, leftLeg, rightLeg, head, torso };
}

/**
 * The Smiler. Short, wide, and all face — the silhouette has to read as *grinning* at the
 * far end of a corridor with no light on it, because that is the moment it matters.
 */
export function buildSmiler(material: ShaderMaterial): ActorRig {
  const group = new Group();

  const torso = new Mesh(new CapsuleGeometry(0.3, 0.45, 4, 10), material);
  torso.position.y = 0.95;
  group.add(torso);

  // Oversized and level, unlike the Blind One's downturned listening posture. This one is
  // looking straight at you, and the head is the whole tell.
  const head = new Mesh(new BoxGeometry(0.52, 0.34, 0.3), material);
  head.position.y = 1.5;
  group.add(head);

  const grin = new Mesh(new BoxGeometry(0.44, 0.09, 0.06), material);
  grin.position.set(0, 1.44, -0.16);
  group.add(grin);

  const leftArm = limb(0.1, 0.55, 0.1, material, true);
  leftArm.position.set(-0.34, 1.2, 0);
  group.add(leftArm);

  const rightArm = limb(0.1, 0.55, 0.1, material, true);
  rightArm.position.set(0.34, 1.2, 0);
  group.add(rightArm);

  const leftLeg = limb(0.13, 0.68, 0.13, material, true);
  leftLeg.position.set(-0.14, 0.68, 0);
  group.add(leftLeg);

  const rightLeg = limb(0.13, 0.68, 0.13, material, true);
  rightLeg.position.set(0.14, 0.68, 0);
  group.add(rightLeg);

  return { group, leftArm, rightArm, leftLeg, rightLeg, head, torso };
}

/**
 * The Watcher. Tall, thin and armless, so that the only thing it can be doing is standing
 * there — which is exactly what it will be doing every single time you look at it.
 */
export function buildWatcher(material: ShaderMaterial): ActorRig {
  const group = new Group();

  const torso = new Mesh(new CapsuleGeometry(0.16, 1.35, 4, 10), material);
  torso.position.y = 1.5;
  group.add(torso);

  const head = new Mesh(new BoxGeometry(0.2, 0.5, 0.2), material);
  head.position.y = 2.42;
  group.add(head);

  // Vestigial arms held flat against the body. The rig contract needs four limbs; the
  // silhouette needs them to be almost invisible.
  const leftArm = limb(0.06, 0.9, 0.06, material, true);
  leftArm.position.set(-0.18, 1.85, 0);
  group.add(leftArm);

  const rightArm = limb(0.06, 0.9, 0.06, material, true);
  rightArm.position.set(0.18, 1.85, 0);
  group.add(rightArm);

  const leftLeg = limb(0.09, 0.82, 0.09, material, true);
  leftLeg.position.set(-0.1, 0.82, 0);
  group.add(leftLeg);

  const rightLeg = limb(0.09, 0.82, 0.09, material, true);
  rightLeg.position.set(0.1, 0.82, 0);
  group.add(rightLeg);

  return { group, leftArm, rightArm, leftLeg, rightLeg, head, torso };
}

/** A remote player: readable at a distance, deliberately unremarkable. */
export function buildPlayerAvatar(material: ShaderMaterial): ActorRig {
  const group = new Group();

  const torso = new Mesh(new CapsuleGeometry(0.26, 0.62, 4, 10), material);
  torso.position.y = 1.05;
  group.add(torso);

  const head = new Mesh(new BoxGeometry(0.26, 0.28, 0.26), material);
  head.position.y = 1.62;
  group.add(head);

  const leftArm = limb(0.12, 0.6, 0.12, material, true);
  leftArm.position.set(-0.32, 1.34, 0);
  group.add(leftArm);

  const rightArm = limb(0.12, 0.6, 0.12, material, true);
  rightArm.position.set(0.32, 1.34, 0);
  group.add(rightArm);

  const leftLeg = limb(0.14, 0.72, 0.14, material, true);
  leftLeg.position.set(-0.13, 0.72, 0);
  group.add(leftLeg);

  const rightLeg = limb(0.14, 0.72, 0.14, material, true);
  rightLeg.position.set(0.13, 0.72, 0);
  group.add(rightLeg);

  return { group, leftArm, rightArm, leftLeg, rightLeg, head, torso };
}

/**
 * Walk cycle driven by distance travelled rather than by time, so an actor's legs always
 * match its speed and never skate.
 */
export function animateRig(rig: ActorRig, phase: number, speed: number, aiState: number, crouching: boolean): void {
  const swing = Math.min(1, speed / 4) * 0.9;
  const s = Math.sin(phase);
  const c = Math.sin(phase + Math.PI);

  rig.leftLeg.rotation.x = s * swing;
  rig.rightLeg.rotation.x = c * swing;
  rig.leftArm.rotation.x = c * swing * 0.7;
  rig.rightArm.rotation.x = s * swing * 0.7;

  if (aiState === AiState.Lunge) {
    // Both arms come up and the body coils: the mandatory warning, made visible.
    rig.leftArm.rotation.x = -2.2;
    rig.rightArm.rotation.x = -2.2;
    rig.torso.rotation.x = -0.3;
    rig.head.rotation.x = -0.2;
  } else if (aiState === AiState.Hunting) {
    rig.torso.rotation.x = 0.28;
    rig.head.rotation.x = 0.5;
  } else {
    rig.torso.rotation.x = 0.05;
    rig.head.rotation.x = 0.42;
  }

  rig.group.scale.y = crouching ? 0.68 : 1;
}
