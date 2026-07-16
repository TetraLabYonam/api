import { afterEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { App } from './App';
import * as client from './api/client';

vi.mock('./api/client', async () => {
  const actual = await vi.importActual<typeof client>('./api/client');
  return {
    ...actual,
    refreshAccessToken: vi.fn(),
    apiFetch: vi.fn(),
  };
});

describe('App', () => {
  afterEach(() => {
    vi.clearAllMocks();
    window.history.pushState({}, '', '/');
  });

  it('로그인 안 된 상태로 / 접근 시 로그인 화면으로 리다이렉트된다', async () => {
    vi.mocked(client.refreshAccessToken).mockResolvedValue(false);
    window.history.pushState({}, '', '/');

    render(<App />);

    expect(await screen.findByRole('heading', { name: '관리자 로그인' })).toBeInTheDocument();
  });

  it('로그인 된 상태(refresh 성공)면 / 접근 시 로비 화면이 보인다', async () => {
    vi.mocked(client.refreshAccessToken).mockResolvedValue(true);
    vi.mocked(client.apiFetch).mockResolvedValue(
      new Response(
        JSON.stringify([
          { unitType: 'PUBLIC_INTEREST', label: '공익형', attendanceRate: 80 },
          { unitType: 'MARKET', label: '시장형', attendanceRate: 90 },
          { unitType: 'SOCIAL_SERVICE', label: '사회서비스형', attendanceRate: 70 },
        ]),
        { status: 200, headers: { 'Content-Type': 'application/json' } }
      )
    );
    window.history.pushState({}, '', '/');

    render(<App />);

    expect(await screen.findByRole('heading', { name: '사업단별 출석 현황' })).toBeInTheDocument();
  });
});
