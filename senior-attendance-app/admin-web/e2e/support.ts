import type { Page } from '@playwright/test';

export const ADMIN_USERNAME = 'admin@example.com';
export const ADMIN_PASSWORD = '1234';

export const SEED_PLACE_NAME = '행복 노인 일자리센터';
export const SEED_MEMBER_PRESENT = '신경준';
export const SEED_MEMBER_ABSENT = '홍길동';
export const SEED_MEMBER_SCHEDULED = '김철수';

export async function login(page: Page): Promise<void> {
  await page.goto('/login');
  await page.fill('#username', ADMIN_USERNAME);
  await page.fill('#password', ADMIN_PASSWORD);
  await page.click('button:has-text("로그인")');
  await page.waitForURL('**/');
}

export function todayIso(): string {
  return new Date().toISOString().slice(0, 10);
}
