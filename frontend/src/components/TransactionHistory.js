import { useCallback, useState } from 'react';
import { downloadFile, getJson } from '../api';
import { useCachedResource } from '../data/DataCache';

const DEFAULT_FILTERS = {
  from: '',
  to: '',
  category: '',
  merchant: '',
  decisionType: '',
  minAmount: '',
  maxAmount: '',
};

const PAGE_SIZE = 10;

function toIsoOrUndefined(localDateTimeValue) {
  // <input type="datetime-local"> gives "2026-01-01T10:00" - already valid ISO-8601 for the
  // backend's @DateTimeFormat(iso = DATE_TIME) parameter.
  return localDateTimeValue || undefined;
}

export default function TransactionHistory() {
  const [filters, setFilters] = useState(DEFAULT_FILTERS);
  // Filters only take effect on "Apply filters" (see onApplyFilters) - kept separate from the
  // live `filters` input state so the effect below doesn't depend on every keystroke, and so
  // applying a filter reliably re-fetches even when the page number doesn't change.
  const [appliedFilters, setAppliedFilters] = useState(DEFAULT_FILTERS);
  const [page, setPage] = useState(0);
  const [sort, setSort] = useState({ field: 'occurredAt', direction: 'DESC' });
  const [exporting, setExporting] = useState(false);
  const [exportError, setExportError] = useState('');

  const buildQuery = useCallback(() => {
    return {
      from: toIsoOrUndefined(appliedFilters.from),
      to: toIsoOrUndefined(appliedFilters.to),
      category: appliedFilters.category.trim() || undefined,
      merchant: appliedFilters.merchant.trim() || undefined,
      decisionType: appliedFilters.decisionType || undefined,
      minAmount: appliedFilters.minAmount || undefined,
      maxAmount: appliedFilters.maxAmount || undefined,
    };
  }, [appliedFilters]);

  const sortParam = `${sort.field},${sort.direction}`;

  // The key has to carry every input the request varies on. Caching only on "history" would serve
  // page 1 of an unfiltered list as though it were page 3 of a filtered one.
  const cacheKey = `history:${JSON.stringify(appliedFilters)}:${page}:${sortParam}`;

  const fetchHistory = useCallback(
    () =>
      getJson('/api/v2/transactions/history', {
        ...buildQuery(),
        page,
        size: PAGE_SIZE,
        sort: sortParam,
      }),
    [buildQuery, page, sortParam]
  );

  const { loading, refreshing, error, data } = useCachedResource(cacheKey, fetchHistory);

  function updateFilter(key) {
    return (e) => setFilters((p) => ({ ...p, [key]: e.target.value }));
  }

  function onApplyFilters(e) {
    e.preventDefault();
    setAppliedFilters(filters);
    setPage(0);
  }

  function toggleSort(field) {
    setSort((prev) =>
      prev.field === field
        ? { field, direction: prev.direction === 'DESC' ? 'ASC' : 'DESC' }
        : { field, direction: 'DESC' }
    );
  }

  async function onExport() {
    setExportError('');
    setExporting(true);
    try {
      await downloadFile('/api/v2/transactions/history/export', buildQuery(), 'transaction-history.csv');
    } catch (err) {
      setExportError(err instanceof Error ? err.message : 'Export failed.');
    } finally {
      setExporting(false);
    }
  }

  function sortIndicator(field) {
    if (sort.field !== field) return '';
    return sort.direction === 'DESC' ? ' ↓' : ' ↑';
  }

  return (
    <div className="Card">
      <div className="CardTitle">Transaction history</div>

      <form className="FilterGrid" onSubmit={onApplyFilters}>
        <label className="Field">
          <div className="FieldLabel">From</div>
          <input className="Input" type="datetime-local" value={filters.from} onChange={updateFilter('from')} />
        </label>
        <label className="Field">
          <div className="FieldLabel">To</div>
          <input className="Input" type="datetime-local" value={filters.to} onChange={updateFilter('to')} />
        </label>
        <label className="Field">
          <div className="FieldLabel">Category</div>
          <input className="Input" value={filters.category} onChange={updateFilter('category')} autoComplete="off" />
        </label>
        <label className="Field">
          <div className="FieldLabel">Merchant</div>
          <input className="Input" value={filters.merchant} onChange={updateFilter('merchant')} autoComplete="off" />
        </label>
        <label className="Field">
          <div className="FieldLabel">Decision</div>
          <select className="Input" value={filters.decisionType} onChange={updateFilter('decisionType')}>
            <option value="">Any</option>
            <option value="ALLOW">ALLOW</option>
            <option value="DELAY">DELAY</option>
            <option value="BLOCK">BLOCK</option>
          </select>
        </label>
        <label className="Field">
          <div className="FieldLabel">Min amount</div>
          <input
            className="Input"
            type="number"
            min="0"
            step="0.01"
            value={filters.minAmount}
            onChange={updateFilter('minAmount')}
          />
        </label>
        <label className="Field">
          <div className="FieldLabel">Max amount</div>
          <input
            className="Input"
            type="number"
            min="0"
            step="0.01"
            value={filters.maxAmount}
            onChange={updateFilter('maxAmount')}
          />
        </label>
        <div className="Field FieldActions">
          <button className="Button" type="submit">
            Apply filters
          </button>
        </div>
      </form>

      <div className="FieldRow" style={{ marginBottom: 8 }}>
        <button type="button" className="Button Button--secondary" onClick={onExport} disabled={exporting}>
          {exporting ? 'Exporting…' : 'Export CSV'}
        </button>
      </div>
      {exportError ? <div className="Alert">{exportError}</div> : null}

      {/* Only a genuine first load (nothing cached) blanks the list. Changing page/sort/filters
          re-renders the previously loaded page until the new one arrives, rather than flashing an
          empty card - see data/DataCache.js. */}
      {loading ? (
        <div className="Inline">
          <div className="Spinner" aria-label="Loading" />
          <div className="Empty">Loading transactions…</div>
        </div>
      ) : null}

      {refreshing && data ? <div className="Hint" aria-live="polite">Refreshing…</div> : null}

      {error && !data ? <div className="Alert">{error}</div> : null}
      {error && data ? (
        <div className="Alert">Could not refresh: {error} Showing last loaded results.</div>
      ) : null}

      {!loading && data ? (
        data.content.length === 0 ? (
          <div className="Empty">No transactions match these filters.</div>
        ) : (
          <>
            <div className="TableWrap">
              <table className="Table">
                <thead>
                  <tr>
                    <th onClick={() => toggleSort('occurredAt')} className="Sortable">
                      Occurred at{sortIndicator('occurredAt')}
                    </th>
                    <th onClick={() => toggleSort('amount')} className="Sortable">
                      Amount{sortIndicator('amount')}
                    </th>
                    <th>Category</th>
                    <th>Merchant</th>
                    <th>Decision</th>
                    <th onClick={() => toggleSort('riskScore')} className="Sortable">
                      Risk score{sortIndicator('riskScore')}
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {data.content.map((tx) => (
                    <tr key={tx.publicId}>
                      <td>{new Date(tx.occurredAt).toLocaleString()}</td>
                      <td>{Number(tx.amount).toFixed(2)}</td>
                      <td>{tx.category}</td>
                      <td>{tx.merchant}</td>
                      <td>
                        <span className={`Badge Badge--${String(tx.decisionType).toLowerCase()}`}>
                          {tx.decisionType}
                        </span>
                      </td>
                      <td>{Number(tx.riskScore).toFixed(2)}</td>
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
                Page {data.page + 1} of {Math.max(1, data.totalPages)} ({data.totalElements} total)
              </div>
              <button
                type="button"
                className="Button Button--secondary"
                disabled={page + 1 >= data.totalPages}
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
