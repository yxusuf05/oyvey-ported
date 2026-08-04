import { expect, test } from '@playwright/test';
import {
  Buttons,
  debugState,
  frameStats,
  frameStatsAtDescent,
  hostRoom,
  openClient,
  setName,
  startRun,
  step,
  stepRealtime,
  stepUntil,
} from './harness';

test.describe('single player', () => {
  test('starts a run, builds a level and renders a live frame', async ({ page }) => {
    const client = await openClient(page);
    await setName(page, 'Solo');
    await hostRoom(page);
    await startRun(page);

    await step(page, 40);

    const state = await debugState(page);
    expect(state.hasLevel).toBe(true);
    expect(state.layoutHash).not.toBe(0);
    // The world is really being drawn, not just cleared: dozens of chunk draws and tens of
    // thousands of triangles.
    expect(Number(state.drawCalls)).toBeGreaterThan(3);
    expect(Number(state.triangles)).toBeGreaterThan(2000);

    const stats = await frameStats(page);
    // Liveness, not pixel equality. A black screen, a failed shader compile or NaN geometry
    // all collapse the histogram; a real frame has a spread of brightness.
    expect(stats.samples).toBeGreaterThan(1000);
    expect(stats.mean).toBeGreaterThan(0.03);
    expect(stats.mean).toBeLessThan(0.97);
    expect(stats.buckets).toBeGreaterThan(6);

    expect(await page.evaluate(() => window.__contextLost)).toBe(false);
    expect(client.errors).toEqual([]);
  });

  test('the player moves, collides, and never leaves the level', async ({ page }) => {
    await openClient(page);
    await setName(page, 'Walker');
    await hostRoom(page);
    await startRun(page);
    await step(page, 20);

    const before = await debugState(page);

    // Walk in several directions; walls should stop the player, never swallow them.
    // The measurement is the *furthest* the player got from the start, not where they ended
    // up: the four headings oppose each other, so on a maze that boxes the player in early
    // the net displacement can legitimately be centimetres. Since each run generates a new
    // seed, asserting on the endpoint is a coin flip on the layout rather than on movement.
    let reached = 0;
    for (const yaw of [0, Math.PI / 2, Math.PI, -Math.PI / 2]) {
      await step(page, 90, Buttons.Forward | Buttons.Sprint, yaw);
      const now = await debugState(page);
      reached = Math.max(
        reached,
        Math.hypot(Number(now.x) - Number(before.x), Number(now.z) - Number(before.z)),
      );
    }

    const after = await debugState(page);
    expect(reached).toBeGreaterThan(0.5);
    // The map is 128 tiles of 2 m centred on the origin, so nothing may exceed 128 m.
    expect(Math.abs(Number(after.x))).toBeLessThan(128);
    expect(Math.abs(Number(after.z))).toBeLessThan(128);
    expect(Number.isFinite(Number(after.x))).toBe(true);
  });

  test('a click in the middle of the screen reaches the world, not the hud', async ({ page }) => {
    await openClient(page);
    await setName(page, 'Looker');
    await hostRoom(page);
    await startRun(page);
    await step(page, 20);

    // Turning the mouse into a camera means pointer lock, and pointer lock is only granted
    // to the element that was actually clicked. The hud spans the whole viewport, so if it
    // takes the click instead, the view simply never turns — no error, no console message,
    // nothing to search for. This asserts the rule the player cares about: clicking the
    // world hits the world.
    const hit = await page.evaluate(() => {
      const element = document.elementFromPoint(window.innerWidth / 2, window.innerHeight / 2);
      return element ? `${element.tagName.toLowerCase()}#${element.id}` : 'nothing';
    });

    expect(hit).toBe('canvas#viewport');
  });

  test('the descent darkens the world', async ({ page }) => {
    await openClient(page);
    await setName(page, 'Descender');
    await hostRoom(page);
    await startRun(page);
    await step(page, 30);

    const bright = await frameStatsAtDescent(page, 0);
    const middle = await frameStatsAtDescent(page, 0.6);
    const dark = await frameStatsAtDescent(page, 1);

    // The whole premise of the game, in three numbers: the same room, the same geometry,
    // the same camera, getting monotonically darker as one uniform moves.
    expect(middle.mean).toBeLessThan(bright.mean * 0.9);
    expect(dark.mean).toBeLessThan(middle.mean * 0.8);
    expect(dark.mean).toBeLessThan(bright.mean * 0.6);
  });

  test('the flashlight puts light into a dark level', async ({ page }) => {
    await openClient(page);
    await setName(page, 'Lamp');
    await hostRoom(page);
    await startRun(page);
    await step(page, 20);

    // Sample every direction rather than whichever one the player happens to face.
    // Players are fanned out around the spawn by their id, so the heading at spawn is not
    // fixed even for a fixed seed — and at descent 1 the frame is so close to black that a
    // wall thirty centimetres away dominates the mean. Averaging over four headings asks
    // the question the test is named after: did switching it on put light into the level.
    const headings = [0, Math.PI / 2, Math.PI, -Math.PI / 2];
    const sweep = async (): Promise<number> => {
      let total = 0;
      for (const yaw of headings) {
        await step(page, 2, 0, yaw);
        total += (await frameStatsAtDescent(page, 1)).mean;
      }
      return total / headings.length;
    };

    const unlit = await sweep();

    // Press and release so the toggle sees a rising edge, then wait for the server to
    // acknowledge it rather than for a fixed number of frames. The flashlight is
    // server-authoritative like everything else, and a fixed wait is a coin flip whenever
    // the machine is busy — which, running last in the suite, it always is.
    await stepRealtime(page, 6, Buttons.Flashlight);
    const on = await stepUntil(page, (state) => state.flashlightOn === true);
    expect(on, 'the flashlight never came on').toBe(true);

    const lit = await sweep();
    expect(lit).toBeGreaterThan(unlit * 1.05);
  });
});
