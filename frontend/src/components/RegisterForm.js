import { useState } from 'react';
import { useAuth } from '../auth/AuthContext';

export default function RegisterForm({ onSwitchToLogin }) {
  const { register } = useAuth();
  const [form, setForm] = useState({ username: '', email: '', password: '' });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState(null);

  function updateField(key) {
    return (e) => setForm((p) => ({ ...p, [key]: e.target.value }));
  }

  function fieldError(field) {
    return fieldErrors?.find((fe) => fe.field === field)?.message;
  }

  async function onSubmit(e) {
    e.preventDefault();
    setError('');
    setFieldErrors(null);
    setLoading(true);
    try {
      await register(form.username.trim(), form.email.trim(), form.password);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Something went wrong.');
      setFieldErrors(err?.fieldErrors || null);
    } finally {
      setLoading(false);
    }
  }

  return (
    <form className="Card" onSubmit={onSubmit}>
      <div className="CardTitle">Create an account</div>

      <label className="Field">
        <div className="FieldLabel">Username</div>
        <input
          className="Input"
          value={form.username}
          onChange={updateField('username')}
          required
          autoComplete="username"
        />
        {fieldError('username') ? <div className="FieldError">{fieldError('username')}</div> : null}
      </label>

      <label className="Field">
        <div className="FieldLabel">Email</div>
        <input
          className="Input"
          type="email"
          value={form.email}
          onChange={updateField('email')}
          required
          autoComplete="email"
        />
        {fieldError('email') ? <div className="FieldError">{fieldError('email')}</div> : null}
      </label>

      <label className="Field">
        <div className="FieldLabel">Password</div>
        <input
          className="Input"
          type="password"
          value={form.password}
          onChange={updateField('password')}
          required
          minLength={8}
          autoComplete="new-password"
        />
        <div className="Hint">At least 8 characters.</div>
        {fieldError('password') ? <div className="FieldError">{fieldError('password')}</div> : null}
      </label>

      {error ? <div className="Alert">{error}</div> : null}

      <button className="Button" type="submit" disabled={loading}>
        {loading ? 'Creating account…' : 'Create account'}
      </button>

      <button type="button" className="LinkButton" onClick={onSwitchToLogin}>
        Already have an account? Log in
      </button>
    </form>
  );
}
