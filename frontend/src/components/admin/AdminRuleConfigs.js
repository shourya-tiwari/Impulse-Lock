import { useEffect, useState } from 'react';
import { getJson, putJson } from '../../api';

function RuleConfigRow({ config, onSaved }) {
  const [weight, setWeight] = useState(String(config.weight));
  const [enabled, setEnabled] = useState(config.enabled);
  const [paramsText, setParamsText] = useState(JSON.stringify(config.params ?? {}, null, 2));
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  async function onSave() {
    setError('');
    let params;
    try {
      params = paramsText.trim() ? JSON.parse(paramsText) : {};
    } catch {
      setError('Params must be valid JSON.');
      return;
    }

    setSaving(true);
    try {
      const updated = await putJson(`/api/v2/admin/rule-configs/${config.ruleCode}`, {
        weight: Number(weight),
        enabled,
        params,
      });
      onSaved(updated);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to save rule config.');
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="Card" style={{ marginBottom: 12 }}>
      <div className="CardTitle">{config.ruleCode}</div>

      <label className="Field FieldRow">
        <div className="FieldLabel">Enabled</div>
        <input
          className="Toggle"
          type="checkbox"
          checked={enabled}
          onChange={(e) => setEnabled(e.target.checked)}
          aria-label={`${config.ruleCode} enabled`}
        />
      </label>

      <label className="Field">
        <div className="FieldLabel">Weight</div>
        <input
          className="Input"
          type="number"
          min="0"
          step="0.01"
          value={weight}
          onChange={(e) => setWeight(e.target.value)}
        />
      </label>

      <label className="Field">
        <div className="FieldLabel">Params (JSON)</div>
        <textarea
          className="Input Textarea"
          value={paramsText}
          onChange={(e) => setParamsText(e.target.value)}
          rows={4}
        />
      </label>

      {error ? <div className="Alert">{error}</div> : null}

      <button className="Button" type="button" onClick={onSave} disabled={saving}>
        {saving ? 'Saving…' : 'Save'}
      </button>
    </div>
  );
}

export default function AdminRuleConfigs() {
  const [state, setState] = useState({ loading: true, error: '', configs: [] });

  async function load() {
    setState((s) => ({ ...s, loading: true, error: '' }));
    try {
      const configs = await getJson('/api/v2/admin/rule-configs');
      setState({ loading: false, error: '', configs });
    } catch (err) {
      setState({ loading: false, error: err instanceof Error ? err.message : 'Failed to load rule configs.', configs: [] });
    }
  }

  useEffect(() => {
    load();
  }, []);

  function onSaved(updated) {
    setState((s) => ({
      ...s,
      configs: s.configs.map((c) => (c.ruleCode === updated.ruleCode ? updated : c)),
    }));
  }

  if (state.loading) {
    return (
      <div className="Inline">
        <div className="Spinner" aria-label="Loading" />
        <div className="Empty">Loading rule configs…</div>
      </div>
    );
  }

  if (state.error) {
    return <div className="Alert">{state.error}</div>;
  }

  return (
    <div>
      {state.configs.map((config) => (
        <RuleConfigRow key={config.ruleCode} config={config} onSaved={onSaved} />
      ))}
    </div>
  );
}
