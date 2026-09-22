import { defineConfig } from 'vitest/config';
import { svelte } from '@sveltejs/vite-plugin-svelte';
import { cpSync, readFileSync } from 'node:fs';
export default defineConfig(({ mode }) => {
  const demo = mode === 'demo';
  const manifest = demo ? JSON.parse(readFileSync('demo-data/manifest.json', 'utf8')) : null;
  return {
    base: demo ? './' : '/',
    define: {
      'import.meta.env.VITE_DEMO_COLLECTION': JSON.stringify(
        manifest?.storageHash ?? manifest?.contentHash ?? '',
      ),
    },
    plugins: [
      svelte(),
      ...(demo
        ? [
            {
              name: 'demo-snapshot',
              closeBundle() {
                cpSync('demo-data', 'dist-demo/demo', { recursive: true });
              },
            },
          ]
        : []),
    ],
    build: { outDir: demo ? 'dist-demo' : 'dist' },
    server: {
      port: 18373,
      strictPort: true,
      proxy: { '/api': process.env.THREECOLOR_API_URL ?? 'http://127.0.0.1:18380' },
    },
    test: { include: ['src/**/*.test.ts'], environment: 'node' },
  };
});
