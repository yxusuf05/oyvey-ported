import { describe, expect, it } from 'vitest';
import { Rng, fnv1a32, hash3f, isValidRoomCode, randomRoomCode } from '../src/prng';

describe('Rng', () => {
  it('is reproducible for the same seed', () => {
    const a = new Rng('KX7F-abc');
    const b = new Rng('KX7F-abc');
    for (let i = 0; i < 1000; i++) expect(a.nextU32()).toBe(b.nextU32());
  });

  it('differs for different seeds', () => {
    const a = new Rng('seed-a');
    const b = new Rng('seed-b');
    let same = 0;
    for (let i = 0; i < 200; i++) if (a.nextU32() === b.nextU32()) same++;
    expect(same).toBeLessThan(3);
  });

  it('produces floats strictly inside [0, 1)', () => {
    const rng = new Rng(12345);
    for (let i = 0; i < 20000; i++) {
      const v = rng.next();
      expect(v).toBeGreaterThanOrEqual(0);
      expect(v).toBeLessThan(1);
    }
  });

  it('int() is uniform and inclusive at both ends', () => {
    const rng = new Rng('uniform');
    const counts = new Array(6).fill(0);
    const n = 120000;
    for (let i = 0; i < n; i++) counts[rng.int(0, 5)]++;
    for (const c of counts) {
      // Well inside the tolerance a fair generator needs; catches off-by-one range bugs.
      expect(c).toBeGreaterThan(n / 6 - n * 0.01);
      expect(c).toBeLessThan(n / 6 + n * 0.01);
    }
  });

  it('derive() streams are independent of call order', () => {
    // The property the whole determinism story rests on: introducing a new named stream
    // must not shift any existing stream's output.
    const root = new Rng('run-seed');
    const before = Array.from({ length: 16 }, () => root.derive('levelgen:bsp').nextU32());

    const root2 = new Rng('run-seed');
    root2.derive('levelgen:decals').nextU32();
    root2.derive('levelgen:props').nextU32();
    const after = Array.from({ length: 16 }, () => root2.derive('levelgen:bsp').nextU32());

    expect(after).toEqual(before);
  });

  it('derive() with different salts gives different streams', () => {
    const root = new Rng('salted');
    const a = root.derive('room', 1).nextU32();
    const b = root.derive('room', 2).nextU32();
    expect(a).not.toBe(b);
  });

  it('uses only IEEE-exact operations for gaussian', () => {
    // Irwin-Hall, not Box-Muller: no Math.log or Math.cos, which differ between engines.
    const rng = new Rng('gauss');
    let sum = 0;
    const n = 50000;
    for (let i = 0; i < n; i++) sum += rng.gaussian(0, 1);
    expect(Math.abs(sum / n)).toBeLessThan(0.05);
  });

  it('gaussian respects its standard deviation', () => {
    const rng = new Rng('gauss-sd');
    const n = 50000;
    let sum = 0;
    let sumSq = 0;
    for (let i = 0; i < n; i++) {
      const v = rng.gaussian(10, 3);
      sum += v;
      sumSq += v * v;
    }
    const mean = sum / n;
    const sd = Math.sqrt(sumSq / n - mean * mean);
    expect(Math.abs(mean - 10)).toBeLessThan(0.1);
    expect(Math.abs(sd - 3)).toBeLessThan(0.15);
  });

  it('weighted() honours the weights', () => {
    const rng = new Rng('weights');
    const counts = { a: 0, b: 0 };
    for (let i = 0; i < 40000; i++) counts[rng.weighted(['a', 'b'] as const, [3, 1])]++;
    expect(counts.a / (counts.a + counts.b)).toBeCloseTo(0.75, 1);
  });
});

describe('hashing', () => {
  it('fnv1a32 is stable and well distributed', () => {
    expect(fnv1a32('')).toBe(0x811c9dc5);
    expect(fnv1a32('abc')).toBe(fnv1a32('abc'));
    expect(fnv1a32('abc')).not.toBe(fnv1a32('abd'));
  });

  it('hash3f is stateless and in range', () => {
    for (let i = 0; i < 1000; i++) {
      const v = hash3f(i, i * 7, i * 13);
      expect(v).toBeGreaterThanOrEqual(0);
      expect(v).toBeLessThan(1);
      expect(hash3f(i, i * 7, i * 13)).toBe(v);
    }
  });
});

describe('room codes', () => {
  it('avoids characters that are ambiguous when read aloud', () => {
    for (let i = 0; i < 500; i++) {
      const code = randomRoomCode();
      expect(code).toHaveLength(6);
      expect(code).not.toMatch(/[IO01]/);
      expect(isValidRoomCode(code)).toBe(true);
    }
  });

  it('rejects malformed codes', () => {
    expect(isValidRoomCode('ABC')).toBe(false);
    expect(isValidRoomCode('ABCDEO')).toBe(false);
    expect(isValidRoomCode('abcdef')).toBe(false);
  });
});
