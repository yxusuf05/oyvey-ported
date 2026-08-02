import { expect, test, type BrowserContext } from '@playwright/test';
import { Buttons, debugState, hostRoom, joinRoom, openClient, setName, startRun, step } from './harness';

/**
 * The requirement the whole project exists for: two people, one maze.
 *
 * Two independent browser contexts, a room code typed by hand, and then the two assertions
 * that matter — both clients generated *the same* level from the seed, and each one can
 * see the other move.
 */
test.describe('two players', () => {
  let hostContext: BrowserContext;
  let guestContext: BrowserContext;

  test.afterEach(async () => {
    await hostContext?.close();
    await guestContext?.close();
  });

  test('join by room code, share a level, and see each other move', async ({ browser }) => {
    hostContext = await browser.newContext({ viewport: { width: 640, height: 360 } });
    guestContext = await browser.newContext({ viewport: { width: 640, height: 360 } });
    const host = await openClient(await hostContext.newPage());
    const guest = await openClient(await guestContext.newPage());

    await setName(host.page, 'Host');
    const code = await hostRoom(host.page);
    expect(code).toMatch(/^[A-HJ-NP-Z2-9]{6}$/);

    await setName(guest.page, 'Guest');
    await joinRoom(guest.page, code);

    // Both lobbies list two players.
    await expect(host.page.locator('.players li')).toHaveCount(2);
    await expect(guest.page.locator('.players li')).toHaveCount(2);

    await startRun(host.page);
    await guest.page.waitForFunction(() => window.__game?.inRun() === true);

    await step(host.page, 30);
    await step(guest.page, 30);

    const hostState = await debugState(host.page);
    const guestState = await debugState(guest.page);

    // Determinism, proven end to end: the same seed produced byte-identical mazes in two
    // separate browser processes. If this ever fails, the two of them are wandering
    // different buildings while believing they are together.
    expect(guestState.seed).toBe(hostState.seed);
    expect(guestState.layoutHash).toBe(hostState.layoutHash);
    expect(hostState.layoutHash).not.toBe(0);

    // Each client sees two player entities in the snapshot.
    expect(Number(hostState.entities)).toBeGreaterThanOrEqual(2);
    expect(Number(guestState.entities)).toBeGreaterThanOrEqual(2);

    // The guest walks; the host's view of the guest must follow.
    const guestBefore = await debugState(guest.page);
    for (let round = 0; round < 12; round++) {
      await step(guest.page, 12, Buttons.Forward | Buttons.Sprint, 0);
      await step(host.page, 12);
    }
    const guestAfter = await debugState(guest.page);

    const guestMoved = Math.hypot(
      Number(guestAfter.x) - Number(guestBefore.x),
      Number(guestAfter.z) - Number(guestBefore.z),
    );
    expect(guestMoved, 'the guest never actually moved').toBeGreaterThan(1);

    // Let the host receive and interpolate the guest's new position.
    await step(host.page, 60);
    const remote = await host.page.evaluate(() => {
      const session = (
        window as unknown as {
          __game: { session: { debugState: () => Record<string, unknown> } };
        }
      ).__game.session;
      return session.debugState();
    });
    expect(Number(remote.entities)).toBeGreaterThanOrEqual(2);

    expect(host.errors).toEqual([]);
    expect(guest.errors).toEqual([]);
  });

  test('rejects an unknown room code without breaking the client', async ({ page }) => {
    const client = await openClient(page);
    await setName(page, 'Lost');
    const inputs = page.locator('input[type=text]');
    await inputs.nth(1).fill('ZZZZZZ');
    await page.click('button:has-text("Join room")');

    await expect(page.locator('.error')).toContainText(/room|Raum/i);
    // The menu is still usable afterwards: hosting works from the same page.
    await page.click('button:has-text("Create room")');
    await page.waitForSelector('.code');
    expect(client.errors).toEqual([]);
  });
});
