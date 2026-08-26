import { render, screen } from '@testing-library/react';
import App from './App';

test('shows the login form when there is no existing session', async () => {
  render(<App />);
  expect(await screen.findByRole('button', { name: /^Log in$/i })).toBeInTheDocument();
  expect(screen.getByLabelText(/Username/i)).toBeInTheDocument();
});
