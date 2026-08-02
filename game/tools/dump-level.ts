/**
 * Prints a generated level as ASCII so a maze can be judged without launching the game.
 *
 * Usage: pnpm level <seed> [themeId]
 */

import { generateLevel } from '../packages/shared/src/levelgen';
import { bfsDistance, levelGrid } from '../packages/shared/src/levelgen/grid';
import { FLOOR } from '../packages/shared/src/levelgen/types';
import { DEFAULT_THEME_ID } from '../packages/shared/src/levelgen/themes';
import { propagateLight, LIGHT_MAX } from '../packages/shared/src/levelgen/light';

const seed = process.argv[2] ?? 'demo';
const themeId = process.argv[3] ?? DEFAULT_THEME_ID;
const level = generateLevel(seed, themeId);
const light = propagateLight(levelGrid(level), level.fixtures);

const marks = new Map<number, string>();
marks.set(level.spawn.y * level.width + level.spawn.x, 'S');
for (const o of level.objectives) {
  marks.set(o.y * level.width + o.x, o.kind === 'fuse' ? 'F' : o.kind === 'exit' ? 'X' : 'G');
}
for (const d of level.doors) {
  const idx = d.y * level.width + d.x;
  if (!marks.has(idx)) marks.set(idx, d.kind === 'tree' ? '+' : ':');
}

// Brightness ramp so the light propagation is visible in the dump too.
const RAMP = ' .:-=+*#%@';
let out = '';
for (let y = 0; y < level.height; y++) {
  let row = '';
  for (let x = 0; x < level.width; x++) {
    const idx = y * level.width + x;
    const mark = marks.get(idx);
    if (mark) {
      row += mark;
    } else if (level.tiles[idx] !== FLOOR) {
      row += '█';
    } else {
      const t = light[idx] / LIGHT_MAX;
      row += RAMP[Math.min(RAMP.length - 1, Math.floor(t * RAMP.length))];
    }
  }
  out += row + '\n';
}
console.log(out);

const reach = bfsDistance(levelGrid(level), [level.spawn]);
let floors = 0;
let dmax = 0;
for (let i = 0; i < level.tiles.length; i++) {
  if (level.tiles[i] === FLOOR) floors++;
  if (reach[i] > dmax) dmax = reach[i];
}
const kinds = new Map<string, number>();
for (const r of level.rooms) kinds.set(r.kind, (kinds.get(r.kind) ?? 0) + 1);

console.log(`seed=${seed} theme=${themeId} hash=${level.layoutHash.toString(16)}`);
console.log(`rooms=${level.rooms.length} doors=${level.doors.length} fixtures=${level.fixtures.length}`);
console.log(`floor=${((floors / level.tiles.length) * 100).toFixed(1)}% furthest=${dmax} tiles`);
console.log(`kinds=${[...kinds].map(([k, v]) => `${k}:${v}`).join(' ')}`);
console.log(`descent events=${level.descent.length} (${level.descent.filter((e) => e.kind === 'seal').length} seals)`);
console.log('legend: S spawn  F fuse  G generator  X exit  + tree door  : braid door');
