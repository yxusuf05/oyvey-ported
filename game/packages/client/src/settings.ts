/**
 * Persisted settings.
 *
 * The accessibility group is not a bolt-on. `scareIntensity`, `flashReduction` and
 * `screenshake` are read by the systems that produce those effects — one scalar each —
 * rather than being special-cased per scare. Retrofitting that later is the usual way
 * these options end up half-working.
 */

import { detectLanguage, setLanguage, type Language } from './i18n';

export type QualityPreset = 'low' | 'medium' | 'high';

export type ActionKey =
  | 'forward'
  | 'back'
  | 'left'
  | 'right'
  | 'sprint'
  | 'crouch'
  | 'interact'
  | 'flashlight'
  | 'beamMode'
  | 'useItem'
  | 'drop';

export interface Settings {
  language: Language;
  playerName: string;

  quality: QualityPreset;
  resolutionScale: number;
  fov: number;
  bloom: number;
  volumetric: boolean;
  grain: number;

  masterVolume: number;
  sfxVolume: number;
  musicVolume: number;
  ambienceVolume: number;

  sensitivity: number;
  invertY: boolean;
  keys: Record<ActionKey, string>;

  subtitles: boolean;
  /** 0 disables scare stings and screen effects entirely; 1 is full force. */
  scareIntensity: number;
  /** Caps frame-to-frame luminance change for photosensitivity. */
  flashReduction: boolean;
  screenshake: boolean;
  hallucinations: boolean;
  crosshair: boolean;
}

export const DEFAULT_KEYS: Record<ActionKey, string> = {
  forward: 'KeyW',
  back: 'KeyS',
  left: 'KeyA',
  right: 'KeyD',
  sprint: 'ShiftLeft',
  crouch: 'ControlLeft',
  interact: 'KeyE',
  flashlight: 'KeyF',
  beamMode: 'KeyR',
  useItem: 'KeyQ',
  drop: 'KeyG',
};

export function defaultSettings(): Settings {
  return {
    language: detectLanguage(),
    playerName: '',
    quality: 'high',
    resolutionScale: 1,
    fov: 78,
    bloom: 1,
    volumetric: true,
    grain: 1,
    masterVolume: 0.9,
    sfxVolume: 1,
    musicVolume: 0.7,
    ambienceVolume: 0.85,
    sensitivity: 1,
    invertY: false,
    keys: { ...DEFAULT_KEYS },
    subtitles: true,
    scareIntensity: 1,
    flashReduction: false,
    screenshake: true,
    hallucinations: true,
    crosshair: true,
  };
}

/** Preset knobs. Each preset only sets the values a preset should own. */
export const QUALITY_PRESETS: Record<QualityPreset, Partial<Settings>> = {
  low: { resolutionScale: 0.65, bloom: 0, volumetric: false, grain: 0.5 },
  medium: { resolutionScale: 0.85, bloom: 0.6, volumetric: true, grain: 1 },
  high: { resolutionScale: 1, bloom: 1, volumetric: true, grain: 1 },
};

const STORAGE_KEY = 'prisma.settings.v1';

let settings: Settings = defaultSettings();
const listeners = new Set<(s: Settings) => void>();

export function loadSettings(): Settings {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (raw) {
      const parsed = JSON.parse(raw) as Partial<Settings>;
      settings = {
        ...defaultSettings(),
        ...parsed,
        keys: { ...DEFAULT_KEYS, ...(parsed.keys ?? {}) },
      };
    }
  } catch {
    // A corrupt blob must never stop the game from starting.
    settings = defaultSettings();
  }
  setLanguage(settings.language);
  return settings;
}

export function getSettings(): Settings {
  return settings;
}

export function updateSettings(patch: Partial<Settings>): Settings {
  settings = { ...settings, ...patch, keys: { ...settings.keys, ...(patch.keys ?? {}) } };
  if (patch.language) setLanguage(patch.language);
  if (patch.quality) {
    settings = { ...settings, ...QUALITY_PRESETS[patch.quality] };
  }
  persist();
  for (const listener of listeners) listener(settings);
  return settings;
}

export function resetSettings(): Settings {
  settings = defaultSettings();
  setLanguage(settings.language);
  persist();
  for (const listener of listeners) listener(settings);
  return settings;
}

export function onSettingsChange(listener: (s: Settings) => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

function persist(): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(settings));
  } catch {
    // Private browsing and full quotas are not worth an error dialog.
  }
}

/** Human-readable label for a `KeyboardEvent.code`. */
export function keyLabel(code: string): string {
  if (code.startsWith('Key')) return code.slice(3);
  if (code.startsWith('Digit')) return code.slice(5);
  if (code.startsWith('Arrow')) return code.slice(5);
  const named: Record<string, string> = {
    ShiftLeft: 'L Shift',
    ShiftRight: 'R Shift',
    ControlLeft: 'L Ctrl',
    ControlRight: 'R Ctrl',
    AltLeft: 'L Alt',
    AltRight: 'R Alt',
    Space: 'Space',
    Escape: 'Esc',
    Enter: 'Enter',
    Tab: 'Tab',
  };
  return named[code] ?? code;
}
