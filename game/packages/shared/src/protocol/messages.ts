/**
 * Control-plane messages (JSON) and the shared vocabulary of the wire protocol.
 *
 * The split is deliberate: anything low-frequency and churn-prone during development —
 * lobby, chat, run lifecycle — stays human-readable JSON so it can be debugged straight
 * from the network tab. Only the per-tick hot path is binary (see `codec.ts`).
 */

// Bumped when the binary snapshot layout changes. The `hello` handshake compares it, so a
// stale tab is told to reload instead of silently decoding the wrong bytes.
export const PROTOCOL_VERSION = 2;

export type PlayerId = number;

export const MessageTag = {
  /** Tags below 0x80 mean "the rest of this frame is UTF-8 JSON". */
  Json: 0x01,
  Input: 0x81,
  Snapshot: 0x82,
} as const;

export const EntityKind = {
  Player: 0,
  Blind: 1,
  Smiler: 2,
  Watcher: 3,
  Swarm: 4,
} as const;
export type EntityKindId = (typeof EntityKind)[keyof typeof EntityKind];

/** Player-legible AI states. Named because the design depends on them being readable. */
export const AiState = {
  Idle: 0,
  Patrol: 1,
  Suspicious: 2,
  Investigate: 3,
  Hunting: 4,
  Lunge: 5,
  Cooldown: 6,
  Stunned: 7,
} as const;
export type AiStateId = (typeof AiState)[keyof typeof AiState];

export const EntityFlags = {
  Alive: 1 << 0,
  Crouching: 1 << 1,
  Sprinting: 1 << 2,
  Downed: 1 << 3,
  Disconnected: 1 << 4,
  FlashlightOn: 1 << 5,
  FocusedBeam: 1 << 6,
  Local: 1 << 7,
} as const;

export type RoomPhase = 'lobby' | 'loading' | 'running' | 'summary';
export type RunOutcome = 'extracted' | 'wipe' | 'abandoned';

export interface LobbyPlayer {
  id: PlayerId;
  name: string;
  ready: boolean;
  connected: boolean;
}

export interface ObjectiveState {
  id: number;
  kind: 'fuse' | 'generator' | 'exit';
  /** Fuses: collected. Generator: powered. Exit: open. */
  done: boolean;
  /** Which player is carrying this fuse, or -1. */
  carriedBy: PlayerId;
}

/** One backpack slot. `null` is an empty slot; slots keep their index. */
export interface InventorySlotState {
  item: string;
  count: number;
}

/**
 * An item lying on the floor.
 *
 * These deliberately do *not* travel in the snapshot. They never move, so paying twenty
 * updates a second for information that changes only when someone drops or picks something
 * up is bandwidth spent on nothing. They ride the same change-driven JSON path as
 * objectives instead.
 */
export interface WorldItemState {
  id: number;
  item: string;
  x: number;
  z: number;
  count: number;
  /**
   * Whether this one is actually burning. A glowstick lying in a cupboard is not a light
   * source — you have to crack it — so "is a glowstick" and "is lit" are different
   * questions and the renderer must not answer the second with the first.
   */
  lit: boolean;
}

/**
 * A chalk scrawl. Marks never move, never expire and are never picked up, so like world
 * items they travel on change rather than in the snapshot — and unlike world items there is
 * no state to keep in sync afterwards at all.
 */
export interface ChalkMarkState {
  id: number;
  x: number;
  z: number;
  /** Facing of the surface it was drawn on, so it lies flat against the wall. */
  yaw: number;
  /** Which player drew it. Everyone's chalk is the same colour; this is for the future. */
  by: number;
}

export interface RunStats {
  fusesCollected: number;
  fusesTotal: number;
  survivors: number;
  durationSeconds: number;
  peakDescent: number;
  /** Meta-progression currency awarded for this run. */
  reward: number;
}

/** Client to server. */
export type C2S =
  | { t: 'hello'; protocol: number; name: string; sessionToken?: string; profileId?: string }
  /** Buy the next level of a perk. The server decides whether it happens. */
  | { t: 'buyPerk'; perk: string }
  | { t: 'createRoom' }
  | { t: 'joinRoom'; code: string }
  | { t: 'leaveRoom' }
  | { t: 'setName'; name: string }
  | { t: 'ready'; ready: boolean }
  | { t: 'startRun'; themeId?: string; seed?: string }
  | { t: 'abandonRun' }
  | { t: 'chat'; text: string }
  | { t: 'requestLevelPatch' }
  | { t: 'ping'; sent: number };

/** Server to client. */
export type S2C =
  | { t: 'welcome'; playerId: PlayerId; sessionToken: string; protocol: number }
  | { t: 'roomState'; code: string; hostId: PlayerId; phase: RoomPhase; players: LobbyPlayer[] }
  | {
      t: 'runStart';
      seed: string;
      themeId: string;
      layoutHash: number;
      spawnTile: [number, number];
      startTick: number;
      fuseTotal: number;
      descentSeconds: number;
    }
  | { t: 'runEnd'; outcome: RunOutcome; stats: RunStats }
  | { t: 'descentEvent'; index: number; tick: number }
  | { t: 'objectives'; objectives: ObjectiveState[] }
  /** Your own backpack. Sent only to its owner — nobody else needs to know what you carry. */
  | { t: 'inventory'; slots: (InventorySlotState | null)[]; activeSlot: number }
  /** Everything lying on the floor, resent whenever the set changes. */
  | { t: 'worldItems'; items: WorldItemState[] }
  /** Every chalk mark drawn so far, resent whenever one is added. */
  | { t: 'marks'; marks: ChalkMarkState[] }
  /**
   * Determinism fallback. If the client's regenerated maze does not match the server's
   * layout hash it asks for this, and a determinism bug degrades to four kilobytes of
   * bandwidth instead of two players wandering different mazes.
   */
  | { t: 'levelPatch'; width: number; height: number; rle: number[] }
  | { t: 'chat'; from: PlayerId; name: string; text: string }
  /** Real, entity-backed scare. Hallucinations never come from the server. */
  | { t: 'scare'; kind: string; entityId: number; intensity: number }
  /** Positional audio cue. `key` doubles as the subtitle key, so every sound is captioned. */
  | { t: 'sound'; key: string; x: number; z: number; loudness: number }
  | { t: 'playerDown'; playerId: PlayerId; by: number }
  | { t: 'playerRevived'; playerId: PlayerId; by: PlayerId }
  | { t: 'pong'; sent: number }
  /** Meta-progression, sent only to its owner after a hello, a run, or a purchase. */
  | { t: 'profile'; credits: number; runs: number; deepest: number; perks: Record<string, number> }
  | { t: 'error'; code: string; message: string };

export function encodeJson(msg: S2C | C2S): string {
  return JSON.stringify(msg);
}

export function decodeJson<T>(data: string): T {
  return JSON.parse(data) as T;
}

/** Run-length encodes the tile array for the `levelPatch` fallback. */
export function rleEncode(tiles: Uint8Array): number[] {
  const out: number[] = [];
  let run = 1;
  for (let i = 1; i <= tiles.length; i++) {
    if (i < tiles.length && tiles[i] === tiles[i - 1] && run < 0xffff) {
      run++;
    } else {
      out.push(tiles[i - 1], run);
      run = 1;
    }
  }
  return out;
}

export function rleDecode(rle: number[], length: number): Uint8Array {
  const out = new Uint8Array(length);
  let pos = 0;
  for (let i = 0; i + 1 < rle.length; i += 2) {
    const value = rle[i];
    const run = rle[i + 1];
    for (let n = 0; n < run && pos < length; n++) out[pos++] = value;
  }
  return out;
}
