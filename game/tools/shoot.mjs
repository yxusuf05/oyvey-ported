/**
 * Drives the real game in headless Chromium and writes screenshots.
 *
 * This exists so the look can be iterated on without a human in the loop: start a run,
 * step the simulation a fixed number of frames, force a descent value, and capture what
 * the player would actually see.
 *
 * Usage: node tools/shoot.mjs [outDir] [descent,descent,...]
 */

import { existsSync, mkdirSync } from 'node:fs';
import { chromium } from 'playwright';

const OUT = process.argv[2] ?? 'screenshots';
const DESCENTS = (process.argv[3] ?? '0,0.35,0.7,0.95').split(',').map(Number);
const URL = process.env.GAME_URL ?? 'http://localhost:8787';

mkdirSync(OUT, { recursive: true });

/**
 * Prefer a preinstalled browser when the container ships one whose build number does not
 * match this Playwright release. The full chromium build is used rather than the headless
 * shell, which has had ANGLE/SwiftShader gaps.
 */
const PREINSTALLED = '/opt/pw-browsers/chromium-1194/chrome-linux/chrome';
const executablePath = process.env.CHROME_PATH ?? (existsSync(PREINSTALLED) ? PREINSTALLED : undefined);

const browser = await chromium.launch({
  executablePath,
  args: [
    // Chromium refuses software WebGL without an explicit opt-in; without these three
    // flags the canvas silently comes back black in a headless container.
    '--use-gl=angle',
    '--use-angle=swiftshader',
    '--enable-unsafe-swiftshader',
    '--disable-vulkan-surface',
    '--no-sandbox',
    '--disable-dev-shm-usage',
    '--no-proxy-server',
  ],
});

const page = await browser.newPage({ viewport: { width: 1280, height: 720 } });
const errors = [];
page.on('console', (msg) => {
  if (msg.type() === 'error') errors.push(msg.text());
});
page.on('pageerror', (error) => errors.push(String(error)));

await page.goto(`${URL}/?e2e=1`, { waitUntil: 'domcontentloaded' });
await page.waitForFunction(() => window.__ready === true, null, { timeout: 30000 });

await page.fill('input[type=text]', 'Shooter');
await page.click('button:has-text("Create room")');
await page.waitForSelector('.code', { timeout: 15000 });
await page.click('button:has-text("Descend")');
await page.waitForFunction(() => window.__game?.inRun() === true, null, { timeout: 20000 });

// The e2e mode deliberately runs at the lowest settings; screenshots are meant to show the
// game as it actually looks, so restore the full stack.
await page.evaluate(() => {
  window.__game.session.renderer.setQuality({
    resolutionScale: 1,
    bloom: 1,
    volumetric: true,
    grain: 1,
    fov: 78,
  });
  for (let i = 0; i < 40; i++) window.__step(16);
});

// Walk out of the spawn room so the shot is somewhere with structure in it, then turn the
// flashlight on: press the bit, then release it, so the edge is registered.
for (let round = 0; round < 10; round++) {
  await page.evaluate((yaw) => {
    window.__game.setInput(1 | (1 << 4), yaw, 0);
    for (let i = 0; i < 12; i++) window.__step(16);
  }, (round % 3) * 1.2);
  await page.waitForTimeout(40);
}

await page.evaluate(() => {
  window.__game.setInput(1 << 8, 0.6, 0);
  window.__step(16);
  window.__game.setInput(0, 0.6, 0);
  for (let i = 0; i < 10; i++) window.__step(16);
});
await page.waitForTimeout(500);

for (const descent of DESCENTS) {
  await page.evaluate((d) => {
    const session = window.__game.session;
    session.renderer.setDescent(d);
    session.hud.descent = d;
    for (let i = 0; i < 8; i++) window.__step(16);
  }, descent);
  await page.waitForTimeout(400);
  const name = `descent-${String(Math.round(descent * 100)).padStart(3, '0')}.png`;
  await page.screenshot({ path: `${OUT}/${name}` });
  console.log(`wrote ${OUT}/${name}`);
}

const debug = await page.evaluate(() => window.__debug());
console.log('debug:', JSON.stringify(debug, null, 2));
if (errors.length > 0) {
  console.log('console errors:');
  for (const error of errors) console.log('  ', error);
} else {
  console.log('no console errors');
}

await browser.close();
