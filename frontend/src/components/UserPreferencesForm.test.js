import { render, screen, waitForElementToBeRemoved } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { rest } from 'msw';
import { server } from '../mocks/server';
import UserPreferencesForm from './UserPreferencesForm';

test('loads the current profile and lets the caller save preferences (no userId field)', async () => {
  const onResult = jest.fn();
  render(<UserPreferencesForm onResult={onResult} />);

  expect(await screen.findByDisplayValue('2000')).toBeInTheDocument();
  expect(screen.queryByText(/user id/i)).not.toBeInTheDocument();
  expect(screen.getByText('luxury')).toBeInTheDocument();

  const user = userEvent.setup();
  await user.clear(screen.getByLabelText(/Daily limit/i));
  await user.type(screen.getByLabelText(/Daily limit/i), '3000');
  await user.click(screen.getByRole('button', { name: /Save Preferences/i }));

  expect(onResult).toHaveBeenCalledWith(expect.objectContaining({ loading: true }));
  await screen.findByRole('button', { name: /Save Preferences/i });
  expect(onResult).toHaveBeenLastCalledWith(expect.objectContaining({ loading: false, error: '' }));
});

test('adding and removing a restricted category calls the granular endpoints', async () => {
  const user = userEvent.setup();
  render(<UserPreferencesForm />);
  await screen.findByText('luxury');

  await user.type(screen.getByPlaceholderText('e.g. luxury'), 'gaming');
  await user.click(screen.getByRole('button', { name: /^Add$/i }));
  expect(await screen.findByText('gaming')).toBeInTheDocument();

  await user.click(screen.getByRole('button', { name: /Remove luxury/i }));
  // The tag is only dropped once the DELETE resolves, so wait for the removal
  // rather than asserting synchronously against a still-pending request.
  await waitForElementToBeRemoved(() => screen.queryByText('luxury'));
});

test('a profile load failure is shown', async () => {
  server.use(
    rest.get('/api/v2/users/me', (req, res, ctx) => res(ctx.status(500), ctx.json({ message: 'boom' })))
  );
  render(<UserPreferencesForm />);
  expect(await screen.findByText(/boom/i)).toBeInTheDocument();
});
