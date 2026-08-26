import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { rest } from 'msw';
import { server } from '../../mocks/server';
import AdminRuleConfigs from './AdminRuleConfigs';

test('lists rule configs and saves an edited weight', async () => {
  const user = userEvent.setup();
  render(<AdminRuleConfigs />);

  expect(await screen.findByText('HIGH_AMOUNT')).toBeInTheDocument();

  const weightInput = screen.getByLabelText(/Weight/i);
  await user.clear(weightInput);
  await user.type(weightInput, '40');
  await user.click(screen.getByRole('button', { name: /^Save$/i }));

  expect(await screen.findByDisplayValue('40')).toBeInTheDocument();
});

test('an invalid params JSON blocks the save with a local error', async () => {
  const user = userEvent.setup();
  render(<AdminRuleConfigs />);
  await screen.findByText('HIGH_AMOUNT');

  const paramsInput = screen.getByLabelText(/Params/i);
  await user.clear(paramsInput);
  await user.type(paramsInput, '{{not valid json');
  await user.click(screen.getByRole('button', { name: /^Save$/i }));

  expect(await screen.findByText(/must be valid JSON/i)).toBeInTheDocument();
});

test('shows an error if rule configs fail to load', async () => {
  server.use(
    rest.get('/api/v2/admin/rule-configs', (req, res, ctx) => res(ctx.status(500), ctx.json({ message: 'boom' })))
  );
  render(<AdminRuleConfigs />);
  expect(await screen.findByText(/boom/i)).toBeInTheDocument();
});
