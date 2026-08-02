/**
 * Item catalogue.
 *
 * Only items that are actually wired up appear here — a loadout screen offering tools
 * that do nothing is worse than a short list. The remaining tools from the design
 * (glowsticks, chalk, camcorder, EMF, almond water, medkit, radio, decoy, door wedge)
 * land with the survival-systems milestone and slot into this same shape.
 */

export interface ItemSpec {
  id: string;
  nameKey: string;
  descriptionKey: string;
  /** Backpack slots consumed. Limited capacity is what makes a loadout a decision. */
  slots: number;
  /** Whether holding this item has a primary action bound to the use key. */
  usable: boolean;
}

export const FLASHLIGHT: ItemSpec = {
  id: 'flashlight',
  nameKey: 'item.flashlight',
  descriptionKey: 'item.flashlight.desc',
  slots: 1,
  usable: true,
};

export const ITEM_SPECS: Record<string, ItemSpec> = {
  flashlight: FLASHLIGHT,
};

export const DEFAULT_LOADOUT: string[] = ['flashlight'];
export const BACKPACK_SLOTS = 4;
