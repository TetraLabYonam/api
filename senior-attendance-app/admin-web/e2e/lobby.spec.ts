import { test, expect } from '@playwright/test';
import { login } from './support';

test.describe('출석 현황 로비', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
  });

  test('사업단 유형별 출석률 카드를 보여준다', async ({ page }) => {
    await expect(page.getByText('공익형')).toBeVisible();
    await expect(page.getByText('시장형')).toBeVisible();
    await expect(page.getByText('사회서비스형')).toBeVisible();
    // 시드 데이터 기준 공익형만 출석 기록이 있어 0%보다 큰 값이 표시된다.
    await expect(page.locator('.card', { hasText: '공익형' })).toContainText('%');
  });

  test('기간 탭을 바꾸면 목록이 다시 조회된다', async ({ page }) => {
    const request = page.waitForResponse((res) => res.url().includes('/api/admin/attendance/summary?period=week'));
    await page.getByRole('tab', { name: '이번주' }).click();
    await request;
    await expect(page.getByRole('tab', { name: '이번주' })).toHaveAttribute('aria-selected', 'true');
  });

  test('사이드바에서 일정별 출석 관리로 이동할 수 있다', async ({ page }) => {
    await page.getByRole('link', { name: '일정별 출석 관리' }).click();
    await expect(page).toHaveURL('/attend-management');
    await expect(page.getByRole('heading', { name: '일정별 출석 관리' })).toBeVisible();
  });
});
