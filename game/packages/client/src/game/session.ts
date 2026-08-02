/**
 * The in-run client: input, prediction, interpolation, and everything the HUD reads.
 *
 * Only the local player is predicted. Remote players and monsters are interpolated a
 * hundred milliseconds in the past and never extrapolated — a monster that lunges forward
 * on a guess and then snaps back destroys the tension the entire game is built on, and no
 * amount of smoothing hides it.
 */

import { Color, Vector3 } from 'three';
import {
  AiState,
  EntityFlags,
  EntityKind,
  type ObjectiveState,
  type S2C,
  rleDecode,
  type SnapshotPayload,
} from '@game/shared/protocol';
import {
  DT,
  Buttons,
  CROUCH_EYE_HEIGHT,
  EYE_HEIGHT,
  FLASHLIGHT_BATTERY_MAX,
  INTERACT_RANGE,
  SANITY_MAX,
  clonePlayerState,
  copyPlayerState,
  createPlayerState,
  stepPlayer,
  type Input,
  type PlayerSimState,
} from '@game/shared/sim';
import {
  generateLevel,
  levelGrid,
  tileToWorld,
  worldToTile,
  type Level,
} from '@game/shared/levelgen';
import { clamp, clamp01, damp, lerp } from '@game/shared/math';
import type { GridView } from '@game/shared/levelgen';
import { hash3f } from '@game/shared/prng';
import type { AudioEngine } from '../audio/engine';
import type { Connection } from '../net/connection';
import type { Settings } from '../settings';
import { WorldRenderer, type DynamicLight, type RenderActor } from '../render/world';

/** How far in the past remote actors are rendered, in milliseconds. */
const BASE_INTERP_DELAY = 100;
const MAX_INTERP_DELAY = 220;
/** Prediction error above this snaps; below it is blended away invisibly. */
const RECONCILE_SNAP = 0.9;
const ERROR_BLEND_RATE = 12;

export interface InteractPrompt {
  key: string;
  params?: Record<string, string | number>;
}

export interface HudState {
  connected: boolean;
  descent: number;
  sanity: number;
  stamina: number;
  battery: number;
  hp: number;
  fusesCollected: number;
  fusesTotal: number;
  carrying: boolean;
  exitOpen: boolean;
  downed: boolean;
  bleedout: number;
  escaped: boolean;
  dead: boolean;
  prompt: InteractPrompt | null;
  flashlightOn: boolean;
  focusBeam: boolean;
  drawCalls: number;
  fps: number;
}

interface TimedSnapshot {
  receivedAt: number;
  payload: SnapshotPayload;
}

interface PendingInput {
  input: Input;
  state: PlayerSimState;
}

export class GameSession {
  readonly renderer: WorldRenderer;
  level: Level | null = null;
  private grid: GridView | null = null;

  readonly hud: HudState = {
    connected: false,
    descent: 0,
    sanity: 1,
    stamina: 1,
    battery: 1,
    hp: 1,
    fusesCollected: 0,
    fusesTotal: 0,
    carrying: false,
    exitOpen: false,
    downed: false,
    bleedout: 0,
    escaped: false,
    dead: false,
    prompt: null,
    flashlightOn: false,
    focusBeam: false,
    drawCalls: 0,
    fps: 0,
  };

  private readonly connection: Connection;
  private readonly audio: AudioEngine;
  private settings: Settings;

  private local: PlayerSimState = createPlayerState(0, 0);
  private pending: PendingInput[] = [];
  private unsent: Input[] = [];
  private inputSeq = 1;
  private lastAckedTick = 0;
  private lastReconciledSeq = 0;
  private accumulator = 0;
  private sendAccumulator = 0;

  /** Residual prediction error, blended out rather than snapped, so the camera never jerks. */
  private errorX = 0;
  private errorZ = 0;

  private snapshots: TimedSnapshot[] = [];
  private objectives: ObjectiveState[] = [];
  private descentApplied = new Set<number>();

  private keys = new Set<string>();
  private yaw = 0;
  private pitch = 0;
  private buttons = 0;
  private edgeButtons = 0;

  private bobPhase = 0;
  private bobAmount = 0;
  private shake = 0;
  private hallucinationActor: RenderActor | null = null;
  private hallucinationUntil = 0;
  private frameTimes: number[] = [];

  private disposers: (() => void)[] = [];

  constructor(
    canvas: HTMLCanvasElement,
    connection: Connection,
    audio: AudioEngine,
    settings: Settings,
    preserveDrawingBuffer = false,
  ) {
    this.connection = connection;
    this.audio = audio;
    this.settings = settings;
    this.renderer = new WorldRenderer(
      canvas,
      {
        resolutionScale: settings.resolutionScale,
        bloom: settings.bloom,
        volumetric: settings.volumetric,
        grain: settings.grain,
        fov: settings.fov,
      },
      preserveDrawingBuffer,
    );

    this.disposers.push(connection.onMessage((msg) => this.handleMessage(msg)));
    this.disposers.push(connection.onSnapshot((snapshot) => this.handleSnapshot(snapshot)));
    this.disposers.push(connection.onStateChange((state) => (this.hud.connected = state === 'open')));
  }

  applySettings(settings: Settings): void {
    this.settings = settings;
    this.renderer.setQuality({
      resolutionScale: settings.resolutionScale,
      bloom: settings.bloom,
      volumetric: settings.volumetric,
      grain: settings.grain,
      fov: settings.fov,
    });
    this.renderer.setFlashReduction(settings.flashReduction);
  }

  // ---------------------------------------------------------------------------
  // Server messages
  // ---------------------------------------------------------------------------

  private handleMessage(msg: S2C): void {
    switch (msg.t) {
      case 'runStart': {
        // Both sides generate the level from the seed; the hash is the proof they agree.
        const level = generateLevel(msg.seed, msg.themeId);
        if (level.layoutHash !== msg.layoutHash) {
          console.warn('[net] layout hash mismatch, requesting level patch from server');
          this.connection.send({ t: 'requestLevelPatch' });
        }
        this.startRun(level, msg.spawnTile, msg.fuseTotal);
        break;
      }

      case 'levelPatch': {
        if (!this.level) break;
        // The maze the server actually simulated wins, always.
        this.level.tiles.set(rleDecode(msg.rle, msg.width * msg.height));
        this.renderer.loadLevel(this.level);
        this.grid = levelGrid(this.level);
        break;
      }

      case 'objectives':
        this.objectives = msg.objectives;
        this.refreshObjectiveVisuals();
        break;

      case 'descentEvent':
        this.applyDescentEvent(msg.index);
        break;

      case 'sound':
        this.audio.play(msg.key, msg.x, msg.z, clamp(msg.loudness / 8, 0.25, 1.4));
        break;

      case 'scare':
        this.triggerScare(msg.intensity);
        break;

      case 'playerDown':
        if (msg.playerId === this.connection.playerId) this.triggerScare(1);
        break;

      case 'runEnd':
        this.hud.escaped = msg.outcome === 'extracted';
        break;

      default:
        break;
    }
  }

  private startRun(level: Level, spawnTile: [number, number], fuseTotal: number): void {
    this.level = level;
    this.grid = levelGrid(level);
    this.renderer.loadLevel(level);

    const spawn = tileToWorld(level, spawnTile[0], spawnTile[1]);
    this.local = createPlayerState(spawn.x, spawn.z);
    this.pending = [];
    this.unsent = [];
    this.snapshots = [];
    this.descentApplied.clear();
    this.errorX = 0;
    this.errorZ = 0;
    this.lastReconciledSeq = 0;
    this.inputSeq = 1;
    this.hud.fusesTotal = fuseTotal;
    this.hud.fusesCollected = 0;
    this.hud.escaped = false;
    this.hud.dead = false;
    this.hud.descent = 0;
    this.renderer.setDescent(0);
    this.renderer.setFlashReduction(this.settings.flashReduction);
    this.audio.setDescent(0);
  }

  private applyDescentEvent(index: number): void {
    if (!this.level || this.descentApplied.has(index)) return;
    this.descentApplied.add(index);
    const event = this.level.descent[index];
    if (!event) return;

    switch (event.kind) {
      case 'lightsOut':
        this.renderer.applyLightsOut(event.rooms);
        break;
      case 'themeShift':
        this.renderer.applyThemeShift(event.rooms, event.stage);
        break;
      case 'seal': {
        const door = this.level.doors[event.door];
        if (!door) break;
        const world = tileToWorld(this.level, door.x, door.y);
        this.renderer.applySeal(world.x, world.z);
        // The collision grid must agree with what the player can see blocking the way.
        this.level.tiles[door.y * this.level.width + door.x] = 0;
        break;
      }
    }
  }

  private handleSnapshot(snapshot: SnapshotPayload): void {
    this.snapshots.push({ receivedAt: performance.now(), payload: snapshot });
    // Two seconds of history is far more than the interpolation delay ever needs.
    while (this.snapshots.length > 40) this.snapshots.shift();

    this.lastAckedTick = snapshot.tick;
    this.hud.descent = snapshot.descent;
    this.hud.sanity = snapshot.sanity / SANITY_MAX;
    this.hud.stamina = snapshot.stamina / 100;
    this.hud.battery = snapshot.battery / FLASHLIGHT_BATTERY_MAX;
    this.hud.hp = snapshot.hp / 100;

    this.renderer.setDescent(snapshot.descent);
    this.renderer.setSanity(this.hud.sanity);
    this.audio.setDescent(snapshot.descent);
    this.audio.setSanity(this.hud.sanity);

    const localRecord = snapshot.entities.find((e) => (e.flags & EntityFlags.Local) !== 0);
    if (localRecord) {
      this.hud.downed = (localRecord.flags & EntityFlags.Downed) !== 0;
      this.hud.flashlightOn = (localRecord.flags & EntityFlags.FlashlightOn) !== 0;
      this.hud.focusBeam = (localRecord.flags & EntityFlags.FocusedBeam) !== 0;
      this.hud.dead = (localRecord.flags & EntityFlags.Alive) === 0;
      this.reconcile(localRecord.x, localRecord.z, snapshot.lastProcessedInputSeq);
    }
  }

  /**
   * Rewind to the authoritative state and replay every input the server has not yet
   * consumed. Small errors are folded into a decaying offset instead of being applied to
   * the simulation, so ordinary network jitter never shows up as a twitch.
   */
  private reconcile(serverX: number, serverZ: number, lastProcessedSeq: number): void {
    if (!this.grid) return;

    // A server tick with no input available consumes nothing and re-sends the same
    // acknowledgement. Reconciling against it again would replay inputs that have already
    // been folded into the current state, advancing the player a second time — a
    // forward teleport every time the input queue runs dry for a frame.
    if (lastProcessedSeq <= this.lastReconciledSeq) return;
    this.lastReconciledSeq = lastProcessedSeq;

    const predictedX = this.local.x;
    const predictedZ = this.local.z;

    // Rewind to what we predicted at exactly this sequence number, then correct only the
    // position. The snapshot carries no velocity or stamina, and keeping the *current*
    // values while resetting the position mixes two different points in time — the replay
    // then starts from the right place at the wrong speed and can never converge.
    const acked = this.pending.findIndex((entry) => entry.input.seq === lastProcessedSeq);
    if (acked >= 0) copyPlayerState(this.pending[acked].state, this.local);

    this.pending = this.pending.filter((entry) => entry.input.seq > lastProcessedSeq);

    this.local.x = serverX;
    this.local.z = serverZ;
    for (const entry of this.pending) {
      stepPlayer(this.local, entry.input, this.grid, DT);
      copyPlayerState(this.local, entry.state);
    }

    const dx = predictedX - this.local.x;
    const dz = predictedZ - this.local.z;
    const error = Math.sqrt(dx * dx + dz * dz);
    if (error > RECONCILE_SNAP) {
      // Genuinely out of sync (teleport, long stall): take the server's word immediately.
      this.errorX = 0;
      this.errorZ = 0;
    } else {
      this.errorX = dx;
      this.errorZ = dz;
    }
  }

  // ---------------------------------------------------------------------------
  // Input
  // ---------------------------------------------------------------------------

  attachInput(canvas: HTMLCanvasElement): void {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.repeat) return;
      this.keys.add(event.code);
    };
    const onKeyUp = (event: KeyboardEvent) => this.keys.delete(event.code);
    const onBlur = () => this.keys.clear();
    const onMouseMove = (event: MouseEvent) => {
      if (document.pointerLockElement !== canvas) return;
      const scale = 0.0022 * this.settings.sensitivity;
      this.yaw += event.movementX * scale;
      this.pitch -= event.movementY * scale * (this.settings.invertY ? -1 : 1);
      this.pitch = clamp(this.pitch, -Math.PI / 2 + 0.02, Math.PI / 2 - 0.02);
    };

    window.addEventListener('keydown', onKeyDown);
    window.addEventListener('keyup', onKeyUp);
    window.addEventListener('blur', onBlur);
    window.addEventListener('mousemove', onMouseMove);

    this.disposers.push(() => {
      window.removeEventListener('keydown', onKeyDown);
      window.removeEventListener('keyup', onKeyUp);
      window.removeEventListener('blur', onBlur);
      window.removeEventListener('mousemove', onMouseMove);
    });
  }

  /** Lets the e2e harness drive the player without a real keyboard or pointer lock. */
  setSyntheticInput(buttons: number, yaw: number, pitch: number): void {
    this.edgeButtons |= buttons & ~this.buttons;
    this.buttons = buttons;
    this.yaw = yaw;
    this.pitch = pitch;
  }

  private collectButtons(): number {
    const keys = this.settings.keys;
    let mask = this.buttons;
    if (this.keys.has(keys.forward)) mask |= Buttons.Forward;
    if (this.keys.has(keys.back)) mask |= Buttons.Back;
    if (this.keys.has(keys.left)) mask |= Buttons.Left;
    if (this.keys.has(keys.right)) mask |= Buttons.Right;
    if (this.keys.has(keys.sprint)) mask |= Buttons.Sprint;
    if (this.keys.has(keys.crouch)) mask |= Buttons.Crouch;
    if (this.keys.has(keys.interact)) mask |= Buttons.Interact;
    if (this.keys.has(keys.flashlight)) mask |= Buttons.Flashlight;
    if (this.keys.has(keys.beamMode)) mask |= Buttons.BeamMode;
    return mask;
  }

  // ---------------------------------------------------------------------------
  // Frame
  // ---------------------------------------------------------------------------

  update(dt: number): void {
    if (!this.level || !this.grid) return;

    this.trackFps(dt);
    this.accumulator += dt;
    this.sendAccumulator += dt;

    let steps = 0;
    while (this.accumulator >= DT && steps < 8) {
      this.accumulator -= DT;
      steps++;

      const input: Input = {
        seq: this.inputSeq++,
        buttons: this.collectButtons() | this.edgeButtons,
        yaw: this.yaw,
        pitch: this.pitch,
        slot: 0,
      };
      this.edgeButtons = 0;

      stepPlayer(this.local, input, this.grid, DT, this.hud.downed || this.hud.dead || this.hud.escaped);
      this.pending.push({ input, state: clonePlayerState(this.local) });
      this.unsent.push(input);
      if (this.pending.length > 180) this.pending.shift();
    }

    // Inputs go out at the snapshot rate. What is sent is the tail of the *unacknowledged*
    // history rather than only the new frames: with three new inputs per packet, repeating
    // the last six means a single dropped packet is fully recovered by the next one and
    // costs the player nothing. Sending only the new frames looks like redundancy and
    // provides none.
    if (this.sendAccumulator >= 1 / 20) {
      this.sendAccumulator = 0;
      if (this.unsent.length > 0) {
        this.connection.sendInputs(
          this.pending.slice(-6).map((entry) => entry.input),
          this.lastAckedTick,
        );
        this.unsent = [];
      }
    }

    this.errorX = damp(this.errorX, 0, ERROR_BLEND_RATE, dt);
    this.errorZ = damp(this.errorZ, 0, ERROR_BLEND_RATE, dt);

    this.updateCamera(dt);
    this.updateActors(dt);
    this.updateLights();
    this.updatePrompt();
    this.updateHallucinations(dt);

    const speed = Math.sqrt(this.local.vx * this.local.vx + this.local.vz * this.local.vz);
    this.audio.updateFootsteps(speed, this.local.crouching, dt);

    this.renderer.render(dt);
    this.hud.drawCalls = this.renderer.drawCalls;
  }

  private updateCamera(dt: number): void {
    const camera = this.renderer.camera;
    const eye = this.local.crouching ? CROUCH_EYE_HEIGHT : EYE_HEIGHT;

    const speed = Math.sqrt(this.local.vx * this.local.vx + this.local.vz * this.local.vz);
    this.bobPhase += speed * dt * 2.4;
    this.bobAmount = damp(this.bobAmount, Math.min(1, speed / 5), 6, dt);

    // Headbob and shake are separate from the simulation on purpose: they are camera
    // decoration, and the accessibility toggle removes them without touching movement.
    const bobY = this.settings.screenshake ? Math.sin(this.bobPhase * 2) * 0.035 * this.bobAmount : 0;
    const bobX = this.settings.screenshake ? Math.cos(this.bobPhase) * 0.025 * this.bobAmount : 0;

    this.shake = Math.max(0, this.shake - dt * 2.5);
    const shakeAmount = this.settings.screenshake ? this.shake : 0;
    const shakeX = (Math.random() - 0.5) * shakeAmount * 0.12;
    const shakeY = (Math.random() - 0.5) * shakeAmount * 0.12;

    const downedDrop = this.hud.downed ? -0.75 : 0;

    camera.position.set(
      this.local.x + this.errorX + bobX + shakeX,
      eye + bobY + shakeY + downedDrop,
      this.local.z + this.errorZ,
    );
    camera.rotation.set(this.pitch, -this.yaw, this.hud.downed ? 0.35 : bobX * 0.4, 'YXZ');

    this.audio.setListener(camera.position.x, camera.position.y, camera.position.z, this.yaw);
    if (this.level) {
      const tile = worldToTile(this.level, this.local.x, this.local.z);
      const room = this.level.roomOf[tile.y * this.level.width + tile.x];
      if (room >= 0) {
        const kind = this.level.rooms[room].kind;
        this.audio.setZone(kind === 'atrium' ? 'hall' : kind === 'corridorBundle' ? 'corridor' : 'small');
      }
    }
  }

  /**
   * Interpolates remote actors between the two snapshots that bracket the render time.
   * The delay adapts to measured latency so a bad connection buys smoothness instead of
   * stutter.
   */
  private updateActors(dt: number): void {
    const delay = clamp(BASE_INTERP_DELAY + this.connection.rtt * 0.35, BASE_INTERP_DELAY, MAX_INTERP_DELAY);
    const renderTime = performance.now() - delay;

    let older: TimedSnapshot | null = null;
    let newer: TimedSnapshot | null = null;
    for (let i = this.snapshots.length - 1; i >= 0; i--) {
      if (this.snapshots[i].receivedAt <= renderTime) {
        older = this.snapshots[i];
        newer = this.snapshots[i + 1] ?? null;
        break;
      }
    }
    if (!older) older = this.snapshots[0] ?? null;
    if (!older) return;

    const actors: RenderActor[] = [];
    const span = newer ? newer.receivedAt - older.receivedAt : 0;
    const t = span > 0 ? clamp01((renderTime - older.receivedAt) / span) : 0;

    for (const entity of older.payload.entities) {
      const next = newer?.payload.entities.find((e) => e.id === entity.id);
      const x = next ? lerp(entity.x, next.x, t) : entity.x;
      const z = next ? lerp(entity.z, next.z, t) : entity.z;
      // Shortest-arc so an actor turning through north does not spin the long way round.
      let yaw = entity.yaw;
      if (next) {
        let delta = next.yaw - entity.yaw;
        while (delta > Math.PI) delta -= Math.PI * 2;
        while (delta < -Math.PI) delta += Math.PI * 2;
        yaw = entity.yaw + delta * t;
      }
      actors.push({ id: entity.id, kind: entity.kind, flags: entity.flags, x, z, yaw, aiState: entity.aiState });
    }

    if (this.hallucinationActor && performance.now() < this.hallucinationUntil) {
      actors.push(this.hallucinationActor);
    } else {
      this.hallucinationActor = null;
    }

    this.renderer.updateActors(actors, dt);
  }

  private updateLights(): void {
    const lights: DynamicLight[] = [];
    const camera = this.renderer.camera;
    const forward = new Vector3(0, 0, -1).applyQuaternion(camera.quaternion);

    if (this.hud.flashlightOn) {
      lights.push({
        position: camera.position.clone(),
        direction: forward.clone(),
        // Deliberately dimmer than the ceiling lights. In a lit room the flashlight should
        // be a suggestion; in a dark one it is the only thing there is, and that contrast
        // does the work rather than raw intensity.
        color: new Color(0.78, 0.71, 0.58),
        range: this.hud.focusBeam ? 30 : 19,
        // Roughly 18 degrees of hot spot and 30 to the edge on the wide setting, 8 and 15
        // focused. A wider cone than this stops reading as a torch and starts reading as
        // fog lights, because at a 78-degree field of view it simply fills the screen.
        cosInner: this.hud.focusBeam ? 0.99 : 0.95,
        cosOuter: this.hud.focusBeam ? 0.965 : 0.86,
        shadowSteps: 22,
      });
    }

    // Teammates' flashlights are real lights too — seeing a beam sweep a wall two rooms
    // away is most of what makes co-op here feel like being somewhere together.
    const latest = this.snapshots[this.snapshots.length - 1];
    if (latest) {
      for (const entity of latest.payload.entities) {
        if (entity.kind !== EntityKind.Player) continue;
        if ((entity.flags & EntityFlags.Local) !== 0) continue;
        if ((entity.flags & EntityFlags.FlashlightOn) === 0) continue;
        if (lights.length >= 8) break;
        const focus = (entity.flags & EntityFlags.FocusedBeam) !== 0;
        lights.push({
          position: new Vector3(entity.x, EYE_HEIGHT, entity.z),
          direction: new Vector3(Math.sin(entity.yaw), -0.08, -Math.cos(entity.yaw)).normalize(),
          color: new Color(0.66, 0.6, 0.49),
          range: focus ? 28 : 18,
          cosInner: focus ? 0.99 : 0.95,
          cosOuter: focus ? 0.965 : 0.86,
          shadowSteps: 14,
        });
      }
    }

    this.renderer.setDynamicLights(lights);
  }

  private updatePrompt(): void {
    this.hud.prompt = null;
    if (!this.level || this.hud.downed || this.hud.dead || this.hud.escaped) return;

    let best: InteractPrompt | null = null;
    let bestDistance = INTERACT_RANGE;

    for (const placement of this.level.objectives) {
      const state = this.objectives[placement.id];
      if (!state) continue;
      const world = tileToWorld(this.level, placement.x, placement.y);
      const distance = Math.hypot(world.x - this.local.x, world.z - this.local.z);
      if (distance > bestDistance) continue;

      if (placement.kind === 'fuse') {
        if (state.done || state.carriedBy !== -1 || this.hud.carrying) continue;
        best = { key: 'hud.interact.fuse' };
      } else if (placement.kind === 'generator') {
        best = { key: this.hud.carrying ? 'hud.interact.generator' : 'hud.interact.generatorLocked' };
      } else {
        best = { key: state.done ? 'hud.interact.exit' : 'hud.interact.exitLocked' };
      }
      bestDistance = distance;
    }

    this.hud.prompt = best;
  }

  private refreshObjectiveVisuals(): void {
    let collected = 0;
    for (const state of this.objectives) {
      if (state.kind === 'fuse') {
        if (state.done) collected++;
        // A carried or spent fuse is no longer lying on the floor.
        this.renderer.setObjectiveVisible(state.id, !state.done && state.carriedBy === -1);
        if (state.carriedBy === this.connection.playerId) this.hud.carrying = true;
      }
      if (state.kind === 'exit') {
        this.hud.exitOpen = state.done;
        this.renderer.setExitPowered(state.id, state.done);
      }
    }
    this.hud.fusesCollected = collected;
    this.hud.carrying = this.objectives.some((o) => o.kind === 'fuse' && o.carriedBy === this.connection.playerId);
  }

  /**
   * Hallucinations are generated entirely client-side from the player's own sanity, seeded
   * so they are reproducible. They cannot touch game state, which is exactly why each
   * player gets different ones — "did you see that?" / "see what?" is the point.
   */
  private updateHallucinations(dt: number): void {
    if (!this.settings.hallucinations || !this.level) return;
    if (this.hud.sanity > 0.7) return;

    this.audio.updateHallucinations(dt, true, (key) => {
      const angle = Math.random() * Math.PI * 2;
      const distance = 4 + Math.random() * 8;
      this.audio.play(key, this.local.x + Math.cos(angle) * distance, this.local.z + Math.sin(angle) * distance, 0.8);
    });

    // A figure at the end of the corridor, for well under a second.
    const bucket = Math.floor(performance.now() / 1000);
    const roll = hash3f(this.connection.playerId, bucket, this.level.layoutHash);
    const pressure = clamp01((0.7 - this.hud.sanity) / 0.7);
    if (this.hallucinationActor === null && roll < pressure * 0.06) {
      const distance = 7 + roll * 40;
      const x = this.local.x + Math.sin(this.yaw) * distance;
      const z = this.local.z - Math.cos(this.yaw) * distance;
      const tile = worldToTile(this.level, x, z);
      if (this.level.tiles[tile.y * this.level.width + tile.x] === 1) {
        this.hallucinationActor = {
          id: 60000,
          kind: EntityKind.Player,
          flags: EntityFlags.Alive,
          x,
          z,
          yaw: this.yaw + Math.PI,
          aiState: AiState.Idle,
        };
        this.hallucinationUntil = performance.now() + 420 + roll * 500;
      }
    }
  }

  private triggerScare(intensity: number): void {
    const scaled = intensity * this.settings.scareIntensity;
    if (scaled <= 0.01) return;
    this.renderer.pulseScare(scaled);
    if (this.settings.screenshake) this.shake = Math.max(this.shake, scaled);
  }

  private trackFps(dt: number): void {
    this.frameTimes.push(dt);
    if (this.frameTimes.length > 30) this.frameTimes.shift();
    const total = this.frameTimes.reduce((sum, value) => sum + value, 0);
    this.hud.fps = total > 0 ? Math.round(this.frameTimes.length / total) : 0;
  }

  resize(): void {
    this.renderer.resize();
  }

  /** Exposed for the e2e harness and the debug overlay. */
  debugState(): Record<string, unknown> {
    return {
      hasLevel: this.level !== null,
      layoutHash: this.level?.layoutHash ?? 0,
      seed: this.level?.seed ?? '',
      x: this.local.x,
      z: this.local.z,
      yaw: this.yaw,
      descent: this.hud.descent,
      sanity: this.hud.sanity,
      entities: this.snapshots[this.snapshots.length - 1]?.payload.entities.length ?? 0,
      drawCalls: this.renderer.drawCalls,
      triangles: this.renderer.triangles,
      fps: this.hud.fps,
    };
  }

  dispose(): void {
    for (const dispose of this.disposers) dispose();
    this.disposers = [];
    this.renderer.dispose();
  }
}
