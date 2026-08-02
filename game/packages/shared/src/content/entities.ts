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

export const ENTITY_SPECS: Record<string, EntitySpec> = {
  blind: BLIND_ONE,
};

export function getEntitySpec(kind: string): EntitySpec {
  return ENTITY_SPECS[kind] ?? BLIND_ONE;
}
