import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { rest } from 'msw';
import { server } from '../mocks/server';
import TransactionHistory from './TransactionHistory';

const ROW = {
  publicId: 'tx-1',
  amount: 199.5,
  category: 'groceries',
  merchant: 'Store',
  occurredAt: '2026-01-05T10:00:00',
  decisionType: 'ALLOW',
  riskScore: 12,
  explanation: 'fine',
  triggeredRules: [],
};

function pageOf(content, overrides = {}) {
  return { content, page: 0, size: 10, totalElements: content.length, totalPages: 1, ...overrides };
}

test('shows an empty state when there are no matching transactions', async () => {
  render(<TransactionHistory />);
  expect(await screen.findByText(/No transactions match these filters/i)).toBeInTheDocument();
});

test('renders rows and supports pagination and column sorting', async () => {
  server.use(
    rest.get('/api/v2/transactions/history', (req, res, ctx) => {
      const page = Number(req.url.searchParams.get('page') || '0');
      if (page === 0) {
        return res(ctx.status(200), ctx.json(pageOf([ROW], { totalElements: 2, totalPages: 2 })));
      }
      return res(
        ctx.status(200),
        ctx.json(pageOf([{ ...ROW, publicId: 'tx-2', category: 'gaming' }], { page: 1, totalElements: 2, totalPages: 2 }))
      );
    })
  );

  const user = userEvent.setup();
  render(<TransactionHistory />);

  expect(await screen.findByText('groceries')).toBeInTheDocument();
  expect(screen.getByText(/Page 1 of 2/i)).toBeInTheDocument();

  await user.click(screen.getByRole('button', { name: /^Next$/i }));
  expect(await screen.findByText('gaming')).toBeInTheDocument();
  expect(screen.getByText(/Page 2 of 2/i)).toBeInTheDocument();

  await user.click(screen.getByRole('columnheader', { name: /Amount/i }));
  expect(await screen.findByText(/Amount ↓/i)).toBeInTheDocument();
});

test('applying filters resets to page 0 and sends them as query params', async () => {
  let lastQuery = null;
  server.use(
    rest.get('/api/v2/transactions/history', (req, res, ctx) => {
      lastQuery = Object.fromEntries(req.url.searchParams.entries());
      return res(ctx.status(200), ctx.json(pageOf([])));
    })
  );

  const user = userEvent.setup();
  render(<TransactionHistory />);
  await screen.findByText(/No transactions match these filters/i);

  await user.type(screen.getByLabelText(/Category/i), 'luxury');
  await user.click(screen.getByRole('button', { name: /Apply filters/i }));

  expect(lastQuery.category).toBe('luxury');
  expect(lastQuery.page).toBe('0');
});

test('CSV export triggers a file download', async () => {
  const createObjectURL = jest.fn(() => 'blob:mock-url');
  const revokeObjectURL = jest.fn();
  global.URL.createObjectURL = createObjectURL;
  global.URL.revokeObjectURL = revokeObjectURL;

  server.use(
    rest.get('/api/v2/transactions/history/export', (req, res, ctx) =>
      res(ctx.status(200), ctx.set('Content-Type', 'text/csv'), ctx.body('publicId,amount\n'))
    )
  );

  const user = userEvent.setup();
  render(<TransactionHistory />);
  await screen.findByText(/No transactions match these filters/i);

  await user.click(screen.getByRole('button', { name: /Export CSV/i }));

  expect(await screen.findByRole('button', { name: /^Export CSV$/i })).toBeInTheDocument();
  expect(createObjectURL).toHaveBeenCalled();
  expect(revokeObjectURL).toHaveBeenCalledWith('blob:mock-url');
});
