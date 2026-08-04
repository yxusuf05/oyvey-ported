/**
 * The two hotbar rules that used to be hardcoded fours.
 *
 * `BACKPACK_SLOTS` is the *default* backpack, not every backpack: the backpack perk buys
 * real slots on the server, and both the number-key handler and the hud were bounded by the
 * default. The result was a perk that cost credits and delivered slots the player could
 * neither see nor select — the server accepted them, so no test on the server side noticed.
 *
 * Pure and free of the DOM, so the rules can be asserted directly.
 */

import { BACKPACK_SLOTS } from '@game/shared/content';

const DIGIT = /^Digit([1-9])$/;

/**
 * Which slot a number key selects, or null if this player's backpack has no such slot.
 *
 * `slotCount` is what the server last said the backpack holds. Zero means no inventory has
 * arrived yet, and the default is the safe assumption until one does.
 */
export function slotForDigit(code: string, slotCount: number): number | null {
  const match = DIGIT.exec(code);
  if (!match) return null;

  const slot = Number(match[1]) - 1;
  const limit = slotCount > 0 ? slotCount : BACKPACK_SLOTS;
  return slot < limit ? slot : null;
}

/**
 * How many cells the hotbar should draw.
 *
 * Never fewer than the default, so the row does not collapse in the moment between joining
 * a run and the first inventory message.
 */
export function visibleSlotCount(inventoryLength: number): number {
  return Math.max(BACKPACK_SLOTS, inventoryLength);
}
