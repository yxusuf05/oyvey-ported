import { describe, expect, it } from 'vitest';
import { en } from '../src/i18n/en';
import { de } from '../src/i18n/de';

/**
 * The dictionaries are already typed against each other, so a missing key is a compile
 * error. What the type system cannot check is the *content*: an interpolation placeholder
 * that exists in one language and not the other produces a literal "{name}" on screen.
 */
describe('translations', () => {
  const keys = Object.keys(en) as (keyof typeof en)[];

  it('covers every key in both languages', () => {
    expect(Object.keys(de).sort()).toEqual([...keys].sort());
  });

  it('has no empty strings', () => {
    for (const key of keys) {
      expect(en[key].length, `en:${key}`).toBeGreaterThan(0);
      expect(de[key].length, `de:${key}`).toBeGreaterThan(0);
    }
  });

  it('uses the same interpolation placeholders in both languages', () => {
    const placeholders = (text: string): string[] =>
      [...text.matchAll(/\{(\w+)\}/g)].map((match) => match[1]).sort();

    for (const key of keys) {
      expect(placeholders(de[key]), `placeholders differ for ${key}`).toEqual(placeholders(en[key]));
    }
  });

  it('provides a subtitle for every sound the game can emit', () => {
    // Sound keys are the contract between the audio engine and the caption system; a cue
    // without a caption is gameplay information a player with sound off never receives.
    const soundKeys = [
      'entity.step',
      'entity.run',
      'entity.alerted',
      'entity.telegraph',
      'item.flashlightClick',
      'item.batteryDead',
      'objective.fusePickup',
      'objective.fuseInsert',
      'objective.exitOpen',
      'objective.extract',
      'player.hurt',
      'player.died',
      'player.revived',
      'hallucination.whisper',
      'hallucination.step',
    ];
    for (const sound of soundKeys) {
      const key = `subtitle.${sound}` as keyof typeof en;
      expect(en[key], `missing en subtitle for ${sound}`).toBeTruthy();
      expect(de[key], `missing de subtitle for ${sound}`).toBeTruthy();
    }
  });

  it('keeps German strings from being wildly longer than the layout allows', () => {
    // German is habitually longer than English; this catches the cases that would overflow
    // a button rather than merely wrap.
    for (const key of keys) {
      if (!key.startsWith('menu.') && !key.startsWith('lobby.') && !key.startsWith('settings.quality')) continue;
      expect(de[key].length, `${key} is far longer in German`).toBeLessThan(en[key].length + 18);
    }
  });
});
