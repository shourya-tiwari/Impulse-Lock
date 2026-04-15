import { useEffect, useMemo, useState } from 'react';
import { postJson } from '../api';

const STORAGE_KEY = 'impulselock:lastPreferences';

function parseRestrictedCategories(value) {
  if (!value) return [];
  return String(value)
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean);
}

export default function UserPreferencesForm({ onResult }) {
  const initial = useMemo(
    () => ({
      userId: '',
      dailyLimit: '',
      nightSpendingAllowed: false,
      sensitivityLevel: 5,
      restrictedCategoriesText: 'luxury,gaming',
    }),
    []
  );

  const [form, setForm] = useState(initial);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (!raw) return;
      const saved = JSON.parse(raw);
      if (saved && typeof saved === 'object') {
        setForm((prev) => ({ ...prev, ...saved }));
      }
    } catch {
      // ignore
    }
  }, []);

  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(form));
    } catch {
      // ignore
    }
  }, [form]);

  function updateField(key) {
    return (e) => setForm((p) => ({ ...p, [key]: e.target.value }));
  }

  function updateToggle(key) {
    return (e) => setForm((p) => ({ ...p, [key]: e.target.checked }));
  }

  async function onSubmit(e) {
    e.preventDefault();
    setError('');
    setLoading(true);
    onResult?.({ loading: true, error: '', result: null });

    try {
      const payload = {
        userId: String(form.userId).trim(),
        dailyLimit: Number(form.dailyLimit),
        nightSpendingAllowed: Boolean(form.nightSpendingAllowed),
        sensitivityLevel: Number(form.sensitivityLevel),
        restrictedCategories: parseRestrictedCategories(form.restrictedCategoriesText),
      };

      const data = await postJson('/users', payload);
      onResult?.({ loading: false, error: '', result: data });
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'Something went wrong.';
      setError(msg);
      onResult?.({ loading: false, error: msg, result: null });
    } finally {
      setLoading(false);
    }
  }

  return (
    <form className="Card" onSubmit={onSubmit}>
      <div className="CardTitle">User preferences</div>

      <label className="Field">
        <div className="FieldLabel">User ID</div>
        <input
          className="Input"
          value={form.userId}
          onChange={updateField('userId')}
          placeholder="e.g. U101"
          required
          autoComplete="off"
        />
      </label>

      <label className="Field">
        <div className="FieldLabel">Daily limit</div>
        <input
          className="Input"
          value={form.dailyLimit}
          onChange={updateField('dailyLimit')}
          placeholder="e.g. 2000"
          required
          inputMode="decimal"
          type="number"
          min="0"
          step="0.01"
        />
      </label>

      <label className="Field FieldRow">
        <div>
          <div className="FieldLabel">Night spending allowed</div>
          <div className="Hint">If off, spending 11PM–6AM increases risk.</div>
        </div>
        <input
          className="Toggle"
          type="checkbox"
          checked={form.nightSpendingAllowed}
          onChange={updateToggle('nightSpendingAllowed')}
          aria-label="Night spending allowed"
        />
      </label>

      <label className="Field">
        <div className="FieldLabel">Sensitivity level</div>
        <input
          className="Input"
          value={form.sensitivityLevel}
          onChange={updateField('sensitivityLevel')}
          type="range"
          min="1"
          max="10"
          step="1"
        />
        <div className="RangeValue">{Number(form.sensitivityLevel)}</div>
      </label>

      <label className="Field">
        <div className="FieldLabel">Restricted categories</div>
        <input
          className="Input"
          value={form.restrictedCategoriesText}
          onChange={updateField('restrictedCategoriesText')}
          placeholder="luxury,gaming"
          autoComplete="off"
        />
        <div className="Hint">Comma-separated (example: luxury,gaming).</div>
      </label>

      {error ? <div className="Alert">{error}</div> : null}

      <button className="Button" type="submit" disabled={loading}>
        {loading ? 'Saving…' : 'Save Preferences'}
      </button>
    </form>
  );
}

