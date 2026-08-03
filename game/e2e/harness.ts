import type { Page } from '@playwright/test';

/** Mirrors the button bitfield in `@game/shared/sim`. */
export const Buttons = {
  Forward: 1 << 0,
  Back: 1 << 1,
  Left: 1 << 2,
  Right: 1 << 3,
  Sprint: 1 << 4,
  Crouch: 1 << 5,
  Interact: 1 << 6,
  UseItem: 1 << 7,
  Flashlight: 1 << 8,
  BeamMode: 1 << 9,
  Drop: 1 << 10,
} as const;

declare global {
  interface Window {
    __step: (ms: number) => void;
    __ready: boolean;
    __debug: () => Record<string, unknown>;
    __frameStats: () => { mean: number; buckets: number; samples: number };
    __contextLost: boolean;
    __game: {
      setInput: (buttons: number, yaw: number, pitch: number) => void;
      inRun: () => boolean;
      session: { renderer: { setDescent: (descent: number) => void } };
    };
  }
}

export interface Client {
  page: Page;
  errors: string[];
}

/** Opens the game in deterministic mode and captures anything logged as an error. */
export async function openClient(page: Page): Promise<Client> {
  const errors: string[] = [];
  page.on('console', (msg) => {
    if (msg.type() === 'error') errors.push(msg.text());
  });
  page.on('pageerror', (error) => errors.push(String(error)));

  await page.goto('/?e2e=1', { waitUntil: 'domcontentloaded' });
  await page.waitForFunction(() => window.__ready === true);
  return { page, errors };
}

export async function setName(page: Page, name: string): Promise<void> {
  await page.fill('input[type=text]', name);
}

export async function hostRoom(page: Page): Promise<string> {
  await page.click('button:has-text("Create room")');
  await page.waitForSelector('.code');
  return ((await page.textContent('.code')) ?? '').trim();
}

export async function joinRoom(page: Page, code: string): Promise<void> {
  const inputs = page.locator('input[type=text]');
  await inputs.nth(1).fill(code);
  await page.click('button:has-text("Join room")');
  await page.waitForSelector('.code');
}

export async function startRun(page: Page): Promise<void> {
  await page.click('button:has-text("Descend")');
  await page.waitForFunction(() => window.__game?.inRun() === true);
}

/** Advances the simulation by a fixed number of frames — no wall clock, no flake. */
export async function step(page: Page, frames: number, buttons = 0, yaw = 0): Promise<void> {
  await page.evaluate(
    ({ frames: n, buttons: mask, yaw: heading }) => {
      window.__game.setInput(mask, heading, 0);
      for (let i = 0; i < n; i++) window.__step(16);
    },
    { frames, buttons, yaw },
  );
}

/**
 * Steps the simulation while also letting real time pass.
 *
 * `step` is synchronous, so no network round trip can complete during it. Anything that
 * depends on the server answering — a flashlight toggle, another player's position —
 * needs wall-clock time as well as frames.
 */
export async function stepRealtime(page: Page, frames: number, buttons = 0, yaw = 0): Promise<void> {
  const chunk = 6;
  for (let done = 0; done < frames; done += chunk) {
    await step(page, Math.min(chunk, frames - done), buttons, yaw);
    await page.waitForTimeout(25);
  }
}

export async function debugState(page: Page): Promise<Record<string, unknown>> {
  return page.evaluate(() => window.__debug());
}

/**
 * Steps until `predicate` holds, or gives up.
 *
 * Anything server-authoritative — a flashlight toggle, a pickup — takes an unknown number of
 * frames to come back, and a fixed wait turns that into a coin flip the moment the machine
 * is busy. Waiting for the state itself is both faster and honest about what is being
 * tested.
 */
export async function stepUntil(
  page: Page,
  predicate: (state: Record<string, unknown>) => boolean,
  attempts = 40,
): Promise<boolean> {
  for (let i = 0; i < attempts; i++) {
    if (predicate(await debugState(page))) return true;
    await stepRealtime(page, 6);
  }
  return predicate(await debugState(page));
}

export async function frameStats(page: Page): Promise<{ mean: number; buckets: number; samples: number }> {
  return page.evaluate(() => window.__frameStats());
}

/**
 * Forces a descent value and samples the frame in the same task.
 *
 * Splitting these across two `evaluate` calls lets the socket pump run in between, and an
 * arriving snapshot carries the run's real descent — which quietly undoes the value the
 * test just set. The symptom is a test that passes or fails depending on network timing.
 */
export async function frameStatsAtDescent(
  page: Page,
  descent: number,
): Promise<{ mean: number; buckets: number; samples: number }> {
  return page.evaluate((d) => {
    window.__game.session.renderer.setDescent(d);
    window.__step(16);
    window.__step(16);
    return window.__frameStats();
  }, descent);
}
