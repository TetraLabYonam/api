// @vitest-environment node
import { afterEach, describe, expect, it, vi } from 'vitest';
import { apiFetch, login, setAccessToken } from './client';
import { installFakeFetch, jsonResponse } from '../test/support/fakeFetch';

describe('login', () => {
  afterEach(() => {
    setAccessToken(null);
    vi.unstubAllGlobals();
  });

  it('성공하면 true를 반환하고 accessToken을 저장한다', async () => {
    installFakeFetch(() => jsonResponse({ accessToken: 'abc123' }));

    const ok = await login('admin@example.com', '1234');

    expect(ok).toBe(true);
  });

  it('401이면 false를 반환한다', async () => {
    installFakeFetch(() => jsonResponse({ error: 'Invalid credentials' }, 401));

    const ok = await login('wrong@example.com', 'wrong');

    expect(ok).toBe(false);
  });
});

describe('apiFetch', () => {
  afterEach(() => {
    setAccessToken(null);
    vi.unstubAllGlobals();
  });

  it('accessToken이 있으면 Authorization 헤더를 붙인다', async () => {
    setAccessToken('token-1');
    const fetchMock = installFakeFetch(() => jsonResponse({ ok: true }));

    await apiFetch('/api/admin/attendance/summary?period=today');

    const [, init] = fetchMock.mock.calls[0];
    expect((init?.headers as Record<string, string>).Authorization).toBe('Bearer token-1');
  });

  it('401을 받으면 refresh 후 한 번 재시도한다', async () => {
    setAccessToken('expired-token');
    let attendanceCallCount = 0;
    installFakeFetch((url) => {
      if (url.includes('/api/auth/refresh')) {
        return jsonResponse({ accessToken: 'new-token' });
      }
      attendanceCallCount += 1;
      return attendanceCallCount === 1 ? jsonResponse({}, 401) : jsonResponse({ data: 'ok' });
    });

    const res = await apiFetch('/api/admin/attendance/summary?period=today');

    expect(res.status).toBe(200);
    expect(attendanceCallCount).toBe(2);
  });
});
