/**
 * Entity definitions.
 *
 * Every entity is meant to be a rule the player can learn, not a dice roll. That is why
 * the tuning lives in data with named fields: `hearingRadius` with `sightRange: 0` is the
 * whole design of the Blind One, and it should be readable without opening the AI code.
 */

import { EntityKind } from '../protocol/messages';

export interface EntitySpec {
  kind: string;
  kindId: number;
  nameKey: string;
  /** Metres per second per behaviour state. */
  patrolSpeed: number;
  investigateSpeed: number;
  huntSpeed: number;
  /** Tiles. Zero means the entity cannot see at all. */
  sightRange: number;
  /** Half-angle of the vision cone, radians. */
  sightHalfAngle: number;
  /**
   * How strongly darkness helps or hurts. 1 = sees only in bright light,
   * -1 = sees only in darkness, 0 = light makes no difference.
   */
  lightBias: number;
  /** Tiles. Zero means the entity is deaf. */
  hearingRadius: number;
  /** Loudness below which a noise is ignored entirely. */
  hearingThreshold: number;
  /** Awareness accumulates rather than snapping, so players can read the build-up. */
  awarenessRise: number;
  awarenessDecay: number;
  /** Awareness at which the entity commits to a hunt. */
  huntThreshold: number;
  /** Metres. */
  attackRange: number;
  attackDamage: number;
  /** Mandatory warning before anything lethal — enforced by the director too. */
  telegraphSeconds: number;
  attackCooldown: number;
  /** Metres at which the entity's own sounds become audible to the player. */
  audibleRange: number;
  /** Collision radius. */
  radius: number;
  /**
   * Freezes solid while a player's flashlight beam is on it. The Smiler's whole rule, and
   * the reason its counter costs battery.
   */
  freezesInBeam?: boolean;
  /**
   * Moves only while no living player is looking at it. Checked entirely server-side —
   * "I am not looking" is exactly the claim a modified client would want to make.
   */
  movesOnlyUnobserved?: boolean;
  /**
   * Marks a swarm. One field rather than three flags, because the three properties are one
   * idea and never occur apart: it steers toward light instead of noise, it arrives as a
   * group, and the director never sees it.
   *
   * That last part is not a detail. The director caps concurrent hunters at one to three;
   * five bodies all asking to hunt would eat the entire budget and silently switch the
   * Blind One and the Smiler off. A swarm is ambient pressure, not a chase.
   */
  swarm?: { group: number };
}

export const BLIND_ONE: EntitySpec = {
  kind: 'blind',
  kindId: EntityKind.Blind,
  nameKey: 'entity.blind',
  patrolSpeed: 1.15,
  investigateSpeed: 2.15,
  huntSpeed: 4.55,
  // Genuinely blind. A player who crouches and stops moving is invisible to it, and that
  // is the entire lesson this entity teaches before the roster gets complicated.
  sightRange: 0,
  sightHalfAngle: 0,
  lightBias: 0,
  hearingRadius: 30,
  hearingThreshold: 1.1,
  awarenessRise: 0.85,
  awarenessDecay: 0.22,
  huntThreshold: 1,
  attackRange: 1.35,
  attackDamage: 55,
  telegraphSeconds: 1.6,
  attackCooldown: 3.5,
  audibleRange: 22,
  radius: 0.45,
};

/**
 * The Smiler. It sees only in the dark, and the light that blinds it also holds it still.
 *
 * That tension is the whole design: point the torch and it cannot move, but the beam is
 * also the only reason it cannot find you — switch off to save battery and you are visible
 * again, immediately. `lightBias: -1` is the entire "sees in the dark" half, handled by the
 * same visibility formula every other entity uses.
 */
export const SMILER: EntitySpec = {
  kind: 'smiler',
  kindId: EntityKind.Smiler,
  nameKey: 'entity.smiler',
  patrolSpeed: 1.4,
  investigateSpeed: 2.6,
  // Faster than the Blind One in a straight line, because the counter is reliable when you
  // have battery. The threat is running out, not losing a race.
  huntSpeed: 4.9,
  sightRange: 22,
  sightHalfAngle: 1.05,
  lightBias: -1,
  // Not deaf, but nearly. Sound only points it roughly your way; the kill comes from sight.
  hearingRadius: 12,
  hearingThreshold: 3.5,
  awarenessRise: 1.1,
  awarenessDecay: 0.3,
  huntThreshold: 1,
  attackRange: 1.5,
  attackDamage: 48,
  telegraphSeconds: 1.5,
  attackCooldown: 4,
  audibleRange: 18,
  radius: 0.42,
  freezesInBeam: true,
};

/**
 * The Watcher. It moves only when nobody is looking.
 *
 * This is the one entity that requires two players to handle gracefully: one holds it in
 * place with a glance while the other does the work. Alone it is a puzzle about how long
 * you dare keep your back turned.
 */
export const WATCHER: EntitySpec = {
  kind: 'watcher',
  kindId: EntityKind.Watcher,
  nameKey: 'entity.watcher',
  // Slow is fine — it never has to hurry, it just has to never be seen moving.
  patrolSpeed: 1.0,
  investigateSpeed: 2.4,
  huntSpeed: 3.9,
  sightRange: 26,
  sightHalfAngle: 1.35,
  lightBias: 0,
  hearingRadius: 16,
  hearingThreshold: 2.2,
  awarenessRise: 0.7,
  awarenessDecay: 0.14,
  huntThreshold: 1,
  attackRange: 1.4,
  attackDamage: 42,
  // Longer warning than the others: being cornered by something that only moved while you
  // blinked is punishing enough without a fast strike on top.
  telegraphSeconds: 2.1,
  attackCooldown: 4.5,
  audibleRange: 14,
  radius: 0.4,
  movesOnlyUnobserved: true,
};

/**
 * The Swarm. Small, many, and interested in light rather than in you.
 *
 * It is the first thing in the game that turns an item against its owner: the glowstick
 * that has been your best way of not getting lost becomes bait, and the counter is to put
 * one down and walk away from it. Individually one of these is a scratch; the danger is
 * arithmetic.
 */
export const SWARM: EntitySpec = {
  kind: 'swarm',
  kindId: EntityKind.Swarm,
  nameKey: 'entity.swarm',
  patrolSpeed: 1.9,
  investigateSpeed: 3.1,
  // Never actually hunts, so this is only ever used if the spec is reused elsewhere.
  huntSpeed: 3.4,
  // It does not see in any useful sense; light is a destination, not a target.
  sightRange: 0,
  sightHalfAngle: 0,
  lightBias: 0,
  hearingRadius: 8,
  hearingThreshold: 6,
  awarenessRise: 0.6,
  awarenessDecay: 0.4,
  huntThreshold: 1,
  attackRange: 1.1,
  // A scratch on its own. Five of them at this cooldown is forty damage a second, which is
  // where "individually weak, lethally many" stops being a slogan and becomes a number.
  attackDamage: 8,
  telegraphSeconds: 0.5,
  attackCooldown: 1.1,
  audibleRange: 12,
  radius: 0.26,
  swarm: { group: 5 },
};

export const ENTITY_SPECS: Record<string, EntitySpec> = {
  blind: BLIND_ONE,
  smiler: SMILER,
  watcher: WATCHER,
  swarm: SWARM,
};

export function getEntitySpec(kind: string): EntitySpec {
  return ENTITY_SPECS[kind] ?? BLIND_ONE;
}
