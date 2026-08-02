/** Grid primitives used by generation, validation, pathfinding and light propagation. */

import { FLOOR, SOLID, type Level } from './types';

export interface GridView {
  width: number;
  height: number;
  tiles: Uint8Array;
}

export const NEIGHBOURS_4: ReadonlyArray<readonly [number, number]> = [
  [1, 0],
  [-1, 0],
  [0, 1],
  [0, -1],
];

export function inBounds(g: GridView, x: number, y: number): boolean {
  return x >= 0 && y >= 0 && x < g.width && y < g.height;
}

export function get(g: GridView, x: number, y: number): number {
  if (!inBounds(g, x, y)) return SOLID;
  return g.tiles[y * g.width + x];
}

export function set(g: GridView, x: number, y: number, v: number): void {
  if (!inBounds(g, x, y)) return;
  g.tiles[y * g.width + x] = v;
}

export function fillRect(g: GridView, x0: number, y0: number, x1: number, y1: number, v: number): void {
  for (let y = Math.max(0, y0); y <= Math.min(g.height - 1, y1); y++) {
    const row = y * g.width;
    for (let x = Math.max(0, x0); x <= Math.min(g.width - 1, x1); x++) g.tiles[row + x] = v;
  }
}

/**
 * Breadth-first distance field over floor tiles, in tile steps.
 * Unreachable tiles stay at `-1`. This one function backs connectivity validation,
 * objective placement and the entity navigation fallback.
 */
export function bfsDistance(
  g: GridView,
  sources: ReadonlyArray<{ x: number; y: number }>,
  blocked?: (x: number, y: number) => boolean,
): Int32Array {
  const dist = new Int32Array(g.width * g.height).fill(-1);
  // A plain array used as a ring buffer beats Array#shift by orders of magnitude here.
  const queue = new Int32Array(g.width * g.height);
  let head = 0;
  let tail = 0;

  for (const s of sources) {
    if (!inBounds(g, s.x, s.y) || g.tiles[s.y * g.width + s.x] !== FLOOR) continue;
    const idx = s.y * g.width + s.x;
    if (dist[idx] !== -1) continue;
    dist[idx] = 0;
    queue[tail++] = idx;
  }

  while (head < tail) {
    const idx = queue[head++];
    const x = idx % g.width;
    const y = (idx / g.width) | 0;
    const d = dist[idx];
    for (let n = 0; n < 4; n++) {
      const nx = x + NEIGHBOURS_4[n][0];
      const ny = y + NEIGHBOURS_4[n][1];
      if (nx < 0 || ny < 0 || nx >= g.width || ny >= g.height) continue;
      const nIdx = ny * g.width + nx;
      if (dist[nIdx] !== -1) continue;
      if (g.tiles[nIdx] !== FLOOR) continue;
      if (blocked && blocked(nx, ny)) continue;
      dist[nIdx] = d + 1;
      queue[tail++] = nIdx;
    }
  }
  return dist;
}

/** True when every listed target is reachable from `from`. */
export function allReachable(
  g: GridView,
  from: { x: number; y: number },
  targets: ReadonlyArray<{ x: number; y: number }>,
  blocked?: (x: number, y: number) => boolean,
): boolean {
  const dist = bfsDistance(g, [from], blocked);
  for (const t of targets) {
    if (!inBounds(g, t.x, t.y)) return false;
    if (dist[t.y * g.width + t.x] < 0) return false;
  }
  return true;
}

/**
 * Carves a straight L-shaped passage between two tiles. Used by the repair pass when a
 * room's interior detailing accidentally strands a pocket of floor.
 */
export function carveL(g: GridView, ax: number, ay: number, bx: number, by: number): void {
  const stepX = Math.sign(bx - ax);
  const stepY = Math.sign(by - ay);
  let x = ax;
  let y = ay;
  while (x !== bx) {
    set(g, x, y, FLOOR);
    x += stepX;
  }
  while (y !== by) {
    set(g, x, y, FLOOR);
    y += stepY;
  }
  set(g, bx, by, FLOOR);
}

/**
 * Guarantees the whole floor area is one connected component.
 *
 * The BSP spanning tree already makes rooms reachable from each other; this catches
 * pockets created by the per-room detail passes (cubicle warrens especially). Each
 * orphan component is joined to the main one by the shortest axis-aligned carve.
 */
export function repairConnectivity(g: GridView, from: { x: number; y: number }): number {
  let repairs = 0;
  for (let guard = 0; guard < 64; guard++) {
    const dist = bfsDistance(g, [from]);
    // Find the orphan tile closest to any reachable tile.
    let bestOrphan = -1;
    let bestAnchor = -1;
    let bestCost = Infinity;
    for (let idx = 0; idx < g.tiles.length; idx++) {
      if (g.tiles[idx] !== FLOOR || dist[idx] >= 0) continue;
      const ox = idx % g.width;
      const oy = (idx / g.width) | 0;
      // Search outward for a reachable tile; the radius cap keeps this cheap.
      for (let r = 1; r <= 12 && r < bestCost; r++) {
        for (let dy = -r; dy <= r; dy++) {
          const ay = oy + dy;
          if (ay < 0 || ay >= g.height) continue;
          const span = r - Math.abs(dy);
          for (const dx of [-span, span]) {
            const ax = ox + dx;
            if (ax < 0 || ax >= g.width) continue;
            const aIdx = ay * g.width + ax;
            if (g.tiles[aIdx] !== FLOOR || dist[aIdx] < 0) continue;
            if (r < bestCost) {
              bestCost = r;
              bestOrphan = idx;
              bestAnchor = aIdx;
            }
          }
        }
      }
    }
    if (bestOrphan < 0) return repairs;
    carveL(
      g,
      bestOrphan % g.width,
      (bestOrphan / g.width) | 0,
      bestAnchor % g.width,
      (bestAnchor / g.width) | 0,
    );
    repairs++;
  }
  return repairs;
}

/** Nearest floor tile to a point, searched in expanding rings. */
export function nearestFloor(g: GridView, x: number, y: number, maxRadius = 24): { x: number; y: number } | null {
  if (get(g, x, y) === FLOOR) return { x, y };
  for (let r = 1; r <= maxRadius; r++) {
    for (let dy = -r; dy <= r; dy++) {
      const span = r - Math.abs(dy);
      for (const dx of span === 0 ? [0] : [-span, span]) {
        const nx = x + dx;
        const ny = y + dy;
        if (get(g, nx, ny) === FLOOR) return { x: nx, y: ny };
      }
    }
  }
  return null;
}

/**
 * Digital differential analyser line-of-sight test against the wall grid.
 *
 * Both the renderer's flashlight shadows and the server's entity vision use this exact
 * traversal, so what a monster can see matches what the player sees it seeing.
 * Coordinates are in tile space (fractional).
 */
export function hasLineOfSight(g: GridView, ax: number, ay: number, bx: number, by: number): boolean {
  let x = Math.floor(ax);
  let y = Math.floor(ay);
  const endX = Math.floor(bx);
  const endY = Math.floor(by);
  const dx = bx - ax;
  const dy = by - ay;
  const stepX = dx > 0 ? 1 : -1;
  const stepY = dy > 0 ? 1 : -1;
  const invDx = dx === 0 ? Infinity : Math.abs(1 / dx);
  const invDy = dy === 0 ? Infinity : Math.abs(1 / dy);
  let tMaxX = dx === 0 ? Infinity : (dx > 0 ? x + 1 - ax : ax - x) * invDx;
  let tMaxY = dy === 0 ? Infinity : (dy > 0 ? y + 1 - ay : ay - y) * invDy;

  for (let guard = 0; guard < 512; guard++) {
    if (x === endX && y === endY) return true;
    if (tMaxX < tMaxY) {
      x += stepX;
      tMaxX += invDx;
    } else {
      y += stepY;
      tMaxY += invDy;
    }
    if (!inBounds(g, x, y)) return false;
    if (g.tiles[y * g.width + x] !== FLOOR) return false;
    if (tMaxX > 1 && tMaxY > 1) return x === endX && y === endY;
  }
  return false;
}

export function levelGrid(level: Level): GridView {
  return { width: level.width, height: level.height, tiles: level.tiles };
}
