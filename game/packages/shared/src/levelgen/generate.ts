/**
 * Structural level generation: BSP partition, room detailing, spanning-tree doors,
 * braiding, and the connectivity guarantee.
 *
 * Why BSP and not wave function collapse: the BSP tree *is* a spanning tree, so putting
 * exactly one door on each internal node's shared boundary makes the level connected by
 * construction, with no post-hoc repair needed for the room graph. It also produces the
 * long straight sightlines the backrooms aesthetic depends on, which WFC's local-patch
 * coherence tends to destroy.
 */

import { Rng } from '../prng';
import { fillRect, get, repairConnectivity, set, type GridView } from './grid';
import { FLOOR, SOLID, ATRIUM_CEILING_HEIGHT, CEILING_HEIGHT, type Door, type Room, type RoomKind } from './types';
import type { ThemeSpec } from './themes';

interface BspNode {
  x0: number;
  y0: number;
  x1: number;
  y1: number;
  branch: number;
  depth: number;
  axis?: 'x' | 'y';
  pos?: number;
  left?: BspNode;
  right?: BspNode;
  leafId?: number;
}

export interface StructureResult {
  grid: GridView;
  rooms: Room[];
  doors: Door[];
  roomOf: Int16Array;
}

/** Recursively splits the playable rectangle. Leaves become rooms. */
function partition(rng: Rng, spec: ThemeSpec, x0: number, y0: number, x1: number, y1: number): BspNode {
  const root: BspNode = { x0, y0, x1, y1, branch: 0, depth: 0 };
  const stack: BspNode[] = [root];

  while (stack.length > 0) {
    const node = stack.pop()!;
    const w = node.x1 - node.x0 + 1;
    const h = node.y1 - node.y0 + 1;

    // A split needs room for two minimum leaves plus the one-tile wall line between them.
    const canX = w >= spec.minLeaf * 2 + 1;
    const canY = h >= spec.minLeaf * 2 + 1;
    const mustSplit = w > spec.maxLeaf || h > spec.maxLeaf;

    if (!canX && !canY) continue;
    if (node.depth >= spec.maxDepth && !mustSplit) continue;
    if (!mustSplit && rng.derive('bsp:stop', node.branch * 97 + node.depth).chance(0.22)) continue;

    let axis: 'x' | 'y';
    if (canX && canY) {
      // Split the longer side unless the rectangle is near-square, then let the seed decide.
      const ratio = w / h;
      if (ratio > 1.25) axis = 'x';
      else if (ratio < 0.8) axis = 'y';
      else axis = rng.derive('bsp:axis', node.branch * 131 + node.depth).chance(0.5) ? 'x' : 'y';
    } else {
      axis = canX ? 'x' : 'y';
    }

    const lo = axis === 'x' ? node.x0 + spec.minLeaf : node.y0 + spec.minLeaf;
    const hi = axis === 'x' ? node.x1 - spec.minLeaf : node.y1 - spec.minLeaf;
    if (hi < lo) continue;

    const posRng = rng.derive('bsp:pos', node.branch * 7919 + node.depth);
    const mid = (lo + hi) / 2;
    // Gaussian jitter keeps rooms varied without producing slivers.
    const jittered = Math.round(posRng.gaussian(mid, (hi - lo) * 0.22));
    const pos = Math.max(lo, Math.min(hi, jittered));

    node.axis = axis;
    node.pos = pos;
    node.left =
      axis === 'x'
        ? { x0: node.x0, y0: node.y0, x1: pos - 1, y1: node.y1, branch: (node.branch << 1) | 0, depth: node.depth + 1 }
        : { x0: node.x0, y0: node.y0, x1: node.x1, y1: pos - 1, branch: (node.branch << 1) | 0, depth: node.depth + 1 };
    node.right =
      axis === 'x'
        ? { x0: pos + 1, y0: node.y0, x1: node.x1, y1: node.y1, branch: (node.branch << 1) | 1, depth: node.depth + 1 }
        : { x0: node.x0, y0: pos + 1, x1: node.x1, y1: node.y1, branch: (node.branch << 1) | 1, depth: node.depth + 1 };

    stack.push(node.left, node.right);
  }
  return root;
}

function collectLeaves(root: BspNode): BspNode[] {
  const out: BspNode[] = [];
  const stack = [root];
  while (stack.length) {
    const n = stack.pop()!;
    if (n.left && n.right) {
      stack.push(n.left, n.right);
    } else {
      n.leafId = out.length;
      out.push(n);
    }
  }
  // Depth-first order depends on stack order, which is deterministic — but sort anyway so
  // room ids are a stable function of position rather than traversal implementation.
  out.sort((a, b) => a.y0 - b.y0 || a.x0 - b.x0);
  out.forEach((n, i) => (n.leafId = i));
  return out;
}

/** Fills a room's interior according to its kind. Every kind must leave at least one floor tile. */
function carveRoom(g: GridView, room: Room, rng: Rng): void {
  const { x0, y0, x1, y1, kind } = room;
  switch (kind) {
    case 'openHall':
    case 'atrium':
      fillRect(g, x0, y0, x1, y1, FLOOR);
      break;

    case 'pillarField': {
      fillRect(g, x0, y0, x1, y1, FLOOR);
      const spacing = rng.int(3, 4);
      const offX = rng.int(1, spacing);
      const offY = rng.int(1, spacing);
      for (let y = y0 + offY; y < y1; y += spacing) {
        for (let x = x0 + offX; x < x1; x += spacing) set(g, x, y, SOLID);
      }
      break;
    }

    case 'corridorBundle': {
      fillRect(g, x0, y0, x1, y1, SOLID);
      const w = x1 - x0 + 1;
      const h = y1 - y0 + 1;
      const alongX = w >= h;
      const spacing = rng.int(3, 4);
      if (alongX) {
        for (let y = y0 + rng.int(0, 1); y <= y1; y += spacing) fillRect(g, x0, y, x1, y, FLOOR);
        // One perpendicular spine so the parallel corridors are joined to each other.
        const spine = rng.int(x0, x1);
        fillRect(g, spine, y0, spine, y1, FLOOR);
      } else {
        for (let x = x0 + rng.int(0, 1); x <= x1; x += spacing) fillRect(g, x, y0, x, y1, FLOOR);
        const spine = rng.int(y0, y1);
        fillRect(g, x0, spine, x1, spine, FLOOR);
      }
      break;
    }

    case 'cubicleWarren': {
      fillRect(g, x0, y0, x1, y1, FLOOR);
      warren(g, rng, x0, y0, x1, y1, 0);
      break;
    }
  }
}

/**
 * The signature backrooms interior: nested partitions with narrow gaps, producing the
 * "every room looks like the last one" disorientation that the whole game trades on.
 */
function warren(g: GridView, rng: Rng, x0: number, y0: number, x1: number, y1: number, depth: number): void {
  const w = x1 - x0 + 1;
  const h = y1 - y0 + 1;
  if (depth >= 4 || (w < 6 && h < 6)) return;

  const horizontal = h > w ? true : w > h ? false : rng.chance(0.5);
  if (horizontal) {
    if (h < 6) return;
    const p = rng.int(y0 + 2, y1 - 2);
    fillRect(g, x0, p, x1, p, SOLID);
    const gaps = 1 + (w > 11 ? 1 : 0);
    for (let i = 0; i < gaps; i++) set(g, rng.int(x0, x1), p, FLOOR);
    warren(g, rng, x0, y0, x1, p - 1, depth + 1);
    warren(g, rng, x0, p + 1, x1, y1, depth + 1);
  } else {
    if (w < 6) return;
    const p = rng.int(x0 + 2, x1 - 2);
    fillRect(g, p, y0, p, y1, SOLID);
    const gaps = 1 + (h > 11 ? 1 : 0);
    for (let i = 0; i < gaps; i++) set(g, p, rng.int(y0, y1), FLOOR);
    warren(g, rng, x0, y0, p - 1, y1, depth + 1);
    warren(g, rng, p + 1, y0, x1, y1, depth + 1);
  }
}

/** Punches the one door that makes an internal BSP node's two subtrees reachable. */
function punchTreeDoor(g: GridView, node: BspNode, rng: Rng, doors: Door[], roomOf: Int16Array): void {
  const axis = node.axis!;
  const pos = node.pos!;
  const lo = axis === 'x' ? node.y0 : node.x0;
  const hi = axis === 'x' ? node.y1 : node.x1;

  const candidates: number[] = [];
  for (let t = lo; t <= hi; t++) {
    const ax = axis === 'x' ? pos - 1 : t;
    const ay = axis === 'x' ? t : pos - 1;
    const bx = axis === 'x' ? pos + 1 : t;
    const by = axis === 'x' ? t : pos + 1;
    if (get(g, ax, ay) === FLOOR && get(g, bx, by) === FLOOR) candidates.push(t);
  }

  let t: number;
  if (candidates.length > 0) {
    t = candidates[rng.int(0, candidates.length - 1)];
  } else {
    // No aligned floor on both sides — force a passage by tunnelling outward from the
    // wall line until floor is met. This keeps the connectivity guarantee absolute.
    t = Math.floor((lo + hi) / 2);
    for (const dir of [-1, 1]) {
      for (let step = 1; step <= 8; step++) {
        const x = axis === 'x' ? pos + dir * step : t;
        const y = axis === 'x' ? t : pos + dir * step;
        if (get(g, x, y) === FLOOR) break;
        set(g, x, y, FLOOR);
      }
    }
  }

  const dx = axis === 'x' ? pos : t;
  const dy = axis === 'x' ? t : pos;
  set(g, dx, dy, FLOOR);

  const aIdx = axis === 'x' ? dy * g.width + (dx - 1) : (dy - 1) * g.width + dx;
  const bIdx = axis === 'x' ? dy * g.width + (dx + 1) : (dy + 1) * g.width + dx;
  doors.push({
    id: doors.length,
    x: dx,
    y: dy,
    a: roomOf[aIdx] ?? -1,
    b: roomOf[bIdx] ?? -1,
    kind: 'tree',
  });
}

/**
 * Adds loops. A pure BSP tree is all dead ends, and a chase in a dead-end maze is just a
 * coin flip — the player either happens to run the right way or dies. Loops turn fleeing
 * into a decision.
 */
function braid(g: GridView, rng: Rng, roomOf: Int16Array, doors: Door[], target: number): void {
  const existing = new Set<string>();
  for (const d of doors) existing.add(d.a < d.b ? `${d.a}:${d.b}` : `${d.b}:${d.a}`);

  const candidates: { x: number; y: number; a: number; b: number }[] = [];
  for (let y = 1; y < g.height - 1; y++) {
    for (let x = 1; x < g.width - 1; x++) {
      if (g.tiles[y * g.width + x] === FLOOR) continue;
      // Horizontal opening.
      const l = g.tiles[y * g.width + (x - 1)];
      const r = g.tiles[y * g.width + (x + 1)];
      const u = g.tiles[(y - 1) * g.width + x];
      const d = g.tiles[(y + 1) * g.width + x];
      let a = -1;
      let b = -1;
      if (l === FLOOR && r === FLOOR && u !== FLOOR && d !== FLOOR) {
        a = roomOf[y * g.width + (x - 1)];
        b = roomOf[y * g.width + (x + 1)];
      } else if (u === FLOOR && d === FLOOR && l !== FLOOR && r !== FLOOR) {
        a = roomOf[(y - 1) * g.width + x];
        b = roomOf[(y + 1) * g.width + x];
      }
      if (a < 0 || b < 0 || a === b) continue;
      candidates.push({ x, y, a, b });
    }
  }

  rng.shuffle(candidates);
  const perPair = new Map<string, number>();
  let added = 0;
  for (const c of candidates) {
    if (added >= target) break;
    const key = c.a < c.b ? `${c.a}:${c.b}` : `${c.b}:${c.a}`;
    const count = perPair.get(key) ?? (existing.has(key) ? 1 : 0);
    // At most two connections between the same pair of rooms, or the level turns to soup.
    if (count >= 2) continue;
    perPair.set(key, count + 1);
    set(g, c.x, c.y, FLOOR);
    doors.push({ id: doors.length, x: c.x, y: c.y, a: c.a, b: c.b, kind: 'braid' });
    added++;
  }
}

export function buildStructure(seed: string, spec: ThemeSpec): StructureResult {
  const rng = new Rng(seed);
  const grid: GridView = {
    width: spec.width,
    height: spec.height,
    tiles: new Uint8Array(spec.width * spec.height).fill(SOLID),
  };

  // The outermost ring stays solid so the maze has an edge you cannot walk off.
  const root = partition(rng.derive('levelgen:bsp'), spec, 2, 2, spec.width - 3, spec.height - 3);
  const leaves = collectLeaves(root);

  const kinds = spec.roomKinds.map((r) => r.kind);
  const weights = spec.roomKinds.map((r) => r.weight);

  const rooms: Room[] = leaves.map((leaf, id) => {
    const kindRng = rng.derive('levelgen:roomKind', id);
    let kind: RoomKind = kindRng.weighted(kinds, weights);
    // Tiny leaves cannot host structure-heavy kinds without collapsing to nothing.
    const w = leaf.x1 - leaf.x0 + 1;
    const h = leaf.y1 - leaf.y0 + 1;
    if ((w < 8 || h < 8) && (kind === 'cubicleWarren' || kind === 'corridorBundle')) kind = 'openHall';
    if ((w < 12 || h < 12) && kind === 'atrium') kind = 'openHall';
    return {
      id,
      x0: leaf.x0,
      y0: leaf.y0,
      x1: leaf.x1,
      y1: leaf.y1,
      kind,
      cx: (leaf.x0 + leaf.x1) >> 1,
      cy: (leaf.y0 + leaf.y1) >> 1,
      branch: leaf.branch,
      branchDepth: leaf.depth,
      doors: [],
      ceilingHeight: kind === 'atrium' ? ATRIUM_CEILING_HEIGHT : CEILING_HEIGHT,
    };
  });

  for (const room of rooms) carveRoom(grid, room, rng.derive('levelgen:carve', room.id));

  // Room ownership must be assigned before doors, because a door records which two rooms
  // it joins and reads that from the tiles either side of it.
  const roomOf = new Int16Array(grid.width * grid.height).fill(-1);
  for (const room of rooms) {
    for (let y = room.y0; y <= room.y1; y++) {
      for (let x = room.x0; x <= room.x1; x++) roomOf[y * grid.width + x] = room.id;
    }
  }

  const doors: Door[] = [];
  const stack = [root];
  const internal: BspNode[] = [];
  while (stack.length) {
    const n = stack.pop()!;
    if (n.left && n.right) {
      internal.push(n);
      stack.push(n.left, n.right);
    }
  }
  // Deepest nodes first keeps door placement independent of traversal order.
  internal.sort((a, b) => b.depth - a.depth || a.x0 - b.x0 || a.y0 - b.y0);
  for (const node of internal) {
    punchTreeDoor(grid, node, rng.derive('levelgen:door', node.branch * 31 + node.depth), doors, roomOf);
  }

  braid(
    grid,
    rng.derive('levelgen:braid'),
    roomOf,
    doors,
    Math.round(spec.braidFactor * rooms.length),
  );

  for (const d of doors) {
    if (d.a >= 0) rooms[d.a].doors.push(d.id);
    if (d.b >= 0 && d.b !== d.a) rooms[d.b].doors.push(d.id);
  }

  // Move each centroid onto an actual floor tile so spawn/objective placement is safe.
  for (const room of rooms) {
    if (get(grid, room.cx, room.cy) !== FLOOR) {
      const found = findFloorIn(grid, room);
      if (found) {
        room.cx = found.x;
        room.cy = found.y;
      }
    }
  }

  const anchor = rooms.find((r) => get(grid, r.cx, r.cy) === FLOOR) ?? rooms[0];
  repairConnectivity(grid, { x: anchor.cx, y: anchor.cy });

  return { grid, rooms, doors, roomOf };
}

function findFloorIn(g: GridView, room: Room): { x: number; y: number } | null {
  for (let y = room.y0; y <= room.y1; y++) {
    for (let x = room.x0; x <= room.x1; x++) {
      if (get(g, x, y) === FLOOR) return { x, y };
    }
  }
  return null;
}
