import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { rest } from 'msw';
import { server } from '../mocks/server';
import { AuthProvider, useAuth } from '../auth/AuthContext';
import RegisterForm from './RegisterForm';

function Probe() {
  const { isAuthenticated, user } = useAuth();
  return <div>{isAuthenticated ? `authenticated:${user.username}` : 'anonymous'}</div>;
}

function renderWithAuth() {
  return render(
    <AuthProvider>
      <RegisterForm onSwitchToLogin={() => {}} />
      <Probe />
    </AuthProvider>
  );
}

test('registering with valid details authenticates the session', async () => {
  const user = userEvent.setup();
  renderWithAuth();
  await screen.findByText('anonymous');

  await user.type(screen.getByLabelText(/Username/i), 'newuser');
  await user.type(screen.getByLabelText(/Email/i), 'newuser@example.com');
  await user.type(screen.getByLabelText(/Password/i), 'password123');
  await user.click(screen.getByRole('button', { name: /Create account/i }));

  expect(await screen.findByText('authenticated:demo')).toBeInTheDocument();
});

test('field-level validation errors from the server render next to their field', async () => {
  server.use(
    rest.post('/api/v2/auth/register', (req, res, ctx) =>
      res(
        ctx.status(400),
        ctx.json({
          message: 'Validation failed',
          fieldErrors: [{ field: 'email', message: 'must be a well-formed email address' }],
        })
      )
    )
  );

  const user = userEvent.setup();
  renderWithAuth();
  await screen.findByText('anonymous');

  await user.type(screen.getByLabelText(/Username/i), 'newuser');
  await user.type(screen.getByLabelText(/Email/i), 'not-an-email');
  await user.type(screen.getByLabelText(/Password/i), 'password123');
  await user.click(screen.getByRole('button', { name: /Create account/i }));

  expect(await screen.findByText(/must be a well-formed email address/i)).toBeInTheDocument();
});
