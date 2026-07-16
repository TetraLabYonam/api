import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { LobbyPage } from './LobbyPage';
import * as client from '../../api/client';

vi.mock('../../api/client', async () => {
  const actual = await vi.importActual<typeof client>('../../api/client');
  return { ...actual, apiFetch: vi.fn() };
});

function summaryResponse(rates: { unitType: string; label: string; attendanceRate: number }[]) {
  return new Response(JSON.stringify(rates), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  });
}

describe('LobbyPage', () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('마운트 시 오늘 기준으로 조회해서 사업단 유형별 카드를 보여준다', async () => {
    vi.mocked(client.apiFetch).mockResolvedValue(
      summaryResponse([
        { unitType: 'PUBLIC_INTEREST', label: '공익형', attendanceRate: 87.3 },
        { unitType: 'MARKET', label: '시장형', attendanceRate: 92.0 },
        { unitType: 'SOCIAL_SERVICE', label: '사회서비스형', attendanceRate: 78.5 },
      ])
    );

    render(<LobbyPage />);

    expect(await screen.findByText('87.3%')).toBeInTheDocument();
    expect(client.apiFetch).toHaveBeenCalledWith('/api/admin/attendance/summary?period=today');
  });

  it('기간 탭을 바꾸면 해당 period로 다시 조회한다', async () => {
    vi.mocked(client.apiFetch)
      .mockResolvedValueOnce(
        summaryResponse([
          { unitType: 'PUBLIC_INTEREST', label: '공익형', attendanceRate: 50 },
          { unitType: 'MARKET', label: '시장형', attendanceRate: 50 },
          { unitType: 'SOCIAL_SERVICE', label: '사회서비스형', attendanceRate: 50 },
        ])
      )
      .mockResolvedValueOnce(
        summaryResponse([
          { unitType: 'PUBLIC_INTEREST', label: '공익형', attendanceRate: 60 },
          { unitType: 'MARKET', label: '시장형', attendanceRate: 60 },
          { unitType: 'SOCIAL_SERVICE', label: '사회서비스형', attendanceRate: 60 },
        ])
      );
    render(<LobbyPage />);
    await screen.findAllByText('50.0%');
    const user = userEvent.setup();

    await user.click(screen.getByRole('tab', { name: '이번주' }));

    expect(client.apiFetch).toHaveBeenLastCalledWith('/api/admin/attendance/summary?period=week');
    const updatedCards = await screen.findAllByText('60.0%');
    expect(updatedCards).toHaveLength(3);
    expect(screen.queryByText('50.0%')).not.toBeInTheDocument();
  });

  it('API 실패 시 에러 메시지와 재시도 버튼을 보여준다', async () => {
    vi.mocked(client.apiFetch).mockResolvedValue(new Response(null, { status: 500 }));

    render(<LobbyPage />);

    expect(await screen.findByText('출석률을 불러오지 못했습니다')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '재시도' })).toBeInTheDocument();
  });
});
