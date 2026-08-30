import { rest } from 'msw';
import { server } from './mocks/server';
import {
  apiFetch,
  getAccessToken,
  registerSessionHandlers,
  resetColdStartTracking,
  setAccessToken,
} from './api';

beforeEach(() => {
  setAccessToken(null);
  resetColdStartTracking();
  registerSessionHandlers({ onAuthUpdated: () => {}, onSessionExpired: () => {} });
});

test('a 401 triggers exactly one silent refresh, then retries the original request', async () => {
  setAccessToken('expired-token');
  let protectedCallCount = 0;

  server.use(
    rest.get('/api/v2/widgets', (req, res, ctx) => {
      protectedCallCount += 1;
      const authHeader = req.headers.get('authorization');
      if (authHeader === 'Bearer expired-token') {
        return res(ctx.status(401), ctx.json({ message: 'Token expired' }));
      }
      return res(ctx.status(200), ctx.json({ ok: true }));
    }),
    rest.post('/api/v2/auth/refresh', (req, res, ctx) =>
      res(ctx.status(200), ctx.json({ accessToken: 'fresh-token', expiresInSeconds: 900, user: { username: 'demo' } }))
    )
  );

  const onAuthUpdated = jest.fn();
  registerSessionHandlers({ onAuthUpdated, onSessionExpired: () => {} });

  const data = await apiFetch('/api/v2/widgets');

  expect(data).toEqual({ ok: true });
  expect(protectedCallCount).toBe(2);
  expect(getAccessToken()).toBe('fresh-token');
  expect(onAuthUpdated).toHaveBeenCalledWith(
    expect.objectContaining({ accessToken: 'fresh-token' })
  );
});

test('when the refresh itself fails, the original 401 propagates and the session is cleared', async () => {
  setAccessToken('expired-token');

  server.use(
    rest.get('/api/v2/widgets', (req, res, ctx) => res(ctx.status(401), ctx.json({ message: 'Token expired' }))),
    rest.post('/api/v2/auth/refresh', (req, res, ctx) => res(ctx.status(401), ctx.json({ message: 'No session' })))
  );

  const onSessionExpired = jest.fn();
  registerSessionHandlers({ onAuthUpdated: () => {}, onSessionExpired });

  await expect(apiFetch('/api/v2/widgets')).rejects.toThrow(/401/);
  expect(onSessionExpired).toHaveBeenCalled();
});

test('/auth/refresh itself never triggers a nested refresh attempt on its own 401', async () => {
  let refreshCallCount = 0;
  server.use(
    rest.post('/api/v2/auth/refresh', (req, res, ctx) => {
      refreshCallCount += 1;
      return res(ctx.status(401), ctx.json({ message: 'No session' }));
    })
  );

  await expect(apiFetch('/api/v2/auth/refresh', { method: 'POST' })).rejects.toThrow(/401/);
  expect(refreshCallCount).toBe(1);
});

// --- Cold-start handling -------------------------------------------------------------------
//
// Render's free tier stops the backend after 15 minutes idle and takes ~50s+ to come back, which a
// flat 15s timeout reported as a plain "Request timed out" - indistinguishable, to a user, from a
// broken app. These cover both halves of the fix: the longer budget that gives a cold boot a real
// chance to land, and the message shown when even that runs out.

// A request that hangs until the abort signal fires, then rejects the way a real browser fetch
// does. Mocking fetch rather than serving an infinite delay through MSW is a concession to the
// test environment, not a shortcut: react-scripts 5 runs jsdom with the whatwg-fetch (XHR-backed)
// polyfill, and MSW's XHR interceptor does not propagate an AbortController abort through it, so
// an MSW-served hang never rejects at all and the test just times out. The branch under test is
// api.js's handling of an AbortError, and this produces a real one.
function hangUntilAborted() {
  jest.spyOn(global, 'fetch').mockImplementation(
    (url, opts) =>
      new Promise((resolve, reject) => {
        opts.signal.addEventListener('abort', () => {
          const abortError = new Error('The user aborted a request.');
          abortError.name = 'AbortError';
          reject(abortError);
        });
      })
  );
}

// The abort timer is the only observable difference between the cold and warm budgets, so read the
// delay straight off it rather than waiting out 75 real seconds. The spy still schedules through
// the *same* setTimeout it replaced, so api.js's clearTimeout in its finally block still cancels
// what it created - delegating to a different timer implementation would strand a live 75s handle
// and leave Jest unable to exit.
function captureAbortTimeoutMs() {
  const delays = [];
  const realSetTimeout = global.setTimeout;
  jest.spyOn(global, 'setTimeout').mockImplementation((fn, delay, ...rest) => {
    delays.push(delay);
    return realSetTimeout(fn, delay, ...rest);
  });
  return delays;
}

afterEach(() => {
  jest.restoreAllMocks();
});

test('the first request of a session gets the cold-start timeout budget, not the warm one', async () => {
  const delays = captureAbortTimeoutMs();
  server.use(rest.get('/api/v2/widgets', (req, res, ctx) => res(ctx.status(200), ctx.json({ ok: true }))));

  await apiFetch('/api/v2/widgets');

  expect(delays).toContain(75000);
  expect(delays).not.toContain(15000);
});

test('once the server has answered, later requests drop back to the warm timeout', async () => {
  server.use(rest.get('/api/v2/widgets', (req, res, ctx) => res(ctx.status(200), ctx.json({ ok: true }))));
  await apiFetch('/api/v2/widgets');

  const delays = captureAbortTimeoutMs();
  await apiFetch('/api/v2/widgets');

  expect(delays).toContain(15000);
  expect(delays).not.toContain(75000);
});

test('a timeout while the server may be asleep explains the cold start instead of blaming the app', async () => {
  hangUntilAborted();

  // Overriding the budget keeps the test fast; the branch under test is the message, which is
  // chosen from whether the server had been reached recently, not from the timeout's length.
  const error = await apiFetch('/api/v2/widgets', { timeoutMs: 20 }).catch((e) => e);

  expect(error.message).toMatch(/waking up the server/i);
  expect(error.message).toMatch(/free tier/i);
  expect(error.isColdStart).toBe(true);
});

test('a timeout against a server we just reached stays a plain timeout, not a cold-start excuse', async () => {
  server.use(rest.get('/api/v2/ping', (req, res, ctx) => res(ctx.status(200), ctx.json({ ok: true }))));
  await apiFetch('/api/v2/ping');

  hangUntilAborted();
  const error = await apiFetch('/api/v2/widgets', { timeoutMs: 20 }).catch((e) => e);

  expect(error.message).toBe('Request timed out. Please try again.');
  expect(error.isColdStart).toBe(false);
});

test('an error response still counts as the server being awake', async () => {
  server.use(rest.get('/api/v2/widgets', (req, res, ctx) => res(ctx.status(500), ctx.json({ message: 'boom' }))));
  await expect(apiFetch('/api/v2/widgets')).rejects.toThrow(/500/);

  // A 500 proves the container is up - it answered. Treating only 2xx as contact would have left
  // the app on the patient timeout while a genuinely broken warm server kept failing slowly.
  const delays = captureAbortTimeoutMs();
  server.use(rest.get('/api/v2/widgets', (req, res, ctx) => res(ctx.status(200), ctx.json({ ok: true }))));
  await apiFetch('/api/v2/widgets');

  expect(delays).toContain(15000);
});
