import { test, expect } from '@playwright/test';
import { login, SEED_PLACE_NAME, SEED_MEMBER_PRESENT } from './support';

test.describe('회원 관리', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
    await page.getByRole('link', { name: '회원 관리' }).click();
    await expect(page).toHaveURL('/member-management');
  });

  test('시드 회원 목록을 보여준다', async ({ page }) => {
    await expect(page.getByRole('row', { name: new RegExp(SEED_MEMBER_PRESENT) })).toBeVisible();
  });

  test('신규 회원을 등록하면 QR이 표시되고 목록에 나타난다', async ({ page }) => {
    const uniquePhone = `010${Date.now().toString().slice(-8)}`;

    await page.selectOption('#member-place-select', { label: SEED_PLACE_NAME });
    await page.fill('#member-name-input', '이영희');
    await page.fill('#member-phone-input', uniquePhone);
    await page.click('button:has-text("등록")');

    await expect(page.getByText(/이영희님 \(사번 \d+\) 등록 완료/)).toBeVisible();
    await expect(page.getByRole('img', { name: /QR/ })).toBeVisible();
    await expect(page.getByRole('link', { name: 'QR 다운로드' })).toBeVisible();
    await expect(page.getByRole('row', { name: /이영희/ })).toBeVisible();
  });

  test('장소/이름/전화번호 중 하나라도 비어 있으면 등록 버튼이 비활성화된다', async ({ page }) => {
    await expect(page.getByRole('button', { name: '등록' })).toBeDisabled();

    await page.fill('#member-name-input', '이영희');
    await expect(page.getByRole('button', { name: '등록' })).toBeDisabled();

    await page.fill('#member-phone-input', '01099999999');
    await expect(page.getByRole('button', { name: '등록' })).toBeDisabled();

    await page.selectOption('#member-place-select', { label: SEED_PLACE_NAME });
    await expect(page.getByRole('button', { name: '등록' })).toBeEnabled();
  });

  test('활성/비활성 토글을 누르면 상태가 바뀌고 새로고침 후에도 유지된다', async ({ page }) => {
    const row = page.getByRole('row', { name: new RegExp(SEED_MEMBER_PRESENT) });
    await expect(row.locator('.badge')).toHaveText('활성');

    await row.getByRole('button', { name: new RegExp(`${SEED_MEMBER_PRESENT} 비활성화`) }).click();
    await expect(row.locator('.badge')).toHaveText('비활성');

    await page.reload();
    const rowAfterReload = page.getByRole('row', { name: new RegExp(SEED_MEMBER_PRESENT) });
    await expect(rowAfterReload.locator('.badge')).toHaveText('비활성');

    // 다음 테스트 실행을 위해 원상복구
    await rowAfterReload.getByRole('button', { name: new RegExp(`${SEED_MEMBER_PRESENT} 활성화`) }).click();
    await expect(rowAfterReload.locator('.badge')).toHaveText('활성');
  });
});
