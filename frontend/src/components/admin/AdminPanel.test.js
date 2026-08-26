import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import AdminPanel from './AdminPanel';

test('defaults to the Users tab and switches sections on click', async () => {
  const user = userEvent.setup();
  render(<AdminPanel />);

  expect(await screen.findByText('demo')).toBeInTheDocument();

  await user.click(screen.getByRole('tab', { name: /Rule configs/i }));
  expect(await screen.findByText('HIGH_AMOUNT')).toBeInTheDocument();

  await user.click(screen.getByRole('tab', { name: /Audit logs/i }));
  expect(await screen.findByText(/No audit log entries match/i)).toBeInTheDocument();
});
