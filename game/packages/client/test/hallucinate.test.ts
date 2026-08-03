/**
 * Hallucinations, asserted as rules.
 *
 * The two that matter most are the safety switch and the co-op property. The first is a
 * promise to a player who told the game they do not want to be frightened, and "almost
 * never" is not an answer to that. The second is the entire design goal — "did you see
 * that?" / "see what?" only lands if the two players genuinely saw different things.
 */

import { describe, expect, it } from 'vitest';
import {
  HALLUCINATION_COOLDOWN,
  HALLUCINATION_THRESHOLD,
  decideHallucination,
  type HallucinationInput,
} from '../src/game/hallucinate';

const BASE: HallucinationInput = {
  sanity: 0.1,
  scareIntensity: 1,
  enabled: true,
  bucket: 0,
  playerId: 1,
  layoutHash: 0x51ee9,
  sinceLast: 999,
};

/** How many of `count` consecutive seconds produce a hallucination. */
function hits(over: Partial<HallucinationInput>, count = 4000): number {
  let seen = 0;
  for (let bucket = 0; bucket < count; bucket++) {
    if (decideHallucination({ ...BASE, ...over, bucket })) seen++;
  }
  return seen;
}

describe('hallucinations', () => {
  it('never happens to a player at full sanity', () => {
    expect(hits({ sanity: 1 })).toBe(0);
    expect(hits({ sanity: HALLUCINATION_THRESHOLD })).toBe(0);
  });

  it('never happens at all with scares turned off', () => {
    // The absolute one. Not rarer — never.
    expect(hits({ scareIntensity: 0, sanity: 0 })).toBe(0);
    expect(hits({ enabled: false, sanity: 0 })).toBe(0);
  });

  it('gives two players different hallucinations from the same run', () => {
    const one: number[] = [];
    const two: number[] = [];
    for (let bucket = 0; bucket < 4000; bucket++) {
      if (decideHallucination({ ...BASE, playerId: 1, bucket })) one.push(bucket);
      if (decideHallucination({ ...BASE, playerId: 2, bucket })) two.push(bucket);
    }

    expect(one.length).toBeGreaterThan(20);
    expect(two.length).toBeGreaterThan(20);
    // A handful of coincidences is fine and expected; near-identical lists would mean the
    // seed does not actually depend on who is looking.
    const shared = one.filter((bucket) => two.includes(bucket)).length;
    expect(shared).toBeLessThan(Math.min(one.length, two.length) * 0.35);
  });

  it('is reproducible: the same second always gives the same answer', () => {
    for (let bucket = 0; bucket < 200; bucket++) {
      expect(decideHallucination({ ...BASE, bucket })).toEqual(decideHallucination({ ...BASE, bucket }));
    }
  });

  it('stays quiet during the cooldown', () => {
    expect(hits({ sinceLast: 0, sanity: 0 })).toBe(0);
    expect(hits({ sinceLast: HALLUCINATION_COOLDOWN - 0.01, sanity: 0 })).toBe(0);
    expect(hits({ sinceLast: HALLUCINATION_COOLDOWN, sanity: 0 })).toBeGreaterThan(0);
  });

  it('gets worse as sanity falls, and never better', () => {
    const curve = [0.65, 0.5, 0.35, 0.2, 0.05, 0].map((sanity) => hits({ sanity }));
    for (let i = 1; i < curve.length; i++) {
      expect(curve[i], `sanity step ${i} should not be calmer than the one before`).toBeGreaterThanOrEqual(
        curve[i - 1],
      );
    }
    expect(curve[0]).toBeLessThan(curve[curve.length - 1]);
  });

  it('scales with the intensity scalar rather than ignoring it', () => {
    const full = hits({ sanity: 0, scareIntensity: 1 });
    const half = hits({ sanity: 0, scareIntensity: 0.5 });
    expect(half).toBeLessThan(full);
    expect(half).toBeGreaterThan(0);
  });

  it('decides how far, how long and what kind independently of whether it fires', () => {
    // One hash driving all four would correlate them: rare hallucinations would then always
    // also be near ones, and players feel that pattern long before they can name it.
    const results = [];
    for (let bucket = 0; bucket < 6000; bucket++) {
      const decision = decideHallucination({ ...BASE, sanity: 0, bucket });
      if (decision) results.push(decision);
    }
    expect(results.length).toBeGreaterThan(50);

    const distances = results.map((r) => r.distance);
    expect(Math.max(...distances) - Math.min(...distances)).toBeGreaterThan(15);
    expect(new Set(results.map((r) => r.kind)).size).toBe(3);
  });

  it('never returns an intensity above the ceiling the player chose', () => {
    for (const scareIntensity of [0.25, 0.5, 1]) {
      for (let bucket = 0; bucket < 2000; bucket++) {
        const decision = decideHallucination({ ...BASE, sanity: 0, scareIntensity, bucket });
        if (decision) expect(decision.intensity).toBeLessThanOrEqual(scareIntensity);
      }
    }
  });
});
