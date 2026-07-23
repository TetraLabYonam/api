import { test, expect } from '@playwright/test';
import { login, SEED_PLACE_NAME } from './support';

test.describe('장소 관리', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
    await page.getByRole('link', { name: '장소 관리' }).click();
    await expect(page).toHaveURL('/place-management');
  });

  test('시드 장소를 보여준다', async ({ page }) => {
    await expect(page.getByRole('row', { name: new RegExp(SEED_PLACE_NAME) })).toBeVisible();
  });

  test('신규 장소를 등록하면 목록에 나타난다', async ({ page }) => {
    const uniqueName = `테스트장소${Date.now()}`;

    await page.fill('#place-name-input', uniqueName);
    await page.fill('#place-address-input', '서울시 테스트구');
    await page.selectOption('#place-unittype-select', 'MARKET');
    await page.fill('#place-latitude-input', '37.5');
    await page.fill('#place-longitude-input', '127.0');
    await page.click('button:has-text("등록")');

    await expect(page.getByRole('row', { name: new RegExp(uniqueName) })).toBeVisible();
  });

  test('필수값 미입력 시 등록 버튼이 비활성화된다', async ({ page }) => {
    await expect(page.getByRole('button', { name: '등록' })).toBeDisabled();

    await page.fill('#place-name-input', '이름만입력');
    await expect(page.getByRole('button', { name: '등록' })).toBeDisabled();

    await page.fill('#place-address-input', '주소');
    await page.selectOption('#place-unittype-select', 'MARKET');
    await page.fill('#place-latitude-input', '37.5');
    await page.fill('#place-longitude-input', '127.0');
    await expect(page.getByRole('button', { name: '등록' })).toBeEnabled();
  });

  test('수정 모달로 필드를 바꾸면 목록에 반영된다', async ({ page }) => {
    const uniqueName = `필드변경${Date.now()}`;

    await page.fill('#place-name-input', uniqueName);
    await page.fill('#place-address-input', '수정전주소');
    await page.selectOption('#place-unittype-select', 'MARKET');
    await page.fill('#place-latitude-input', '37.5');
    await page.fill('#place-longitude-input', '127.0');
    await page.click('button:has-text("등록")');

    const row = page.getByRole('row', { name: new RegExp(uniqueName) });
    await expect(row).toBeVisible();
    await row.getByRole('button', { name: '수정' }).click();

    const dialog = page.getByRole('dialog', { name: '장소 수정' });
    const updatedName = `${uniqueName}-수정됨`;
    await dialog.locator('#edit-name-input').fill(updatedName);
    await dialog.getByRole('button', { name: '저장' }).click();

    await expect(page.getByRole('row', { name: new RegExp(updatedName) })).toBeVisible();
  });

  test('활성/비활성 토글이 새로고침 후에도 유지되고, 비활성 장소는 회원 등록 드롭다운에서 사라진다', async ({ page }) => {
    const uniqueName = `토글테스트${Date.now()}`;

    await page.fill('#place-name-input', uniqueName);
    await page.fill('#place-address-input', '주소');
    await page.selectOption('#place-unittype-select', 'MARKET');
    await page.fill('#place-latitude-input', '37.5');
    await page.fill('#place-longitude-input', '127.0');
    await page.click('button:has-text("등록")');

    const row = page.getByRole('row', { name: new RegExp(uniqueName) });
    await expect(row).toBeVisible();
    await row.getByRole('button', { name: new RegExp(`${uniqueName} 비활성화`) }).click();
    await expect(row.locator('.badge')).toHaveText('비활성');

    await page.reload();
    const rowAfterReload = page.getByRole('row', { name: new RegExp(uniqueName) });
    await expect(rowAfterReload.locator('.badge')).toHaveText('비활성');

    await page.getByRole('link', { name: '회원 관리' }).click();
    await expect(page).toHaveURL('/member-management');
    const placeSelect = page.locator('#member-place-select');
    await expect(placeSelect.locator('option', { hasText: uniqueName })).toHaveCount(0);
  });
});
