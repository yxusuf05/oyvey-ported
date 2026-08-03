import { existsSync } from 'node:fs';
import { defineConfig } from '@playwright/test';

/**
 * Headless WebGL needs an explicit opt-in: Chromium refuses software rendering by default,
 * and without these flags the canvas comes back black with no error at all.
 *
 * The full chromium build is used rather than the headless shell, which has had gaps in
 * its ANGLE/SwiftShader support. When the container ships a browser whose build number
 * does not match this Playwright release, point at it directly instead of downloading.
 */
const PREINSTALLED = '/opt/pw-browsers/chromium-1194/chrome-linux/chrome';
const executablePath = process.env.CHROME_PATH ?? (existsSync(PREINSTALLED) ? PREINSTALLED : undefined);

const PORT = Number(process.env.E2E_PORT ?? 8788);

export default defineConfig({
  testDir: './e2e',
  // Software rendering is slow; these are generous on purpose so a slow frame is not
  // reported as a failure.
  timeout: 120_000,
  expect: { timeout: 20_000 },
  fullyParallel: false,
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',

  use: {
    baseURL: `http://127.0.0.1:${PORT}`,
    // A small viewport keeps SwiftShader in the region where it renders a frame in
    // milliseconds rather than seconds.
    viewport: { width: 640, height: 360 },
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    launchOptions: {
      executablePath,
      args: [
        '--use-gl=angle',
        '--use-angle=swiftshader',
        '--enable-unsafe-swiftshader',
        '--disable-vulkan-surface',
        '--no-sandbox',
        '--disable-dev-shm-usage',
        '--no-proxy-server',
      ],
    },
  },

  webServer: {
    command: 'pnpm run build && node packages/server/dist/index.js',
    url: `http://127.0.0.1:${PORT}/healthz`,
    // In-memory progression: the browser tests must not write a database file, and must
    // not inherit whatever the previous run banked.
    env: { PORT: String(PORT), HOST: '127.0.0.1', PRISMA_MEMORY_DB: '1' },
    reuseExistingServer: !process.env.CI,
    timeout: 180_000,
    stdout: 'pipe',
    stderr: 'pipe',
  },
});
