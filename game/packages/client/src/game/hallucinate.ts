/**
 * What the player thinks they saw.
 *
 * Hallucinations are the one thing in the game the server never decides. They cannot touch
 * state, so letting every player have their own costs nothing — and that is precisely the
 * point: "did you see that?" / "see what?" only works if you genuinely did not.
 *
 * This module is deliberately pure. No `Math.random`, no clock, no renderer: given the same
 * inputs it always returns the same answer. That makes the whole system unit-testable, and
 * it is the reason the safety switch can be *proven* rather than eyeballed.
 */

import { clamp01 } from '@game/shared/math';
import { hash3f } from '@game/shared/prng';

export type HallucinationKind =
  /** A figure standing down the corridor, gone before you can focus on it. */
  | 'figure'
  /** Something at the edge of vision, off to one side. */
  | 'peripheral'
  /** No figure at all. A sting and a flinch, with nothing there. */
  | 'falseScare';

export interface HallucinationInput {
  /** 0..1. */
  sanity: number;
  /** The accessibility scalar. At 0 this function is guaranteed to return null. */
  scareIntensity: number;
  /** The `hallucinations` setting. */
  enabled: boolean;
  /** One-second bucket; the same bucket always yields the same answer. */
  bucket: number;
  playerId: number;
  layoutHash: number;
  /** Seconds since the last hallucination, so they cannot stack up. */
  sinceLast: number;
}

export interface Hallucination {
  kind: HallucinationKind;
  /** Metres ahead of the player, for the kinds that place something. */
  distance: number;
  /** Sideways offset in radians from the player's facing. */
  bearing: number;
  /** Seconds it stays. */
  duration: number;
  /** Feeds `triggerScare`, already scaled by the accessibility scalar. */
  intensity: number;
}

/** Nothing happens above this much sanity, and everything scales in below it. */
export const HALLUCINATION_THRESHOLD = 0.7;
/** Seconds of quiet enforced between hallucinations. */
export const HALLUCINATION_COOLDOWN = 8;
/** Chance per second at zero sanity, before the accessibility scalar. */
const MAX_CHANCE_PER_BUCKET = 0.16;

/**
 * Three independent draws, not one.
 *
 * The obvious implementation reuses a single hash for whether it fires, how far away it is
 * and how long it lasts — which correlates all three: a rare hallucination is then always
 * also a near and short one, and players feel the pattern long before they can name it.
 */
const ROLL_TRIGGER = 0x9e37;
const ROLL_PLACE = 0x85eb;
const ROLL_SHAPE = 0xc2b2;

export function decideHallucination(input: HallucinationInput): Hallucination | null {
  if (!input.enabled) return null;
  // The safety switch is absolute, not a reduction. Someone who sets scares to zero has
  // said they do not want this, and "almost never" is not an answer to that.
  if (input.scareIntensity <= 0) return null;
  if (input.sinceLast < HALLUCINATION_COOLDOWN) return null;
  if (input.sanity >= HALLUCINATION_THRESHOLD) return null;

  const pressure = clamp01((HALLUCINATION_THRESHOLD - input.sanity) / HALLUCINATION_THRESHOLD);
  const chance = pressure * MAX_CHANCE_PER_BUCKET * clamp01(input.scareIntensity);

  const trigger = hash3f(input.playerId ^ ROLL_TRIGGER, input.bucket, input.layoutHash);
  if (trigger >= chance) return null;

  const place = hash3f(input.playerId ^ ROLL_PLACE, input.bucket, input.layoutHash);
  const shape = hash3f(input.playerId ^ ROLL_SHAPE, input.bucket, input.layoutHash);

  // The deeper the player is, the more likely the hallucination is one that grabs rather
  // than one that merely suggests. Early on it is almost always something in the corner of
  // the eye, which is the version players learn to doubt themselves over.
  const kind: HallucinationKind =
    shape < 0.18 + pressure * 0.22 ? 'falseScare' : shape < 0.62 ? 'peripheral' : 'figure';

  return {
    kind,
    distance: 7 + place * 26,
    // Figures stand where you are looking; peripheral shapes sit out at the edge, far
    // enough that turning to face them is a decision.
    bearing: kind === 'peripheral' ? (place < 0.5 ? -1 : 1) * (0.7 + shape * 0.5) : (place - 0.5) * 0.35,
    duration: 0.35 + shape * 0.55,
    intensity: (kind === 'falseScare' ? 0.55 + pressure * 0.45 : 0.2) * input.scareIntensity,
  };
}
