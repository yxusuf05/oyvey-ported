import { en, type TranslationKey } from './en';
import { de } from './de';

export type Language = 'en' | 'de';
export type { TranslationKey };

const DICTIONARIES: Record<Language, Record<TranslationKey, string>> = { en, de };

let current: Language = 'en';
const listeners = new Set<() => void>();

export function setLanguage(lang: Language): void {
  if (current === lang) return;
  current = lang;
  document.documentElement.lang = lang;
  for (const listener of listeners) listener();
}

export function getLanguage(): Language {
  return current;
}

/** Re-renders any UI that has been rendered with `t()` when the language changes. */
export function onLanguageChange(listener: () => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

/** Picks a starting language from the browser, defaulting to English. */
export function detectLanguage(): Language {
  const nav = typeof navigator !== 'undefined' ? navigator.language.toLowerCase() : 'en';
  return nav.startsWith('de') ? 'de' : 'en';
}

/** Translates a key, substituting `{name}` placeholders. */
export function t(key: TranslationKey, params?: Record<string, string | number>): string {
  const raw = DICTIONARIES[current][key] ?? DICTIONARIES.en[key] ?? key;
  if (!params) return raw;
  return raw.replace(/\{(\w+)\}/g, (match, name: string) =>
    name in params ? String(params[name]) : match,
  );
}

/** Subtitle text for an audio cue, falling back to the raw key so nothing is silent. */
export function subtitleFor(soundKey: string): string {
  const key = `subtitle.${soundKey}` as TranslationKey;
  const dict = DICTIONARIES[current] as Record<string, string>;
  return dict[key] ?? (DICTIONARIES.en as Record<string, string>)[key] ?? '';
}
