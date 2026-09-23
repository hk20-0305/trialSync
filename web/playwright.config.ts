import { defineConfig, devices } from '@playwright/test'

const webUrl = 'http://127.0.0.1:5175'
const apiUrl = process.env.API_URL ?? 'http://127.0.0.1:8000'

export default defineConfig({
  testDir: './e2e',

  fullyParallel: false,
  workers: 1,
  retries: 0,

  reporter: [['list']],

  use: {
    ...devices['Desktop Chrome'],
    baseURL: webUrl,
    headless: true,

    launchOptions: {
      executablePath:
        process.env.PLAYWRIGHT_CHROMIUM_PATH ?? '/usr/bin/chromium',
      args: ['--no-sandbox'],
    },

    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
  },

  webServer: [
    {
      command: `cross-env VITE_API_BASE_URL=${apiUrl}/api/v1 npm run dev -- --host 127.0.0.1 --port 5175`,
      url: webUrl,
      timeout: 30_000,
      reuseExistingServer: true,
      stdout: 'pipe',
      stderr: 'pipe',
    },
  ],
})