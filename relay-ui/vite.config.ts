import { fileURLToPath, URL } from 'node:url';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';
import { rmSync } from 'node:fs';
import { resolve } from 'node:path';
import { defineConfig, loadEnv, type Plugin } from 'vite';
import { CSP } from './csp';
import { configDefaults } from 'vitest/config';

/** public/mockServiceWorker.js is only for `--mode mock`; keep it out of real builds. */
function dropMockWorker(mode: string, outDir: string): Plugin {
  return {
    name: 'relay-drop-mock-worker',
    apply: 'build',
    closeBundle() {
      if (mode !== 'mock') rmSync(resolve(outDir, 'mockServiceWorker.js'), { force: true });
    },
  };
}

export default defineConfig(({ mode }) => {
  // Only RELAY_API_TARGET is read here; it never reaches the browser bundle (no VITE_ prefix).
  const env = loadEnv(mode, process.cwd(), 'RELAY_');
  const apiTarget = env.RELAY_API_TARGET ?? 'http://localhost:8080';

  return {
    plugins: [react(), tailwindcss(), dropMockWorker(mode, 'dist')],
    resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
    server: {
      port: 5173,
      strictPort: true,
      // Same-origin /api means the backend needs no CORS change and Idempotency-Key /
      // Idempotent-Replayed survive (the dev CORS config does not allow or expose them).
      proxy: { '/api': { target: apiTarget, changeOrigin: true } },
    },
    preview: { port: 4173, strictPort: true, headers: { 'Content-Security-Policy': CSP } },
    build: {
      sourcemap: true,
      rollupOptions: {
        // zod ships comments Rollup cannot place; harmless, but it floods CI logs.
        onwarn(warning, warn) {
          if (warning.code === 'INVALID_ANNOTATION' && warning.id?.includes('node_modules/zod'))
            return;
          warn(warning);
        },
      },
    },
    test: {
      environment: 'jsdom',
      globals: false,
      setupFiles: ['./src/test/setup.ts'],
      exclude: [...configDefaults.exclude, 'e2e/**'],
      restoreMocks: true,
      unstubGlobals: true,
    },
  };
});
