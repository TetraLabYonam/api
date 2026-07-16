import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { LoginPage } from './LoginPage';
import { AuthProvider } from './AuthContext';
import * as client from '../../api/client';

vi.mock('../../api/client', async () => {
  const actual = await vi.importActual<typeof client>('../../api/client');
  return {
    ...actual,
    refreshAccessToken: vi.fn().mockResolvedValue(false),
    login: vi.fn(),
  };
});

function renderLoginPage() {
  return render(
    <MemoryRouter>
      <AuthProvider>
        <LoginPage />
      </AuthProvider>
    </MemoryRouter>
  );
}

describe('LoginPage', () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('로그인 성공 시 에러 메시지가 없다', async () => {
    vi.mocked(client.login).mockResolvedValue(true);
    renderLoginPage();
    const user = userEvent.setup();

    await user.type(screen.getByLabelText('아이디'), 'admin@example.com');
    await user.type(screen.getByLabelText('비밀번호'), '1234');
    await user.click(screen.getByRole('button', { name: '로그인' }));

    expect(client.login).toHaveBeenCalledWith('admin@example.com', '1234');
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('로그인 실패 시 에러 메시지를 보여준다', async () => {
    vi.mocked(client.login).mockResolvedValue(false);
    renderLoginPage();
    const user = userEvent.setup();

    await user.type(screen.getByLabelText('아이디'), 'wrong@example.com');
    await user.type(screen.getByLabelText('비밀번호'), 'wrong');
    await user.click(screen.getByRole('button', { name: '로그인' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      '아이디 또는 비밀번호가 올바르지 않습니다'
    );
  });
});
