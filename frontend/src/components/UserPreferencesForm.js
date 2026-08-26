import { useEffect, useState } from 'react';
import { deleteJson, getJson, postJson, putJson } from '../api';

// Rewritten for Phase 7: userId dropped (the caller is always resolved from the access token),
// and restricted categories moved off the old bulk-replace text field onto the granular
// /users/me/restricted-categories endpoints Phase 3 already built (see
// docs/v2/tasks.md Phase 4's cleanup note on UserPreferencesUpdateRequest).
export default function UserPreferencesForm({ onResult }) {
  const [form, setForm] = useState({
    dailyLimit: '',
    nightSpendingAllowed: false,
    sensitivityLevel: 5,
  });
  const [categories, setCategories] = useState([]);
  const [newCategory, setNewCategory] = useState('');
  const [loading, setLoading] = useState(false);
  const [loadingProfile, setLoadingProfile] = useState(true);
  const [error, setError] = useState('');
  const [categoryError, setCategoryError] = useState('');

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const profile = await getJson('/api/v2/users/me');
        if (cancelled) return;
        setForm({
          dailyLimit: String(profile.dailyLimit ?? ''),
          nightSpendingAllowed: Boolean(profile.nightSpendingAllowed),
          sensitivityLevel: profile.sensitivityLevel ?? 5,
        });
        setCategories(profile.restrictedCategories || []);
      } catch (err) {
        if (!cancelled) setError(err instanceof Error ? err.message : 'Failed to load preferences.');
      } finally {
        if (!cancelled) setLoadingProfile(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

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
        dailyLimit: Number(form.dailyLimit),
        nightSpendingAllowed: Boolean(form.nightSpendingAllowed),
        sensitivityLevel: Number(form.sensitivityLevel),
      };

      const data = await putJson('/api/v2/users/me/preferences', payload);
      onResult?.({ loading: false, error: '', result: data });
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'Something went wrong.';
      setError(msg);
      onResult?.({ loading: false, error: msg, result: null });
    } finally {
      setLoading(false);
    }
  }

  async function onAddCategory(e) {
    e.preventDefault();
    const category = newCategory.trim();
    if (!category) return;
    setCategoryError('');
    try {
      const updated = await postJson('/api/v2/users/me/restricted-categories', { category });
      setCategories(updated);
      setNewCategory('');
    } catch (err) {
      setCategoryError(err instanceof Error ? err.message : 'Failed to add category.');
    }
  }

  async function onRemoveCategory(category) {
    setCategoryError('');
    try {
      await deleteJson(`/api/v2/users/me/restricted-categories/${encodeURIComponent(category)}`);
      setCategories((prev) => prev.filter((c) => c !== category));
    } catch (err) {
      setCategoryError(err instanceof Error ? err.message : 'Failed to remove category.');
    }
  }

  return (
    <form className="Card" onSubmit={onSubmit}>
      <div className="CardTitle">User preferences</div>

      {loadingProfile ? (
        <div className="Inline">
          <div className="Spinner" aria-label="Loading" />
          <div className="Empty">Loading your preferences…</div>
        </div>
      ) : (
        <>
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

          {error ? <div className="Alert">{error}</div> : null}

          <button className="Button" type="submit" disabled={loading}>
            {loading ? 'Saving…' : 'Save Preferences'}
          </button>

          <div className="FieldLabel" style={{ marginTop: 16 }}>
            Restricted categories
          </div>
          <div className="TagList">
            {categories.length === 0 ? (
              <div className="Hint">None yet - add one below.</div>
            ) : (
              categories.map((category) => (
                <span className="Tag" key={category}>
                  {category}
                  <button
                    type="button"
                    className="TagRemove"
                    aria-label={`Remove ${category}`}
                    onClick={() => onRemoveCategory(category)}
                  >
                    ×
                  </button>
                </span>
              ))
            )}
          </div>
          <div className="FieldRow" style={{ marginTop: 8 }}>
            <input
              className="Input"
              value={newCategory}
              onChange={(e) => setNewCategory(e.target.value)}
              placeholder="e.g. luxury"
              autoComplete="off"
            />
            <button type="button" className="Button Button--secondary" onClick={onAddCategory}>
              Add
            </button>
          </div>
          {categoryError ? <div className="Alert">{categoryError}</div> : null}
        </>
      )}
    </form>
  );
}
