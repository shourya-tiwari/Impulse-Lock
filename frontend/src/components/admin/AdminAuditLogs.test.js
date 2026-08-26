import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { rest } from 'msw';
import { server } from '../../mocks/server';
import AdminAuditLogs from './AdminAuditLogs';

const ENTRY = {
  id: 1,
  actorUsername: 'demo',
  action: 'LOGIN_SUCCESS',
  entityType: 'USER',
  entityId: '1',
  metadata: {},
  ipAddress: '127.0.0.1',
  correlationId: 'abc-123',
  createdAt: '2026-01-01T09:00:00',
};

test('shows an empty state, then rows after filtering by action', async () => {
  render(<AdminAuditLogs />);
  expect(await screen.findByText(/No audit log entries match/i)).toBeInTheDocument();

  server.use(
    rest.get('/api/v2/admin/audit-logs', (req, res, ctx) => {
      const action = req.url.searchParams.get('action');
      const content = action === 'LOGIN_SUCCESS' ? [ENTRY] : [];
      return res(ctx.status(200), ctx.json({ content, page: 0, size: 20, totalElements: content.length, totalPages: 1 }));
    })
  );

  const user = userEvent.setup();
  await user.type(screen.getByLabelText(/Action/i), 'LOGIN_SUCCESS');
  await user.click(screen.getByRole('button', { name: /Apply filters/i }));

  expect(await screen.findByText('LOGIN_SUCCESS')).toBeInTheDocument();
  expect(screen.getByText('abc-123')).toBeInTheDocument();
});
