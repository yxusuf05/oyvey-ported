/**
 * Navigation on the tile grid.
 *
 * Two tiers, no navmesh. Tier one is A* over the room/portal graph, which has fewer than
 * a hundred nodes and resolves instantly. Tier two is grid A* restricted to the corridor
 * of rooms tier one chose, which bounds the search to a few hundred cells even on a
 * 128x128 map. A monster that thinks for two milliseconds is a monster that never stalls
 * the tick.
 */

import { FLOOR, type Level } from '@game/shared/levelgen';

export interface NavTile {
  x: number;
  y: number;
}

const MAX_GRID_NODES = 4000;

export class Navigator {
  private readonly level: Level;
  /** Doors closed by the descent. Paths through them are invalid. */
  private readonly sealed = new Set<number>();
  /** Adjacency over rooms, keyed by room id. */
  private readonly roomGraph: number[][] = [];
  private roomPathCache = new Map<string, number[] | null>();

  constructor(level: Level) {
    this.level = level;
    this.roomGraph = level.rooms.map(() => []);
    this.rebuildRoomGraph();
  }

  sealDoor(doorId: number): void {
    this.sealed.add(doorId);
    this.roomPathCache.clear();
    this.rebuildRoomGraph();
  }

  isSealedTile(x: number, y: number): boolean {
    for (const id of this.sealed) {
      const d = this.level.doors[id];
      if (d.x === x && d.y === y) return true;
    }
    return false;
  }

  private rebuildRoomGraph(): void {
    for (const list of this.roomGraph) list.length = 0;
    for (const door of this.level.doors) {
      if (this.sealed.has(door.id)) continue;
      if (door.a < 0 || door.b < 0 || door.a === door.b) continue;
      this.roomGraph[door.a].push(door.b);
      this.roomGraph[door.b].push(door.a);
    }
  }

  passable(x: number, y: number): boolean {
    if (x < 0 || y < 0 || x >= this.level.width || y >= this.level.height) return false;
    if (this.level.tiles[y * this.level.width + x] !== FLOOR) return false;
    return !this.isSealedTile(x, y);
  }

  roomAt(x: number, y: number): number {
    if (x < 0 || y < 0 || x >= this.level.width || y >= this.level.height) return -1;
    return this.level.roomOf[y * this.level.width + x];
  }

  /** Breadth-first over the room graph — the graph is tiny, so BFS is already optimal. */
  private roomPath(from: number, to: number): number[] | null {
    if (from < 0 || to < 0) return null;
    if (from === to) return [from];
    const key = `${from}:${to}`;
    const cached = this.roomPathCache.get(key);
    if (cached !== undefined) return cached;

    const prev = new Int32Array(this.level.rooms.length).fill(-2);
    prev[from] = -1;
    const queue = [from];
    let found = false;
    for (let head = 0; head < queue.length && !found; head++) {
      const node = queue[head];
      for (const next of this.roomGraph[node]) {
        if (prev[next] !== -2) continue;
        prev[next] = node;
        if (next === to) {
          found = true;
          break;
        }
        queue.push(next);
      }
    }
    if (!found) {
      this.roomPathCache.set(key, null);
      return null;
    }
    const path: number[] = [];
    for (let node = to; node !== -1; node = prev[node]) path.push(node);
    path.reverse();
    this.roomPathCache.set(key, path);
    return path;
  }

  /**
   * Grid A* from `start` to `goal`, restricted to the rooms on the room-graph path so the
   * open set stays small. Returns tile waypoints, or null if unreachable.
   */
  findPath(start: NavTile, goal: NavTile): NavTile[] | null {
    if (!this.passable(goal.x, goal.y)) return null;
    if (start.x === goal.x && start.y === goal.y) return [];

    const startRoom = this.roomAt(start.x, start.y);
    const goalRoom = this.roomAt(goal.x, goal.y);
    const corridor = this.roomPath(startRoom, goalRoom);
    // A null corridor means the rooms are genuinely disconnected; an unrestricted search
    // would just burn the node budget confirming it.
    if (corridor === null && startRoom !== goalRoom) return null;
    const allowed = corridor ? new Set(corridor) : null;

    const w = this.level.width;
    const startIdx = start.y * w + start.x;
    const goalIdx = goal.y * w + goal.x;

    const gScore = new Map<number, number>();
    const cameFrom = new Map<number, number>();
    // Binary heap keyed by f-score.
    const heap: { idx: number; f: number }[] = [];
    const push = (idx: number, f: number): void => {
      heap.push({ idx, f });
      let i = heap.length - 1;
      while (i > 0) {
        const parent = (i - 1) >> 1;
        if (heap[parent].f <= heap[i].f) break;
        [heap[parent], heap[i]] = [heap[i], heap[parent]];
        i = parent;
      }
    };
    const pop = (): { idx: number; f: number } | undefined => {
      if (heap.length === 0) return undefined;
      const top = heap[0];
      const last = heap.pop()!;
      if (heap.length > 0) {
        heap[0] = last;
        let i = 0;
        for (;;) {
          const l = i * 2 + 1;
          const r = l + 1;
          let smallest = i;
          if (l < heap.length && heap[l].f < heap[smallest].f) smallest = l;
          if (r < heap.length && heap[r].f < heap[smallest].f) smallest = r;
          if (smallest === i) break;
          [heap[smallest], heap[i]] = [heap[i], heap[smallest]];
          i = smallest;
        }
      }
      return top;
    };

    const heuristic = (idx: number): number => {
      const x = idx % w;
      const y = (idx / w) | 0;
      return Math.abs(x - goal.x) + Math.abs(y - goal.y);
    };

    gScore.set(startIdx, 0);
    push(startIdx, heuristic(startIdx));
    let expanded = 0;

    while (heap.length > 0 && expanded < MAX_GRID_NODES) {
      const current = pop()!;
      if (current.idx === goalIdx) return this.reconstruct(cameFrom, goalIdx);
      expanded++;
      const cx = current.idx % w;
      const cy = (current.idx / w) | 0;
      const g = gScore.get(current.idx) ?? 0;

      for (let n = 0; n < 4; n++) {
        const nx = cx + (n === 0 ? 1 : n === 1 ? -1 : 0);
        const ny = cy + (n === 2 ? 1 : n === 3 ? -1 : 0);
        if (!this.passable(nx, ny)) continue;
        if (allowed) {
          const room = this.roomAt(nx, ny);
          // Doorway tiles belong to one side; allow anything adjacent to the corridor.
          if (room >= 0 && !allowed.has(room)) continue;
        }
        const nIdx = ny * w + nx;
        const tentative = g + 1;
        if (tentative >= (gScore.get(nIdx) ?? Infinity)) continue;
        gScore.set(nIdx, tentative);
        cameFrom.set(nIdx, current.idx);
        push(nIdx, tentative + heuristic(nIdx));
      }
    }
    return null;
  }

  private reconstruct(cameFrom: Map<number, number>, goalIdx: number): NavTile[] {
    const w = this.level.width;
    const out: NavTile[] = [];
    let idx: number | undefined = goalIdx;
    while (idx !== undefined) {
      out.push({ x: idx % w, y: (idx / w) | 0 });
      idx = cameFrom.get(idx);
    }
    out.reverse();
    out.shift(); // drop the tile we are already standing on
    return out;
  }

  /** A random reachable floor tile, for patrol targets. */
  randomReachableTile(random: () => number, near?: NavTile, radius = 40): NavTile | null {
    for (let attempt = 0; attempt < 64; attempt++) {
      let x: number;
      let y: number;
      if (near) {
        x = near.x + Math.floor((random() * 2 - 1) * radius);
        y = near.y + Math.floor((random() * 2 - 1) * radius);
      } else {
        x = Math.floor(random() * this.level.width);
        y = Math.floor(random() * this.level.height);
      }
      if (this.passable(x, y)) return { x, y };
    }
    return null;
  }
}
