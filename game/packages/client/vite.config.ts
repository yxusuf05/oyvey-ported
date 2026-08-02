import { defineConfig } from 'vite';

export default defineConfig({
  server: {
    port: 5173,
    // One origin in development and in production: the game server owns both the static
    // files and the socket, so nothing about CORS or ports ever reaches the player.
    proxy: {
      '/ws': { target: 'ws://localhost:8787', ws: true },
      '/healthz': { target: 'http://localhost:8787' },
    },
  },
  optimizeDeps: {
    // The shared package is TypeScript source, not a built artifact; pre-bundling it would
    // silently freeze a stale copy of the level generator into the dev server.
    exclude: ['@game/shared'],
  },
  build: {
    target: 'es2022',
    sourcemap: true,
    chunkSizeWarningLimit: 900,
  },
});
