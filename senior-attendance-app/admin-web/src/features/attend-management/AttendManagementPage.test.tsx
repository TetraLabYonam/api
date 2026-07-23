import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { AttendManagementPage } from './AttendManagementPage';
import * as client from '../../api/client';
import * as authContext from '../auth/AuthContext';

function renderPage() {
  return render(
    <MemoryRouter>
      <AttendManagementPage />
    </MemoryRouter>
  );
}

vi.mock('../../api/client', async () => {
  const actual = await vi.importActual<typeof client>('../../api/client');
  return { ...actual, apiFetch: vi.fn() };
});

vi.mock('../auth/AuthContext', async () => {
  const actual = await vi.importActual<typeof authContext>('../auth/AuthContext');
  return { ...actual, useAuth: vi.fn() };
});

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

const PLACES = [
  { id: 1, name: '행복경로당', address: '서울시', unitType: 'PUBLIC_INTEREST', description: '', latitude: 0, longitude: 0, active: true },
  { id: 2, name: '사랑경로당', address: '서울시', unitType: 'MARKET', description: '', latitude: 0, longitude: 0, active: true },
];

function scheduleWith(
  attendees: {
    attendId: number;
    memberId: number;
    memberName: string;
    status: string;
    note: string | null;
    attendedAt: string | null;
  }[]
) {
  return {
    scheduleId: 10,
    title: '오전 프로그램',
    scheduleDate: '2026-07-20',
    startTime: '09:00',
    endTime: '12:00',
    placeName: '행복경로당',
    attendees,
  };
}

async function selectPlaceAndSearch() {
  const user = userEvent.setup();
  await screen.findByRole('option', { name: '행복경로당' });
  await user.selectOptions(screen.getByRole('combobox', { name: '장소' }), '1');
  const dateInput = screen.getByLabelText('날짜') as HTMLInputElement;
  await user.clear(dateInput);
  await user.type(dateInput, '2026-07-20');
  await user.click(screen.getByRole('button', { name: '조회' }));
  return user;
}

describe('AttendManagementPage', () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  beforeEach(() => {
    vi.mocked(authContext.useAuth).mockReturnValue({
      isLoggedIn: true,
      isLoading: false,
      login: vi.fn(),
      logout: vi.fn(),
    });
  });

  it('장소+날짜 조회에 성공하면 출석자 테이블을 렌더링한다', async () => {
    vi.mocked(client.apiFetch)
      .mockResolvedValueOnce(jsonResponse(PLACES))
      .mockResolvedValueOnce(
        jsonResponse(
          scheduleWith([
            { attendId: 100, memberId: 1, memberName: '김철수', status: 'SCHEDULED', note: null, attendedAt: null },
          ])
        )
      );

    renderPage();
    await selectPlaceAndSearch();

    expect(await screen.findByText('김철수')).toBeInTheDocument();
    expect(client.apiFetch).toHaveBeenLastCalledWith('/api/admin/schedules?placeId=1&date=2026-07-20');
  });

  it('일정이 없으면(404) 안내 문구를 보여준다', async () => {
    vi.mocked(client.apiFetch)
      .mockResolvedValueOnce(jsonResponse(PLACES))
      .mockResolvedValueOnce(new Response(null, { status: 404 }));

    renderPage();
    await selectPlaceAndSearch();

    expect(await screen.findByText('해당 날짜에 일정이 없습니다')).toBeInTheDocument();
  });

  it('행의 상태/사유를 수정하면 PATCH를 호출하고 목록이 갱신된 값을 보여준다', async () => {
    vi.mocked(client.apiFetch)
      .mockResolvedValueOnce(jsonResponse(PLACES))
      .mockResolvedValueOnce(
        jsonResponse(
          scheduleWith([
            { attendId: 100, memberId: 1, memberName: '김철수', status: 'SCHEDULED', note: null, attendedAt: null },
          ])
        )
      )
      .mockResolvedValueOnce(
        jsonResponse({
          attendId: 100,
          memberId: 1,
          memberName: '김철수',
          status: 'PRESENT',
          note: '정상 출석',
          attendedAt: '2026-07-20T09:00:00',
        })
      )
      .mockResolvedValueOnce(
        jsonResponse(
          scheduleWith([
            {
              attendId: 100,
              memberId: 1,
              memberName: '김철수',
              status: 'PRESENT',
              note: '정상 출석',
              attendedAt: '2026-07-20T09:00:00',
            },
          ])
        )
      );

    renderPage();
    const user = await selectPlaceAndSearch();

    await screen.findByText('김철수');
    const row = screen.getByText('김철수').closest('tr')!;

    await user.selectOptions(within(row).getByRole('combobox', { name: '김철수 상태' }), 'PRESENT');
    const noteInput = within(row).getByRole('textbox', { name: '김철수 사유' });
    await user.clear(noteInput);
    await user.type(noteInput, '정상 출석');
    await user.click(within(row).getByRole('button', { name: '저장' }));

    await waitFor(() => {
      expect(client.apiFetch).toHaveBeenCalledWith('/api/admin/attend/100', {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ status: 'PRESENT', note: '정상 출석' }),
      });
    });

    expect(await screen.findByDisplayValue('정상 출석')).toBeInTheDocument();
  });
});
