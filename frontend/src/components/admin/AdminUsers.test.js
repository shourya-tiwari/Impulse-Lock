import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { rest } from 'msw';
import { server } from '../../mocks/server';
import AdminUsers from './AdminUsers';

test('lists users and toggles a user\'s enabled status', async () => {
  let enabled = true;
  server.use(
    rest.get('/api/v2/admin/users', (req, res, ctx) =>
      res(
        ctx.status(200),
        ctx.json({
          content: [{ id: 1, username: 'demo', email: 'demo@example.com', enabled }],
          page: 0,
          size: 20,
          totalElements: 1,
          totalPages: 1,
        })
      )
    ),
    rest.patch('/api/v2/admin/users/:id/status', (req, res, ctx) => {
      enabled = false;
      return res(ctx.status(200), ctx.json({ id: 1, username: 'demo', email: 'demo@example.com', enabled }));
    })
  );

  const user = userEvent.setup();
  render(<AdminUsers />);

  expect(await screen.findByText('demo')).toBeInTheDocument();
  expect(screen.getByText('Enabled')).toBeInTheDocument();

  await user.click(screen.getByRole('button', { name: /^Disable$/i }));
  expect(await screen.findByText('Disabled')).toBeInTheDocument();
});

test('shows an error if the user list fails to load', async () => {
  server.use(
    rest.get('/api/v2/admin/users', (req, res, ctx) => res(ctx.status(500), ctx.json({ message: 'boom' })))
  );
  render(<AdminUsers />);
  expect(await screen.findByText(/boom/i)).toBeInTheDocument();
});
