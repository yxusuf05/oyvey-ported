/**
 * Noise propagation.
 *
 * Sound is gameplay here, not decoration: this field is the only thing a Sound Hunter
 * can perceive. It is a persistent heat map that decays over time, so a monster arrives
 * where you *were* a moment ago rather than teleporting to where you are — which is what
 * makes crouching and then holding still an actual escape rather than a formality.
 */

import { FLOOR } from '../levelgen/types';
import type { GridView } from '../levelgen/grid';
import { NOISE_DECAY_PER_SEC, NOISE_WALL_COST } from './constants';

/**
 * Loudness lost per tile of open floor.
 *
 * Tuned against the hearing threshold so the ranges are legible: a sprint (loudness 6)
 * carries about fourteen tiles of open floor, a walk (3) about five, and a crouch (0.55)
 * is below the threshold at the source and therefore inaudible at any distance. Crossing
 * a wall costs more than seventeen tiles' worth, so sound goes around corners, not through
 * them.
 */
const FLOOR_STEP_COST = 0.35;
/** Values below this are treated as silence and stop the flood. */
const EPSILON = 0.2;
const BUCKET_RESOLUTION = 0.25;

export class NoiseField {
  readonly width: number;
  readonly height: number;
  readonly field: Float32Array;
  private readonly tiles: Uint8Array;
  /** Scratch buffers reused between emissions so a busy tick allocates nothing. */
  private readonly best: Float32Array;

  constructor(grid: GridView) {
    this.width = grid.width;
    this.height = grid.height;
    this.tiles = grid.tiles;
    this.field = new Float32Array(grid.width * grid.height);
    this.best = new Float32Array(grid.width * grid.height);
  }

  /**
   * Floods `loudness` outward from a tile, attenuating with distance and heavily through
   * walls. Implemented as a bucket-queue Dijkstra: loudness is bounded and quantised, so
   * a binary heap would be slower than an array of buckets.
   */
  emit(sx: number, sy: number, loudness: number): void {
    if (loudness < EPSILON) return;
    if (sx < 0 || sy < 0 || sx >= this.width || sy >= this.height) return;

    const bucketCount = Math.ceil(loudness / BUCKET_RESOLUTION) + 2;
    const buckets: number[][] = Array.from({ length: bucketCount }, () => []);
    const best = this.best;
    const touched: number[] = [];

    const startIdx = sy * this.width + sx;
    best[startIdx] = loudness;
    touched.push(startIdx);
    buckets[0].push(startIdx);

    // Sound crosses a wall at a steep cost rather than not at all, which is what lets a
    // monster in the next room hear you without being able to pinpoint you.
    const relax = (nIdx: number, value: number): void => {
      const cost = this.tiles[nIdx] === FLOOR ? FLOOR_STEP_COST : NOISE_WALL_COST;
      const next = value - cost;
      if (next < EPSILON) return;
      if (best[nIdx] >= next) return;
      if (best[nIdx] === 0) touched.push(nIdx);
      best[nIdx] = next;
      const targetBucket = Math.min(bucketCount - 1, Math.floor((loudness - next) / BUCKET_RESOLUTION));
      buckets[targetBucket].push(nIdx);
    };

    for (let b = 0; b < bucketCount; b++) {
      const bucket = buckets[b];
      for (let i = 0; i < bucket.length; i++) {
        const idx = bucket[i];
        const value = best[idx];
        // Stale entry: this tile was improved after being queued at this level.
        if (loudness - value > (b + 1) * BUCKET_RESOLUTION) continue;
        if (value < EPSILON) continue;

        const x = idx % this.width;
        const y = (idx / this.width) | 0;
        if (x > 0) relax(idx - 1, value);
        if (x < this.width - 1) relax(idx + 1, value);
        if (y > 0) relax(idx - this.width, value);
        if (y < this.height - 1) relax(idx + this.width, value);
      }
    }

    for (const idx of touched) {
      if (best[idx] > this.field[idx]) this.field[idx] = best[idx];
      best[idx] = 0;
    }
  }

  /** Exponential fade. Called once per simulation tick. */
  decay(dt: number): void {
    const factor = Math.pow(NOISE_DECAY_PER_SEC, dt);
    const f = this.field;
    for (let i = 0; i < f.length; i++) {
      const v = f[i] * factor;
      f[i] = v < EPSILON ? 0 : v;
    }
  }

  at(x: number, y: number): number {
    if (x < 0 || y < 0 || x >= this.width || y >= this.height) return 0;
    return this.field[y * this.width + x];
  }

  /**
   * Loudest tile within `radius`, which is what an entity actually navigates towards.
   * Returns null when everything nearby is below the hearing threshold.
   */
  loudestNear(x: number, y: number, radius: number, threshold: number): { x: number; y: number; value: number } | null {
    let bestValue = threshold;
    let bx = -1;
    let by = -1;
    const minX = Math.max(0, x - radius);
    const maxX = Math.min(this.width - 1, x + radius);
    const minY = Math.max(0, y - radius);
    const maxY = Math.min(this.height - 1, y + radius);
    for (let ty = minY; ty <= maxY; ty++) {
      for (let tx = minX; tx <= maxX; tx++) {
        const v = this.field[ty * this.width + tx];
        if (v > bestValue) {
          bestValue = v;
          bx = tx;
          by = ty;
        }
      }
    }
    return bx < 0 ? null : { x: bx, y: by, value: bestValue };
  }

  clear(): void {
    this.field.fill(0);
  }
}
