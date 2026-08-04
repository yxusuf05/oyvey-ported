/**
 * In-run HUD.
 *
 * Subtitles are a first-class element rather than an accessibility afterthought: audio
 * carries gameplay information in this game — where a monster is, whether it heard you —
 * so a player with the sound off must still get that information.
 */

import { getItemSpec } from '@game/shared/content';
import { t, subtitleFor, type TranslationKey } from '../i18n';
import { visibleSlotCount } from '../game/hotbar';
import type { HudState } from '../game/session';
import type { Settings } from '../settings';
import { el } from './dom';

interface Meter {
  root: HTMLElement;
  fill: HTMLElement;
}

function meter(labelKey: TranslationKey, modifier: string): Meter {
  const fill = el('div', { class: `meter__fill meter__fill--${modifier}` });
  const root = el(
    'div',
    { class: 'meter' },
    el('span', { class: 'meter__label', text: t(labelKey) }),
    el('div', { class: 'meter__track' }, fill),
  );
  return { root, fill };
}

export class Hud {
  readonly root: HTMLElement;

  private readonly sanity: Meter;
  private readonly stamina: Meter;
  private readonly battery: Meter;
  private readonly descentFill: HTMLElement;
  private readonly objectiveValue: HTMLElement;
  private readonly objectiveLabel: HTMLElement;
  private readonly prompt: HTMLElement;
  private readonly banner: HTMLElement;
  private readonly subtitles: HTMLElement;
  private readonly crosshair: HTMLElement;
  private readonly debug: HTMLElement;
  private readonly hint: HTMLElement;
  private readonly hotbar: HTMLElement;
  private readonly downedPanel: HTMLElement;
  private readonly downedLabel: HTMLElement;
  private readonly downedTimer: HTMLElement;
  private readonly reviveFill: HTMLElement;
  private readonly hotbarSlots: HTMLElement[] = [];

  private activeSubtitles: { key: string; until: number; node: HTMLElement }[] = [];
  private settings: Settings;

  constructor(settings: Settings) {
    this.settings = settings;

    this.sanity = meter('hud.sanity', 'sanity');
    this.stamina = meter('hud.stamina', 'stamina');
    this.battery = meter('hud.battery', 'battery');

    this.descentFill = el('div', { class: 'meter__fill' });
    this.objectiveValue = el('strong', { text: '0 / 0' });
    this.objectiveLabel = el('span', { text: t('hud.fuses') });
    this.prompt = el('div', { class: 'hud__prompt hidden' });
    this.banner = el('div', { class: 'hud__banner hidden' });
    this.subtitles = el('div', { class: 'hud__subtitles' });
    this.crosshair = el('div', { class: 'hud__crosshair' });
    this.debug = el('div', { class: 'hud__debug' });
    this.hint = el('div', { class: 'hud__hint', text: t('hud.clickToPlay') });

    // Fixed cells rather than a list that grows and shrinks during play: the slot a number
    // key selects has to stay in the same place on screen, empty or not. The *count* is not
    // fixed, though — the backpack perk buys real slots, and a slot the player paid for and
    // cannot see is a perk that only exists in the shop.
    this.hotbar = el('div', { class: 'hud__hotbar' });
    this.growHotbar(visibleSlotCount(0));

    // The most dramatic moment in the game had no readout at all: a sixty-second timer
    // nobody could see is not tension, it is an unexplained death.
    this.downedLabel = el('span', { class: 'downed__label' });
    this.downedTimer = el('strong', { class: 'downed__timer' });
    this.reviveFill = el('div', { class: 'meter__fill meter__fill--revive' });
    this.downedPanel = el(
      'div',
      { class: 'hud__downed hidden' },
      el('div', { class: 'downed__row' }, this.downedLabel, this.downedTimer),
      el('div', { class: 'meter__track' }, this.reviveFill),
    );

    this.root = el(
      'div',
      { class: 'hud' },
      el(
        'div',
        { class: 'hud__descent' },
        el('span', { class: 'meter__label', text: t('hud.descent') }),
        el('div', { class: 'meter__track' }, this.descentFill),
      ),
      el('div', { class: 'hud__objective' }, this.objectiveValue, this.objectiveLabel),
      el('div', { class: 'hud__corner' }, this.sanity.root, this.stamina.root, this.battery.root),
      this.hotbar,
      this.downedPanel,
      this.prompt,
      this.banner,
      this.subtitles,
      this.crosshair,
      this.debug,
      this.hint,
    );
  }

  applySettings(settings: Settings): void {
    this.settings = settings;
    this.crosshair.classList.toggle('hidden', !settings.crosshair);
    this.subtitles.classList.toggle('hidden', !settings.subtitles);
  }

  setPointerLocked(locked: boolean): void {
    this.hint.classList.toggle('hidden', locked);
  }

  /** Called by the audio engine for every cue, whether or not sound is actually playing. */
  pushSubtitle(soundKey: string): void {
    if (!this.settings.subtitles) return;
    const text = subtitleFor(soundKey);
    if (!text) return;

    const existing = this.activeSubtitles.find((s) => s.key === soundKey);
    if (existing) {
      existing.until = performance.now() + 2200;
      return;
    }

    const node = el('div', { text });
    this.subtitles.append(node);
    this.activeSubtitles.push({ key: soundKey, until: performance.now() + 2200, node });
    // More than four lines at once stops being readable and starts being noise.
    while (this.activeSubtitles.length > 4) {
      const oldest = this.activeSubtitles.shift();
      oldest?.node.remove();
    }
  }

  /** Adds cells until there are `count` of them. Backpacks only ever get bigger. */
  private growHotbar(count: number): void {
    for (let i = this.hotbarSlots.length; i < count; i++) {
      const cell = el(
        'div',
        { class: 'hotbar__slot' },
        el('span', { class: 'hotbar__key', text: String(i + 1) }),
        el('span', { class: 'hotbar__name' }),
        el('span', { class: 'hotbar__count' }),
      );
      this.hotbarSlots.push(cell);
      this.hotbar.append(cell);
    }
  }

  private updateHotbar(hud: HudState): void {
    // The server is the authority on how big this player's backpack is, and it says so with
    // every inventory message.
    this.growHotbar(visibleSlotCount(hud.inventory.length));

    for (let i = 0; i < this.hotbarSlots.length; i++) {
      const cell = this.hotbarSlots[i];
      const slot = hud.inventory[i] ?? null;
      const spec = slot ? getItemSpec(slot.item) : null;

      cell.classList.toggle('hotbar__slot--active', i === hud.activeSlot);
      cell.classList.toggle('hotbar__slot--empty', slot === null);
      (cell.children[1] as HTMLElement).textContent = spec ? t(spec.nameKey as TranslationKey) : '';
      // A count of one is noise on every slot that can only ever hold one thing.
      (cell.children[2] as HTMLElement).textContent = slot && slot.count > 1 ? `×${slot.count}` : '';
    }
  }

  private updateDowned(hud: HudState): void {
    const visible = hud.downed && !hud.dead && !hud.escaped;
    this.downedPanel.classList.toggle('hidden', !visible);
    if (!visible) return;

    // Ceil, so the number only reaches zero when the time actually has. Watching a timer
    // sit on 0 while you are still alive reads as a bug.
    const seconds = Math.max(0, Math.ceil(hud.bleedout));
    const reviving = hud.reviveProgress > 0;
    this.downedLabel.textContent = reviving ? t('hud.reviving') : t('hud.bleedout', { seconds });
    this.downedTimer.textContent = `${seconds}s`;
    this.downedPanel.classList.toggle('hud__downed--reviving', reviving);
    this.reviveFill.style.width = `${Math.round(hud.reviveProgress * 100)}%`;
  }

  update(hud: HudState, showDebug: boolean): void {
    this.sanity.fill.style.width = `${Math.round(hud.sanity * 100)}%`;
    this.stamina.fill.style.width = `${Math.round(hud.stamina * 100)}%`;
    this.battery.fill.style.width = `${Math.round(hud.battery * 100)}%`;
    this.descentFill.style.width = `${Math.round(hud.descent * 100)}%`;

    this.updateHotbar(hud);
    this.updateDowned(hud);

    this.objectiveValue.textContent = `${hud.fusesCollected} / ${hud.fusesTotal}`;
    this.objectiveLabel.textContent = hud.carrying ? t('hud.carrying') : t('hud.fuses');

    if (hud.prompt) {
      this.prompt.textContent = t(hud.prompt.key as TranslationKey, hud.prompt.params);
      this.prompt.classList.remove('hidden');
    } else {
      this.prompt.classList.add('hidden');
    }

    let bannerText = '';
    if (hud.dead) bannerText = t('hud.dead');
    else if (hud.escaped) bannerText = t('hud.escaped');
    else if (hud.downed) bannerText = t('hud.downed');
    else if (hud.exitOpen) bannerText = t('hud.exitOpen');
    this.banner.textContent = bannerText;
    this.banner.classList.toggle('hidden', bannerText === '');

    const now = performance.now();
    this.activeSubtitles = this.activeSubtitles.filter((entry) => {
      if (entry.until > now) return true;
      entry.node.remove();
      return false;
    });

    this.debug.classList.toggle('hidden', !showDebug);
    if (showDebug) {
      this.debug.textContent = `${hud.fps} fps · ${hud.drawCalls} draws`;
    }
  }
}
