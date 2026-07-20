import { test, expect } from '@playwright/test';
import { ADMIN_USERNAME, ADMIN_PASSWORD } from './support';

test.describe('로그인', () => {
  test('올바른 계정으로 로그인하면 출석 현황 화면으로 이동한다', async ({ page }) => {
    await page.goto('/login');
    await page.fill('#username', ADMIN_USERNAME);
    await page.fill('#password', ADMIN_PASSWORD);
    await page.click('button:has-text("로그인")');

    await expect(page).toHaveURL('/');
    await expect(page.getByRole('heading', { name: '사업단별 출석 현황' })).toBeVisible();
  });

  test('잘못된 비밀번호면 에러 메시지를 보여주고 로그인 화면에 머무른다', async ({ page }) => {
    await page.goto('/login');
    await page.fill('#username', ADMIN_USERNAME);
    await page.fill('#password', 'wrong-password');
    await page.click('button:has-text("로그인")');

    await expect(page.getByRole('alert')).toHaveText('아이디 또는 비밀번호가 올바르지 않습니다');
    await expect(page).toHaveURL('/login');
  });

  test('로그인하지 않고 / 에 접근하면 로그인 화면으로 리다이렉트된다', async ({ page }) => {
    await page.goto('/');
    await expect(page).toHaveURL('/login');
  });
});
