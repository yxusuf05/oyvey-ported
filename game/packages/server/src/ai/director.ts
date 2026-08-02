/**
 * The director.
 *
 * A roguelite without one produces runs that feel arbitrary rather than hard: three
 * monsters converge at minute two and the players learn nothing except that the game is
 * unfair. What is tuned here is the tension curve — how many things may hunt at once, how
 * long the quiet after a chase lasts, and how close to a player anything may appear.
 */

import { clamp } from '@game/shared/math';

/** Minimum and maximum seconds of calm after a chase resolves. */
const QUIET_MIN = 18;
const QUIET_MAX = 35;
/** Metres. Nothing may spawn or wake up closer than this to a living player. */
export const MIN_SPAWN_DISTANCE = 20;

export class Director {
  private quietRemaining = 0;
  private huntingIds = new Set<number>();
  private readonly random: () => number;

  constructor(random: () => number) {
    this.random = random;
  }

  /** Concurrent hunters allowed, ramping with the descent. */
  maxHunters(descent: number): number {
    if (descent < 0.35) return 1;
    if (descent < 0.7) return 2;
    return 3;
  }

  update(dt: number): void {
    if (this.quietRemaining > 0) this.quietRemaining = Math.max(0, this.quietRemaining - dt);
  }

  get inQuietPeriod(): boolean {
    return this.quietRemaining > 0;
  }

  /**
   * Whether `entityId` may escalate to a hunt right now. An entity already hunting always
   * gets to keep hunting — revoking that mid-chase would look like the monster losing
   * interest for no reason, which reads as a bug.
   */
  canHunt(entityId: number, descent: number): boolean {
    if (this.huntingIds.has(entityId)) return true;
    if (this.quietRemaining > 0) return false;
    return this.huntingIds.size < this.maxHunters(descent);
  }

  beginHunt(entityId: number): void {
    this.huntingIds.add(entityId);
  }

  endHunt(entityId: number): void {
    if (!this.huntingIds.delete(entityId)) return;
    // The quiet period starts only when the last hunter gives up, so two monsters
    // trading off does not reset the clock and rob the players of any breathing room.
    if (this.huntingIds.size === 0) {
      this.quietRemaining = QUIET_MIN + this.random() * (QUIET_MAX - QUIET_MIN);
    }
  }

  get activeHunts(): number {
    return this.huntingIds.size;
  }

  /**
   * Escalation pressure in [0, 1], combining descent with how much of the objective is
   * done. Collecting fuses makes the level more dangerous — the game punishes progress,
   * and that is where the tension in a run actually comes from.
   */
  static pressure(descent: number, fusesCollected: number, fuseTotal: number): number {
    const objective = fuseTotal > 0 ? fusesCollected / fuseTotal : 0;
    return clamp(descent * 0.7 + objective * 0.3, 0, 1);
  }
}
