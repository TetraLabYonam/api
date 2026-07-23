import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { PlaceManagementPage } from './PlaceManagementPage';
import * as client from '../../api/client';
import * as authContext from '../auth/AuthContext';

function renderPage() {
  return render(
    <MemoryRouter>
      <PlaceManagementPage />
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
  {
    id: 1,
    name: '행복경로당',
    address: '서울시',
    unitType: 'PUBLIC_INTEREST',
    description: '',
    latitude: 37.5,
    longitude: 127.0,
    active: true,
  },
];

describe('PlaceManagementPage', () => {
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

  it('등록 폼 제출 성공 시 목록에 반영한다', async () => {
    vi.mocked(client.apiFetch)
      .mockResolvedValueOnce(jsonResponse([]))
      .mockResolvedValueOnce(
        jsonResponse({
          id: 2,
          name: '새장소',
          address: '부산시',
          unitType: 'MARKET',
          description: '',
          latitude: 35.1,
          longitude: 129.0,
          active: true,
        })
      )
      .mockResolvedValueOnce(
        jsonResponse([
          {
            id: 2,
            name: '새장소',
            address: '부산시',
            unitType: 'MARKET',
            description: '',
            latitude: 35.1,
            longitude: 129.0,
            active: true,
          },
        ])
      );

    const user = userEvent.setup();
    renderPage();

    await user.type(screen.getByLabelText('이름'), '새장소');
    await user.type(screen.getByLabelText('주소'), '부산시');
    await user.selectOptions(screen.getByRole('combobox', { name: '유형' }), 'MARKET');
    await user.type(screen.getByLabelText('위도'), '35.1');
    await user.type(screen.getByLabelText('경도'), '129.0');
    await user.click(screen.getByRole('button', { name: '등록' }));

    expect(await screen.findByText('새장소')).toBeInTheDocument();
    expect(client.apiFetch).toHaveBeenCalledWith('/api/admin/places', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        name: '새장소',
        address: '부산시',
        unitType: 'MARKET',
        description: null,
        latitude: 35.1,
        longitude: 129.0,
      }),
    });
  });

  it('필수값 미입력 시 등록 버튼이 비활성화된다', async () => {
    vi.mocked(client.apiFetch).mockResolvedValueOnce(jsonResponse([]));

    const user = userEvent.setup();
    renderPage();

    await user.type(screen.getByLabelText('이름'), '새장소');

    expect(screen.getByRole('button', { name: '등록' })).toBeDisabled();
  });

  it('목록을 렌더링하고 활성 토글을 누르면 PATCH를 호출한다', async () => {
    vi.mocked(client.apiFetch)
      .mockResolvedValueOnce(jsonResponse(PLACES))
      .mockResolvedValueOnce(jsonResponse({ ...PLACES[0], active: false }))
      .mockResolvedValueOnce(jsonResponse([{ ...PLACES[0], active: false }]));

    const user = userEvent.setup();
    renderPage();

    await screen.findByText('행복경로당');
    await user.click(screen.getByRole('button', { name: '행복경로당 비활성화' }));

    await waitFor(() => {
      expect(client.apiFetch).toHaveBeenCalledWith('/api/admin/places/1', {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          name: '행복경로당',
          address: '서울시',
          unitType: 'PUBLIC_INTEREST',
          description: '',
          latitude: 37.5,
          longitude: 127.0,
          active: false,
        }),
      });
    });

    expect(await screen.findByRole('button', { name: '행복경로당 활성화' })).toBeInTheDocument();
  });

  it('수정 버튼을 누르면 모달에 현재 값이 채워지고, 저장하면 PATCH를 호출한다', async () => {
    vi.mocked(client.apiFetch)
      .mockResolvedValueOnce(jsonResponse(PLACES))
      .mockResolvedValueOnce(jsonResponse({ ...PLACES[0], name: '변경된이름' }))
      .mockResolvedValueOnce(jsonResponse([{ ...PLACES[0], name: '변경된이름' }]));

    const user = userEvent.setup();
    renderPage();

    await screen.findByText('행복경로당');
    await user.click(screen.getByRole('button', { name: '수정' }));

    const dialog = screen.getByRole('dialog', { name: '장소 수정' });
    const nameInput = within(dialog).getByLabelText('이름') as HTMLInputElement;
    expect(nameInput.value).toBe('행복경로당');

    await user.clear(nameInput);
    await user.type(nameInput, '변경된이름');
    await user.click(within(dialog).getByRole('button', { name: '저장' }));

    await waitFor(() => {
      expect(client.apiFetch).toHaveBeenCalledWith('/api/admin/places/1', {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          name: '변경된이름',
          address: '서울시',
          unitType: 'PUBLIC_INTEREST',
          description: '',
          latitude: 37.5,
          longitude: 127.0,
          active: true,
        }),
      });
    });

    expect(await screen.findByText('변경된이름')).toBeInTheDocument();
  });
});
