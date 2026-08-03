/**
 * Menus: main, lobby, settings, how-to-play and the run summary.
 *
 * Everything re-renders from state on demand, which is cheap at this size and removes a
 * whole class of "the label says ready but the button does not" bugs. Language changes
 * simply trigger a re-render.
 */

import { getLanguage, onLanguageChange, t, type Language, type TranslationKey } from '../i18n';
import {
  DEFAULT_KEYS,
  QUALITY_PRESETS,
  getSettings,
  keyLabel,
  resetSettings,
  updateSettings,
  type ActionKey,
  type QualityPreset,
  type Settings,
} from '../settings';
import type { LobbyPlayer, RunOutcome, RunStats } from '@game/shared/protocol';
import { PERK_SPECS, perkCost } from '@game/shared/content';
import { clear, el, field, toggle } from './dom';

export type ScreenName = 'menu' | 'lobby' | 'settings' | 'howto' | 'hub' | 'summary' | 'game';

export interface ScreenCallbacks {
  host(name: string): void;
  join(name: string, code: string): void;
  leave(): void;
  ready(ready: boolean): void;
  start(seed: string): void;
  settingsChanged(settings: Settings): void;
  backToLobby(): void;
  buyPerk(perk: string): void;
}

/** Meta-progression as the shop sees it. Everything here comes from the server. */
export interface ProfileView {
  credits: number;
  runs: number;
  deepest: number;
  perks: Record<string, number>;
}

export interface LobbyView {
  code: string;
  hostId: number;
  localId: number;
  players: LobbyPlayer[];
  connecting: boolean;
}

export class Screens {
  private readonly root: HTMLElement;
  private readonly callbacks: ScreenCallbacks;

  private current: ScreenName = 'menu';
  private previous: ScreenName = 'menu';
  private settingsTab = 'graphics';
  private error = '';
  private lobby: LobbyView | null = null;
  private summary: { outcome: RunOutcome; stats: RunStats } | null = null;
  private profile: ProfileView = { credits: 0, runs: 0, deepest: 0, perks: {} };
  private pendingRebind: ActionKey | null = null;

  constructor(root: HTMLElement, callbacks: ScreenCallbacks) {
    this.root = root;
    this.callbacks = callbacks;
    onLanguageChange(() => this.render());
    window.addEventListener('keydown', (event) => this.onKeyDown(event));
  }

  show(screen: ScreenName): void {
    if (screen !== 'settings' && screen !== 'howto') this.previous = screen;
    this.current = screen;
    this.error = '';
    this.render();
  }

  get screen(): ScreenName {
    return this.current;
  }

  setError(message: string): void {
    this.error = message;
    this.render();
  }

  setLobby(view: LobbyView): void {
    this.lobby = view;
    if (this.current === 'lobby') this.render();
  }

  setSummary(outcome: RunOutcome, stats: RunStats): void {
    this.summary = { outcome, stats };
    this.show('summary');
  }

  private onKeyDown(event: KeyboardEvent): void {
    if (this.pendingRebind) {
      event.preventDefault();
      const action = this.pendingRebind;
      this.pendingRebind = null;
      if (event.code !== 'Escape') {
        const settings = updateSettings({ keys: { [action]: event.code } as Record<ActionKey, string> });
        this.callbacks.settingsChanged(settings);
      }
      this.render();
      return;
    }
    if (event.code === 'Escape' && (this.current === 'settings' || this.current === 'howto')) {
      this.show(this.previous);
    }
  }

  private render(): void {
    clear(this.root);
    if (this.current === 'game') return;

    const screen = el('div', { class: 'screen' });
    switch (this.current) {
      case 'menu':
        screen.append(this.renderMenu());
        break;
      case 'lobby':
        screen.append(this.renderLobby());
        break;
      case 'settings':
        screen.append(this.renderSettings());
        break;
      case 'howto':
        screen.append(this.renderHowTo());
        break;
      case 'hub':
        screen.append(this.renderHub());
        break;
      case 'summary':
        screen.append(this.renderSummary());
        break;
    }
    this.root.append(screen);
  }

  // ---------------------------------------------------------------------------

  private renderMenu(): HTMLElement {
    const settings = getSettings();
    const nameInput = el('input', {
      type: 'text',
      value: settings.playerName,
      placeholder: t('lobby.name'),
      maxlength: '20',
    }) as HTMLInputElement;

    const codeInput = el('input', {
      type: 'text',
      placeholder: t('lobby.codePlaceholder'),
      maxlength: '6',
      style: 'text-transform:uppercase',
    }) as HTMLInputElement;

    const name = (): string => {
      const value = nameInput.value.trim() || 'Anon';
      updateSettings({ playerName: value });
      return value;
    };

    const joinNow = (): void => {
      const code = codeInput.value.trim().toUpperCase();
      if (code.length !== 6) {
        this.setError(t('error.bad_code'));
        return;
      }
      this.callbacks.join(name(), code);
    };
    codeInput.addEventListener('keydown', (event) => {
      if ((event as KeyboardEvent).key === 'Enter') joinNow();
    });

    return el(
      'div',
      { class: 'panel' },
      el('h1', { class: 'title', text: t('app.title') }),
      el('p', { class: 'subtitle', text: t('app.subtitle') }),
      el('div', { class: 'row' }, el('div', { class: 'grow' }, nameInput)),
      el('button', { class: 'btn btn--primary', text: t('lobby.host'), onclick: () => this.callbacks.host(name()) }),
      el(
        'div',
        { class: 'row' },
        el('div', { class: 'grow' }, codeInput),
        el('button', { class: 'btn btn--row', text: t('lobby.join'), onclick: joinNow }),
      ),
      el('button', { class: 'btn', text: t('menu.hub'), onclick: () => this.show('hub') }),
      el('button', { class: 'btn', text: t('menu.settings'), onclick: () => this.show('settings') }),
      el('button', { class: 'btn', text: t('menu.howToPlay'), onclick: () => this.show('howto') }),
      el('p', { class: 'error', text: this.error }),
    );
  }

  /** Called whenever the server sends a profile: at hello, after a run, after a purchase. */
  setProfile(profile: ProfileView): void {
    this.profile = profile;
    if (this.current === 'hub') this.render();
  }

  /**
   * The hub: what a run was worth, and what to spend it on.
   *
   * Every price and every level here is only a display of what the server already decided.
   * Pressing buy sends a wish; the panel redraws when the answer comes back, so a refused
   * purchase visibly snaps back instead of leaving an optimistic number on screen.
   */
  private renderHub(): HTMLElement {
    const rows = Object.values(PERK_SPECS).map((spec) => {
      const level = this.profile.perks[spec.id] ?? 0;
      const cost = perkCost(spec.id, level);
      const affordable = cost !== null && this.profile.credits >= cost;

      return el(
        'div',
        { class: 'field' },
        el(
          'div',
          {},
          el('div', { class: 'field__label', text: `${t(spec.nameKey as TranslationKey)}  ${level}/${spec.maxLevel}` }),
          el('p', { class: 'hint', text: t(spec.descriptionKey as TranslationKey) }),
        ),
        cost === null
          ? el('span', { class: 'field__value', text: t('hub.maxed') })
          : el('button', {
              class: `btn btn--row${affordable ? ' btn--primary' : ''}`,
              text: t('hub.buy', { cost }),
              disabled: affordable ? undefined : 'disabled',
              onclick: () => this.callbacks.buyPerk(spec.id),
            }),
      );
    });

    return el(
      'div',
      { class: 'panel panel--wide' },
      el('h1', { class: 'title', text: t('hub.title') }),
      el('p', { class: 'subtitle', text: t('hub.subtitle') }),
      el('div', { class: 'stat' }, el('span', { text: t('hub.credits') }), el('span', { text: String(this.profile.credits) })),
      el('div', { class: 'stat' }, el('span', { text: t('hub.runs') }), el('span', { text: String(this.profile.runs) })),
      el(
        'div',
        { class: 'stat' },
        el('span', { text: t('hub.deepest') }),
        el('span', { text: `${Math.round(this.profile.deepest * 100)}%` }),
      ),
      el('h2', { text: t('hub.perks') }),
      ...rows,
      el('button', { class: 'btn btn--primary', text: t('menu.back'), onclick: () => this.show('menu') }),
    );
  }

  private renderLobby(): HTMLElement {
    const lobby = this.lobby;
    if (!lobby) return el('div', { class: 'panel', text: t('lobby.connecting') });

    const isHost = lobby.hostId === lobby.localId;
    const me = lobby.players.find((p) => p.id === lobby.localId);
    const seedInput = el('input', {
      type: 'text',
      placeholder: t('lobby.seedPlaceholder'),
      maxlength: '32',
    }) as HTMLInputElement;

    const copyButton = el('button', {
      class: 'btn btn--row',
      text: t('lobby.copy'),
      onclick: () => {
        void navigator.clipboard?.writeText(lobby.code).then(() => {
          copyButton.textContent = t('lobby.copied');
          setTimeout(() => (copyButton.textContent = t('lobby.copy')), 1400);
        });
      },
    });

    const list = el('ul', { class: 'players' });
    for (const player of lobby.players) {
      list.append(
        el(
          'li',
          {},
          el('span', { text: player.name }),
          el(
            'span',
            {},
            player.id === lobby.hostId ? el('span', { class: 'tag', text: t('lobby.hostTag') }) : null,
            !player.connected ? el('span', { class: 'tag', text: t('lobby.disconnected') }) : null,
            player.ready ? el('span', { class: 'tag tag--ready', text: t('lobby.ready') }) : null,
          ),
        ),
      );
    }

    return el(
      'div',
      { class: 'panel' },
      el('h2', { text: t('lobby.code') }),
      el('div', { class: 'row' }, el('div', { class: 'code grow', text: lobby.code }), copyButton),
      el('p', { class: 'hint', text: t('lobby.share') }),
      el('h2', { text: `${t('lobby.players')} (${lobby.players.length})` }),
      list,
      el('h2', { text: t('lobby.seed') }),
      seedInput,
      el('div', { style: 'height:14px' }),
      el('button', {
        class: 'btn',
        text: me?.ready ? t('lobby.ready') : t('lobby.notReady'),
        onclick: () => this.callbacks.ready(!me?.ready),
      }),
      isHost
        ? el('button', {
            class: 'btn btn--primary',
            text: t('lobby.start'),
            onclick: () => this.callbacks.start(seedInput.value.trim()),
          })
        : el('p', { class: 'hint', text: t('lobby.waitingForHost') }),
      el('button', { class: 'btn', text: t('menu.settings'), onclick: () => this.show('settings') }),
      el('button', { class: 'btn', text: t('lobby.leave'), onclick: () => this.callbacks.leave() }),
      el('p', { class: 'error', text: this.error }),
    );
  }

  // ---------------------------------------------------------------------------

  private renderSettings(): HTMLElement {
    const settings = getSettings();
    const apply = (patch: Partial<Settings>): void => {
      const next = updateSettings(patch);
      this.callbacks.settingsChanged(next);
    };
    const rerenderApply = (patch: Partial<Settings>): void => {
      apply(patch);
      this.render();
    };

    const tabs = el('div', { class: 'tabs' });
    const tabNames: [string, TranslationKey][] = [
      ['graphics', 'settings.graphics'],
      ['audio', 'settings.audio'],
      ['controls', 'settings.controls'],
      ['accessibility', 'settings.accessibility'],
      ['game', 'settings.game'],
    ];
    for (const [id, key] of tabNames) {
      const tab = el('button', { class: 'tab', text: t(key) });
      tab.setAttribute('aria-selected', String(this.settingsTab === id));
      tab.addEventListener('click', () => {
        this.settingsTab = id;
        this.render();
      });
      tabs.append(tab);
    }

    const body = el('div', {});
    switch (this.settingsTab) {
      case 'graphics':
        body.append(...this.graphicsFields(settings, rerenderApply, apply));
        break;
      case 'audio':
        body.append(...this.audioFields(settings, apply));
        break;
      case 'controls':
        body.append(...this.controlFields(settings, apply));
        break;
      case 'accessibility':
        body.append(...this.accessibilityFields(settings, apply));
        break;
      default:
        body.append(...this.gameFields(settings, rerenderApply));
        break;
    }

    return el(
      'div',
      { class: 'panel panel--wide' },
      el('h1', { class: 'title', style: 'font-size:26px', text: t('settings.title') }),
      tabs,
      body,
      el('div', { style: 'height:18px' }),
      el('button', {
        class: 'btn',
        text: t('settings.reset'),
        onclick: () => {
          const next = resetSettings();
          this.callbacks.settingsChanged(next);
          this.render();
        },
      }),
      el('button', { class: 'btn btn--primary', text: t('menu.back'), onclick: () => this.show(this.previous) }),
    );
  }

  private graphicsFields(
    settings: Settings,
    rerenderApply: (patch: Partial<Settings>) => void,
    apply: (patch: Partial<Settings>) => void,
  ): HTMLElement[] {
    const presets = el('div', { class: 'row', style: 'margin:0' });
    for (const preset of ['low', 'medium', 'high'] as QualityPreset[]) {
      const button = el('button', { class: 'tab', text: t(`settings.quality.${preset}` as TranslationKey) });
      button.setAttribute('aria-selected', String(settings.quality === preset));
      button.addEventListener('click', () => rerenderApply({ quality: preset, ...QUALITY_PRESETS[preset] }));
      presets.append(button);
    }

    return [
      field(t('settings.quality'), presets),
      this.sliderField(t('settings.resolutionScale'), settings.resolutionScale, 0.4, 1.5, 0.05, (v) =>
        apply({ resolutionScale: v }),
      ),
      this.sliderField(t('settings.fov'), settings.fov, 60, 105, 1, (v) => apply({ fov: v }), (v) => `${v}°`),
      this.sliderField(t('settings.bloom'), settings.bloom, 0, 1.5, 0.05, (v) => apply({ bloom: v })),
      field(t('settings.volumetric'), toggle(settings.volumetric, (v) => apply({ volumetric: v }))),
      this.sliderField(t('settings.grain'), settings.grain, 0, 2, 0.05, (v) => apply({ grain: v })),
    ];
  }

  private audioFields(settings: Settings, apply: (patch: Partial<Settings>) => void): HTMLElement[] {
    return [
      this.sliderField(t('settings.master'), settings.masterVolume, 0, 1, 0.01, (v) => apply({ masterVolume: v })),
      this.sliderField(t('settings.sfx'), settings.sfxVolume, 0, 1, 0.01, (v) => apply({ sfxVolume: v })),
      this.sliderField(t('settings.music'), settings.musicVolume, 0, 1, 0.01, (v) => apply({ musicVolume: v })),
      this.sliderField(t('settings.ambience'), settings.ambienceVolume, 0, 1, 0.01, (v) => apply({ ambienceVolume: v })),
    ];
  }

  private controlFields(settings: Settings, apply: (patch: Partial<Settings>) => void): HTMLElement[] {
    const rows: HTMLElement[] = [
      this.sliderField(t('settings.sensitivity'), settings.sensitivity, 0.2, 3, 0.05, (v) =>
        apply({ sensitivity: v }),
      ),
      field(t('settings.invertY'), toggle(settings.invertY, (v) => apply({ invertY: v }))),
    ];

    for (const action of Object.keys(DEFAULT_KEYS) as ActionKey[]) {
      const isRebinding = this.pendingRebind === action;
      const button = el('button', {
        class: 'tab',
        text: isRebinding ? t('settings.pressKey') : keyLabel(settings.keys[action]),
        title: t('settings.rebind'),
      });
      button.setAttribute('aria-selected', String(isRebinding));
      button.addEventListener('click', () => {
        this.pendingRebind = action;
        this.render();
      });
      rows.push(field(t(`key.${action}` as TranslationKey), button));
    }
    return rows;
  }

  private accessibilityFields(settings: Settings, apply: (patch: Partial<Settings>) => void): HTMLElement[] {
    return [
      field(t('settings.subtitles'), toggle(settings.subtitles, (v) => apply({ subtitles: v }))),
      el('p', { class: 'hint', text: t('settings.subtitles.hint') }),
      this.sliderField(t('settings.scareIntensity'), settings.scareIntensity, 0, 1, 0.05, (v) =>
        apply({ scareIntensity: v }),
      ),
      field(t('settings.flashReduction'), toggle(settings.flashReduction, (v) => apply({ flashReduction: v }))),
      el('p', { class: 'hint', text: t('settings.flashReduction.hint') }),
      field(t('settings.screenshake'), toggle(settings.screenshake, (v) => apply({ screenshake: v }))),
      field(t('settings.hallucinations'), toggle(settings.hallucinations, (v) => apply({ hallucinations: v }))),
      el('p', { class: 'hint', text: t('settings.hallucinations.hint') }),
      field(t('settings.crosshair'), toggle(settings.crosshair, (v) => apply({ crosshair: v }))),
    ];
  }

  private gameFields(settings: Settings, rerenderApply: (patch: Partial<Settings>) => void): HTMLElement[] {
    const select = el('select', {}) as HTMLSelectElement;
    for (const [value, label] of [
      ['en', 'English'],
      ['de', 'Deutsch'],
    ] as [Language, string][]) {
      const option = el('option', { value, text: label }) as HTMLOptionElement;
      option.selected = getLanguage() === value;
      select.append(option);
    }
    select.addEventListener('change', () => rerenderApply({ language: select.value as Language }));
    void settings;
    return [field(t('settings.language'), select)];
  }

  private sliderField(
    label: string,
    value: number,
    min: number,
    max: number,
    step: number,
    onChange: (value: number) => void,
    format: (value: number) => string = (v) => v.toFixed(2),
  ): HTMLElement {
    const readout = el('span', { class: 'field__value', text: format(value) });
    const input = el('input', {
      type: 'range',
      min: String(min),
      max: String(max),
      step: String(step),
      value: String(value),
    }) as HTMLInputElement;
    input.addEventListener('input', () => {
      const next = Number(input.value);
      readout.textContent = format(next);
      onChange(next);
    });
    return el(
      'div',
      { class: 'field' },
      el('span', { class: 'field__label', text: label }),
      el('div', { class: 'row', style: 'margin:0' }, input, readout),
    );
  }

  // ---------------------------------------------------------------------------

  private renderHowTo(): HTMLElement {
    const keys: TranslationKey[] = ['howto.goal', 'howto.descent', 'howto.noise', 'howto.light', 'howto.coop'];
    return el(
      'div',
      { class: 'panel panel--wide' },
      el('h1', { class: 'title', style: 'font-size:26px', text: t('howto.title') }),
      ...keys.map((key) => el('p', { class: 'hint', style: 'font-size:13px;max-width:60ch', text: t(key) })),
      el('div', { style: 'height:12px' }),
      el('button', { class: 'btn btn--primary', text: t('menu.back'), onclick: () => this.show(this.previous) }),
    );
  }

  private renderSummary(): HTMLElement {
    const summary = this.summary;
    if (!summary) return el('div', { class: 'panel' });
    const { outcome, stats } = summary;
    const titleKey: TranslationKey =
      outcome === 'extracted' ? 'run.extracted' : outcome === 'wipe' ? 'run.wipe' : 'run.abandoned';

    const stat = (label: string, value: string): HTMLElement =>
      el('div', { class: 'stat' }, el('span', { text: label }), el('span', { text: value }));

    return el(
      'div',
      { class: 'panel' },
      el('h1', {
        class: 'title',
        style: `font-size:30px;${outcome === 'extracted' ? '' : '-webkit-text-fill-color:#d9614c;color:#d9614c'}`,
        text: t(titleKey),
      }),
      stat(t('run.fuses'), `${stats.fusesCollected} / ${stats.fusesTotal}`),
      stat(t('run.survivors'), String(stats.survivors)),
      stat(t('run.duration'), `${Math.floor(stats.durationSeconds / 60)}:${String(stats.durationSeconds % 60).padStart(2, '0')}`),
      stat(t('run.peakDescent'), `${Math.round(stats.peakDescent * 100)}%`),
      stat(t('run.reward'), String(stats.reward)),
      el('div', { style: 'height:18px' }),
      el('button', { class: 'btn btn--primary', text: t('run.continue'), onclick: () => this.callbacks.backToLobby() }),
    );
  }
}
