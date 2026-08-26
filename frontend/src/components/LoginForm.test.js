import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { rest } from 'msw';
import { server } from '../mocks/server';
import { AuthProvider, useAuth } from '../auth/AuthContext';
import LoginForm from './LoginForm';

function Probe() {
  const { isAuthenticated, user } = useAuth();
  return <div data-testid="probe">{isAuthenticated ? `authenticated:${user.username}` : 'anonymous'}</div>;
}

function renderWithAuth() {
  return render(
    <AuthProvider>
      <LoginForm onSwitchToRegister={() => {}} />
      <Probe />
    </AuthProvider>
  );
}

test('logging in with valid credentials authenticates the session', async () => {
  const user = userEvent.setup();
  renderWithAuth();

  await screen.findByText('anonymous');

  await user.type(screen.getByLabelText(/Username/i), 'demo');
  await user.type(screen.getByLabelText(/Password/i), 'password123');
  await user.click(screen.getByRole('button', { name: /^Log in$/i }));

  expect(await screen.findByText('authenticated:demo')).toBeInTheDocument();
});

test('a failed login shows the server error message', async () => {
  server.use(
    rest.post('/api/v2/auth/login', (req, res, ctx) =>
      res(ctx.status(401), ctx.json({ message: 'Invalid username or password' }))
    )
  );

  const user = userEvent.setup();
  renderWithAuth();
  await screen.findByText('anonymous');

  await user.type(screen.getByLabelText(/Username/i), 'demo');
  await user.type(screen.getByLabelText(/Password/i), 'wrong');
  await user.click(screen.getByRole('button', { name: /^Log in$/i }));

  expect(await screen.findByText(/Invalid username or password/i)).toBeInTheDocument();
  expect(screen.getByText('anonymous')).toBeInTheDocument();
});
