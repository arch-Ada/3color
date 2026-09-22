import { defineConfig } from '@playwright/test';
export default defineConfig({
  testDir: './tests',
  timeout: 120_000,
  workers: 1,
  use: {
    baseURL: 'http://127.0.0.1:15173',
    locale: 'en-US',
    colorScheme: 'light',
    actionTimeout: 15_000,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  webServer: [
    {
      command: 'java -Xmx512m -jar ../api/build/libs/three-color.jar --server.port=18080',
      url: 'http://127.0.0.1:18080/actuator/health',
      timeout: 300_000,
      reuseExistingServer: false,
    },
    {
      command: 'npm run dev -- --port 15173 --strictPort',
      env: { THREECOLOR_API_URL: 'http://127.0.0.1:18080' },
      url: 'http://127.0.0.1:15173',
      timeout: 30_000,
      reuseExistingServer: false,
    },
  ],
});
