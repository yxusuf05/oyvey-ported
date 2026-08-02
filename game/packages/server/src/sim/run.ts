/**
 * The authoritative run.
 *
 * Everything that decides who wins lives here: positions, objectives, entity AI, the
 * descent clock, sanity, damage. The client predicts its own movement and renders the
 * rest, but it is never consulted. The one deliberate exception is hallucinations, which
 * the client generates locally from its own sanity value — they cannot affect state, so
 * letting each player hallucinate differently costs nothing and is the best co-op horror
 * beat the game has.
 */

import {
  AiState,
  EntityFlags,
  EntityKind,
  type ObjectiveState,
  type RunOutcome,
  type RunStats,
  type S2C,
} from '@game/shared/protocol';
import {
  generateLevel,
  getTheme,
  levelGrid,
  lightAt,
  propagateLight,
  tileToWorld,
  worldToTile,
  type GridView,
  type Level,
  type ThemeSpec,
} from '@game/shared/levelgen';
import {
  Buttons,
  DOWNED_BLEEDOUT_SECONDS,
  FLASHLIGHT_BATTERY_MAX,
  FLASHLIGHT_DRAIN_FOCUS,
  FLASHLIGHT_DRAIN_WIDE,
  INTERACT_RANGE,
  NoiseField,
  PLAYER_MAX_HP,
  REVIVE_SECONDS,
  SANITY_BUDDY_RANGE,
  SANITY_BUDDY_REGEN,
  SANITY_DARK_DRAIN,
  SANITY_LIGHT_REGEN,
  SANITY_MAX,
  SANITY_SEEN_DRAIN,
  clonePlayerState,
  createPlayerState,
  hasButton,
  stepPlayer,
  type Input,
  type PlayerSimState,
} from '@game/shared/sim';
import { Rng } from '@game/shared/prng';
import { clamp, clamp01 } from '@game/shared/math';
import { getEntitySpec } from '@game/shared/content';
import { Director, MIN_SPAWN_DISTANCE } from '../ai/director';
import { Navigator } from '../ai/nav';
import { createEntity, updateEntity, type AiPlayerView, type AiWorld, type ServerEntity } from '../ai/brains';

export interface ServerPlayer {
  id: number;
  name: string;
  state: PlayerSimState;
  /** Inputs received but not yet simulated, ordered by sequence. */
  pending: Input[];
  lastProcessedSeq: number;
  lastAckedTick: number;
  lastButtons: number;
  hp: number;
  sanity: number;
  battery: number;
  flashlightOn: boolean;
  focusBeam: boolean;
  downed: boolean;
  bleedout: number;
  reviveProgress: number;
  carrying: number;
  escaped: boolean;
  connected: boolean;
  /** Seconds since the socket dropped; the body stays in the world during the grace period. */
  disconnectedFor: number;
}

export class Run {
  readonly level: Level;
  readonly grid: GridView;
  readonly theme: ThemeSpec;
  readonly nav: Navigator;
  readonly noise: NoiseField;
  readonly objectives: ObjectiveState[];
  readonly director: Director;

  lightField: Uint8Array;
  darkRooms = new Set<number>();
  entities: ServerEntity[] = [];

  descent = 0;
  peakDescent = 0;
  tick = 0;
  elapsed = 0;
  outcome: RunOutcome | null = null;

  /** Drained by the room each tick and forwarded to clients. */
  outbox: S2C[] = [];
  objectivesDirty = false;

  private nextEntityId = 1000;
  private spawnCooldown = 6;
  private descentIndex = 0;
  private readonly rng: Rng;

  constructor(seed: string, themeId: string) {
    this.level = generateLevel(seed, themeId);
    this.theme = getTheme(themeId);
    this.grid = levelGrid(this.level);
    this.nav = new Navigator(this.level);
    this.noise = new NoiseField(this.grid);
    this.lightField = propagateLight(this.grid, this.level.fixtures);
    this.rng = new Rng(`${seed}:run`);
    this.director = new Director(() => this.rng.next());
    this.objectives = this.level.objectives.map((o) => ({
      id: o.id,
      kind: o.kind,
      done: false,
      carriedBy: -1,
    }));
  }

  get fuseTotal(): number {
    return this.objectives.filter((o) => o.kind === 'fuse').length;
  }

  get fusesCollected(): number {
    return this.objectives.filter((o) => o.kind === 'fuse' && o.done).length;
  }

  spawnPoint(): { x: number; z: number } {
    return tileToWorld(this.level, this.level.spawn.x, this.level.spawn.y);
  }

  createPlayer(id: number, name: string): ServerPlayer {
    const spawn = this.spawnPoint();
    // Fan players out slightly so four bodies do not start inside each other.
    const angle = (id % 8) * (Math.PI / 4);
    return {
      id,
      name,
      state: createPlayerState(spawn.x + Math.cos(angle) * 0.7, spawn.z + Math.sin(angle) * 0.7),
      pending: [],
      lastProcessedSeq: 0,
      lastAckedTick: 0,
      lastButtons: 0,
      hp: PLAYER_MAX_HP,
      sanity: SANITY_MAX,
      battery: FLASHLIGHT_BATTERY_MAX,
      flashlightOn: false,
      focusBeam: false,
      downed: false,
      bleedout: DOWNED_BLEEDOUT_SECONDS,
      reviveProgress: 0,
      carrying: -1,
      escaped: false,
      connected: true,
      disconnectedFor: 0,
    };
  }

  step(players: ServerPlayer[], dt: number): void {
    if (this.outcome) return;
    this.tick++;
    this.elapsed += dt;

    this.stepPlayers(players, dt);
    this.stepDescent(players, dt);
    this.stepEntities(players, dt);
    this.stepSurvival(players, dt);
    this.checkOutcome(players);
  }

  // ---------------------------------------------------------------------------
  // Players
  // ---------------------------------------------------------------------------

  private stepPlayers(players: ServerPlayer[], dt: number): void {
    this.noise.decay(dt);

    for (const player of players) {
      const active = player.connected && !player.escaped && this.isAlive(player);
      // A disconnected body stays put and is ignored by monsters; the player has 90
      // seconds to come back to it rather than losing the run to a dropped connection.
      const frozen = !active || player.downed;

      const input = player.pending.shift();
      if (input) {
        stepPlayer(player.state, input, this.grid, dt, frozen);
        player.lastProcessedSeq = input.seq;
        this.handleButtons(player, input, players);
        player.lastButtons = input.buttons;
      } else if (frozen) {
        stepPlayer(
          player.state,
          { seq: player.lastProcessedSeq, buttons: 0, yaw: player.state.yaw, pitch: player.state.pitch, slot: 0 },
          this.grid,
          dt,
          true,
        );
      }
      // With no input and an active player, the body simply holds position. Synthesising a
      // filler step here would be the intuitive thing to do and is quietly fatal: the
      // authoritative position would stop being a pure function of the input sequence, so
      // the client's replay could never reproduce it and every stalled packet would leave
      // permanent drift behind.

      if (active && !player.downed && player.state.noise > 0) {
        const tile = worldToTile(this.level, player.state.x, player.state.z);
        this.noise.emit(tile.x, tile.y, player.state.noise);
      }
      // A downed player screams continuously — reviving is meant to be a real risk.
      if (player.downed && this.isAlive(player)) {
        const tile = worldToTile(this.level, player.state.x, player.state.z);
        this.noise.emit(tile.x, tile.y, 4.5);
      }
    }
  }

  private handleButtons(player: ServerPlayer, input: Input, players: ServerPlayer[]): void {
    const pressed = (bit: number): boolean =>
      hasButton(input.buttons, bit) && !hasButton(player.lastButtons, bit);

    if (pressed(Buttons.Flashlight) && player.battery > 0) {
      player.flashlightOn = !player.flashlightOn;
      this.emitSound('item.flashlightClick', player.state.x, player.state.z, 2);
    }
    if (pressed(Buttons.BeamMode) && player.flashlightOn) {
      player.focusBeam = !player.focusBeam;
    }
    if (pressed(Buttons.Interact)) this.handleInteract(player, players);
  }

  private handleInteract(player: ServerPlayer, players: ServerPlayer[]): void {
    if (player.downed || !this.isAlive(player) || player.escaped) return;

    // Reviving a teammate outranks everything else within reach.
    const downedMate = players.find(
      (p) =>
        p.id !== player.id &&
        p.downed &&
        this.isAlive(p) &&
        this.distance(player, p) <= INTERACT_RANGE,
    );
    if (downedMate) {
      downedMate.reviveProgress = Math.max(downedMate.reviveProgress, 0.0001);
      return;
    }

    for (const placement of this.level.objectives) {
      const state = this.objectives[placement.id];
      const world = tileToWorld(this.level, placement.x, placement.y);
      const dx = world.x - player.state.x;
      const dz = world.z - player.state.z;
      if (Math.sqrt(dx * dx + dz * dz) > INTERACT_RANGE) continue;

      if (placement.kind === 'fuse' && !state.done && state.carriedBy === -1 && player.carrying === -1) {
        state.carriedBy = player.id;
        player.carrying = placement.id;
        this.objectivesDirty = true;
        this.emitSound('objective.fusePickup', world.x, world.z, 5);
        return;
      }

      if (placement.kind === 'generator' && player.carrying !== -1) {
        const fuse = this.objectives[player.carrying];
        fuse.done = true;
        fuse.carriedBy = -1;
        player.carrying = -1;
        this.objectivesDirty = true;
        this.emitSound('objective.fuseInsert', world.x, world.z, 9);

        if (this.fusesCollected >= this.fuseTotal) {
          const generator = this.objectives.find((o) => o.kind === 'generator')!;
          const exit = this.objectives.find((o) => o.kind === 'exit')!;
          generator.done = true;
          exit.done = true;
          this.emitSound('objective.exitOpen', world.x, world.z, 16);
          // Powering the exit is the loudest thing that happens in a run, and it wakes
          // the level up. Progress is punished; that is the whole tension curve.
          this.descent = Math.min(1, this.descent + 0.08);
        }
        return;
      }

      if (placement.kind === 'exit' && this.objectives[placement.id].done) {
        player.escaped = true;
        this.emitSound('objective.extract', world.x, world.z, 6);
        return;
      }
    }
  }

  // ---------------------------------------------------------------------------
  // Descent
  // ---------------------------------------------------------------------------

  private stepDescent(players: ServerPlayer[], dt: number): void {
    let totalNoise = 0;
    let brightness = 0;
    let counted = 0;
    for (const player of players) {
      if (!this.isAlive(player) || player.escaped) continue;
      totalNoise += player.state.noise;
      const tile = worldToTile(this.level, player.state.x, player.state.z);
      brightness += lightAt(this.lightField, this.level.width, tile.x, tile.y);
      counted++;
    }
    const avgBrightness = counted > 0 ? brightness / counted : 0;

    const base = dt / this.theme.descentSeconds;
    const noiseFactor = 1 + totalNoise * 0.055;
    const objectiveFactor = 1 + this.fusesCollected * 0.32;
    // Standing in bright light genuinely slows the rot. It gives players something to do
    // with the light beyond seeing, and makes the darkening feel earned rather than timed.
    const lightFactor = avgBrightness > 0.55 ? 0.7 : 1;

    this.descent = clamp01(this.descent + base * noiseFactor * objectiveFactor * lightFactor);
    this.peakDescent = Math.max(this.peakDescent, this.descent);

    while (
      this.descentIndex < this.level.descent.length &&
      this.level.descent[this.descentIndex].at <= this.descent
    ) {
      this.applyDescentEvent(this.descentIndex);
      this.descentIndex++;
    }
  }

  private applyDescentEvent(index: number): void {
    const event = this.level.descent[index];
    switch (event.kind) {
      case 'lightsOut': {
        for (const room of event.rooms) this.darkRooms.add(room);
        // Only the light field is recomputed; the geometry never changes for this event.
        this.lightField = propagateLight(this.grid, this.level.fixtures, this.darkRooms);
        break;
      }
      case 'seal':
        this.nav.sealDoor(event.door);
        break;
      case 'themeShift':
        // Purely visual on the client; the server just relays the index.
        break;
    }
    this.outbox.push({ t: 'descentEvent', index, tick: this.tick });
  }

  // ---------------------------------------------------------------------------
  // Entities
  // ---------------------------------------------------------------------------

  private stepEntities(players: ServerPlayer[], dt: number): void {
    this.director.update(dt);
    this.spawnCooldown -= dt;

    const views: AiPlayerView[] = players.map((p) => {
      const tile = worldToTile(this.level, p.state.x, p.state.z);
      return {
        id: p.id,
        x: p.state.x,
        z: p.state.z,
        tileX: clamp(tile.x, 0, this.level.width - 1),
        tileY: clamp(tile.y, 0, this.level.height - 1),
        alive: this.isAlive(p) && !p.escaped && p.connected,
        downed: p.downed,
        crouching: p.state.crouching,
      };
    });

    if (this.spawnCooldown <= 0) {
      this.trySpawn(views);
      this.spawnCooldown = 12;
    }

    const world: AiWorld = {
      level: this.level,
      grid: this.grid,
      nav: this.nav,
      noise: this.noise,
      lightField: this.lightField,
      players: views,
      descent: this.descent,
      director: this.director,
      random: () => this.rng.next(),
      emitSound: (key, x, z, loudness) => this.emitSound(key, x, z, loudness),
      damagePlayer: (playerId, amount, byEntity) => this.damagePlayer(players, playerId, amount, byEntity),
      onScare: (entityId, kind, intensity) => {
        this.outbox.push({ t: 'scare', kind, entityId, intensity });
      },
    };

    for (const entity of this.entities) updateEntity(entity, world, dt);
  }

  private trySpawn(views: AiPlayerView[]): void {
    for (const roster of this.theme.entities) {
      if (this.descent < roster.wakesAt) continue;
      const live = this.entities.filter((e) => e.kind === roster.kind).length;
      if (live >= roster.max) continue;

      // Never appear on top of someone. A monster that materialises in your face is a
      // cheap shock; one you hear coming from three rooms away is dread.
      for (let attempt = 0; attempt < 48; attempt++) {
        const tile = this.nav.randomReachableTile(() => this.rng.next());
        if (!tile) continue;
        const world = tileToWorld(this.level, tile.x, tile.y);
        const tooClose = views.some((p) => {
          if (!p.alive) return false;
          const dx = p.x - world.x;
          const dz = p.z - world.z;
          return Math.sqrt(dx * dx + dz * dz) < MIN_SPAWN_DISTANCE;
        });
        if (tooClose) continue;

        const spec = getEntitySpec(roster.kind);
        this.entities.push(createEntity(this.nextEntityId++, roster.kind, spec, tile, this.level));
        return;
      }
    }
  }

  // ---------------------------------------------------------------------------
  // Survival: sanity, battery, bleeding out, revives
  // ---------------------------------------------------------------------------

  private stepSurvival(players: ServerPlayer[], dt: number): void {
    for (const player of players) {
      if (!this.isAlive(player) || player.escaped) continue;

      if (player.flashlightOn) {
        const drain = player.focusBeam ? FLASHLIGHT_DRAIN_FOCUS : FLASHLIGHT_DRAIN_WIDE;
        player.battery = Math.max(0, player.battery - drain * dt);
        if (player.battery <= 0) {
          player.flashlightOn = false;
          this.emitSound('item.batteryDead', player.state.x, player.state.z, 3);
        }
      }

      const tile = worldToTile(this.level, player.state.x, player.state.z);
      const brightness = lightAt(this.lightField, this.level.width, tile.x, tile.y);
      const litByOwnLamp = player.flashlightOn ? 0.45 : 0;
      const effectiveLight = Math.max(brightness, litByOwnLamp);

      let sanityDelta = effectiveLight > 0.35 ? SANITY_LIGHT_REGEN * effectiveLight : -SANITY_DARK_DRAIN * (1 - effectiveLight);

      // Being watched is far worse than being in the dark.
      const hunted = this.entities.some(
        (e) =>
          (e.state === AiState.Hunting || e.state === AiState.Lunge) &&
          Math.hypot(e.x - player.state.x, e.z - player.state.z) < 14,
      );
      if (hunted) sanityDelta -= SANITY_SEEN_DRAIN;

      const buddy = players.some(
        (p) => p.id !== player.id && this.isAlive(p) && !p.downed && this.distance(player, p) < SANITY_BUDDY_RANGE,
      );
      if (buddy) sanityDelta += SANITY_BUDDY_REGEN;

      player.sanity = clamp(player.sanity + sanityDelta * dt, 0, SANITY_MAX);

      if (player.downed) {
        if (player.reviveProgress > 0) {
          player.reviveProgress += dt / REVIVE_SECONDS;
          if (player.reviveProgress >= 1) {
            player.downed = false;
            player.reviveProgress = 0;
            player.hp = Math.round(PLAYER_MAX_HP * 0.45);
            player.bleedout = DOWNED_BLEEDOUT_SECONDS;
            this.outbox.push({ t: 'playerRevived', playerId: player.id, by: -1 });
            this.emitSound('player.revived', player.state.x, player.state.z, 5);
          }
        } else {
          player.bleedout -= dt;
          if (player.bleedout <= 0) {
            player.hp = 0;
            this.dropCarried(player);
            this.emitSound('player.died', player.state.x, player.state.z, 7);
          }
        }
      }

      if (player.connected) player.disconnectedFor = 0;
      else player.disconnectedFor += dt;
    }

    // Revive channels lapse if the reviver walks away or goes down themselves.
    for (const player of players) {
      if (!player.downed || player.reviveProgress <= 0) continue;
      const helper = players.some(
        (p) => p.id !== player.id && !p.downed && this.isAlive(p) && this.distance(player, p) <= INTERACT_RANGE,
      );
      if (!helper) player.reviveProgress = 0;
    }
  }

  private damagePlayer(players: ServerPlayer[], playerId: number, amount: number, byEntity: number): void {
    const player = players.find((p) => p.id === playerId);
    if (!player || !this.isAlive(player) || player.escaped) return;
    if (player.downed) {
      player.hp = 0;
      this.dropCarried(player);
      return;
    }
    player.hp = Math.max(0, player.hp - amount);
    this.emitSound('player.hurt', player.state.x, player.state.z, 6);
    if (player.hp <= 0) {
      player.downed = true;
      player.hp = 1;
      player.bleedout = DOWNED_BLEEDOUT_SECONDS;
      player.reviveProgress = 0;
      this.dropCarried(player);
      this.outbox.push({ t: 'playerDown', playerId: player.id, by: byEntity });
    }
  }

  private dropCarried(player: ServerPlayer): void {
    if (player.carrying === -1) return;
    const state = this.objectives[player.carrying];
    state.carriedBy = -1;
    // The fuse stays where it was picked up rather than dropping at the corpse: hunting
    // for a dropped objective in a level that has gone dark is not fun, it is a punishment
    // on top of a punishment.
    player.carrying = -1;
    this.objectivesDirty = true;
  }

  private checkOutcome(players: ServerPlayer[]): void {
    const participants = players.filter((p) => p.connected || p.disconnectedFor < 90);
    if (participants.length === 0) return;
    const anyPlaying = participants.some((p) => this.isAlive(p) && !p.escaped);
    if (anyPlaying) return;

    const survivors = participants.filter((p) => p.escaped).length;
    this.outcome = survivors > 0 ? 'extracted' : 'wipe';
  }

  stats(players: ServerPlayer[]): RunStats {
    const survivors = players.filter((p) => p.escaped).length;
    const fuses = this.fusesCollected;
    return {
      fusesCollected: fuses,
      fusesTotal: this.fuseTotal,
      survivors,
      durationSeconds: Math.round(this.elapsed),
      peakDescent: this.peakDescent,
      reward: Math.round(fuses * 40 + survivors * 90 + this.peakDescent * 60),
    };
  }

  isAlive(player: ServerPlayer): boolean {
    return player.hp > 0;
  }

  private distance(a: ServerPlayer, b: ServerPlayer): number {
    return Math.hypot(a.state.x - b.state.x, a.state.z - b.state.z);
  }

  private emitSound(key: string, x: number, z: number, loudness: number): void {
    this.outbox.push({ t: 'sound', key, x, z, loudness });
  }

  /** Entity records for the snapshot, plus the flags the client renders them with. */
  entitySnapshots(): {
    id: number;
    kind: number;
    flags: number;
    x: number;
    z: number;
    yaw: number;
    aiState: number;
    hp: number;
  }[] {
    return this.entities.map((e) => ({
      id: e.id,
      kind: EntityKind.Blind,
      flags: EntityFlags.Alive,
      x: e.x,
      z: e.z,
      yaw: e.yaw,
      aiState: e.state,
      hp: e.hp,
    }));
  }

  playerFlags(player: ServerPlayer): number {
    let flags = 0;
    if (this.isAlive(player) && !player.escaped) flags |= EntityFlags.Alive;
    if (player.state.crouching) flags |= EntityFlags.Crouching;
    if (player.downed) flags |= EntityFlags.Downed;
    if (!player.connected) flags |= EntityFlags.Disconnected;
    if (player.flashlightOn) flags |= EntityFlags.FlashlightOn;
    if (player.focusBeam) flags |= EntityFlags.FocusedBeam;
    return flags;
  }

  /** Copy used by tests to compare against a client-side prediction. */
  snapshotPlayerState(player: ServerPlayer): PlayerSimState {
    return clonePlayerState(player.state);
  }
}
