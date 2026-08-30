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

// --- Cold-start handling -----------------------------------------------------------------
//
// The deployed backend runs on Render's free tier, which stops the container after 15 minutes
// without traffic (see DEPLOYMENT.md). The next request then has to wait out a full cold boot -
// container start, JVM start, Spring context, Flyway - which routinely runs past 50 seconds.
//
// Against a flat 15s timeout that meant every first visit after a quiet spell ended in
// "Request timed out. Please try again." The app looked broken at precisely the moment a new
// visitor was deciding whether it worked, the advice was wrong (retrying within 15s could not
// succeed either), and nothing on screen hinted that waiting was the answer.
//
// So the timeout is not a constant. When the backend might be asleep we allow a cold boot's worth
// of time; once it has answered we go back to a tight one, because a warm server taking 15s means
// something is genuinely wrong and spending a minute to say so would help nobody.
const WARM_TIMEOUT_MS = 15000;
const COLD_START_TIMEOUT_MS = 75000;

// Render's idle window is 15 minutes. Sitting slightly under it means that at the boundary we err
// towards the patient timeout rather than the impatient one - the cost of being wrong in that
// direction is a slower error message, in the other it is a spurious failure.
const ASSUME_COLD_AFTER_IDLE_MS = 14 * 60 * 1000;

// When the server last proved it was awake. Null until it first does, which is why the opening
// request of a session always gets the cold-start budget.
let lastServerContactAt = null;

// Deliberately keyed on elapsed idle time rather than a one-shot "first request" flag: a session
// left open over a long lunch faces exactly the same cold boot as a freshly loaded tab, and a flag
// would have covered only the second case.
function serverMayBeAsleep() {
  if (lastServerContactAt === null) return true;
  return Date.now() - lastServerContactAt > ASSUME_COLD_AFTER_IDLE_MS;
}

// Any HTTP response proves the container is up, including a 4xx/5xx - it is reachability being
// tracked here, not success.
function noteServerContact() {
  lastServerContactAt = Date.now();
}

// Test seam: lets a test start from a known warmth state instead of inheriting whatever an earlier
// test in the same module instance left behind.
export function resetColdStartTracking() {
  lastServerContactAt = null;
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
    timeoutMs,
    skipAuthRetry = false,
  } = options;

  // Captured once up front so the catch block below reports on the situation this request actually
  // ran under - by the time it aborts, a request that raced ahead of it may already have flipped
  // the shared warmth state.
  const assumeCold = serverMayBeAsleep();
  const effectiveTimeoutMs =
    timeoutMs !== undefined ? timeoutMs : assumeCold ? COLD_START_TIMEOUT_MS : WARM_TIMEOUT_MS;

  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), effectiveTimeoutMs);

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

    noteServerContact();

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
      // Same underlying event, two very different things to tell the user about it. Cold start is
      // expected free-tier behaviour with a known remedy (wait, then retry); a timeout against a
      // server we were talking to moments ago is a real fault, and saying "it's just waking up"
      // there would be a comforting lie that sends the user off retrying forever.
      const err2 = new Error(
        assumeCold
          ? 'Waking up the server - this can take up to a minute on the free tier. Please try again shortly.'
          : 'Request timed out. Please try again.'
      );
      err2.isColdStart = assumeCold;
      throw err2;
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
