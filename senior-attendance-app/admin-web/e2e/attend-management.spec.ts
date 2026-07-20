import { test, expect } from '@playwright/test';
import { login, SEED_PLACE_NAME, SEED_MEMBER_PRESENT, SEED_MEMBER_ABSENT, SEED_MEMBER_SCHEDULED, todayIso } from './support';

test.describe('일정별 출석 관리', () => {
  test.beforeEach(async ({ page }) => {
    await login(page);
    await page.getByRole('link', { name: '일정별 출석 관리' }).click();
    await expect(page).toHaveURL('/attend-management');
  });

  test('장소+오늘 날짜로 조회하면 시드 데이터의 출석자 목록을 보여준다', async ({ page }) => {
    await page.selectOption('#place-select', { label: SEED_PLACE_NAME });
    await page.fill('#date-input', todayIso());
    await page.click('button:has-text("조회")');

    await expect(page.getByRole('row', { name: new RegExp(SEED_MEMBER_PRESENT) })).toBeVisible();
    await expect(page.getByRole('row', { name: new RegExp(SEED_MEMBER_ABSENT) })).toBeVisible();
    await expect(page.getByRole('row', { name: new RegExp(SEED_MEMBER_SCHEDULED) })).toBeVisible();
  });

  test('과거 날짜 등 일정이 없는 조합을 조회하면 안내 문구를 보여준다', async ({ page }) => {
    await page.selectOption('#place-select', { label: SEED_PLACE_NAME });
    await page.fill('#date-input', '2000-01-01');
    await page.click('button:has-text("조회")');

    await expect(page.getByText('해당 날짜에 일정이 없습니다')).toBeVisible();
  });

  test('출석 상태와 사유를 수정해 저장하면 실제로 반영된다', async ({ page }) => {
    await page.selectOption('#place-select', { label: SEED_PLACE_NAME });
    await page.fill('#date-input', todayIso());
    await page.click('button:has-text("조회")');

    const row = page.getByRole('row', { name: new RegExp(SEED_MEMBER_SCHEDULED) });
    await row.getByRole('combobox').selectOption('PRESENT');
    await row.getByRole('textbox').fill('e2e 저장 확인');
    await row.getByRole('button', { name: '저장' }).click();

    // 저장 응답이 성공(에러 배너 없음)하고, 재조회된 값이 화면에 그대로 남아있는지 확인한다.
    await expect(page.getByText('출석 정보를 불러오지 못했습니다')).toHaveCount(0);
    await expect(row.getByRole('combobox')).toHaveValue('PRESENT');
    await expect(row.getByRole('textbox')).toHaveValue('e2e 저장 확인');

    // 새로고침 후에도(=서버에 실제로 저장됐는지) 값이 유지되는지 재확인한다.
    await page.reload();
    await page.selectOption('#place-select', { label: SEED_PLACE_NAME });
    await page.fill('#date-input', todayIso());
    await page.click('button:has-text("조회")');
    const rowAfterReload = page.getByRole('row', { name: new RegExp(SEED_MEMBER_SCHEDULED) });
    await expect(rowAfterReload.getByRole('textbox')).toHaveValue('e2e 저장 확인');
  });
});
