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

    const unlit = await frameStatsAtDescent(page, 1);

    // Press and release so the toggle sees a rising edge, then give the server time to
    // acknowledge it — the flashlight is server-authoritative, like everything else.
    await stepRealtime(page, 6, Buttons.Flashlight);
    await stepRealtime(page, 60);

    const lit = await frameStatsAtDescent(page, 1);
    expect(lit.mean).toBeGreaterThan(unlit.mean * 1.05);
  });
});
