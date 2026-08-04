/**
 * Themes are data, not code. One generator produces every level; a `ThemeSpec` decides
 * what it feels like. Adding a level means adding an entry here, not a new algorithm.
 */

import { DEFAULT_HAZARDS, type ThemeHazards } from './descent';
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
  /** How this place falls apart. See `ThemeHazards`. */
  hazards: ThemeHazards;
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
  hazards: DEFAULT_HAZARDS,
  entities: [
    { kind: 'blind', wakesAt: 0.18, max: 2 },
    // Staggered on purpose. Each entity gets a stretch of run where it is the only new
    // thing, so its rule can be learned before the next one complicates the picture.
    { kind: 'smiler', wakesAt: 0.42, max: 2 },
    { kind: 'swarm', wakesAt: 0.55, max: 10 },
    { kind: 'watcher', wakesAt: 0.66, max: 1 },
  ],
};

/**
 * Level 1 — "Lagerhalle".
 *
 * Long racking aisles and cold tubes. It is the theme that lives by its lighting, so the
 * descent takes the lighting: more blackout waves than anywhere else, and almost no sealing
 * — the danger is not that the building closes in, it is that you cannot see it any more.
 * The Swarm wakes early here because a hall full of fixtures is a hall full of destinations.
 */
const WAREHOUSE: ThemeSpec = {
  id: 'warehouse',
  nameKey: 'theme.warehouse',
  width: 144,
  height: 144,
  // Big leaves: this place is about sightlines down an aisle, not about warrens.
  minLeaf: 14,
  maxLeaf: 34,
  maxDepth: 5,
  roomKinds: [
    { kind: 'openHall', weight: 34 },
    { kind: 'pillarField', weight: 30 },
    { kind: 'corridorBundle', weight: 18 },
    { kind: 'cubicleWarren', weight: 10 },
    { kind: 'atrium', weight: 8 },
  ],
  braidFactor: 0.42,
  fixtureSpacing: 7,
  fuses: 4,
  descentSeconds: 17 * 60,
  palette: [
    {
      wallA: 0xd8dcd4,
      wallB: 0xc2c9c0,
      carpet: 0x6f7370,
      ceiling: 0xd2d6cf,
      trim: 0xa9b0a6,
      fog: 0xd6dbd4,
      fogDensity: 0.0065,
      lightColor: 0xeaf3ff,
      lightIntensity: 1.0,
      ambient: 0x3a3f3d,
      saturation: 0.92,
      bloom: 0.4,
      vignette: 0.12,
      grain: 0.02,
      aberration: 0.0,
      rot: 0.0,
    },
    {
      wallA: 0xb9bdb0,
      wallB: 0x9ca396,
      carpet: 0x5c5f5a,
      ceiling: 0xacb2a8,
      trim: 0x8d9488,
      fog: 0xa8ada4,
      fogDensity: 0.022,
      lightColor: 0xd8e6f2,
      lightIntensity: 0.74,
      ambient: 0x2f3432,
      saturation: 0.72,
      bloom: 0.42,
      vignette: 0.28,
      grain: 0.06,
      aberration: 0.5,
      rot: 0.3,
    },
    {
      wallA: 0x6e6a5c,
      wallB: 0x554f44,
      carpet: 0x3b3a36,
      ceiling: 0x565349,
      trim: 0x4c4840,
      fog: 0x33352f,
      fogDensity: 0.052,
      lightColor: 0x9fa892,
      lightIntensity: 0.34,
      ambient: 0x13150f,
      saturation: 0.42,
      bloom: 0.2,
      vignette: 0.48,
      grain: 0.16,
      aberration: 1.5,
      rot: 0.7,
    },
    {
      wallA: 0x1c1d1a,
      wallB: 0x131412,
      carpet: 0x0e0f0d,
      ceiling: 0x101110,
      trim: 0x161714,
      fog: 0x040505,
      fogDensity: 0.125,
      lightColor: 0x4d5340,
      lightIntensity: 0.05,
      ambient: 0x040404,
      saturation: 0.18,
      bloom: 0.08,
      vignette: 0.75,
      grain: 0.36,
      aberration: 3.2,
      rot: 1.0,
    },
  ],
  audio: {
    droneHz: 48,
    corridorRt60: 1.6,
    hallRt60: 3.2,
    footstepHz: 380,
    footstepQ: 0.9,
    motifBpm: 88,
  },
  hazards: { sealBias: 0.35, lightsOutBias: 1 },
  entities: [
    { kind: 'blind', wakesAt: 0.22, max: 2 },
    { kind: 'swarm', wakesAt: 0.4, max: 12 },
    { kind: 'smiler', wakesAt: 0.58, max: 2 },
  ],
};

/**
 * Level 2 — "Rohre".
 *
 * Wet concrete and narrow service runs. The opposite hazard profile to the warehouse: the
 * lights barely matter because there were never many, and instead the maze physically
 * tightens around you. Sight lines are short, which is why the Watcher belongs here and the
 * Swarm does not — there is nowhere for a crowd to be a crowd.
 */
const PIPES: ThemeSpec = {
  id: 'pipes',
  nameKey: 'theme.pipes',
  width: 112,
  height: 112,
  // Small leaves and a low braid factor: claustrophobia by construction.
  minLeaf: 7,
  maxLeaf: 16,
  maxDepth: 7,
  roomKinds: [
    { kind: 'corridorBundle', weight: 40 },
    { kind: 'cubicleWarren', weight: 32 },
    { kind: 'openHall', weight: 16 },
    { kind: 'pillarField', weight: 12 },
  ],
  braidFactor: 0.28,
  fixtureSpacing: 6,
  fuses: 3,
  descentSeconds: 13 * 60,
  palette: [
    {
      wallA: 0xa8a89e,
      wallB: 0x8f8f86,
      carpet: 0x5a5a54,
      ceiling: 0x9a9a91,
      trim: 0x7d7d75,
      fog: 0x9a9c94,
      fogDensity: 0.011,
      lightColor: 0xd9e4c8,
      lightIntensity: 0.8,
      ambient: 0x30332e,
      saturation: 0.8,
      bloom: 0.3,
      vignette: 0.2,
      grain: 0.03,
      aberration: 0.0,
      rot: 0.0,
    },
    {
      wallA: 0x7e8677,
      wallB: 0x66705f,
      carpet: 0x434840,
      ceiling: 0x6f776a,
      trim: 0x5a6154,
      fog: 0x565f52,
      fogDensity: 0.034,
      lightColor: 0xb9cba4,
      lightIntensity: 0.56,
      ambient: 0x232720,
      saturation: 0.62,
      bloom: 0.28,
      vignette: 0.34,
      grain: 0.08,
      aberration: 0.7,
      rot: 0.35,
    },
    {
      wallA: 0x414a3c,
      wallB: 0x323a2f,
      carpet: 0x252923,
      ceiling: 0x353c31,
      trim: 0x2c3327,
      fog: 0x1d221b,
      fogDensity: 0.068,
      lightColor: 0x6d7f5c,
      lightIntensity: 0.26,
      ambient: 0x0d0f0b,
      saturation: 0.38,
      bloom: 0.16,
      vignette: 0.54,
      grain: 0.18,
      aberration: 1.8,
      rot: 0.75,
    },
    {
      wallA: 0x13160f,
      wallB: 0x0d0f0a,
      carpet: 0x080905,
      ceiling: 0x0b0c08,
      trim: 0x101208,
      fog: 0x030403,
      fogDensity: 0.145,
      lightColor: 0x2f4526,
      lightIntensity: 0.04,
      ambient: 0x030403,
      saturation: 0.14,
      bloom: 0.06,
      vignette: 0.8,
      grain: 0.4,
      aberration: 3.4,
      rot: 1.0,
    },
  ],
  audio: {
    droneHz: 62,
    // Tight, slappy reverb: a metre of concrete on both sides.
    corridorRt60: 0.7,
    hallRt60: 1.3,
    footstepHz: 700,
    footstepQ: 1.8,
    motifBpm: 104,
  },
  hazards: { sealBias: 1, lightsOutBias: 0.5 },
  entities: [
    { kind: 'blind', wakesAt: 0.14, max: 2 },
    { kind: 'watcher', wakesAt: 0.44, max: 2 },
    { kind: 'smiler', wakesAt: 0.7, max: 1 },
  ],
};

export const THEMES: Record<string, ThemeSpec> = {
  level0: LEVEL0,
  warehouse: WAREHOUSE,
  pipes: PIPES,
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
