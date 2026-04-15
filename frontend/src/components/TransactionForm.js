import { useEffect, useState } from 'react';
import { postJson } from '../api';

const STORAGE_KEY = 'impulselock:lastTransactionForm';

export default function TransactionForm({ onResult }) {
  const [form, setForm] = useState({
    userId: '',
    amount: '',
    category: '',
    merchant: '',
  });

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

  async function onSubmit(e) {
    e.preventDefault();
    setError('');
    setLoading(true);
    onResult?.({ loading: true, error: '', result: null });

    try {
      const payload = {
        userId: String(form.userId).trim(),
        amount: Number(form.amount),
        category: String(form.category).trim(),
        merchant: String(form.merchant).trim(),
      };

      const data = await postJson('/transaction/evaluate', payload);
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
      <div className="CardTitle">Transaction evaluation</div>

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
        <div className="FieldLabel">Amount</div>
        <input
          className="Input"
          value={form.amount}
          onChange={updateField('amount')}
          placeholder="e.g. 499.99"
          required
          inputMode="decimal"
          type="number"
          min="0"
          step="0.01"
        />
      </label>

      <label className="Field">
        <div className="FieldLabel">Category</div>
        <input
          className="Input"
          value={form.category}
          onChange={updateField('category')}
          placeholder="e.g. luxury / groceries / gaming"
          required
          autoComplete="off"
        />
      </label>

      <label className="Field">
        <div className="FieldLabel">Merchant</div>
        <input
          className="Input"
          value={form.merchant}
          onChange={updateField('merchant')}
          placeholder="e.g. Amazon"
          required
          autoComplete="off"
        />
      </label>

      {error ? <div className="Alert">{error}</div> : null}

      <button className="Button" type="submit" disabled={loading}>
        {loading ? 'Evaluating…' : 'Evaluate Transaction'}
      </button>
    </form>
  );
}

