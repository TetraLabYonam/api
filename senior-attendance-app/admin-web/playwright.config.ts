import { defineConfig, devices } from '@playwright/test';

const FRONTEND_PORT = 5173;
const BACKEND_PORT = 8080;

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  reporter: 'list',
  use: {
    baseURL: `http://localhost:${FRONTEND_PORT}`,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  webServer: [
    {
      command: 'npm run dev -- --port ' + FRONTEND_PORT,
      url: `http://localhost:${FRONTEND_PORT}`,
      reuseExistingServer: !process.env.CI,
      timeout: 30_000,
    },
    {
      command: 'cd ../backend && ./gradlew bootRun --args="--spring.profiles.active=local,e2e-seed"',
      // No health endpoint is exposed; a plain TCP accept on the port is enough
      // proof Spring Boot finished starting (Tomcat only binds the port at the end).
      port: BACKEND_PORT,
      reuseExistingServer: !process.env.CI,
      timeout: 180_000,
      env: {
        MEMBER_OTP_HASH_SECRET: 'local-dev-otp-secret-change-me',
        REFRESH_TOKEN_HASH_SECRET: 'local-dev-refresh-secret-change-me',
      },
    },
  ],
});
