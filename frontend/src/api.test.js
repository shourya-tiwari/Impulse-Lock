import { rest } from 'msw';
import { server } from './mocks/server';
import { apiFetch, getAccessToken, registerSessionHandlers, setAccessToken } from './api';

beforeEach(() => {
  setAccessToken(null);
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
