import { useState } from 'react';
import { useAuth } from '../auth/AuthContext';

export default function LoginForm({ onSwitchToRegister }) {
  const { login } = useAuth();
  const [form, setForm] = useState({ username: '', password: '' });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  function updateField(key) {
    return (e) => setForm((p) => ({ ...p, [key]: e.target.value }));
  }

  async function onSubmit(e) {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      await login(form.username.trim(), form.password);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Something went wrong.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <form className="Card" onSubmit={onSubmit}>
      <div className="CardTitle">Log in</div>

      <label className="Field">
        <div className="FieldLabel">Username</div>
        <input
          className="Input"
          value={form.username}
          onChange={updateField('username')}
          required
          autoComplete="username"
        />
      </label>

      <label className="Field">
        <div className="FieldLabel">Password</div>
        <input
          className="Input"
          type="password"
          value={form.password}
          onChange={updateField('password')}
          required
          autoComplete="current-password"
        />
      </label>

      {error ? <div className="Alert">{error}</div> : null}

      <button className="Button" type="submit" disabled={loading}>
        {loading ? 'Logging in…' : 'Log in'}
      </button>

      <button type="button" className="LinkButton" onClick={onSwitchToRegister}>
        Need an account? Register
      </button>
    </form>
  );
}
