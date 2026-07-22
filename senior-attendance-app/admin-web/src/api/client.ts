const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';

let accessToken: string | null = null;

export function setAccessToken(token: string | null): void {
  accessToken = token;
}

export async function login(username: string, password: string): Promise<boolean> {
  const res = await fetch(`${BASE_URL}/api/auth/login`, {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  });
  if (!res.ok) {
    return false;
  }
  const body = await res.json();
  accessToken = body.accessToken;
  return true;
}

// The refresh token is single-use (rotated on every call), so two calls firing
// close together — e.g. React 18 StrictMode's double effect invocation in dev —
// would race: the second call arrives after the first already rotated the
// cookie and fails as "invalid token". Sharing one in-flight promise collapses
// concurrent callers onto a single network call instead.
let refreshInFlight: Promise<boolean> | null = null;

export async function refreshAccessToken(): Promise<boolean> {
  if (refreshInFlight) {
    return refreshInFlight;
  }
  refreshInFlight = (async () => {
    try {
      const res = await fetch(`${BASE_URL}/api/auth/refresh`, {
        method: 'POST',
        credentials: 'include',
      });
      if (!res.ok) {
        accessToken = null;
        return false;
      }
      const body = await res.json();
      accessToken = body.accessToken;
      return true;
    } finally {
      refreshInFlight = null;
    }
  })();
  return refreshInFlight;
}

export async function apiFetch(path: string, init: RequestInit = {}): Promise<Response> {
  const doFetch = () =>
    fetch(`${BASE_URL}${path}`, {
      ...init,
      credentials: 'include',
      headers: {
        ...init.headers,
        ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
      },
    });

  let res = await doFetch();
  if (res.status === 401) {
    const refreshed = await refreshAccessToken();
    if (refreshed) {
      res = await doFetch();
    }
  }
  return res;
}
