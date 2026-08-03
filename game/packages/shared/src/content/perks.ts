/**
 * Meta-progression perks.
 *
 * Pure data, like items and entities. The rule this file exists to enforce is that every
 * perk here **changes something measurable in a run**: a perk that only appears in a shop
 * list is a fake feature, and the tests assert the effect rather than the purchase.
 *
 * Costs rise per level so the first point in something is cheap enough to try and the last
 * one is a decision. A run that goes well pays roughly one early level.
 */

export interface PerkSpec {
  id: string;
  nameKey: string;
  descriptionKey: string;
  maxLevel: number;
  /** Cost of buying level `n`, one-indexed. */
  costs: number[];
}

export const PERK_SPECS: Record<string, PerkSpec> = {
  backpack: {
    id: 'backpack',
    nameKey: 'perk.backpack',
    descriptionKey: 'perk.backpack.desc',
    // Two extra slots at most. Six is already enough to stop the loadout being a decision,
    // and the decision is the point of the backpack existing.
    maxLevel: 2,
    costs: [220, 520],
  },
  boots: {
    id: 'boots',
    nameKey: 'perk.boots',
    descriptionKey: 'perk.boots.desc',
    maxLevel: 3,
    costs: [140, 300, 620],
  },
  battery: {
    id: 'battery',
    nameKey: 'perk.battery',
    descriptionKey: 'perk.battery.desc',
    maxLevel: 3,
    costs: [120, 260, 540],
  },
  medic: {
    id: 'medic',
    nameKey: 'perk.medic',
    descriptionKey: 'perk.medic.desc',
    maxLevel: 2,
    costs: [180, 420],
  },
};

export type PerkLevels = Record<string, number>;

export function getPerkSpec(id: string): PerkSpec | null {
  return PERK_SPECS[id] ?? null;
}

/** Cost of the next level, or null if the perk is unknown or already maxed. */
export function perkCost(id: string, currentLevel: number): number | null {
  const spec = getPerkSpec(id);
  if (!spec) return null;
  if (currentLevel >= spec.maxLevel) return null;
  return spec.costs[currentLevel] ?? null;
}

export function perkLevel(perks: PerkLevels, id: string): number {
  return perks[id] ?? 0;
}

// ---------------------------------------------------------------------------
// Effects — one function per perk, so the numbers live next to the description
// ---------------------------------------------------------------------------

/** Extra backpack slots. */
export function bonusSlots(perks: PerkLevels): number {
  return perkLevel(perks, 'backpack');
}

/** Multiplier on the noise a player's movement puts into the field. */
export function noiseFactor(perks: PerkLevels): number {
  return 0.8 ** perkLevel(perks, 'boots');
}

/** Multiplier on flashlight battery drain. */
export function batteryFactor(perks: PerkLevels): number {
  return 0.8 ** perkLevel(perks, 'battery');
}

/** Multiplier on how long picking a teammate up takes. */
export function reviveFactor(perks: PerkLevels): number {
  return 0.75 ** perkLevel(perks, 'medic');
}
