/**
 * Level generation entry point.
 *
 * `generateLevel(seed, themeId)` is a pure function: the server calls it to run the
 * simulation, the client calls it with the same arguments to build geometry, and the two
 * results are asserted identical via `layoutHash`. Nothing in here may read the clock,
 * `Math.random`, or any ambient state.
 */

import { fnv1a32Bytes, Rng } from '../prng';
import { buildDescentTimeline } from './descent';
import { buildStructure } from './generate';
import { placeFixtures } from './light';
import { placeObjectives } from './objectives';
import { getTheme } from './themes';
import type { Level } from './types';

export function generateLevel(seed: string, themeId: string): Level {
  const spec = getTheme(themeId);
  const { grid, rooms, doors, roomOf } = buildStructure(seed, spec);
  const rng = new Rng(seed);

  const { spawn, objectives } = placeObjectives(
    rng.derive('levelgen:objectives'),
    grid,
    rooms,
    roomOf,
    spec.fuses,
  );

  const fixtures = placeFixtures(rng.derive('levelgen:fixtures'), grid, rooms, spec.fixtureSpacing);

  const descent = buildDescentTimeline(
    rng.derive('levelgen:descent'),
    grid,
    rooms,
    doors,
    spawn,
    objectives,
  );

  return {
    seed,
    themeId: spec.id,
    width: grid.width,
    height: grid.height,
    tiles: grid.tiles,
    roomOf,
    rooms,
    doors,
    fixtures,
    spawn,
    objectives,
    descent,
    layoutHash: fnv1a32Bytes(grid.tiles),
  };
}

export * from './types';
export * from './themes';
export * from './grid';
export * from './light';
export { eventsCrossed } from './descent';
