import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { rest } from 'msw';
import { server } from './mocks/server';
import App from './App';

test('shows the login form when there is no existing session', async () => {
  render(<App />);
  expect(await screen.findByRole('button', { name: /^Log in$/i })).toBeInTheDocument();
  expect(screen.getByLabelText(/Username/i)).toBeInTheDocument();
});

test('switching tabs clears a previous form result instead of leaking it (docs/v1/design-decisions.md item 11)', async () => {
  server.use(
    rest.post('/api/v2/transactions/evaluate', (req, res, ctx) =>
      res(
        ctx.status(200),
        ctx.json({
          publicId: 'tx-1',
          amount: 5000,
          category: 'luxury',
          merchant: 'Store',
          occurredAt: '2026-01-01T10:00:00',
          decisionType: 'BLOCK',
          riskScore: 95,
          explanation: 'Exceeds daily limit.',
          triggeredRules: [],
        })
      )
    )
  );

  const user = userEvent.setup();
  render(<App />);

  await user.type(await screen.findByLabelText(/Username/i), 'demo');
  await user.type(screen.getByLabelText(/Password/i), 'password123');
  await user.click(screen.getByRole('button', { name: /^Log in$/i }));

  await user.click(await screen.findByRole('tab', { name: /Transaction/i }));
  await user.type(screen.getByLabelText(/Amount/i), '5000');
  await user.type(screen.getByLabelText(/Category/i), 'luxury');
  await user.type(screen.getByLabelText(/Merchant/i), 'Store');
  await user.click(screen.getByRole('button', { name: /Evaluate Transaction/i }));

  expect(await screen.findByText('BLOCK')).toBeInTheDocument();

  await user.click(screen.getByRole('tab', { name: /Preferences/i }));

  expect(screen.queryByText('BLOCK')).not.toBeInTheDocument();
  expect(screen.queryByText('Exceeds daily limit.')).not.toBeInTheDocument();
  expect(screen.getByText('Save preferences to store your behavior settings.')).toBeInTheDocument();
});
