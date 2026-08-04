/**
 * The descent timeline: the scripted decay of an already-generated level.
 *
 * Built at generation time rather than at runtime for two reasons. First, the client can
 * pre-build every post-mutation chunk mesh while loading, so the world never stalls to
 * re-mesh mid-chase. Second — and this is the important one — every `seal` is validated
 * against a fresh flood fill here, so the maze provably stays completable no matter how
 * far the descent goes. The server only ever transmits `{index, tick}`.
 */

import type { Rng } from '../prng';
import { bfsDistance, type GridView } from './grid';
import type { DescentEvent, Door, ObjectivePlacement, Room } from './types';

const LIGHTS_OUT_THRESHOLDS = [0.26, 0.38, 0.5, 0.61, 0.72, 0.84, 0.94];
const THEME_SHIFT_THRESHOLDS = [0.2, 0.48, 0.76];
const SEAL_THRESHOLDS = [0.45, 0.58, 0.68, 0.78, 0.87, 0.93];

/**
 * How hard a theme leans on each kind of decay.
 *
 * The two are meant to pull in different directions: a place that lives by its lights is
 * ruined by losing them, and a place that is already narrow is ruined by getting narrower.
 * One timeline builder, two opposite kinds of dread — and the seal validation below is
 * untouched either way, so a level stays provably completable however aggressive the bias.
 */
export interface ThemeHazards {
  /** Multiplier on how many doorways the descent is allowed to close. */
  sealBias: number;
  /** Multiplier on how many blackout waves are scheduled. */
  lightsOutBias: number;
}

export const DEFAULT_HAZARDS: ThemeHazards = { sealBias: 1, lightsOutBias: 1 };

/** Keeps a bias from removing a stage entirely or inventing thresholds that do not exist. */
function scaleStages(count: number, bias: number): number {
  return Math.max(1, Math.min(count, Math.round(count * bias)));
}

export function buildDescentTimeline(
  rng: Rng,
  grid: GridView,
  rooms: Room[],
  doors: Door[],
  spawn: { x: number; y: number },
  objectives: ObjectivePlacement[],
  hazards: ThemeHazards = DEFAULT_HAZARDS,
): DescentEvent[] {
  const events: DescentEvent[] = [];
  const dist = bfsDistance(grid, [spawn]);
  const distOf = (r: Room) => {
    const d = dist[r.cy * grid.width + r.cx];
    return d < 0 ? Number.MAX_SAFE_INTEGER : d;
  };

  // --- Lights out: sweeps from the far edge inward, so the player walks into darkness
  // rather than watching it arrive. ---
  const byDistanceDesc = [...rooms].sort((a, b) => distOf(b) - distOf(a));
  const lightWaves = scaleStages(LIGHTS_OUT_THRESHOLDS.length, hazards.lightsOutBias);
  const waves = chunk(byDistanceDesc, lightWaves);
  waves.forEach((wave, i) => {
    if (wave.length === 0) return;
    const shuffled = rng.derive('descent:lights', i).shuffle(wave.map((r) => r.id));
    // Fewer, bigger waves on a theme that leans away from darkness; more, smaller ones on
    // a theme that lives by its lights. The thresholds themselves never move, so the whole
    // curve still lands inside the same run.
    events.push({ kind: 'lightsOut', at: LIGHTS_OUT_THRESHOLDS[i], rooms: shuffled });
  });

  // --- Theme shift: the rot creeps outward from where the players are, the opposite
  // direction to the darkness. The two waves crossing is what makes the middle of a run
  // feel like the level is closing around you. ---
  const byDistanceAsc = [...rooms].sort((a, b) => distOf(a) - distOf(b));
  chunk(byDistanceAsc, THEME_SHIFT_THRESHOLDS.length).forEach((wave, i) => {
    if (wave.length === 0) return;
    events.push({
      kind: 'themeShift',
      at: THEME_SHIFT_THRESHOLDS[i],
      rooms: wave.map((r) => r.id),
      stage: i + 1,
    });
  });

  // --- Seals: the maze physically tightens. Validated cumulatively. ---
  const targets = objectives.map((o) => ({ x: o.x, y: o.y }));
  const sealed = new Set<number>();
  const sealRng = rng.derive('descent:seal');
  // Braid doors are preferred because removing a loop cannot disconnect anything, but a
  // tree door is allowed too when the flood fill proves it is safe.
  const candidates = [
    ...sealRng.shuffle(doors.filter((d) => d.kind === 'braid').map((d) => d.id)),
    ...sealRng.shuffle(doors.filter((d) => d.kind === 'tree').map((d) => d.id)),
  ];

  const blocked = (x: number, y: number): boolean => {
    for (const id of sealed) {
      const d = doors[id];
      if (d.x === x && d.y === y) return true;
    }
    return false;
  };

  const sealStages = scaleStages(SEAL_THRESHOLDS.length, hazards.sealBias);
  for (const threshold of SEAL_THRESHOLDS.slice(0, sealStages)) {
    for (const id of candidates) {
      if (sealed.has(id)) continue;
      sealed.add(id);
      const reach = bfsDistance(grid, [spawn], blocked);
      const ok = targets.every((t) => reach[t.y * grid.width + t.x] >= 0);
      if (ok) {
        events.push({ kind: 'seal', at: threshold, door: id });
        break;
      }
      sealed.delete(id);
    }
  }

  events.sort((a, b) => a.at - b.at);
  return events;
}

function chunk<T>(items: T[], parts: number): T[][] {
  const out: T[][] = Array.from({ length: parts }, () => []);
  if (items.length === 0) return out;
  const per = items.length / parts;
  items.forEach((item, i) => {
    const bucket = Math.min(parts - 1, Math.floor(i / per));
    out[bucket].push(item);
  });
  return out;
}

/** Events whose threshold lies in `(from, to]` — what the server broadcasts each tick. */
export function eventsCrossed(events: DescentEvent[], from: number, to: number): number[] {
  const out: number[] = [];
  for (let i = 0; i < events.length; i++) {
    if (events[i].at > from && events[i].at <= to) out.push(i);
  }
  return out;
}
