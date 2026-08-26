// Evolved for V2 Phase 7 (see docs/v2/architecture.md): every request now attaches the access
// token held in memory, and a 401 from any non-auth endpoint triggers exactly one silent
// /auth/refresh + retry before surfacing an error - the refresh token itself never appears here,
// it travels only as the httpOnly cookie CorsConfig/AuthController already set up in Phase 1.

let accessToken = null;
let sessionHandlers = { onAuthUpdated: () => {}, onSessionExpired: () => {} };

export function setAccessToken(token) {
  accessToken = token || null;
}

export function getAccessToken() {
  return accessToken;
}

// Called once by AuthProvider so this module can push session changes (a successful silent
// refresh, or a refresh failure that means the user is logged out) back into React state without
// api.js depending on React itself.
export function registerSessionHandlers(handlers) {
  sessionHandlers = { ...sessionHandlers, ...handlers };
}

function isProbablyCorsOrNetworkError(err) {
  const msg = String(err?.message || '').toLowerCase();
  return (
    msg.includes('failed to fetch') ||
    msg.includes('networkerror') ||
    msg.includes('load failed') ||
    msg.includes('cors')
  );
}

export function getApiBaseUrl() {
  // Prefer relative URLs when proxy is set in CRA (avoids CORS in dev).
  // If you set REACT_APP_API_BASE_URL, it will use absolute URLs.
  return (process.env.REACT_APP_API_BASE_URL || '').replace(/\/+$/, '');
}

function buildUrl(pathOrUrl) {
  return pathOrUrl.startsWith('http') ? pathOrUrl : `${getApiBaseUrl()}${pathOrUrl}`;
}

async function toApiError(res) {
  const contentType = res.headers.get('content-type') || '';
  const isJson = contentType.includes('application/json');
  const data = isJson ? await res.json().catch(() => null) : null;
  const text = !isJson ? await res.text().catch(() => '') : '';

  const serverMsg = (data && (data.message || data.error)) || text || 'Request failed.';
  const err = new Error(`Request failed (${res.status}). ${serverMsg}`.trim());
  err.status = res.status;
  err.fieldErrors = data?.fieldErrors || null;
  return err;
}

const AUTH_PATH_PREFIX = '/api/v2/auth/';

function isAuthEndpoint(path) {
  return path.startsWith(AUTH_PATH_PREFIX) || path.startsWith('/auth/');
}

/**
 * Core request function. Options: method, body (object, JSON-encoded), query (object of
 * primitives, undefined/null entries skipped), responseType ('json' | 'blob' | 'none'),
 * timeoutMs, skipAuthRetry (used internally to avoid refreshing off of /auth/refresh itself).
 */
export async function apiFetch(path, options = {}) {
  const {
    method = 'GET',
    body,
    query,
    responseType = 'json',
    timeoutMs = 15000,
    skipAuthRetry = false,
  } = options;

  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), timeoutMs);

  try {
    let url = buildUrl(path);
    if (query && typeof query === 'object') {
      const params = new URLSearchParams();
      for (const [key, value] of Object.entries(query)) {
        if (value === undefined || value === null || value === '') continue;
        params.set(key, String(value));
      }
      const qs = params.toString();
      if (qs) url += (url.includes('?') ? '&' : '?') + qs;
    }

    const headers = { Accept: 'application/json' };
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    const token = getAccessToken();
    if (token) headers.Authorization = `Bearer ${token}`;

    const res = await fetch(url, {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
      // Needed so the refresh-token cookie travels on /auth/refresh and /auth/logout - harmless
      // elsewhere since the cookie is scoped to /api/v2/auth (see AuthController).
      credentials: 'include',
      signal: controller.signal,
    });

    if (res.status === 401 && !skipAuthRetry && !isAuthEndpoint(path)) {
      const refreshed = await refreshSession();
      if (refreshed) {
        return apiFetch(path, { ...options, skipAuthRetry: true });
      }
      sessionHandlers.onSessionExpired();
      throw await toApiError(res);
    }

    if (!res.ok) {
      throw await toApiError(res);
    }

    if (responseType === 'none' || res.status === 204) return null;
    if (responseType === 'blob') return await res.blob();
    return await res.json().catch(() => null);
  } catch (err) {
    if (err?.name === 'AbortError') {
      throw new Error('Request timed out. Please try again.');
    }
    if (isProbablyCorsOrNetworkError(err)) {
      throw new Error(
        [
          'Failed to reach the backend (network/CORS).',
          'Make sure Spring Boot is running on port 8080.',
          'If you are running React on 3000, ensure CORS is enabled in Spring OR use the CRA proxy.',
        ].join('\n')
      );
    }
    throw err instanceof Error ? err : new Error('Something went wrong.');
  } finally {
    clearTimeout(timeout);
  }
}

export const getJson = (path, query) => apiFetch(path, { method: 'GET', query });
export const postJson = (path, body) => apiFetch(path, { method: 'POST', body });
export const putJson = (path, body) => apiFetch(path, { method: 'PUT', body });
export const patchJson = (path, body) => apiFetch(path, { method: 'PATCH', body });
export const deleteJson = (path) => apiFetch(path, { method: 'DELETE', responseType: 'none' });

export async function downloadFile(path, query, filename) {
  const blob = await apiFetch(path, { method: 'GET', query, responseType: 'blob' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

export async function refreshSession() {
  try {
    const data = await apiFetch('/api/v2/auth/refresh', {
      method: 'POST',
      skipAuthRetry: true,
    });
    if (data?.accessToken) {
      setAccessToken(data.accessToken);
      sessionHandlers.onAuthUpdated(data);
      return data;
    }
    return null;
  } catch {
    setAccessToken(null);
    return null;
  }
}

export async function login(username, password) {
  const data = await apiFetch('/api/v2/auth/login', {
    method: 'POST',
    body: { username, password },
    skipAuthRetry: true,
  });
  setAccessToken(data.accessToken);
  sessionHandlers.onAuthUpdated(data);
  return data;
}

export async function register(username, email, password) {
  const data = await apiFetch('/api/v2/auth/register', {
    method: 'POST',
    body: { username, email, password },
    skipAuthRetry: true,
  });
  setAccessToken(data.accessToken);
  sessionHandlers.onAuthUpdated(data);
  return data;
}

export async function logout() {
  try {
    await apiFetch('/api/v2/auth/logout', {
      method: 'POST',
      responseType: 'none',
      skipAuthRetry: true,
    });
  } finally {
    setAccessToken(null);
    sessionHandlers.onSessionExpired();
  }
}
