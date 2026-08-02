/** Level representation shared by the server simulation and the client renderer. */

/** Metres per tile. Chosen so a one-tile corridor reads as a real hallway. */
export const TILE_SIZE = 2.0;
/** Ceiling height for normal rooms. Atria are taller, see {@link RoomKind}. */
export const CEILING_HEIGHT = 3.0;
export const ATRIUM_CEILING_HEIGHT = 5.4;

export const SOLID = 0;
export const FLOOR = 1;

export type RoomKind = 'openHall' | 'cubicleWarren' | 'corridorBundle' | 'atrium' | 'pillarField';

export interface Room {
  id: number;
  /** Inclusive tile bounds. */
  x0: number;
  y0: number;
  x1: number;
  y1: number;
  kind: RoomKind;
  /** Tile-space centroid, guaranteed to be a floor tile. */
  cx: number;
  cy: number;
  /** Path through the BSP tree as bits, so "is on a different branch" is a cheap test. */
  branch: number;
  branchDepth: number;
  /** Doors touching this room. */
  doors: number[];
  /** Set once the descent has darkened this room. */
  ceilingHeight: number;
}

export interface Door {
  id: number;
  x: number;
  y: number;
  /** Rooms this door joins. */
  a: number;
  b: number;
  /**
   * `tree` doors come from the BSP spanning tree and are what guarantees connectivity.
   * `braid` doors are the extra loops added afterwards so chases have somewhere to go.
   */
  kind: 'tree' | 'braid';
}

export interface Fixture {
  x: number;
  y: number;
  room: number;
}

export type ObjectiveKind = 'fuse' | 'generator' | 'exit';

export interface ObjectivePlacement {
  id: number;
  kind: ObjectiveKind;
  x: number;
  y: number;
  room: number;
}

/**
 * Descent events are generated up front, not at runtime, so the client can pre-build
 * the post-mutation geometry at load time and never mesh during gameplay. The server
 * only broadcasts `{index, tick}` when a threshold is crossed.
 */
export type DescentEvent =
  | { kind: 'lightsOut'; at: number; rooms: number[] }
  | { kind: 'seal'; at: number; door: number }
  | { kind: 'themeShift'; at: number; rooms: number[]; stage: number };

export interface Level {
  seed: string;
  themeId: string;
  width: number;
  height: number;
  /** `SOLID` or `FLOOR` per tile, row-major (`y * width + x`). */
  tiles: Uint8Array;
  /** Room id per tile, `-1` for walls. */
  roomOf: Int16Array;
  rooms: Room[];
  doors: Door[];
  fixtures: Fixture[];
  spawn: { x: number; y: number };
  objectives: ObjectivePlacement[];
  descent: DescentEvent[];
  /** FNV-1a over the tile array — the client asserts this matches the server's. */
  layoutHash: number;
}

export const tileIndex = (level: { width: number }, x: number, y: number): number =>
  y * level.width + x;

export function isFloor(level: Pick<Level, 'tiles' | 'width' | 'height'>, x: number, y: number): boolean {
  if (x < 0 || y < 0 || x >= level.width || y >= level.height) return false;
  return level.tiles[y * level.width + x] === FLOOR;
}

export function isSolid(level: Pick<Level, 'tiles' | 'width' | 'height'>, x: number, y: number): boolean {
  return !isFloor(level, x, y);
}

/** Tile centre in world space. The grid is centred on the origin. */
export function tileToWorld(level: Pick<Level, 'width' | 'height'>, x: number, y: number): { x: number; z: number } {
  return {
    x: (x - level.width / 2 + 0.5) * TILE_SIZE,
    z: (y - level.height / 2 + 0.5) * TILE_SIZE,
  };
}

export function worldToTile(level: Pick<Level, 'width' | 'height'>, wx: number, wz: number): { x: number; y: number } {
  return {
    x: Math.floor(wx / TILE_SIZE + level.width / 2),
    y: Math.floor(wz / TILE_SIZE + level.height / 2),
  };
}
