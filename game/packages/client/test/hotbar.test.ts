/**
 * The hotbar, asserted as the rule a player would state.
 *
 * The rule is short: **a slot you paid for is a slot you can reach.** It failed silently
 * before, because the server happily accepted slot 5 from an upgraded player while the
 * client's key handler and hud were both bounded by the default backpack size. Every
 * server-side test passed; the perk was still unusable.
 */

import { describe, expect, it } from 'vitest';
import { BACKPACK_SLOTS } from '@game/shared/content';
import { slotForDigit, visibleSlotCount } from '../src/game/hotbar';

describe('number keys', () => {
  it('selects the slot printed on the key', () => {
    for (let slot = 0; slot < BACKPACK_SLOTS; slot++) {
      expect(slotForDigit(`Digit${slot + 1}`, BACKPACK_SLOTS)).toBe(slot);
    }
  });

  it('reaches the slots the backpack perk paid for', () => {
    // Two extra slots is the perk at full level. Before the fix these two lines were the
    // whole bug: the keys did nothing at all.
    const upgraded = BACKPACK_SLOTS + 2;
    expect(slotForDigit(`Digit${BACKPACK_SLOTS + 1}`, upgraded)).toBe(BACKPACK_SLOTS);
    expect(slotForDigit(`Digit${BACKPACK_SLOTS + 2}`, upgraded)).toBe(BACKPACK_SLOTS + 1);
  });

  it('refuses a slot the backpack does not have', () => {
    expect(slotForDigit(`Digit${BACKPACK_SLOTS + 1}`, BACKPACK_SLOTS)).toBeNull();
    expect(slotForDigit('Digit9', BACKPACK_SLOTS)).toBeNull();
  });

  it('assumes the default backpack until the server has said otherwise', () => {
    // Zero means no inventory message has arrived yet. Refusing every key in that window
    // would make the first second of a run feel broken.
    expect(slotForDigit('Digit1', 0)).toBe(0);
    expect(slotForDigit(`Digit${BACKPACK_SLOTS}`, 0)).toBe(BACKPACK_SLOTS - 1);
    expect(slotForDigit(`Digit${BACKPACK_SLOTS + 1}`, 0)).toBeNull();
  });

  it('ignores keys that are not slot numbers', () => {
    for (const code of ['KeyQ', 'Digit0', 'Escape', 'ShiftLeft', '']) {
      expect(slotForDigit(code, BACKPACK_SLOTS)).toBeNull();
    }
  });
});

describe('the drawn hotbar', () => {
  it('draws a cell for every slot the player owns', () => {
    expect(visibleSlotCount(BACKPACK_SLOTS + 2)).toBe(BACKPACK_SLOTS + 2);
  });

  it('never collapses below the default, even before the first inventory arrives', () => {
    expect(visibleSlotCount(0)).toBe(BACKPACK_SLOTS);
  });

  it('shows exactly as many cells as there are keys that work', () => {
    // The two functions must never disagree: a cell you can see but not select, or a key
    // that selects a slot with no cell, are both the same bug wearing different clothes.
    for (const owned of [0, BACKPACK_SLOTS, BACKPACK_SLOTS + 1, BACKPACK_SLOTS + 2]) {
      const cells = visibleSlotCount(owned);
      for (let i = 0; i < cells; i++) {
        expect(slotForDigit(`Digit${i + 1}`, owned)).toBe(i);
      }
      expect(slotForDigit(`Digit${cells + 1}`, owned)).toBeNull();
    }
  });
});
