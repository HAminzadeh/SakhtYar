import { defineConfig, devices } from '@playwright/test'
// SAFE: tests are local read-only smoke checks. No credentials or production URL.
export default defineConfig({
  testDir: '.', testMatch: '**/*.spec.mjs', timeout: 20000, retries: 0,
  use: { baseURL: 'http://127.0.0.1:5175', trace: 'off', video: 'off', screenshot: 'off' },
  reporter: [['list']],
  projects: [{ name: 'desktop', use: { ...devices['Desktop Chrome'] } },
             { name: 'mobile', use: { ...devices['Pixel 7'] } }],
})
