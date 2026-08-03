/**
 * Meta-progression, asserted as rules.
 *
 * The single most important one is that progress survives a restart. Everything else in
 * this file describes an object in memory; only that test describes a *roguelite*, which is
 * what the whole block exists to make this game into.
 */

import { beforeAll, describe, expect, it } from 'vitest';
import { BACKPACK_SLOTS, PERK_SPECS, perkCost } from '@game/shared/content';
import { Buttons, DT, type Input } from '@game/shared/sim';
import { worldToTile } from '@game/shared/levelgen';
import { ProgressionStore } from '../src/persist/store';
import { Run, type ServerPlayer } from '../src/sim/run';

// Every store in this file is in memory: no test writes to disk, and none depends on what
// the one before it left behind.
beforeAll(() => {
  process.env.PRISMA_MEMORY_DB = '1';
});

const SEED = 'progression-fixture';

function feed(player: ServerPlayer, buttons: number, seq: number, yaw = 0): void {
  const input: Input = { seq, buttons, yaw, pitch: 0, slot: 0 };
  player.pending.push(input);
}

describe('the store', () => {
  it('starts every profile empty', () => {
    const store = new ProgressionStore();
    const profile = store.load('fresh-player');
    expect(profile.credits).toBe(0);
    expect(profile.runs).toBe(0);
    expect(profile.perks).toEqual({});
    store.close();
  });

  it('banks exactly what a run was worth', () => {
    const store = new ProgressionStore();
    store.award('p1', 340, 0.62);
    const profile = store.award('p1', 160, 0.4);

    expect(profile.credits).toBe(500);
    expect(profile.runs).toBe(2);
    // Deepest is a high-water mark, not the last value — a shallow run after a deep one
    // must not erase the deep one.
    expect(profile.deepest).toBeCloseTo(0.62, 5);
    store.close();
  });

  it('refuses a purchase nobody can afford, and charges nothing for the refusal', () => {
    const store = new ProgressionStore();
    store.award('poor', 10, 0);

    const result = store.buyPerk('poor', 'backpack');
    expect(result.ok).toBe(false);

    const profile = store.load('poor');
    expect(profile.credits).toBe(10);
    expect(profile.perks.backpack ?? 0).toBe(0);
    store.close();
  });

  it('charges exactly the price and grants exactly one level', () => {
    const store = new ProgressionStore();
    store.award('rich', 5000, 0);
    const price = perkCost('boots', 0)!;

    const result = store.buyPerk('rich', 'boots');
    expect(result.ok).toBe(true);
    if (!result.ok) return;

    expect(result.profile.credits).toBe(5000 - price);
    expect(result.profile.perks.boots).toBe(1);
    store.close();
  });

  it('stops at the level cap', () => {
    const store = new ProgressionStore();
    store.award('rich', 100000, 0);
    const spec = PERK_SPECS.backpack;

    for (let level = 0; level < spec.maxLevel; level++) {
      expect(store.buyPerk('rich', 'backpack').ok, `level ${level + 1} should be buyable`).toBe(true);
    }

    const beyond = store.buyPerk('rich', 'backpack');
    expect(beyond.ok).toBe(false);
    expect(store.load('rich').perks.backpack).toBe(spec.maxLevel);
    store.close();
  });

  it('refuses a perk that does not exist', () => {
    const store = new ProgressionStore();
    store.award('rich', 100000, 0);
    expect(store.buyPerk('rich', 'wings').ok).toBe(false);
    expect(store.load('rich').credits).toBe(100000);
    store.close();
  });

  it('keeps progress across a close and reopen', () => {
    // The whole point. Without this the shop is a session-length illusion.
    const file = new URL('.', import.meta.url).pathname;
    delete process.env.PRISMA_MEMORY_DB;

    const directory = `${file}../../../node_modules/.tmp-progression-${Date.now()}`;
    const first = new ProgressionStore(directory);
    first.award('persistent', 900, 0.5);
    first.buyPerk('persistent', 'boots');
    const before = first.load('persistent');
    first.close();

    const second = new ProgressionStore(directory);
    const after = second.load('persistent');
    second.close();
    process.env.PRISMA_MEMORY_DB = '1';

    expect(after.credits).toBe(before.credits);
    expect(after.runs).toBe(before.runs);
    expect(after.perks.boots).toBe(1);
  });
});

describe('perks in a run', () => {
  it('gives the backpack perk real slots', () => {
    const run = new Run(SEED, 'level0');
    const plain = run.createPlayer(1, 'Plain');
    const upgraded = run.createPlayer(2, 'Upgraded', { backpack: 2 });

    expect(plain.inventory).toHaveLength(BACKPACK_SLOTS);
    expect(upgraded.inventory).toHaveLength(BACKPACK_SLOTS + 2);
    // The extra room is at the end, so the flashlight is still slot 1 for everyone.
    expect(upgraded.inventory[0]?.item).toBe('flashlight');
  });

  it('lets the upgraded player select the slots they paid for', () => {
    const run = new Run(SEED, 'level0');
    const upgraded = run.createPlayer(1, 'Upgraded', { backpack: 2 });

    upgraded.pending.push({ seq: 1, buttons: 0, yaw: 0, pitch: 0, slot: BACKPACK_SLOTS + 1 });
    run.step([upgraded], DT);

    expect(upgraded.activeSlot).toBe(BACKPACK_SLOTS + 1);
  });

  it('makes soft boots genuinely quieter', () => {
    // Two identical runs, one player each, same input for the same number of ticks. The
    // only difference is the perk, so the noise field is the only thing that can differ.
    const measure = (perks: Record<string, number>): number => {
      const run = new Run(SEED, 'level0');
      const player = run.createPlayer(1, 'Runner', perks);
      let loudest = 0;
      for (let tick = 0; tick < 120; tick++) {
        feed(player, Buttons.Forward | Buttons.Sprint, tick + 1);
        run.step([player], DT);
        const tile = worldToTile(run.level, player.state.x, player.state.z);
        loudest = Math.max(loudest, run.noise.at(tile.x, tile.y));
      }
      return loudest;
    };

    const plain = measure({});
    const quiet = measure({ boots: 3 });

    expect(plain).toBeGreaterThan(0);
    expect(quiet).toBeGreaterThan(0);
    expect(quiet).toBeLessThan(plain * 0.75);
  });
});
