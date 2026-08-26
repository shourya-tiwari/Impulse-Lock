import { useEffect, useState } from 'react';
import { getJson } from '../../api';

const PAGE_SIZE = 20;

const DEFAULT_FILTERS = { action: '', from: '', to: '' };

export default function AdminAuditLogs() {
  const [filters, setFilters] = useState(DEFAULT_FILTERS);
  // Only takes effect on "Apply filters" (see onApplyFilters) - kept separate from the live
  // `filters` input state so applying reliably re-fetches even when the page doesn't change.
  const [appliedFilters, setAppliedFilters] = useState(DEFAULT_FILTERS);
  const [page, setPage] = useState(0);
  const [state, setState] = useState({ loading: true, error: '', data: null });

  useEffect(() => {
    let cancelled = false;
    setState((s) => ({ ...s, loading: true, error: '' }));
    (async () => {
      try {
        const data = await getJson('/api/v2/admin/audit-logs', {
          action: appliedFilters.action.trim() || undefined,
          from: appliedFilters.from || undefined,
          to: appliedFilters.to || undefined,
          page,
          size: PAGE_SIZE,
        });
        if (!cancelled) setState({ loading: false, error: '', data });
      } catch (err) {
        if (!cancelled) {
          setState({
            loading: false,
            error: err instanceof Error ? err.message : 'Failed to load audit logs.',
            data: null,
          });
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [page, appliedFilters]);

  function onApplyFilters(e) {
    e.preventDefault();
    setAppliedFilters(filters);
    setPage(0);
  }

  return (
    <div>
      <form className="FilterGrid" onSubmit={onApplyFilters}>
        <label className="Field">
          <div className="FieldLabel">Action</div>
          <input
            className="Input"
            value={filters.action}
            onChange={(e) => setFilters((p) => ({ ...p, action: e.target.value }))}
            placeholder="e.g. LOGIN_SUCCESS"
            autoComplete="off"
          />
        </label>
        <label className="Field">
          <div className="FieldLabel">From</div>
          <input
            className="Input"
            type="datetime-local"
            value={filters.from}
            onChange={(e) => setFilters((p) => ({ ...p, from: e.target.value }))}
          />
        </label>
        <label className="Field">
          <div className="FieldLabel">To</div>
          <input
            className="Input"
            type="datetime-local"
            value={filters.to}
            onChange={(e) => setFilters((p) => ({ ...p, to: e.target.value }))}
          />
        </label>
        <div className="Field FieldActions">
          <button className="Button" type="submit">
            Apply filters
          </button>
        </div>
      </form>

      {state.loading ? (
        <div className="Inline">
          <div className="Spinner" aria-label="Loading" />
          <div className="Empty">Loading audit logs…</div>
        </div>
      ) : null}

      {!state.loading && state.error ? <div className="Alert">{state.error}</div> : null}

      {!state.loading && !state.error && state.data ? (
        state.data.content.length === 0 ? (
          <div className="Empty">No audit log entries match these filters.</div>
        ) : (
          <>
            <div className="TableWrap">
              <table className="Table">
                <thead>
                  <tr>
                    <th>When</th>
                    <th>Actor</th>
                    <th>Action</th>
                    <th>Entity</th>
                    <th>Correlation ID</th>
                  </tr>
                </thead>
                <tbody>
                  {state.data.content.map((entry) => (
                    <tr key={entry.id}>
                      <td>{new Date(entry.createdAt).toLocaleString()}</td>
                      <td>{entry.actorUsername}</td>
                      <td>{entry.action}</td>
                      <td>
                        {entry.entityType}
                        {entry.entityId ? ` #${entry.entityId}` : ''}
                      </td>
                      <td className="Mono">{entry.correlationId}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <div className="Pagination">
              <button
                type="button"
                className="Button Button--secondary"
                disabled={page <= 0}
                onClick={() => setPage((p) => p - 1)}
              >
                Previous
              </button>
              <div className="Hint">
                Page {state.data.page + 1} of {Math.max(1, state.data.totalPages)} ({state.data.totalElements} total)
              </div>
              <button
                type="button"
                className="Button Button--secondary"
                disabled={page + 1 >= state.data.totalPages}
                onClick={() => setPage((p) => p + 1)}
              >
                Next
              </button>
            </div>
          </>
        )
      ) : null}
    </div>
  );
}
