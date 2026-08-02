import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    include: ['packages/*/test/**/*.test.ts'],
    environment: 'node',
    // Level-generation property tests sweep thousands of seeds; the default 5 s is tight.
    testTimeout: 60_000,
    reporters: ['default'],
  },
});
