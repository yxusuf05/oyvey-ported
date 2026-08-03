/**
 * A room: a lobby that can hold a run.
 *
 * The server simulates at a fixed 60 Hz and ships snapshots at 20 Hz. Room state itself
 * is in-memory and deliberately ephemeral — rooms exist for one evening, and only meta
 * progression is worth putting on disk.
 */

import type { WebSocket } from 'ws';
import {
  DT,
  MAX_PLAYERS,
  SNAPSHOT_EVERY,
  type Input,
} from '@game/shared/sim';
import {
  EntityFlags,
  EntityKind,
  PROTOCOL_VERSION,
  encodeJson,
  encodeSnapshot,
  rleEncode,
  type C2S,
  type LobbyPlayer,
  type RoomPhase,
  type RunStats,
  type S2C,
  type EntitySnapshot,
} from '@game/shared/protocol';
import { DEFAULT_THEME_ID } from '@game/shared/levelgen';
import type { PerkLevels } from '@game/shared/content';
import type { Profile, ProgressionStore } from '../persist/store';
import { Run, type ServerPlayer } from '../sim/run';

/** Seconds a disconnected player's body stays in the world before it is removed. */
const RECONNECT_GRACE = 90;
/** Idle rooms are collected after this long with nobody connected. */
export const ROOM_IDLE_TTL = 30 * 60;

export interface RoomPlayer {
  id: number;
  name: string;
  ready: boolean;
  sessionToken: string;
  socket: WebSocket | null;
  /** Set while a run is in progress. */
  sim: ServerPlayer | null;
  /** Meta-progression identity. Empty for a client that never sent one. */
  profileId: string;
  /** Perk levels loaded at hello, frozen for the duration of a run. */
  perks: PerkLevels;
}

export class GameRoom {
  readonly code: string;
  readonly players: RoomPlayer[] = [];
  phase: RoomPhase = 'lobby';
  hostId = -1;
  run: Run | null = null;
  idleFor = 0;

  private tickAccumulator = 0;
  private stepsSinceSnapshot = 0;
  private seed = '';
  /** Shared by every room; null in fixtures that do not care about progression. */
  private store: ProgressionStore | null = null;

  constructor(code: string) {
    this.code = code;
  }

  attachStore(store: ProgressionStore): void {
    this.store = store;
  }

  get connectedCount(): number {
    return this.players.filter((p) => p.socket !== null).length;
  }

  get isEmpty(): boolean {
    return this.players.length === 0;
  }

  addPlayer(player: RoomPlayer): void {
    this.players.push(player);
    if (this.hostId === -1) this.hostId = player.id;
    // Joining mid-run drops you straight into the level rather than making you wait it
    // out — a friend who reloads the tab should be back in the maze, not in a menu.
    if (this.run && this.phase === 'running') {
      player.sim = this.run.createPlayer(player.id, player.name, player.perks);
      this.sendRunStart(player);
    }
    this.broadcastRoomState();
  }

  removePlayer(playerId: number): void {
    const index = this.players.findIndex((p) => p.id === playerId);
    if (index === -1) return;
    this.players.splice(index, 1);
    if (this.hostId === playerId) {
      this.hostId = this.players.length > 0 ? Math.min(...this.players.map((p) => p.id)) : -1;
    }
    this.broadcastRoomState();
  }

  findPlayer(playerId: number): RoomPlayer | undefined {
    return this.players.find((p) => p.id === playerId);
  }

  get isFull(): boolean {
    return this.players.length >= MAX_PLAYERS;
  }

  // ---------------------------------------------------------------------------
  // Message handling
  // ---------------------------------------------------------------------------

  handleMessage(player: RoomPlayer, msg: C2S): void {
    switch (msg.t) {
      case 'setName':
        player.name = sanitiseName(msg.name);
        if (player.sim) player.sim.name = player.name;
        this.broadcastRoomState();
        break;

      case 'ready':
        player.ready = msg.ready;
        this.broadcastRoomState();
        break;

      case 'startRun':
        if (player.id !== this.hostId) {
          this.send(player, { t: 'error', code: 'not_host', message: 'Only the host can start a run.' });
          return;
        }
        if (this.phase === 'running') return;
        this.startRun(msg.seed, msg.themeId);
        break;

      case 'abandonRun':
        if (player.id !== this.hostId || !this.run) return;
        this.endRun('abandoned');
        break;

      case 'chat': {
        const text = msg.text.slice(0, 300).trim();
        if (text.length === 0) return;
        this.broadcast({ t: 'chat', from: player.id, name: player.name, text });
        break;
      }

      case 'requestLevelPatch': {
        if (!this.run) return;
        // Determinism fallback: a mismatch costs four kilobytes instead of stranding two
        // players in different mazes.
        this.send(player, {
          t: 'levelPatch',
          width: this.run.level.width,
          height: this.run.level.height,
          rle: rleEncode(this.run.level.tiles),
        });
        break;
      }

      case 'ping':
        this.send(player, { t: 'pong', sent: msg.sent });
        break;

      default:
        break;
    }
  }

  handleInput(player: RoomPlayer, inputs: Input[], lastAckedTick: number): void {
    if (!player.sim) return;
    player.sim.lastAckedTick = lastAckedTick;
    for (const input of inputs) {
      // Inputs are re-sent for redundancy, so duplicates are expected and must be dropped
      // rather than replayed — replaying them would double the player's speed on a lossy
      // connection, which is both a bug and an exploit.
      if (input.seq <= player.sim.lastProcessedSeq) continue;
      if (player.sim.pending.some((p) => p.seq === input.seq)) continue;
      player.sim.pending.push(input);
    }
    player.sim.pending.sort((a, b) => a.seq - b.seq);
    // Bound the queue: a client that stops sending and then floods must not be able to
    // buy itself several seconds of fast-forward.
    if (player.sim.pending.length > 20) {
      player.sim.pending.splice(0, player.sim.pending.length - 20);
    }
  }

  setConnected(player: RoomPlayer, socket: WebSocket | null): void {
    player.socket = socket;
    if (player.sim) {
      player.sim.connected = socket !== null;
      if (socket) player.sim.disconnectedFor = 0;
    }
    this.broadcastRoomState();
  }

  // ---------------------------------------------------------------------------
  // Run lifecycle
  // ---------------------------------------------------------------------------

  startRun(seed?: string, themeId?: string): void {
    this.seed = seed && seed.trim().length > 0 ? seed.trim() : randomSeed();
    this.run = new Run(this.seed, themeId ?? DEFAULT_THEME_ID);
    this.phase = 'running';
    for (const player of this.players) {
      player.sim = this.run.createPlayer(player.id, player.name, player.perks);
      player.sim.connected = player.socket !== null;
      this.sendRunStart(player);
      this.sendInventory(player);
    }
    this.broadcastRoomState();
    this.broadcastObjectives();
    this.broadcastWorldItems();
  }

  private sendRunStart(player: RoomPlayer): void {
    if (!this.run) return;
    this.send(player, {
      t: 'runStart',
      seed: this.run.level.seed,
      themeId: this.run.level.themeId,
      layoutHash: this.run.level.layoutHash,
      spawnTile: [this.run.level.spawn.x, this.run.level.spawn.y],
      startTick: this.run.tick,
      fuseTotal: this.run.fuseTotal,
      descentSeconds: this.run.theme.descentSeconds,
    });
  }

  private endRun(outcome: 'extracted' | 'wipe' | 'abandoned'): void {
    if (!this.run) return;
    const sims = this.sims();
    const stats = this.run.stats(sims);
    this.broadcast({ t: 'runEnd', outcome, stats });
    this.bankRewards(outcome, stats);
    this.run = null;
    this.phase = 'summary';
    for (const player of this.players) {
      player.sim = null;
      player.ready = false;
    }
    this.broadcastRoomState();
  }

  // ---------------------------------------------------------------------------
  // Tick
  // ---------------------------------------------------------------------------

  update(dt: number): void {
    if (this.connectedCount === 0) this.idleFor += dt;
    else this.idleFor = 0;

    if (!this.run || this.phase !== 'running') return;

    // A run with nobody watching it is paused, not fast-forwarded. Simulating an abandoned
    // maze at 60 Hz — entities, noise propagation, the descent — burns a full core per dead
    // room until the half-hour idle sweep collects it, and on a shared server a handful of
    // closed tabs is enough to starve the rooms people are actually playing in. A player who
    // reconnects inside the grace period comes back to the run they left rather than to one
    // that kept getting worse without them.
    if (this.connectedCount === 0) return;

    this.tickAccumulator += dt;
    // Cap catch-up so a stalled process does not fast-forward the world on resume.
    if (this.tickAccumulator > 0.25) this.tickAccumulator = 0.25;

    const sims = this.sims();
    while (this.tickAccumulator >= DT) {
      this.tickAccumulator -= DT;
      this.run.step(sims, DT);
      this.stepsSinceSnapshot++;

      if (this.run.outcome) {
        this.flushRunEvents();
        this.endRun(this.run.outcome);
        return;
      }
    }

    this.flushRunEvents();

    // Players whose grace period expired are removed from the world.
    for (const player of this.players) {
      if (player.sim && !player.sim.connected && player.sim.disconnectedFor > RECONNECT_GRACE) {
        player.sim.hp = 0;
      }
    }

    if (this.stepsSinceSnapshot >= SNAPSHOT_EVERY) {
      this.stepsSinceSnapshot = 0;
      this.sendSnapshots();
    }
  }

  private flushRunEvents(): void {
    if (!this.run) return;
    if (this.run.outbox.length > 0) {
      for (const event of this.run.outbox) this.broadcast(event);
      this.run.outbox.length = 0;
    }
    if (this.run.objectivesDirty) {
      this.run.objectivesDirty = false;
      this.broadcastObjectives();
    }
    if (this.run.worldItemsDirty) {
      this.run.worldItemsDirty = false;
      this.broadcastWorldItems();
    }
    if (this.run.marksDirty) {
      this.run.marksDirty = false;
      this.broadcastMarks();
    }
    // Backpacks go only to their owner. Nobody else has any use for what you are carrying,
    // and a player who can read the others' inventories out of the socket knows things the
    // game never showed them.
    for (const player of this.players) {
      if (!player.sim?.inventoryDirty) continue;
      player.sim.inventoryDirty = false;
      this.sendInventory(player);
    }
  }

  private broadcastObjectives(): void {
    if (!this.run) return;
    this.broadcast({ t: 'objectives', objectives: this.run.objectives.map((o) => ({ ...o })) });
  }

  private broadcastWorldItems(): void {
    if (!this.run) return;
    this.broadcast({
      t: 'worldItems',
      // `burnLeft` is server-only: how long a glowstick has left is atmosphere, not a
      // number the player should be reading off the floor.
      items: this.run.worldItems.map(({ id, item, x, z, count, lit }) => ({ id, item, x, z, count, lit })),
    });
  }

  /**
   * Books the takings from a finished run.
   *
   * A wipe still pays, at a third. A run that returns nothing turns failure into pure
   * punishment, and this is a game you are meant to lose sometimes — the loss should cost
   * you the loot you were carrying, not the evening.
   */
  private bankRewards(outcome: 'extracted' | 'wipe' | 'abandoned', stats: RunStats): void {
    if (!this.store || outcome === 'abandoned') return;
    const share = outcome === 'extracted' ? 1 : 1 / 3;
    const credits = Math.round(stats.reward * share);

    for (const player of this.players) {
      if (player.profileId.length === 0) continue;
      const profile = this.store.award(player.profileId, credits, stats.peakDescent);
      player.perks = profile.perks;
      this.sendProfile(player, profile);
    }
  }

  sendProfile(player: RoomPlayer, profile: Profile): void {
    this.send(player, {
      t: 'profile',
      credits: profile.credits,
      runs: profile.runs,
      deepest: profile.deepest,
      perks: { ...profile.perks },
    });
  }

  private broadcastMarks(): void {
    if (!this.run) return;
    this.broadcast({ t: 'marks', marks: this.run.marks.map((mark) => ({ ...mark })) });
  }

  private sendInventory(player: RoomPlayer): void {
    if (!player.sim) return;
    this.send(player, {
      t: 'inventory',
      slots: player.sim.inventory.map((slot) => (slot ? { ...slot } : null)),
      activeSlot: player.sim.activeSlot,
    });
  }

  private sendSnapshots(): void {
    const run = this.run;
    if (!run) return;

    const entityRecords = run.entitySnapshots();
    const playerRecords: EntitySnapshot[] = [];
    for (const player of this.players) {
      if (!player.sim) continue;
      playerRecords.push({
        id: player.id,
        kind: EntityKind.Player,
        flags: run.playerFlags(player.sim),
        x: player.sim.state.x,
        z: player.sim.state.z,
        yaw: player.sim.state.yaw,
        aiState: 0,
        hp: player.sim.hp,
      });
    }

    for (const player of this.players) {
      if (!player.socket || !player.sim) continue;
      // Each client gets its own copy with the Local flag set, so it knows which record to
      // reconcile against and which to interpolate.
      const entities = [...playerRecords, ...entityRecords].map((e) =>
        e.id === player.id ? { ...e, flags: e.flags | EntityFlags.Local } : e,
      );
      const buffer = encodeSnapshot({
        tick: run.tick,
        baselineTick: player.sim.lastAckedTick,
        lastProcessedInputSeq: player.sim.lastProcessedSeq,
        descent: run.descent,
        sanity: player.sim.sanity,
        stamina: player.sim.state.stamina,
        battery: player.sim.battery,
        hp: player.sim.hp,
        bleedout: player.sim.downed ? player.sim.bleedout : 0,
        reviveProgress: Math.round(player.sim.reviveProgress * 100),
        entities,
      });
      this.sendBinary(player, buffer);
    }
  }

  private sims(): ServerPlayer[] {
    return this.players.map((p) => p.sim).filter((s): s is ServerPlayer => s !== null);
  }

  // ---------------------------------------------------------------------------
  // Transport
  // ---------------------------------------------------------------------------

  send(player: RoomPlayer, msg: S2C): void {
    if (!player.socket || player.socket.readyState !== 1) return;
    player.socket.send(encodeJson(msg));
  }

  private sendBinary(player: RoomPlayer, buffer: ArrayBuffer): void {
    if (!player.socket || player.socket.readyState !== 1) return;
    player.socket.send(buffer, { binary: true });
  }

  broadcast(msg: S2C): void {
    const payload = encodeJson(msg);
    for (const player of this.players) {
      if (player.socket && player.socket.readyState === 1) player.socket.send(payload);
    }
  }

  broadcastRoomState(): void {
    const players: LobbyPlayer[] = this.players.map((p) => ({
      id: p.id,
      name: p.name,
      ready: p.ready,
      connected: p.socket !== null,
    }));
    this.broadcast({ t: 'roomState', code: this.code, hostId: this.hostId, phase: this.phase, players });
  }

  welcome(player: RoomPlayer): void {
    this.send(player, {
      t: 'welcome',
      playerId: player.id,
      sessionToken: player.sessionToken,
      protocol: PROTOCOL_VERSION,
    });
  }
}

export function sanitiseName(raw: string): string {
  const cleaned = raw.replace(/[\u0000-\u001f\u007f]/g, '').trim().slice(0, 20);
  return cleaned.length > 0 ? cleaned : 'Anon';
}

function randomSeed(): string {
  return Math.floor(Math.random() * 0xffffffff).toString(36) + Date.now().toString(36).slice(-4);
}
