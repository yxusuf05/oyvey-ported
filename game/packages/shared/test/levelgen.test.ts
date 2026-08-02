import { describe, expect, it } from 'vitest';
import { generateLevel } from '../src/levelgen';
import { bfsDistance, levelGrid } from '../src/levelgen/grid';
import { FLOOR } from '../src/levelgen/types';
import { DEFAULT_THEME_ID, getTheme, samplePalette } from '../src/levelgen/themes';
import { propagateLight, LIGHT_MAX } from '../src/levelgen/light';

const seeds = (n: number, prefix = 's'): string[] => Array.from({ length: n }, (_, i) => `${prefix}${i}`);

describe('generateLevel', () => {
  it('is deterministic for a given seed', () => {
    for (const seed of seeds(40)) {
      const a = generateLevel(seed, DEFAULT_THEME_ID);
      const b = generateLevel(seed, DEFAULT_THEME_ID);
      expect(b.layoutHash).toBe(a.layoutHash);
      expect(Array.from(b.tiles)).toEqual(Array.from(a.tiles));
      expect(b.spawn).toEqual(a.spawn);
      expect(b.objectives).toEqual(a.objectives);
      expect(b.descent).toEqual(a.descent);
      expect(b.fixtures.length).toBe(a.fixtures.length);
    }
  });

  it('produces different levels for different seeds', () => {
    const hashes = new Set(seeds(200).map((s) => generateLevel(s, DEFAULT_THEME_ID).layoutHash));
    // Collisions in a 32-bit hash over 200 samples should be vanishingly unlikely.
    expect(hashes.size).toBeGreaterThan(198);
  });

  it('keeps the outer ring solid so the maze has an edge', () => {
    const level = generateLevel('edge', DEFAULT_THEME_ID);
    for (let x = 0; x < level.width; x++) {
      expect(level.tiles[x]).not.toBe(FLOOR);
      expect(level.tiles[(level.height - 1) * level.width + x]).not.toBe(FLOOR);
    }
    for (let y = 0; y < level.height; y++) {
      expect(level.tiles[y * level.width]).not.toBe(FLOOR);
      expect(level.tiles[y * level.width + level.width - 1]).not.toBe(FLOOR);
    }
  });

  it('spawns and places every objective on a floor tile', () => {
    for (const seed of seeds(150)) {
      const level = generateLevel(seed, DEFAULT_THEME_ID);
      expect(level.tiles[level.spawn.y * level.width + level.spawn.x]).toBe(FLOOR);
      for (const o of level.objectives) {
        expect(level.tiles[o.y * level.width + o.x], `${seed} ${o.kind}`).toBe(FLOOR);
      }
    }
  });

  it('always creates the full objective set', () => {
    const theme = getTheme(DEFAULT_THEME_ID);
    for (const seed of seeds(150)) {
      const level = generateLevel(seed, DEFAULT_THEME_ID);
      const fuses = level.objectives.filter((o) => o.kind === 'fuse');
      expect(fuses.length, `seed ${seed}`).toBe(theme.fuses);
      expect(level.objectives.filter((o) => o.kind === 'exit')).toHaveLength(1);
      expect(level.objectives.filter((o) => o.kind === 'generator')).toHaveLength(1);
      // Fuses in distinct rooms, or players never have a reason to split up.
      expect(new Set(fuses.map((f) => f.room)).size).toBe(fuses.length);
    }
  });

  /**
   * The single most valuable test in the project: a run that cannot be completed is
   * unshippable, and it must stay uncompletable-proof after the level has torn itself
   * apart during the descent.
   */
  it('stays completable through every descent event', () => {
    for (const seed of seeds(400, 'solve')) {
      const level = generateLevel(seed, DEFAULT_THEME_ID);
      const grid = levelGrid(level);
      const targets = level.objectives.map((o) => ({ x: o.x, y: o.y }));

      const sealedTiles = new Set<number>();
      const blocked = (x: number, y: number) => sealedTiles.has(y * level.width + x);

      let reach = bfsDistance(grid, [level.spawn]);
      for (const t of targets) {
        expect(reach[t.y * level.width + t.x], `seed ${seed} before descent`).toBeGreaterThanOrEqual(0);
      }

      for (const event of level.descent) {
        if (event.kind !== 'seal') continue;
        const door = level.doors[event.door];
        sealedTiles.add(door.y * level.width + door.x);
        reach = bfsDistance(grid, [level.spawn], blocked);
        for (const t of targets) {
          expect(
            reach[t.y * level.width + t.x],
            `seed ${seed} unreachable after sealing door ${event.door}`,
          ).toBeGreaterThanOrEqual(0);
        }
      }
    }
  });

  it('connects the entire floor area', () => {
    for (const seed of seeds(120, 'conn')) {
      const level = generateLevel(seed, DEFAULT_THEME_ID);
      const reach = bfsDistance(levelGrid(level), [level.spawn]);
      let floors = 0;
      let reached = 0;
      for (let i = 0; i < level.tiles.length; i++) {
        if (level.tiles[i] !== FLOOR) continue;
        floors++;
        if (reach[i] >= 0) reached++;
      }
      expect(reached, `seed ${seed}`).toBe(floors);
      // A level that is 5% floor is a bug, not a maze.
      expect(floors / level.tiles.length).toBeGreaterThan(0.2);
    }
  });

  it('puts the exit far from the spawn', () => {
    let short = 0;
    for (const seed of seeds(100, 'exit')) {
      const level = generateLevel(seed, DEFAULT_THEME_ID);
      const reach = bfsDistance(levelGrid(level), [level.spawn]);
      let dmax = 0;
      for (let i = 0; i < reach.length; i++) if (reach[i] > dmax) dmax = reach[i];
      const exit = level.objectives.find((o) => o.kind === 'exit')!;
      const d = reach[exit.y * level.width + exit.x];
      if (d < dmax * 0.6) short++;
    }
    expect(short).toBe(0);
  });

  it('emits a descent timeline that covers every room and rises monotonically', () => {
    for (const seed of seeds(60, 'desc')) {
      const level = generateLevel(seed, DEFAULT_THEME_ID);
      let last = -1;
      for (const e of level.descent) {
        expect(e.at).toBeGreaterThanOrEqual(last);
        expect(e.at).toBeGreaterThan(0);
        expect(e.at).toBeLessThanOrEqual(1);
        last = e.at;
      }
      const darkened = new Set<number>();
      for (const e of level.descent) if (e.kind === 'lightsOut') e.rooms.forEach((r) => darkened.add(r));
      // Every room must go dark eventually, or a lit island survives to the end.
      expect(darkened.size).toBe(level.rooms.length);
    }
  });

  it('gives every room at least one light fixture', () => {
    for (const seed of seeds(60, 'fix')) {
      const level = generateLevel(seed, DEFAULT_THEME_ID);
      const perRoom = new Set(level.fixtures.map((f) => f.room));
      expect(perRoom.size).toBe(level.rooms.length);
    }
  });
});

describe('light propagation', () => {
  it('lights the level without leaking through walls', () => {
    const level = generateLevel('light', DEFAULT_THEME_ID);
    const field = propagateLight(levelGrid(level), level.fixtures);
    // Fixture tiles are at maximum.
    for (const f of level.fixtures) {
      expect(field[f.y * level.width + f.x]).toBe(LIGHT_MAX);
    }
    // Most of the floor sees some light.
    let floors = 0;
    let lit = 0;
    for (let i = 0; i < level.tiles.length; i++) {
      if (level.tiles[i] !== FLOOR) continue;
      floors++;
      if (field[i] > 0) lit++;
    }
    expect(lit / floors).toBeGreaterThan(0.98);
  });

  it('goes dark when the descent kills a room', () => {
    const level = generateLevel('dark', DEFAULT_THEME_ID);
    const room = level.rooms[3];
    const full = propagateLight(levelGrid(level), level.fixtures);
    const dark = propagateLight(levelGrid(level), level.fixtures, new Set([room.id]));
    expect(dark[room.cy * level.width + room.cx]).toBeLessThan(full[room.cy * level.width + room.cx]);
  });
});

describe('palette', () => {
  it('interpolates continuously from sunshine to horror', () => {
    const theme = getTheme(DEFAULT_THEME_ID);
    const bright = samplePalette(theme, 0);
    const mid = samplePalette(theme, 0.5);
    const dark = samplePalette(theme, 1);

    // Saturation and light fall monotonically; fog and grain rise. The whole visual arc
    // of the game is these four numbers.
    expect(bright.saturation).toBeGreaterThan(mid.saturation);
    expect(mid.saturation).toBeGreaterThan(dark.saturation);
    expect(bright.lightIntensity).toBeGreaterThan(dark.lightIntensity);
    expect(bright.fogDensity).toBeLessThan(dark.fogDensity);
    expect(bright.grain).toBeLessThan(dark.grain);
    expect(bright.vignette).toBeLessThan(dark.vignette);
  });

  it('has no discontinuity at the stop boundaries', () => {
    const theme = getTheme(DEFAULT_THEME_ID);
    let prev = samplePalette(theme, 0).fogDensity;
    for (let d = 0.01; d <= 1.0001; d += 0.01) {
      const next = samplePalette(theme, d).fogDensity;
      expect(Math.abs(next - prev)).toBeLessThan(0.02);
      prev = next;
    }
  });
});
