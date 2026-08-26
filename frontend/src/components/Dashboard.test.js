import { render, screen } from '@testing-library/react';
import { rest } from 'msw';
import { server } from '../mocks/server';
import Dashboard from './Dashboard';

test('shows a loading state, then the aggregate numbers from the mocked API', async () => {
  render(<Dashboard />);
  expect(screen.getByText(/Loading dashboard/i)).toBeInTheDocument();

  expect(await screen.findByText('5')).toBeInTheDocument(); // transactionCount
  expect(screen.getByText('500.00')).toBeInTheDocument(); // totalSpend
  expect(screen.getByText('25.00')).toBeInTheDocument(); // averageRiskScore
  expect(screen.getByText('3 / 1 / 1')).toBeInTheDocument(); // allow/delay/block
  expect(screen.getByText('groceries')).toBeInTheDocument();
  expect(screen.getByText('HIGH_AMOUNT')).toBeInTheDocument();
});

test('shows empty states for categories and rules when there is no data', async () => {
  server.use(
    rest.get('/api/v2/dashboard/spending-by-category', (req, res, ctx) => res(ctx.status(200), ctx.json([]))),
    rest.get('/api/v2/dashboard/top-triggered-rules', (req, res, ctx) => res(ctx.status(200), ctx.json([])))
  );

  render(<Dashboard />);
  expect(await screen.findByText(/No transactions yet/i)).toBeInTheDocument();
  expect(screen.getByText(/No rules have fired yet/i)).toBeInTheDocument();
});

test('shows an error if any dashboard call fails', async () => {
  server.use(
    rest.get('/api/v2/dashboard/summary', (req, res, ctx) => res(ctx.status(500), ctx.json({ message: 'boom' })))
  );

  render(<Dashboard />);
  expect(await screen.findByText(/boom/i)).toBeInTheDocument();
});
