import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { rest } from 'msw';
import { server } from '../mocks/server';
import TransactionForm from './TransactionForm';

beforeEach(() => localStorage.clear());

test('submitting evaluates the transaction and reports the result, with no userId field', async () => {
  const onResult = jest.fn();
  const user = userEvent.setup();
  render(<TransactionForm onResult={onResult} />);

  expect(screen.queryByText(/user id/i)).not.toBeInTheDocument();

  await user.type(screen.getByLabelText(/Amount/i), '250');
  await user.type(screen.getByLabelText(/Category/i), 'groceries');
  await user.type(screen.getByLabelText(/Merchant/i), 'Store');
  await user.click(screen.getByRole('button', { name: /Evaluate Transaction/i }));

  expect(onResult).toHaveBeenCalledWith(expect.objectContaining({ loading: true }));
  await screen.findByRole('button', { name: /Evaluate Transaction/i });
  expect(onResult).toHaveBeenLastCalledWith(
    expect.objectContaining({ loading: false, error: '', result: expect.objectContaining({ decisionType: 'ALLOW' }) })
  );
});

test('a server error is shown and reported', async () => {
  server.use(
    rest.post('/api/v2/transactions/evaluate', (req, res, ctx) =>
      res(ctx.status(400), ctx.json({ message: 'amount must not be null' }))
    )
  );

  const onResult = jest.fn();
  const user = userEvent.setup();
  render(<TransactionForm onResult={onResult} />);

  await user.type(screen.getByLabelText(/Amount/i), '250');
  await user.type(screen.getByLabelText(/Category/i), 'groceries');
  await user.type(screen.getByLabelText(/Merchant/i), 'Store');
  await user.click(screen.getByRole('button', { name: /Evaluate Transaction/i }));

  expect(await screen.findByText(/amount must not be null/i)).toBeInTheDocument();
});
