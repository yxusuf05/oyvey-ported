/**
 * Item catalogue.
 *
 * Items are pure data. The server switches on `kind` and reads the numbers; nothing here
 * knows about rendering, audio or the network. Adding a tool means adding an entry and a
 * case in `Run.useActiveItem`, not a new subsystem.
 *
 * The one rule every entry obeys: **using a tool makes noise.** `noiseOnUse` goes into the
 * same noise field footsteps do, which the Blind One hunts by and which drives the descent.
 * That is what stops the backpack from being free power — every advantage you reach for is
 * also an announcement of where you are.
 *
 * The remaining tools from the design (chalk, camcorder, EMF, radio, decoy, door wedge)
 * land next and slot into this same shape.
 */

export type ItemKind =
  /** Held and toggled; has an ongoing effect rather than a one-shot one. */
  | 'tool'
  /** Using it puts an object into the world that stays there. */
  | 'placeable'
  /** Using it spends one and applies an effect to the user. */
  | 'consumable';

export interface ItemSpec {
  id: string;
  nameKey: string;
  descriptionKey: string;
  /** Backpack slots consumed. Limited capacity is what makes a loadout a decision. */
  slots: number;
  /** Whether holding this item has a primary action bound to the use key. */
  usable: boolean;
  kind: ItemKind;
  /** How many fit into one slot. */
  stack: number;
  /** Seconds before the use key does anything again. */
  cooldown: number;
  /** Loudness pushed into the noise field on use. */
  noiseOnUse: number;
  /** Sound key emitted on use. Every value here needs a subtitle in en.ts *and* de.ts. */
  useSound: string;
  /** Placeables: seconds the dropped object survives. 0 means forever. */
  burnSeconds?: number;
  /** Consumables: how much of each pool one use restores. */
  restoreSanity?: number;
  restoreHp?: number;
  /** Placeables that light the room: radius in metres and linear RGB. */
  lightRange?: number;
  lightColor?: [number, number, number];
}

export const FLASHLIGHT: ItemSpec = {
  id: 'flashlight',
  nameKey: 'item.flashlight',
  descriptionKey: 'item.flashlight.desc',
  slots: 1,
  usable: true,
  kind: 'tool',
  stack: 1,
  cooldown: 0.25,
  // A switch clicks. Audible in a silent corridor, nowhere near a footstep.
  noiseOnUse: 1.5,
  useSound: 'item.flashlightClick',
};

export const GLOWSTICK: ItemSpec = {
  id: 'glowstick',
  nameKey: 'item.glowstick',
  descriptionKey: 'item.glowstick.desc',
  slots: 1,
  usable: true,
  kind: 'placeable',
  stack: 4,
  cooldown: 0.4,
  noiseOnUse: 2.5,
  useSound: 'item.glowstickCrack',
  // Three minutes is long enough to mark a route back, short enough that a floor littered
  // with them has stopped being a solution by the time the level goes dark.
  burnSeconds: 180,
  lightRange: 7.5,
  lightColor: [0.35, 0.95, 0.55],
};

export const ALMOND_WATER: ItemSpec = {
  id: 'almondWater',
  nameKey: 'item.almondWater',
  descriptionKey: 'item.almondWater.desc',
  slots: 1,
  usable: true,
  kind: 'consumable',
  stack: 2,
  cooldown: 1.2,
  noiseOnUse: 3,
  useSound: 'item.useConsumable',
  restoreSanity: 45,
};

export const MEDKIT: ItemSpec = {
  id: 'medkit',
  nameKey: 'item.medkit',
  descriptionKey: 'item.medkit.desc',
  slots: 1,
  usable: true,
  kind: 'consumable',
  stack: 1,
  // Slow and loud on purpose: patching yourself up in the open should be a decision you
  // can regret having made at the wrong moment.
  cooldown: 2.5,
  noiseOnUse: 6,
  useSound: 'item.useConsumable',
  restoreHp: 55,
};

export const ITEM_SPECS: Record<string, ItemSpec> = {
  flashlight: FLASHLIGHT,
  glowstick: GLOWSTICK,
  almondWater: ALMOND_WATER,
  medkit: MEDKIT,
};

export function getItemSpec(id: string): ItemSpec | null {
  return ITEM_SPECS[id] ?? null;
}

export const BACKPACK_SLOTS = 4;

/**
 * What everyone starts a run with. One slot is deliberately left empty: a backpack with no
 * room in it turns every pickup in the level into a non-decision.
 */
export const DEFAULT_LOADOUT: { item: string; count: number }[] = [
  { item: 'flashlight', count: 1 },
  { item: 'glowstick', count: 2 },
  { item: 'almondWater', count: 1 },
];

/** What the server scatters through the level as loot, with relative weights. */
export const LOOT_TABLE: { item: string; weight: number }[] = [
  { item: 'glowstick', weight: 42 },
  { item: 'almondWater', weight: 30 },
  { item: 'medkit', weight: 28 },
];
