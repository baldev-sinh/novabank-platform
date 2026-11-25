import { defineConfig, devices } from '@playwright/test';

/**
 * Browser end-to-end tests (8.6b) against a running stack: the web container on :4200 in front of
 * the real services (docker compose --profile apps). Not part of `ng test`.
 *   npx playwright test
 * https://playwright.dev/docs/ci-intro
 */
export default defineConfig({
  testDir: './e2e',
  // One customer's journey, step by step: the tests share state and run in order.
  fullyParallel: false,
  workers: 1,
  forbidOnly: !!process.env['CI'],
  // No retries: a flaky money flow is a bug to find, not to hide.
  retries: 0,
  // Fail the run after 10 minutes so the reporters still write a report.
  globalTimeout: 10 * 60 * 1000,
  timeout: 90 * 1000,
  reporter: process.env['CI'] ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: process.env['E2E_BASE_URL'] ?? 'http://localhost:4200',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
