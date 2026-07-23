import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { MemberManagementPage } from './MemberManagementPage';
import * as client from '../../api/client';
import * as authContext from '../auth/AuthContext';

function renderPage() {
  return render(
    <MemoryRouter>
      <MemberManagementPage />
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
];

const MEMBERS = [{ employeeId: 1001, name: '김철수', placeId: 1, placeName: '행복경로당', active: true }];

async function waitForPlacesLoaded() {
  await screen.findByRole('option', { name: '행복경로당' });
}

describe('MemberManagementPage', () => {
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

  it('등록 폼 제출 성공 시 QR을 표시한다', async () => {
    vi.mocked(client.apiFetch)
      .mockResolvedValueOnce(jsonResponse(PLACES))
      .mockResolvedValueOnce(jsonResponse([]))
      .mockResolvedValueOnce(
        jsonResponse({ employeeId: 1002, name: '이영희', placeId: 1, qrPayload: '1002:01012345678' })
      )
      .mockResolvedValueOnce(jsonResponse([{ employeeId: 1002, name: '이영희', placeId: 1, placeName: '행복경로당', active: true }]));

    const user = userEvent.setup();
    renderPage();
    await waitForPlacesLoaded();

    await user.selectOptions(screen.getByRole('combobox', { name: '장소' }), '1');
    await user.type(screen.getByLabelText('이름'), '이영희');
    await user.type(screen.getByLabelText('전화번호'), '01012345678');
    await user.click(screen.getByRole('button', { name: '등록' }));

    const qr = await screen.findByRole('img', { name: /QR/ });
    expect(qr).toBeInTheDocument();
    expect(client.apiFetch).toHaveBeenCalledWith('/api/admin/members', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name: '이영희', phoneNumber: '01012345678', placeId: 1 }),
    });
  });

  it('장소 미선택 시 등록 버튼이 비활성화된다', async () => {
    vi.mocked(client.apiFetch).mockResolvedValueOnce(jsonResponse(PLACES)).mockResolvedValueOnce(jsonResponse([]));

    const user = userEvent.setup();
    renderPage();
    await waitForPlacesLoaded();

    await user.type(screen.getByLabelText('이름'), '이영희');
    await user.type(screen.getByLabelText('전화번호'), '01012345678');

    expect(screen.getByRole('button', { name: '등록' })).toBeDisabled();
  });

  it('회원 목록을 렌더링하고 활성 토글을 누르면 PATCH를 호출한다', async () => {
    vi.mocked(client.apiFetch)
      .mockResolvedValueOnce(jsonResponse(PLACES))
      .mockResolvedValueOnce(jsonResponse(MEMBERS))
      .mockResolvedValueOnce(jsonResponse({}))
      .mockResolvedValueOnce(
        jsonResponse([{ employeeId: 1001, name: '김철수', placeId: 1, placeName: '행복경로당', active: false }])
      );

    const user = userEvent.setup();
    renderPage();

    await screen.findByText('김철수');
    await user.click(screen.getByRole('button', { name: '김철수 비활성화' }));

    await waitFor(() => {
      expect(client.apiFetch).toHaveBeenCalledWith('/api/admin/members/1001', {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ active: false }),
      });
    });

    expect(await screen.findByRole('button', { name: '김철수 활성화' })).toBeInTheDocument();
  });
});
