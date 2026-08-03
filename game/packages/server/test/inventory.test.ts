/**
 * The backpack, asserted as rules rather than as implementation.
 *
 * Every test here is a sentence a player could say out loud about the game: "a full
 * backpack won't take anything else", "using a tool makes noise", "dropping something and
 * picking it back up loses nothing". If one of these stops holding, the backpack has
 * quietly stopped being the decision the whole loadout screen exists to create.
 */

import { describe, expect, it } from 'vitest';
import { BLIND_ONE, BACKPACK_SLOTS, GLOWSTICK, MEDKIT } from '@game/shared/content';
import { Buttons, DT, PLAYER_MAX_HP, SANITY_MAX, type Input } from '@game/shared/sim';
import { tileToWorld, worldToTile } from '@game/shared/levelgen';
import { Run, type ServerPlayer } from '../src/sim/run';

const SEED = 'inventory-fixture';

function feed(player: ServerPlayer, buttons: number, seq: number, slot = 0): void {
  const input: Input = { seq, buttons, yaw: 0, pitch: 0, slot };
  player.pending.push(input);
}

/** Presses a button for one tick and releases it, so the edge detector sees it exactly once. */
function tap(run: Run, player: ServerPlayer, buttons: number, seq: number, slot = 0): number {
  feed(player, buttons, seq, slot);
  run.step([player], DT);
  feed(player, 0, seq + 1, slot);
  run.step([player], DT);
  return seq + 2;
}

function fresh(): { run: Run; player: ServerPlayer } {
  const run = new Run(SEED, 'level0');
  const player = run.createPlayer(1, 'Packrat');
  // Loot is scattered across the whole floor; these tests want a controlled one.
  run.worldItems.length = 0;
  return { run, player };
}

function slotIndexOf(player: ServerPlayer, item: string): number {
  return player.inventory.findIndex((slot) => slot?.item === item);
}

describe('the backpack', () => {
  it('starts with the default loadout and one free slot to find things with', () => {
    const { player } = fresh();
    expect(player.inventory).toHaveLength(BACKPACK_SLOTS);
    expect(player.inventory.filter((slot) => slot === null)).toHaveLength(1);
    expect(slotIndexOf(player, 'flashlight')).toBeGreaterThanOrEqual(0);
  });

  it('refuses a pickup when it is full, and the item stays on the floor', () => {
    const { run, player } = fresh();
    // Every slot full of something that cannot stack, so there is genuinely nowhere for the
    // almond water to go — leaving the starting bottle in place would let it stack and the
    // test would pass for the wrong reason.
    for (let i = 0; i < player.inventory.length; i++) {
      player.inventory[i] = { item: 'medkit', count: MEDKIT.stack };
    }
    run.worldItems.push({
      id: 999,
      item: 'almondWater',
      x: player.state.x,
      z: player.state.z,
      count: 1,
      lit: false,
      burnLeft: 0,
    });

    tap(run, player, Buttons.Interact, 1);

    expect(slotIndexOf(player, 'almondWater')).toBe(-1);
    expect(run.worldItems.find((w) => w.id === 999)).toBeTruthy();
  });

  it('loses nothing across a drop and a pickup', () => {
    const { run, player } = fresh();
    const slot = slotIndexOf(player, 'glowstick');
    const before = player.inventory[slot]!.count;
    player.activeSlot = slot;

    let seq = tap(run, player, Buttons.Drop, 1, slot);
    expect(player.inventory[slot]).toBeNull();
    expect(run.worldItems).toHaveLength(1);
    expect(run.worldItems[0].count).toBe(before);

    tap(run, player, Buttons.Interact, seq, slot);
    expect(run.worldItems).toHaveLength(0);
    expect(player.inventory[slotIndexOf(player, 'glowstick')]!.count).toBe(before);
  });

  it('counts a consumable down and empties its slot at zero', () => {
    const { run, player } = fresh();
    const slot = slotIndexOf(player, 'almondWater');
    player.inventory[slot] = { item: 'almondWater', count: 2 };
    player.sanity = 10;

    let seq = 1;
    for (let use = 0; use < 2; use++) {
      player.useCooldown = 0;
      seq = tap(run, player, Buttons.UseItem, seq, slot);
    }

    expect(player.inventory[slot]).toBeNull();
  });

  it('caps what a consumable restores', () => {
    const { run, player } = fresh();
    const water = slotIndexOf(player, 'almondWater');
    player.sanity = SANITY_MAX - 2;
    tap(run, player, Buttons.UseItem, 1, water);
    expect(player.sanity).toBe(SANITY_MAX);

    const free = player.inventory.findIndex((slot) => slot === null);
    player.inventory[free] = { item: 'medkit', count: 1 };
    player.hp = PLAYER_MAX_HP - 5;
    player.useCooldown = 0;
    tap(run, player, Buttons.UseItem, 10, free);
    expect(player.hp).toBe(PLAYER_MAX_HP);
  });

  it('makes noise loud enough for the Blind One when a tool is used', () => {
    const { run, player } = fresh();
    const slot = slotIndexOf(player, 'glowstick');
    const tile = worldToTile(run.level, player.state.x, player.state.z);

    run.noise.clear();
    tap(run, player, Buttons.UseItem, 1, slot);

    // Reaching into the backpack is never free. The exact number does not matter; being
    // above the threshold the entity actually listens at is the entire rule.
    expect(run.noise.at(tile.x, tile.y)).toBeGreaterThan(BLIND_ONE.hearingThreshold);
  });

  it('puts a placeable into the world where the player is standing', () => {
    const { run, player } = fresh();
    const slot = slotIndexOf(player, 'glowstick');

    tap(run, player, Buttons.UseItem, 1, slot);

    expect(run.worldItems).toHaveLength(1);
    expect(run.worldItems[0].item).toBe('glowstick');
    expect(run.worldItems[0].burnLeft).toBeCloseTo(GLOWSTICK.burnSeconds!, 0);
    expect(Math.hypot(run.worldItems[0].x - player.state.x, run.worldItems[0].z - player.state.z)).toBeLessThan(0.1);
  });

  it('leaves a glowstick found on the floor unlit until someone cracks it', () => {
    // Scattered loot is not a light source. If "is a glowstick" ever became the same
    // question as "is lit", the level would light itself and the item would stop being
    // worth a backpack slot.
    const run = new Run(SEED, 'level0');
    const found = run.worldItems.filter((world) => world.item === 'glowstick');
    expect(found.length).toBeGreaterThan(0);
    expect(found.every((world) => !world.lit)).toBe(true);

    const player = run.createPlayer(1, 'Packrat');
    run.worldItems.length = 0;
    tap(run, player, Buttons.UseItem, 1, slotIndexOf(player, 'glowstick'));
    expect(run.worldItems[0].lit).toBe(true);
  });

  it('burns a glowstick out and takes it off the floor', () => {
    const { run, player } = fresh();
    const slot = slotIndexOf(player, 'glowstick');
    tap(run, player, Buttons.UseItem, 1, slot);
    expect(run.worldItems).toHaveLength(1);

    // One big step rather than ten thousand small ones: burn-down is linear in dt.
    run.step([player], GLOWSTICK.burnSeconds! + 1);
    expect(run.worldItems).toHaveLength(0);
  });

  it('clamps a slot the client made up instead of reading past the backpack', () => {
    const { run, player } = fresh();
    tap(run, player, 0, 1, 99);
    expect(player.activeSlot).toBe(BACKPACK_SLOTS - 1);

    tap(run, player, 0, 10, -5);
    expect(player.activeSlot).toBe(0);
  });

  it('gives an objective priority over loot lying on the same tile', () => {
    // A glowstick dropped on top of a fuse must never make the fuse unpickable — that is
    // an unwinnable run caused by tidiness.
    const { run, player } = fresh();
    const fuse = run.level.objectives.find((o) => o.kind === 'fuse')!;
    const world = tileToWorld(run.level, fuse.x, fuse.y);

    player.state.x = world.x;
    player.state.z = world.z;
    run.worldItems.push({ id: 7, item: 'medkit', x: world.x, z: world.z, count: 1, lit: false, burnLeft: 0 });

    tap(run, player, Buttons.Interact, 1);

    expect(player.carrying).toBe(fuse.id);
    expect(run.worldItems.find((w) => w.id === 7)).toBeTruthy();
  });
});
