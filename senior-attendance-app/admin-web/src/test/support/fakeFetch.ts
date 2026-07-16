import { vi } from 'vitest';

export type FetchHandler = (url: string, init?: RequestInit) => Response | Promise<Response>;

export function installFakeFetch(handler: FetchHandler) {
  const fetchMock = vi.fn((url: string, init?: RequestInit) =>
    Promise.resolve(handler(url, init)));
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

export function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}
