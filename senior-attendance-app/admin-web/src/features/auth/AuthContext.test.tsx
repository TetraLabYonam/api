import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AuthProvider, useAuth } from './AuthContext';
import * as client from '../../api/client';

vi.mock('../../api/client', async () => {
  const actual = await vi.importActual<typeof client>('../../api/client');
  return {
    ...actual,
    refreshAccessToken: vi.fn().mockResolvedValue(false),
    login: vi.fn(),
    setAccessToken: vi.fn(),
  };
});

function Consumer() {
  const { isLoggedIn, isLoading, login, logout } = useAuth();
  if (isLoading) {
    return <p>로딩 중...</p>;
  }
  return (
    <div>
      <p>{isLoggedIn ? '로그인됨' : '로그아웃됨'}</p>
      <button onClick={() => login('admin@example.com', '1234')}>로그인</button>
      <button onClick={() => logout()}>로그아웃</button>
    </div>
  );
}

describe('AuthContext logout', () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('logout 호출 시 isLoggedIn이 false가 되고 accessToken이 초기화된다', async () => {
    vi.mocked(client.login).mockResolvedValue(true);
    render(
      <AuthProvider>
        <Consumer />
      </AuthProvider>
    );
    const user = userEvent.setup();

    expect(await screen.findByText('로그아웃됨')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '로그인' }));
    expect(await screen.findByText('로그인됨')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '로그아웃' }));

    expect(await screen.findByText('로그아웃됨')).toBeInTheDocument();
    expect(client.setAccessToken).toHaveBeenCalledWith(null);
  });
});
