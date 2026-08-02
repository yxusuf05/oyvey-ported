/**
 * Static light as a propagated grid rather than as light sources.
 *
 * A backrooms floor has several hundred ceiling fixtures. As real point lights that is
 * impossible; as a flood-filled grid it costs nothing per fixture, spills through
 * doorways in exactly the right shape, and can be repaired region-by-region when the
 * descent kills a room's lights. The renderer samples this as a texture, and the server
 * reads the same field to decide what its monsters can see.
 */

import type { Rng } from '../prng';
import { FLOOR, type Fixture, type Room } from './types';
import type { GridView } from './grid';

/**
 * Integer propagation levels — also the reach of a single fixture, in tiles.
 *
 * Deliberately short. Propagating far enough to cover a whole room lights it flatly and
 * evenly, which looks like an ambient term rather than like ceiling tubes. Thirteen tiles
 * against a five-tile fixture spacing leaves visible pools with dimmer ground between them,
 * and that unevenness is what the darkness later has something to take away.
 */
export const LIGHT_MAX = 13;

export function placeFixtures(rng: Rng, grid: GridView, rooms: Room[], spacing: number): Fixture[] {
  const fixtures: Fixture[] = [];
  for (const room of rooms) {
    const jitter = rng.derive('light:room', room.id);
    const offX = jitter.int(0, spacing - 1);
    const offY = jitter.int(0, spacing - 1);
    let placed = 0;
    const taken = new Set<number>();
    for (let y = room.y0 + offY; y <= room.y1; y += spacing) {
      for (let x = room.x0 + offX; x <= room.x1; x += spacing) {
        // Snap onto the nearest floor tile rather than skipping. Corridor bundles and
        // cubicle warrens put walls exactly where a regular grid wants to place fixtures,
        // and skipping left those rooms lit from one end only — long hallways faded to
        // black in the middle, which read as a rendering bug rather than as atmosphere.
        const spot = snapToFloor(grid, room, x, y);
        if (!spot) continue;
        const key = spot.y * grid.width + spot.x;
        if (taken.has(key)) continue;
        taken.add(key);
        fixtures.push({ x: spot.x, y: spot.y, room: room.id });
        placed++;
      }
    }
    // A room with no fixture at all reads as a bug rather than as atmosphere.
    if (placed === 0) {
      for (let y = room.y0; y <= room.y1 && placed === 0; y++) {
        for (let x = room.x0; x <= room.x1 && placed === 0; x++) {
          if (grid.tiles[y * grid.width + x] !== FLOOR) continue;
          fixtures.push({ x, y, room: room.id });
          placed++;
        }
      }
    }
  }
  return fixtures;
}

/** Nearest floor tile to a grid position, searched in rings and clipped to the room. */
function snapToFloor(grid: GridView, room: Room, x: number, y: number): { x: number; y: number } | null {
  if (grid.tiles[y * grid.width + x] === FLOOR) return { x, y };
  for (let r = 1; r <= 3; r++) {
    for (let dy = -r; dy <= r; dy++) {
      const ny = y + dy;
      if (ny < room.y0 || ny > room.y1) continue;
      const span = r - Math.abs(dy);
      for (const dx of span === 0 ? [0] : [-span, span]) {
        const nx = x + dx;
        if (nx < room.x0 || nx > room.x1) continue;
        if (grid.tiles[ny * grid.width + nx] === FLOOR) return { x: nx, y: ny };
      }
    }
  }
  return null;
}

/**
 * Multi-source BFS. Because every fixture injects the same level, breadth-first order is
 * already monotonically decreasing, so no priority queue is needed.
 *
 * @param darkRooms room ids whose fixtures have been killed by the descent
 */
export function propagateLight(
  grid: GridView,
  fixtures: readonly Fixture[],
  darkRooms?: ReadonlySet<number>,
): Uint8Array {
  const { width, height, tiles } = grid;
  const levels = new Uint8Array(width * height);
  const queue = new Int32Array(width * height);
  let head = 0;
  let tail = 0;

  for (const f of fixtures) {
    if (darkRooms?.has(f.room)) continue;
    const idx = f.y * width + f.x;
    if (levels[idx] >= LIGHT_MAX) continue;
    levels[idx] = LIGHT_MAX;
    queue[tail++] = idx;
  }

  while (head < tail) {
    const idx = queue[head++];
    const next = levels[idx] - 1;
    if (next <= 0) continue;
    const x = idx % width;
    const y = (idx / width) | 0;
    if (x > 0) push(idx - 1, next);
    if (x < width - 1) push(idx + 1, next);
    if (y > 0) push(idx - width, next);
    if (y < height - 1) push(idx + width, next);
  }

  function push(nIdx: number, value: number): void {
    if (tiles[nIdx] !== FLOOR) return;
    if (levels[nIdx] >= value) return;
    levels[nIdx] = value;
    queue[tail++] = nIdx;
  }

  // Wall tiles inherit their brightest floor neighbour. Without this the bilinear filter
  // would darken every surface right where it meets a wall, and every room would look
  // like it had a black outline drawn around it.
  const out = new Uint8Array(levels);
  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      const idx = y * width + x;
      if (tiles[idx] === FLOOR) continue;
      let best = 0;
      if (x > 0) best = Math.max(best, levels[idx - 1]);
      if (x < width - 1) best = Math.max(best, levels[idx + 1]);
      if (y > 0) best = Math.max(best, levels[idx - width]);
      if (y < height - 1) best = Math.max(best, levels[idx + width]);
      out[idx] = best;
    }
  }
  return out;
}

/** Normalised brightness in [0, 1] at a tile — the value entity vision is modulated by. */
export function lightAt(field: Uint8Array, width: number, x: number, y: number): number {
  const idx = y * width + x;
  if (idx < 0 || idx >= field.length) return 0;
  return field[idx] / LIGHT_MAX;
}

/** Packs the propagation levels into a byte texture the shader samples by world position. */
export function packLightTexture(field: Uint8Array): Uint8Array {
  const out = new Uint8Array(field.length);
  for (let i = 0; i < field.length; i++) {
    // Squared falloff reads far more like real light than the linear step count does.
    const t = field[i] / LIGHT_MAX;
    out[i] = Math.round(t * t * 255);
  }
  return out;
}

/** Wall occupancy as a byte texture, used for the flashlight's DDA shadow raymarch. */
export function packWallTexture(grid: GridView): Uint8Array {
  const out = new Uint8Array(grid.tiles.length);
  for (let i = 0; i < grid.tiles.length; i++) out[i] = grid.tiles[i] === FLOOR ? 0 : 255;
  return out;
}
