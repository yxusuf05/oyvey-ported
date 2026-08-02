/**
 * Boot and the frame loop.
 *
 * The `?e2e=1` mode replaces the animation frame with an exposed `window.__step(ms)` and
 * never constructs an AudioContext. Headless Chromium has no audio device, and driving the
 * simulation by wall clock makes every screenshot test a coin flip — both problems are
 * cheaper to design out here than to chase later.
 */

import './styles.css';
import type { S2C } from '@game/shared/protocol';
import { AudioEngine } from './audio/engine';
import { GameSession } from './game/session';
import { t } from './i18n';
import { Connection } from './net/connection';
import { getSettings, loadSettings, type Settings } from './settings';
import { Hud } from './ui/hud';
import { Screens } from './ui/screens';

const params = new URLSearchParams(location.search);
const E2E = params.get('e2e') === '1';

const canvas = document.getElementById('viewport') as HTMLCanvasElement;
const uiRoot = document.getElementById('ui') as HTMLElement;

let settings: Settings = loadSettings();
if (E2E) {
  // The software renderer in CI cannot afford the full stack, and the test harness cares
  // about liveness and geometry, not about bloom.
  settings = { ...settings, resolutionScale: 0.5, volumetric: false, bloom: 0, grain: 0 };
}

const connection = new Connection();
const audio = new AudioEngine();
const hud = new Hud(settings);
const session = new GameSession(canvas, connection, audio, settings, E2E);

session.applySettings(settings);
hud.applySettings(settings);
session.attachInput(canvas);
audio.onSubtitle((key) => hud.pushSubtitle(key));

let localId = -1;
let inRun = false;

/** Single place every settings change funnels through, wherever it originated. */
function applySettingsEverywhere(next: Settings): void {
  settings = next;
  session.applySettings(next);
  hud.applySettings(next);
  audio.applySettings(
    {
      master: next.masterVolume,
      sfx: next.sfxVolume,
      music: next.musicVolume,
      ambience: next.ambienceVolume,
    },
    next.scareIntensity,
  );
}

const screens = new Screens(uiRoot, {
  host(name) {
    void ensureConnected(name).then(() => connection.send({ t: 'createRoom' }));
  },
  join(name, code) {
    void ensureConnected(name).then(() => connection.send({ t: 'joinRoom', code }));
  },
  leave() {
    connection.send({ t: 'leaveRoom' });
    connection.close();
    screens.show('menu');
  },
  ready(ready) {
    connection.send({ t: 'ready', ready });
  },
  start(seed) {
    connection.send({ t: 'startRun', seed: seed || undefined });
  },
  settingsChanged(next) {
    applySettingsEverywhere(next);
  },
  backToLobby() {
    screens.show('lobby');
  },
});

screens.show('menu');

async function ensureConnected(name: string): Promise<void> {
  if (connection.state === 'open') return;
  try {
    await connection.connect(name);
  } catch {
    screens.setError(t('error.connection'));
    throw new Error('connection failed');
  }
}

let lastRoomState: Extract<S2C, { t: 'roomState' }> | null = null;

function renderLobby(): void {
  if (!lastRoomState) return;
  screens.setLobby({
    code: lastRoomState.code,
    hostId: lastRoomState.hostId,
    localId,
    players: lastRoomState.players,
    connecting: false,
  });
}

connection.onMessage((msg: S2C) => {
  switch (msg.t) {
    case 'welcome':
      localId = msg.playerId;
      // Re-render in case a room state arrived first; the lobby cannot tell which player
      // is you until this message lands.
      renderLobby();
      break;

    case 'roomState':
      lastRoomState = msg;
      renderLobby();
      if (!inRun && screens.screen !== 'settings' && screens.screen !== 'howto') screens.show('lobby');
      break;

    case 'runStart':
      inRun = true;
      screens.show('game');
      uiRoot.append(hud.root);
      // Audio needs a user gesture; by this point the player has clicked several buttons.
      if (!E2E) void audio.start().then(() => applySettingsEverywhere(getSettings()));
      break;

    case 'runEnd':
      inRun = false;
      hud.root.remove();
      document.exitPointerLock?.();
      screens.setSummary(msg.outcome, msg.stats);
      break;

    case 'error':
      screens.setError(msg.message || t('error.connection'));
      break;

    default:
      break;
  }
});

connection.onStateChange((state) => {
  if (state === 'closed' && inRun) {
    inRun = false;
    hud.root.remove();
    screens.show('menu');
    screens.setError(t('error.connection'));
  }
});

// ---------------------------------------------------------------------------
// Pointer lock
// ---------------------------------------------------------------------------

canvas.addEventListener('click', () => {
  if (!inRun || E2E) return;
  void canvas.requestPointerLock();
});
document.addEventListener('pointerlockchange', () => {
  hud.setPointerLocked(document.pointerLockElement === canvas);
});
window.addEventListener('resize', () => session.resize());
session.resize();

// ---------------------------------------------------------------------------
// Frame loop
// ---------------------------------------------------------------------------

let showDebug = E2E || params.get('debug') === '1';
window.addEventListener('keydown', (event) => {
  if (event.code === 'F3') {
    showDebug = !showDebug;
    event.preventDefault();
  }
});

function frame(dt: number): void {
  if (inRun) {
    session.update(dt);
    hud.update(session.hud, showDebug);
  }
}

declare global {
  interface Window {
    __step?: (ms: number) => void;
    __ready?: boolean;
    __debug?: () => Record<string, unknown>;
    __frameStats?: () => { mean: number; buckets: number; samples: number };
    __contextLost?: boolean;
    __game?: {
      connection: Connection;
      session: GameSession;
      setInput: (buttons: number, yaw: number, pitch: number) => void;
      inRun: () => boolean;
    };
  }
}

if (E2E) {
  window.__contextLost = false;
  canvas.addEventListener('webglcontextlost', () => (window.__contextLost = true));
  window.__step = (ms: number) => frame(Math.min(0.1, ms / 1000));
  window.__debug = () => session.debugState();
  // Render and read back inside one task: the pixels are only guaranteed to exist until
  // the frame is handed to the compositor, which happens the moment this yields.
  window.__frameStats = () => {
    frame(1 / 60);
    return session.renderer.readPixelStats();
  };
  window.__game = {
    connection,
    session,
    setInput: (buttons, yaw, pitch) => session.setSyntheticInput(buttons, yaw, pitch),
    inRun: () => inRun,
  };
  window.__ready = true;
} else {
  let last = performance.now();
  const loop = (now: number): void => {
    // Cap the step so an alt-tabbed tab does not resume by simulating five seconds at once.
    const dt = Math.min(0.1, (now - last) / 1000);
    last = now;
    frame(dt);
    requestAnimationFrame(loop);
  };
  requestAnimationFrame(loop);
}
