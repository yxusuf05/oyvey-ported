/**
 * Themes are data, not code. One generator produces every level; a `ThemeSpec` decides
 * what it feels like. Adding a level means adding an entry here, not a new algorithm.
 */

import type { RoomKind } from './types';

/**
 * One rung of the descent ladder. The renderer never reads a hard-coded colour — it
 * interpolates between adjacent stops by the descent value, which is why the transition
 * from sunshine to horror is continuous instead of a cut.
 */
export interface PaletteStop {
  /** Wallpaper base and stripe colours, linear-ish sRGB hex. */
  wallA: number;
  wallB: number;
  carpet: number;
  ceiling: number;
  trim: number;
  fog: number;
  fogDensity: number;
  /** Colour and strength of the ceiling fixtures baked into the light grid. */
  lightColor: number;
  lightIntensity: number;
  ambient: number;
  /** Post-processing targets. */
  saturation: number;
  bloom: number;
  vignette: number;
  grain: number;
  aberration: number;
  /** How far the wallpaper stripes drift off their hue, 0 = pristine. */
  rot: number;
}

export interface ThemeAudioSpec {
  /** Base frequency of the drone bed. */
  droneHz: number;
  /** Reverb tail for corridors, seconds. */
  corridorRt60: number;
  hallRt60: number;
  /** Footstep filter centre frequency and Q per surface. */
  footstepHz: number;
  footstepQ: number;
  /** Tempo of the music box motif at descent 0. */
  motifBpm: number;
}

export interface ThemeSpec {
  id: string;
  /** i18n key, resolved client-side. */
  nameKey: string;
  width: number;
  height: number;
  /** BSP tuning. `minLeaf` is what actually controls how claustrophobic the level feels. */
  minLeaf: number;
  maxLeaf: number;
  maxDepth: number;
  roomKinds: { kind: RoomKind; weight: number }[];
  /** Extra non-tree doors as a fraction of leaf count. Loops keep chases readable. */
  braidFactor: number;
  /** Ceiling fixture spacing in tiles. */
  fixtureSpacing: number;
  /** Number of fuses that must be collected before the exit powers up. */
  fuses: number;
  /** Seconds of pure elapsed time it takes to go from descent 0 to 1 with no other input. */
  descentSeconds: number;
  palette: [PaletteStop, PaletteStop, PaletteStop, PaletteStop];
  audio: ThemeAudioSpec;
  /** Entity kinds allowed to spawn here, with the descent value at which they wake up. */
  entities: { kind: string; wakesAt: number; max: number }[];
}

/**
 * Level 0 — "Sonnenschein-Flure".
 *
 * The whole point of this theme is that stop 0 is *too* friendly. Saturation above 1,
 * warm white fog reading as sun haze, pastel rainbow wallpaper. Every later stop reuses
 * the same geometry and lighting model and only moves the numbers.
 */
const LEVEL0: ThemeSpec = {
  id: 'level0',
  nameKey: 'theme.level0',
  width: 128,
  height: 128,
  minLeaf: 9,
  maxLeaf: 24,
  maxDepth: 6,
  roomKinds: [
    { kind: 'cubicleWarren', weight: 34 },
    { kind: 'openHall', weight: 24 },
    { kind: 'corridorBundle', weight: 22 },
    { kind: 'pillarField', weight: 14 },
    { kind: 'atrium', weight: 6 },
  ],
  braidFactor: 0.35,
  fixtureSpacing: 5,
  fuses: 3,
  descentSeconds: 15 * 60,
  palette: [
    {
      // 0.00 — rainbow and sunshine
      wallA: 0xf4dfa4,
      wallB: 0xf0bfd2,
      carpet: 0xbe8a48,
      ceiling: 0xf2e6cb,
      trim: 0xe8d7a6,
      fog: 0xf6e8c8,
      fogDensity: 0.0072,
      lightColor: 0xfff0c9,
      lightIntensity: 0.92,
      ambient: 0x453d2c,
      saturation: 1.14,
      bloom: 0.45,
      vignette: 0.1,
      grain: 0.015,
      aberration: 0.0,
      rot: 0.0,
    },
    {
      // 0.35 — the first cracks
      wallA: 0xe8d79a,
      wallB: 0xd9bda6,
      carpet: 0xbe8f4e,
      ceiling: 0xe4dcc6,
      trim: 0xded0a8,
      fog: 0xd9cdb0,
      fogDensity: 0.024,
      lightColor: 0xf2e6bd,
      lightIntensity: 0.78,
      ambient: 0x3d382a,
      saturation: 0.95,
      bloom: 0.5,
      vignette: 0.26,
      grain: 0.05,
      aberration: 0.4,
      rot: 0.25,
    },
    {
      // 0.70 — decay
      wallA: 0x8f8461,
      wallB: 0x6f6a52,
      carpet: 0x5c4a2c,
      ceiling: 0x736c58,
      trim: 0x6c6349,
      fog: 0x4a4736,
      fogDensity: 0.05,
      lightColor: 0xbfae7c,
      lightIntensity: 0.42,
      ambient: 0x191811,
      saturation: 0.55,
      bloom: 0.24,
      vignette: 0.45,
      grain: 0.14,
      aberration: 1.3,
      rot: 0.65,
    },
    {
      // 1.00 — the descent
      wallA: 0x241f18,
      wallB: 0x1a1712,
      carpet: 0x140f09,
      ceiling: 0x14120e,
      trim: 0x1c1913,
      fog: 0x05060a,
      fogDensity: 0.115,
      lightColor: 0x6b3f2a,
      lightIntensity: 0.07,
      ambient: 0x050505,
      saturation: 0.22,
      bloom: 0.1,
      vignette: 0.72,
      grain: 0.35,
      aberration: 3.0,
      rot: 1.0,
    },
  ],
  audio: {
    droneHz: 55,
    corridorRt60: 1.15,
    hallRt60: 2.4,
    footstepHz: 520,
    footstepQ: 1.2,
    motifBpm: 96,
  },
  entities: [
    { kind: 'blind', wakesAt: 0.18, max: 2 },
    // Staggered on purpose. Each entity gets a stretch of run where it is the only new
    // thing, so its rule can be learned before the next one complicates the picture.
    { kind: 'smiler', wakesAt: 0.42, max: 2 },
    { kind: 'swarm', wakesAt: 0.55, max: 10 },
    { kind: 'watcher', wakesAt: 0.66, max: 1 },
  ],
};

export const THEMES: Record<string, ThemeSpec> = {
  level0: LEVEL0,
};

export const DEFAULT_THEME_ID = 'level0';

export function getTheme(id: string): ThemeSpec {
  return THEMES[id] ?? LEVEL0;
}

/**
 * Samples the palette ladder at an arbitrary descent value.
 * Stops sit at 0, 0.35, 0.70 and 1.0 — deliberately uneven so the pleasant phase lasts
 * long enough for the player to relax before it is taken away.
 */
const STOP_POSITIONS = [0, 0.35, 0.7, 1.0];

export function samplePalette(theme: ThemeSpec, descent: number): PaletteStop {
  const d = descent <= 0 ? 0 : descent >= 1 ? 1 : descent;
  let i = 0;
  while (i < STOP_POSITIONS.length - 2 && d > STOP_POSITIONS[i + 1]) i++;
  const a = theme.palette[i];
  const b = theme.palette[i + 1];
  const span = STOP_POSITIONS[i + 1] - STOP_POSITIONS[i];
  const t = span <= 0 ? 0 : (d - STOP_POSITIONS[i]) / span;
  return mixStops(a, b, t);
}

const mixHex = (a: number, b: number, t: number): number => {
  const ar = (a >> 16) & 0xff;
  const ag = (a >> 8) & 0xff;
  const ab = a & 0xff;
  const br = (b >> 16) & 0xff;
  const bg = (b >> 8) & 0xff;
  const bb = b & 0xff;
  const r = Math.round(ar + (br - ar) * t);
  const g = Math.round(ag + (bg - ag) * t);
  const bl = Math.round(ab + (bb - ab) * t);
  return (r << 16) | (g << 8) | bl;
};

const mix = (a: number, b: number, t: number): number => a + (b - a) * t;

export function mixStops(a: PaletteStop, b: PaletteStop, t: number): PaletteStop {
  return {
    wallA: mixHex(a.wallA, b.wallA, t),
    wallB: mixHex(a.wallB, b.wallB, t),
    carpet: mixHex(a.carpet, b.carpet, t),
    ceiling: mixHex(a.ceiling, b.ceiling, t),
    trim: mixHex(a.trim, b.trim, t),
    fog: mixHex(a.fog, b.fog, t),
    fogDensity: mix(a.fogDensity, b.fogDensity, t),
    lightColor: mixHex(a.lightColor, b.lightColor, t),
    lightIntensity: mix(a.lightIntensity, b.lightIntensity, t),
    ambient: mixHex(a.ambient, b.ambient, t),
    saturation: mix(a.saturation, b.saturation, t),
    bloom: mix(a.bloom, b.bloom, t),
    vignette: mix(a.vignette, b.vignette, t),
    grain: mix(a.grain, b.grain, t),
    aberration: mix(a.aberration, b.aberration, t),
    rot: mix(a.rot, b.rot, t),
  };
}
