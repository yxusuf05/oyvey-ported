/**
 * Procedural surface textures.
 *
 * There is no art pipeline and no asset budget, so every surface is painted at runtime
 * into a canvas. This is not a compromise for this particular game: the backrooms look is
 * flat colour, one repeating wallpaper and fluorescent light, which is the rare case where
 * "generated" and "correct" are the same thing.
 *
 * Channel packing lets one texture drive several looks:
 *   R — luminance detail (mottling, stains, scuffs, panel shading)
 *   G — pattern id (which wallpaper stripe / ceiling tile a pixel belongs to)
 *   B — grime mask, used to rot the surface as the descent progresses
 */

import { DataTexture, LinearFilter, LinearMipmapLinearFilter, RGBAFormat, RepeatWrapping, Texture, UnsignedByteType } from 'three';
import { Rng } from '@game/shared/prng';

const SIZE = 512;

/** Hash-based value noise. Deterministic per seed, no allocation in the inner loop. */
function makeNoise(seed: number) {
  const perm = new Uint8Array(512);
  const rng = new Rng(seed);
  const base = new Uint8Array(256);
  for (let i = 0; i < 256; i++) base[i] = i;
  rng.shuffle(Array.from(base)).forEach((v, i) => (base[i] = v));
  for (let i = 0; i < 512; i++) perm[i] = base[i & 255];

  const grad = (hash: number): number => ((hash & 15) / 15) * 2 - 1;
  const fade = (t: number): number => t * t * (3 - 2 * t);

  const noise2 = (x: number, y: number): number => {
    const xi = Math.floor(x) & 255;
    const yi = Math.floor(y) & 255;
    const xf = x - Math.floor(x);
    const yf = y - Math.floor(y);
    const u = fade(xf);
    const v = fade(yf);
    const aa = grad(perm[perm[xi] + yi]);
    const ab = grad(perm[perm[xi] + yi + 1]);
    const ba = grad(perm[perm[xi + 1] + yi]);
    const bb = grad(perm[perm[xi + 1] + yi + 1]);
    const top = aa + u * (ba - aa);
    const bottom = ab + u * (bb - ab);
    return (top + v * (bottom - top)) * 0.5 + 0.5;
  };

  const fbm = (x: number, y: number, octaves: number, lacunarity = 2.1, gain = 0.5): number => {
    let sum = 0;
    let amp = 1;
    let freq = 1;
    let norm = 0;
    for (let o = 0; o < octaves; o++) {
      sum += noise2(x * freq, y * freq) * amp;
      norm += amp;
      amp *= gain;
      freq *= lacunarity;
    }
    return sum / norm;
  };

  return { noise2, fbm };
}

function toTexture(data: Uint8Array, repeat: number, anisotropy: number): Texture {
  const tex = new DataTexture(data, SIZE, SIZE, RGBAFormat, UnsignedByteType);
  tex.wrapS = RepeatWrapping;
  tex.wrapT = RepeatWrapping;
  tex.magFilter = LinearFilter;
  tex.minFilter = LinearMipmapLinearFilter;
  tex.generateMipmaps = true;
  tex.anisotropy = anisotropy;
  tex.repeat.set(repeat, repeat);
  tex.needsUpdate = true;
  return tex;
}

/**
 * Wallpaper. Vertical stripes with a per-stripe id in the green channel, so the shader can
 * give every stripe its own hue at descent 0 (the rainbow) and collapse them all to one
 * sick colour by descent 1 — without regenerating the texture.
 */
export function makeWallpaper(seed: number, anisotropy: number): Texture {
  const { fbm, noise2 } = makeNoise(seed);
  const data = new Uint8Array(SIZE * SIZE * 4);
  const stripes = 8;
  const stripePx = SIZE / stripes;

  // A stable pseudo-random id per stripe, tiling seamlessly across the texture edge.
  const stripeIds = new Float32Array(stripes);
  const rng = new Rng(seed ^ 0x51ed);
  for (let i = 0; i < stripes; i++) stripeIds[i] = rng.next();

  // Water stains: a handful of irregular dark blooms, mostly near the top.
  const stains = Array.from({ length: 5 }, () => ({
    x: rng.next() * SIZE,
    y: rng.next() * SIZE * 0.55,
    r: 40 + rng.next() * 90,
    strength: 0.25 + rng.next() * 0.4,
  }));

  for (let y = 0; y < SIZE; y++) {
    for (let x = 0; x < SIZE; x++) {
      const i = (y * SIZE + x) * 4;
      const stripe = Math.floor(x / stripePx) % stripes;
      const inStripe = (x % stripePx) / stripePx;

      // Subtle vertical shading inside each stripe reads as printed paper rather than flat fill.
      let lum = 0.86 + Math.sin(inStripe * Math.PI) * 0.09;
      // Fine paper grain plus broad mottling.
      lum *= 0.94 + fbm(x * 0.02, y * 0.02, 4) * 0.12;
      lum *= 0.97 + noise2(x * 0.35, y * 0.35) * 0.06;
      // Seam line between stripes.
      if (inStripe < 0.012 || inStripe > 0.988) lum *= 0.82;

      // Scuff marks along the bottom where a floor meets the wall.
      const fromBottom = 1 - y / SIZE;
      if (fromBottom < 0.16) {
        const scuff = fbm(x * 0.05, y * 0.12, 3);
        lum *= 1 - (0.16 - fromBottom) * 2.2 * scuff;
      }

      let grime = fbm(x * 0.006 + 40, y * 0.006 + 12, 3);
      for (const stain of stains) {
        const dx = x - stain.x;
        const dy = (y - stain.y) * 1.6;
        const d = Math.sqrt(dx * dx + dy * dy) / stain.r;
        if (d < 1) {
          const edge = 1 - d;
          const wobble = 0.75 + fbm(x * 0.03, y * 0.03, 2) * 0.5;
          const amount = Math.min(1, edge * wobble * 2) * stain.strength;
          lum *= 1 - amount * 0.45;
          grime = Math.min(1, grime + amount);
        }
      }

      data[i] = Math.max(0, Math.min(255, lum * 255));
      data[i + 1] = stripeIds[stripe] * 255;
      data[i + 2] = grime * 255;
      data[i + 3] = 255;
    }
  }
  // One repeat per two metres, matching the tile size, so stripes line up with geometry.
  return toTexture(data, 1, anisotropy);
}

/** Carpet: dense two-octave noise with a directional smear, plus traffic wear. */
export function makeCarpet(seed: number, anisotropy: number): Texture {
  const { fbm, noise2 } = makeNoise(seed ^ 0x2f1a);
  const data = new Uint8Array(SIZE * SIZE * 4);
  for (let y = 0; y < SIZE; y++) {
    for (let x = 0; x < SIZE; x++) {
      const i = (y * SIZE + x) * 4;
      // Fibres: high-frequency noise stretched along one axis.
      const fibre = noise2(x * 0.9, y * 0.22) * 0.5 + noise2(x * 0.25, y * 1.1) * 0.5;
      const broad = fbm(x * 0.012, y * 0.012, 4);
      let lum = 0.72 + fibre * 0.18 + broad * 0.16;
      const wear = fbm(x * 0.005 + 90, y * 0.005 + 30, 3);
      lum *= 0.88 + wear * 0.22;
      // A very low-frequency blotch pattern on top. Fine fibre detail vanishes into the
      // mip chain within a few metres, and without this the floor reads as flat colour
      // everywhere except directly underfoot.
      const blotch = fbm(x * 0.0022 + 300, y * 0.0022 + 155, 2);
      lum *= 0.82 + blotch * 0.36;
      data[i] = Math.max(0, Math.min(255, lum * 255));
      data[i + 1] = 128;
      data[i + 2] = (1 - wear) * 255;
      data[i + 3] = 255;
    }
  }
  return toTexture(data, 1, anisotropy);
}

/** Ceiling: suspended tiles with perforation, sag and brown blooms around the seams. */
export function makeCeiling(seed: number, anisotropy: number): Texture {
  const { fbm, noise2 } = makeNoise(seed ^ 0x77c3);
  const data = new Uint8Array(SIZE * SIZE * 4);
  const tiles = 2;
  const tilePx = SIZE / tiles;
  const rng = new Rng(seed ^ 0x77c3);
  const tileIds = new Float32Array(tiles * tiles);
  for (let i = 0; i < tileIds.length; i++) tileIds[i] = rng.next();

  for (let y = 0; y < SIZE; y++) {
    for (let x = 0; x < SIZE; x++) {
      const i = (y * SIZE + x) * 4;
      const tx = Math.floor(x / tilePx);
      const ty = Math.floor(y / tilePx);
      const u = (x % tilePx) / tilePx;
      const v = (y % tilePx) / tilePx;

      // Sag: each tile is brightest at its edges and dips in the middle.
      const sag = 1 - Math.sin(u * Math.PI) * Math.sin(v * Math.PI) * 0.16;
      let lum = 0.93 * sag;
      // Perforation dots.
      const dot = noise2(x * 1.7, y * 1.7);
      lum *= 0.93 + dot * 0.12;
      // Grid seams.
      const seam = Math.min(Math.min(u, 1 - u), Math.min(v, 1 - v));
      if (seam < 0.02) lum *= 0.55;

      let grime = fbm(x * 0.008 + 5, y * 0.008 + 71, 3);
      // Brown blooms creep in from the seams, which is where real ceiling tiles stain.
      const seamBloom = Math.max(0, 1 - seam * 8) * fbm(x * 0.02, y * 0.02, 3);
      grime = Math.min(1, grime * 0.6 + seamBloom * 0.8);
      lum *= 1 - seamBloom * 0.25;

      data[i] = Math.max(0, Math.min(255, lum * 255));
      data[i + 1] = tileIds[ty * tiles + tx] * 255;
      data[i + 2] = grime * 255;
      data[i + 3] = 255;
    }
  }
  return toTexture(data, 1, anisotropy);
}

export interface SurfaceTextures {
  wall: Texture;
  carpet: Texture;
  ceiling: Texture;
}

export function makeSurfaceTextures(seed: number, anisotropy = 4): SurfaceTextures {
  return {
    wall: makeWallpaper(seed, anisotropy),
    carpet: makeCarpet(seed, anisotropy),
    ceiling: makeCeiling(seed, anisotropy),
  };
}

/** Blue-ish noise used to dither the volumetric raymarch and the film grain. */
export function makeNoiseTexture(seed: number): Texture {
  const size = 64;
  const data = new Uint8Array(size * size * 4);
  const rng = new Rng(seed ^ 0xbeef);
  for (let i = 0; i < size * size; i++) {
    data[i * 4] = rng.int(0, 255);
    data[i * 4 + 1] = rng.int(0, 255);
    data[i * 4 + 2] = rng.int(0, 255);
    data[i * 4 + 3] = 255;
  }
  const tex = new DataTexture(data, size, size, RGBAFormat, UnsignedByteType);
  tex.wrapS = RepeatWrapping;
  tex.wrapT = RepeatWrapping;
  tex.magFilter = LinearFilter;
  tex.minFilter = LinearFilter;
  tex.needsUpdate = true;
  return tex;
}
