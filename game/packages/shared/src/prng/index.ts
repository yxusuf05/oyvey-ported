/**
 * Deterministic pseudo-random number generation.
 *
 * Every value that both the server and the client must agree on comes from here.
 * The generator is integer-only (`Math.imul`, `>>>`) so that Node and every browser
 * engine produce bit-identical output — floating point would eventually drift.
 *
 * The important design element is {@link Rng.derive}: named, independent streams.
 * Adding a new `derive('levelgen:decals')` call somewhere cannot shift the output of
 * any other stream, so a new feature never silently invalidates existing seeds or the
 * golden-hash test. Do not replace this with sequential draws off one shared stream.
 */

/** FNV-1a over a string. Used for stream labels and short seed strings. */
export function fnv1a32(str: string): number {
  let h = 0x811c9dc5;
  for (let i = 0; i < str.length; i++) {
    h ^= str.charCodeAt(i) & 0xff;
    h = Math.imul(h, 0x01000193);
    // Multi-byte code units contribute their high byte too, so non-ASCII seeds hash stably.
    const hi = str.charCodeAt(i) >>> 8;
    if (hi !== 0) {
      h ^= hi;
      h = Math.imul(h, 0x01000193);
    }
  }
  return h >>> 0;
}

/** FNV-1a over raw bytes. Used for the level layout hash. */
export function fnv1a32Bytes(bytes: ArrayLike<number>): number {
  let h = 0x811c9dc5;
  for (let i = 0; i < bytes.length; i++) {
    h ^= bytes[i] & 0xff;
    h = Math.imul(h, 0x01000193);
  }
  return h >>> 0;
}

/** SplitMix32 — used only to expand a single seed into generator state. */
function splitmix32(seed: number): () => number {
  let a = seed >>> 0;
  return () => {
    a = (a + 0x9e3779b9) | 0;
    let t = a ^ (a >>> 16);
    t = Math.imul(t, 0x21f0aaad);
    t ^= t >>> 15;
    t = Math.imul(t, 0x735a2d97);
    t ^= t >>> 15;
    return t >>> 0;
  };
}

const rotl = (x: number, k: number): number => ((x << k) | (x >>> (32 - k))) >>> 0;

/** xoshiro128** — fast, small state, excellent distribution, fully integer. */
export class Rng {
  private s0 = 0;
  private s1 = 0;
  private s2 = 0;
  private s3 = 0;

  /** Root seed retained so derived streams stay independent of draw order. */
  readonly rootSeed: number;

  constructor(seed: number | string) {
    const s = typeof seed === 'string' ? fnv1a32(seed) : seed >>> 0;
    this.rootSeed = s;
    const sm = splitmix32(s === 0 ? 0x9e3779b9 : s);
    this.s0 = sm();
    this.s1 = sm();
    this.s2 = sm();
    this.s3 = sm();
    // A zero state is absorbing; the odds are negligible but the check is free.
    if ((this.s0 | this.s1 | this.s2 | this.s3) === 0) this.s0 = 0x9e3779b9;
  }

  nextU32(): number {
    const result = Math.imul(rotl(Math.imul(this.s1, 5) >>> 0, 7), 9) >>> 0;
    const t = (this.s1 << 9) >>> 0;
    this.s2 = (this.s2 ^ this.s0) >>> 0;
    this.s3 = (this.s3 ^ this.s1) >>> 0;
    this.s1 = (this.s1 ^ this.s2) >>> 0;
    this.s0 = (this.s0 ^ this.s3) >>> 0;
    this.s2 = (this.s2 ^ t) >>> 0;
    this.s3 = rotl(this.s3, 11);
    return result;
  }

  /** Float in [0, 1). 24 bits of mantissa — plenty, and identical everywhere. */
  next(): number {
    return (this.nextU32() >>> 8) / 0x1000000;
  }

  /** Integer in [min, max], inclusive. */
  int(min: number, max: number): number {
    if (max <= min) return min;
    const span = max - min + 1;
    // Rejection sampling keeps the distribution exactly uniform.
    const limit = (0x100000000 - (0x100000000 % span)) >>> 0;
    let v: number;
    do {
      v = this.nextU32();
    } while (v >= limit && limit !== 0);
    return min + (v % span);
  }

  /** Float in [min, max). */
  range(min: number, max: number): number {
    return min + this.next() * (max - min);
  }

  chance(p: number): boolean {
    return this.next() < p;
  }

  pick<T>(items: readonly T[]): T {
    return items[this.int(0, items.length - 1)];
  }

  /** Picks an index from parallel arrays of items and weights. */
  weighted<T>(items: readonly T[], weights: readonly number[]): T {
    let total = 0;
    for (let i = 0; i < items.length; i++) total += weights[i] ?? 0;
    if (total <= 0) return items[0];
    let roll = this.next() * total;
    for (let i = 0; i < items.length; i++) {
      roll -= weights[i] ?? 0;
      if (roll < 0) return items[i];
    }
    return items[items.length - 1];
  }

  /** In-place Fisher-Yates. */
  shuffle<T>(items: T[]): T[] {
    for (let i = items.length - 1; i > 0; i--) {
      const j = this.int(0, i);
      const tmp = items[i];
      items[i] = items[j];
      items[j] = tmp;
    }
    return items;
  }

  /**
   * Approximate gaussian, for jitter that should cluster around a mean.
   *
   * Deliberately Irwin-Hall (sum of six uniforms) rather than Box-Muller: `Math.log` and
   * `Math.cos` are implementation-defined and differ between JS engines, so a Box-Muller
   * draw would generate a different maze in Firefox than on the Node server. Only
   * addition, subtraction and multiplication appear here, and those are IEEE-exact.
   */
  gaussian(mean = 0, stdDev = 1): number {
    let sum = 0;
    for (let i = 0; i < 6; i++) sum += this.next();
    // Sum of six uniforms has mean 3 and standard deviation sqrt(0.5).
    return mean + stdDev * (sum - 3) * 1.4142135623730951;
  }

  /**
   * An independent stream keyed by a label (and optional numeric salt).
   *
   * Two calls with the same label and salt on the same root seed always produce the
   * same stream, and adding a new label anywhere leaves every other stream untouched.
   */
  derive(label: string, salt = 0): Rng {
    const mixed = (this.rootSeed ^ fnv1a32(label) ^ Math.imul(salt + 1, 0x9e3779b9)) >>> 0;
    return new Rng(mixed);
  }
}

/**
 * Stable hash of a (seed, key, bucket) triple, for stateless per-frame decisions such
 * as client-local hallucinations that must not consume a shared stream.
 */
export function hash3(a: number, b: number, c: number): number {
  let h = 0x811c9dc5;
  h = Math.imul(h ^ (a >>> 0), 0x01000193);
  h = Math.imul(h ^ (b >>> 0), 0x01000193);
  h = Math.imul(h ^ (c >>> 0), 0x01000193);
  h ^= h >>> 15;
  h = Math.imul(h, 0x2545f491);
  h ^= h >>> 13;
  return h >>> 0;
}

/** hash3 mapped to [0, 1). */
export function hash3f(a: number, b: number, c: number): number {
  return (hash3(a, b, c) >>> 8) / 0x1000000;
}

const ROOM_CODE_ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';

/** Room codes avoid I/O/0/1 so they survive being read aloud over voice chat. */
export function randomRoomCode(random: () => number = Math.random): string {
  let out = '';
  for (let i = 0; i < 6; i++) {
    out += ROOM_CODE_ALPHABET[Math.floor(random() * ROOM_CODE_ALPHABET.length) % ROOM_CODE_ALPHABET.length];
  }
  return out;
}

export function isValidRoomCode(code: string): boolean {
  if (code.length !== 6) return false;
  for (const ch of code) if (!ROOM_CODE_ALPHABET.includes(ch)) return false;
  return true;
}
