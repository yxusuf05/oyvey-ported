/**
 * Spawn and objective placement.
 *
 * Placement is what gives a run its shape. Fuses sit in the middle-to-far distance band
 * and are forced apart from each other, so players spread out; the exit sits far away on
 * a different branch of the BSP tree, so the run ends with a sprint back through a level
 * that has gone dark in the meantime.
 */

import type { Rng } from '../prng';
import { bfsDistance, get, type GridView } from './grid';
import { FLOOR, type ObjectivePlacement, type Room } from './types';

export interface PlacementResult {
  spawn: { x: number; y: number };
  objectives: ObjectivePlacement[];
}

/** Rooms whose interior is mostly open make better spawn points than warrens. */
function spawnScore(room: Room): number {
  const area = (room.x1 - room.x0 + 1) * (room.y1 - room.y0 + 1);
  const kindBonus = room.kind === 'openHall' ? 1.5 : room.kind === 'atrium' ? 1.3 : room.kind === 'pillarField' ? 1.1 : 0.6;
  return area * kindBonus;
}

export function placeObjectives(
  rng: Rng,
  grid: GridView,
  rooms: Room[],
  roomOf: Int16Array,
  fuseCount: number,
): PlacementResult {
  // Spawn: a roomy, open leaf, biased towards the middle of the map so no direction is
  // obviously "the long way".
  const cx = grid.width / 2;
  const cy = grid.height / 2;
  const spawnCandidates = rooms
    .filter((r) => get(grid, r.cx, r.cy) === FLOOR)
    .map((r) => {
      const dx = r.cx - cx;
      const dy = r.cy - cy;
      const centreBias = 1 - Math.min(1, Math.sqrt(dx * dx + dy * dy) / (grid.width * 0.5));
      return { room: r, score: spawnScore(r) * (0.5 + centreBias) };
    })
    .sort((a, b) => b.score - a.score);

  const spawnRoom = spawnCandidates.length > 0
    ? spawnCandidates[rng.int(0, Math.min(4, spawnCandidates.length - 1))].room
    : rooms[0];
  const spawn = { x: spawnRoom.cx, y: spawnRoom.cy };

  const dist = bfsDistance(grid, [spawn]);
  let dmax = 0;
  for (let i = 0; i < dist.length; i++) if (dist[i] > dmax) dmax = dist[i];
  if (dmax === 0) dmax = 1;

  const objectives: ObjectivePlacement[] = [];

  // --- Exit: far away and on a different top-level branch than the spawn. ---
  const spawnSide = spawnRoom.branchDepth > 0 ? spawnRoom.branch >> (spawnRoom.branchDepth - 1) : 0;
  const exitCandidates = rooms.filter((r) => {
    if (r.id === spawnRoom.id) return false;
    if (get(grid, r.cx, r.cy) !== FLOOR) return false;
    const d = dist[r.cy * grid.width + r.cx];
    return d >= 0 && d >= dmax * 0.72;
  });
  const differentBranch = exitCandidates.filter(
    (r) => (r.branchDepth > 0 ? r.branch >> (r.branchDepth - 1) : 0) !== spawnSide,
  );
  const exitPool = differentBranch.length > 0 ? differentBranch : exitCandidates.length > 0 ? exitCandidates : rooms;
  const exitRoom = exitPool[rng.int(0, exitPool.length - 1)];
  objectives.push({ id: objectives.length, kind: 'exit', x: exitRoom.cx, y: exitRoom.cy, room: exitRoom.id });

  // --- Fuses: middle-to-far band, forced apart geodesically. ---
  const minD = dmax * 0.4;
  const maxD = dmax * 0.95;
  const separation = dmax * 0.3;

  const pool: { x: number; y: number; room: number }[] = [];
  for (const room of rooms) {
    if (room.id === exitRoom.id || room.id === spawnRoom.id) continue;
    const spot = pickSpotInRoom(rng, grid, room);
    if (!spot) continue;
    const d = dist[spot.y * grid.width + spot.x];
    if (d < minD || d > maxD) continue;
    pool.push({ ...spot, room: room.id });
  }
  rng.shuffle(pool);

  const chosen: { x: number; y: number; room: number }[] = [];
  // Relax the separation constraint monotonically rather than failing — a slightly
  // clustered run is far better than a run that cannot be generated at all.
  for (let relax = 0; relax < 6 && chosen.length < fuseCount; relax++) {
    const required = separation * (1 - relax * 0.18);
    for (const candidate of pool) {
      if (chosen.length >= fuseCount) break;
      if (chosen.some((c) => c.room === candidate.room)) continue;
      const far = chosen.every((c) => {
        const cd = bfsDistance(grid, [c]);
        return cd[candidate.y * grid.width + candidate.x] >= required;
      });
      if (far) chosen.push(candidate);
    }
  }
  // Last-ditch fill so the objective count is always met.
  for (const candidate of pool) {
    if (chosen.length >= fuseCount) break;
    if (chosen.includes(candidate)) continue;
    if (chosen.some((c) => c.room === candidate.room)) continue;
    chosen.push(candidate);
  }

  for (const c of chosen) {
    objectives.push({ id: objectives.length, kind: 'fuse', x: c.x, y: c.y, room: c.room });
  }

  // --- Generator: near the exit, this is what the fuses are carried to. ---
  const genSpot = pickSpotInRoom(rng, grid, exitRoom, [{ x: exitRoom.cx, y: exitRoom.cy }]) ?? {
    x: exitRoom.cx,
    y: exitRoom.cy,
  };
  objectives.push({ id: objectives.length, kind: 'generator', x: genSpot.x, y: genSpot.y, room: exitRoom.id });

  void roomOf;
  return { spawn, objectives };
}

/** A floor tile inside a room, preferring spots away from its edges. */
function pickSpotInRoom(
  rng: Rng,
  grid: GridView,
  room: Room,
  avoid: ReadonlyArray<{ x: number; y: number }> = [],
): { x: number; y: number } | null {
  const options: { x: number; y: number; score: number }[] = [];
  for (let y = room.y0; y <= room.y1; y++) {
    for (let x = room.x0; x <= room.x1; x++) {
      if (get(grid, x, y) !== FLOOR) continue;
      if (avoid.some((a) => Math.abs(a.x - x) + Math.abs(a.y - y) < 3)) continue;
      const edge = Math.min(x - room.x0, room.x1 - x, y - room.y0, room.y1 - y);
      options.push({ x, y, score: edge });
    }
  }
  if (options.length === 0) return null;
  options.sort((a, b) => b.score - a.score);
  // Choose among the most interior third so objectives are never jammed into a corner.
  const top = options.slice(0, Math.max(1, Math.floor(options.length / 3)));
  const pick = top[rng.int(0, top.length - 1)];
  return { x: pick.x, y: pick.y };
}
