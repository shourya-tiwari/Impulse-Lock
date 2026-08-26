import { rest } from 'msw';

// Fake JWTs for tests - only the payload matters, decoded client-side purely for UI role gating
// (see AuthContext.decodeRoles); no signature verification happens on the client.
function fakeToken(roles) {
  const header = btoa(JSON.stringify({ alg: 'none' }));
  const payload = btoa(JSON.stringify({ sub: 'demo', roles }));
  return `${header}.${payload}.signature`;
}

export const USER_TOKEN = fakeToken(['ROLE_USER']);
export const ADMIN_TOKEN = fakeToken(['ROLE_USER', 'ROLE_ADMIN']);

export const DEMO_USER = {
  id: 1,
  username: 'demo',
  email: 'demo@example.com',
  dailyLimit: 2000,
  nightSpendingAllowed: false,
  sensitivityLevel: 5,
  enabled: true,
  restrictedCategories: ['luxury'],
  createdAt: '2026-01-01T00:00:00',
  updatedAt: '2026-01-01T00:00:00',
};

function authResponse(token, user = DEMO_USER) {
  return { accessToken: token, tokenType: 'Bearer', expiresInSeconds: 900, user };
}

export const handlers = [
  rest.post('/api/v2/auth/login', (req, res, ctx) => res(ctx.status(200), ctx.json(authResponse(USER_TOKEN)))),
  rest.post('/api/v2/auth/register', (req, res, ctx) => res(ctx.status(200), ctx.json(authResponse(USER_TOKEN)))),
  rest.post('/api/v2/auth/refresh', (req, res, ctx) => res(ctx.status(401), ctx.json({ message: 'No session' }))),
  rest.post('/api/v2/auth/logout', (req, res, ctx) => res(ctx.status(204))),

  rest.get('/api/v2/users/me', (req, res, ctx) => res(ctx.status(200), ctx.json(DEMO_USER))),
  rest.put('/api/v2/users/me/preferences', (req, res, ctx) => res(ctx.status(200), ctx.json(DEMO_USER))),
  rest.post('/api/v2/users/me/restricted-categories', (req, res, ctx) =>
    res(ctx.status(200), ctx.json([...DEMO_USER.restrictedCategories, 'gaming']))
  ),
  rest.delete('/api/v2/users/me/restricted-categories/:category', (req, res, ctx) => res(ctx.status(204))),

  rest.post('/api/v2/transactions/evaluate', (req, res, ctx) =>
    res(
      ctx.status(200),
      ctx.json({
        publicId: 'tx-1',
        amount: 100,
        category: 'groceries',
        merchant: 'Store',
        occurredAt: '2026-01-01T10:00:00',
        decisionType: 'ALLOW',
        riskScore: 10,
        explanation: 'Looks fine.',
        triggeredRules: [],
      })
    )
  ),
  rest.get('/api/v2/transactions/history', (req, res, ctx) =>
    res(ctx.status(200), ctx.json({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }))
  ),

  rest.get('/api/v2/dashboard/summary', (req, res, ctx) =>
    res(
      ctx.status(200),
      ctx.json({
        transactionCount: 5,
        totalSpend: 500,
        allowCount: 3,
        delayCount: 1,
        blockCount: 1,
        averageRiskScore: 25,
      })
    )
  ),
  rest.get('/api/v2/dashboard/spending-by-category', (req, res, ctx) =>
    res(ctx.status(200), ctx.json([{ category: 'groceries', totalAmount: 300, transactionCount: 3 }]))
  ),
  rest.get('/api/v2/dashboard/risk-trend', (req, res, ctx) =>
    res(ctx.status(200), ctx.json([{ date: '2026-01-01', transactionCount: 2, averageRiskScore: 20 }]))
  ),
  rest.get('/api/v2/dashboard/top-triggered-rules', (req, res, ctx) =>
    res(ctx.status(200), ctx.json([{ ruleCode: 'HIGH_AMOUNT', triggerCount: 4 }]))
  ),

  rest.get('/api/v2/admin/users', (req, res, ctx) =>
    res(ctx.status(200), ctx.json({ content: [DEMO_USER], page: 0, size: 20, totalElements: 1, totalPages: 1 }))
  ),
  rest.patch('/api/v2/admin/users/:id/status', (req, res, ctx) =>
    res(ctx.status(200), ctx.json({ ...DEMO_USER, enabled: false }))
  ),
  rest.get('/api/v2/admin/rule-configs', (req, res, ctx) =>
    res(
      ctx.status(200),
      ctx.json([
        {
          ruleCode: 'HIGH_AMOUNT',
          weight: 30,
          enabled: true,
          params: {},
          updatedAt: '2026-01-01T00:00:00',
        },
      ])
    )
  ),
  rest.put('/api/v2/admin/rule-configs/:ruleCode', (req, res, ctx) =>
    res(
      ctx.status(200),
      ctx.json({ ruleCode: 'HIGH_AMOUNT', weight: 40, enabled: true, params: {}, updatedAt: '2026-01-02T00:00:00' })
    )
  ),
  rest.get('/api/v2/admin/audit-logs', (req, res, ctx) =>
    res(ctx.status(200), ctx.json({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }))
  ),
];
