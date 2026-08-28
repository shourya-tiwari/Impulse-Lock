import { useCallback } from 'react';
import { getJson } from '../api';
import { useCachedResource } from '../data/DataCache';

function BarList({ items, labelKey, valueKey, countKey, formatValue }) {
  const max = Math.max(1, ...items.map((i) => Number(i[valueKey]) || 0));
  return (
    <div className="BarList">
      {items.map((item) => (
        <div className="BarRow" key={item[labelKey]}>
          <div className="BarLabel">{item[labelKey]}</div>
          <div className="BarTrack">
            <div
              className="BarFill"
              style={{ width: `${(Number(item[valueKey]) / max) * 100}%` }}
            />
          </div>
          <div className="BarValue">
            {formatValue ? formatValue(item[valueKey]) : item[valueKey]}
            {countKey ? ` (${item[countKey]})` : ''}
          </div>
        </div>
      ))}
    </div>
  );
}

function RiskTrendChart({ points }) {
  const width = 320;
  const height = 120;
  const padding = 8;

  if (points.length === 0) return <div className="Empty">No data yet.</div>;

  const maxScore = Math.max(10, ...points.map((p) => p.averageRiskScore));
  const step = points.length > 1 ? (width - padding * 2) / (points.length - 1) : 0;

  const coords = points.map((p, i) => {
    const x = padding + i * step;
    const y = height - padding - (p.averageRiskScore / maxScore) * (height - padding * 2);
    return [x, y];
  });

  const polyline = coords.map(([x, y]) => `${x},${y}`).join(' ');

  return (
    <svg
      className="RiskTrendSvg"
      viewBox={`0 0 ${width} ${height}`}
      role="img"
      aria-label="Average risk score over time"
    >
      <polyline points={polyline} fill="none" stroke="#6366f1" strokeWidth="2" />
      {coords.map(([x, y], idx) => (
        <circle key={points[idx].date} cx={x} cy={y} r="2.5" fill="#22c55e" />
      ))}
    </svg>
  );
}

export default function Dashboard() {
  const fetchDashboard = useCallback(async () => {
    const [summary, byCategory, riskTrend, topRules] = await Promise.all([
      getJson('/api/v2/dashboard/summary'),
      getJson('/api/v2/dashboard/spending-by-category'),
      getJson('/api/v2/dashboard/risk-trend'),
      getJson('/api/v2/dashboard/top-triggered-rules'),
    ]);
    return { summary, byCategory, riskTrend, topRules };
  }, []);

  const { loading, refreshing, error, data } = useCachedResource('dashboard', fetchDashboard);

  // Only the very first load blanks the card. Every later visit to this tab renders the cached
  // dashboard immediately and revalidates behind it (see data/DataCache.js).
  if (loading) {
    return (
      <div className="Card">
        <div className="CardTitle">Dashboard</div>
        <div className="Inline">
          <div className="Spinner" aria-label="Loading" />
          <div className="Empty">Loading dashboard…</div>
        </div>
      </div>
    );
  }

  // Only reachable when the first load itself failed - a failed refresh keeps the last good data
  // on screen and reports itself through the inline notice below instead.
  if (error && !data) {
    return (
      <div className="Card">
        <div className="CardTitle">Dashboard</div>
        <div className="Alert">{error}</div>
      </div>
    );
  }

  const { summary, byCategory, riskTrend, topRules } = data;

  return (
    <div className="DashboardGrid">
      {error ? <div className="Alert">Could not refresh: {error} Showing last loaded data.</div> : null}
      <div className="Card">
        <div className="CardTitle">
          Summary (last 30 days)
          {refreshing ? <span className="RefreshHint" aria-live="polite"> · refreshing…</span> : null}
        </div>
        <div className="StatGrid">
          <div className="Stat">
            <div className="StatLabel">Transactions</div>
            <div className="StatValue">{summary.transactionCount}</div>
          </div>
          <div className="Stat">
            <div className="StatLabel">Total spend</div>
            <div className="StatValue">{Number(summary.totalSpend).toFixed(2)}</div>
          </div>
          <div className="Stat">
            <div className="StatLabel">Avg risk score</div>
            <div className="StatValue">{Number(summary.averageRiskScore).toFixed(2)}</div>
          </div>
          <div className="Stat">
            <div className="StatLabel">Allow / Delay / Block</div>
            <div className="StatValue StatValue--small">
              {summary.allowCount} / {summary.delayCount} / {summary.blockCount}
            </div>
          </div>
        </div>
      </div>

      <div className="Card">
        <div className="CardTitle">Spending by category</div>
        {byCategory.length === 0 ? (
          <div className="Empty">No transactions yet.</div>
        ) : (
          <BarList
            items={byCategory}
            labelKey="category"
            valueKey="totalAmount"
            countKey="transactionCount"
            formatValue={(v) => Number(v).toFixed(2)}
          />
        )}
      </div>

      <div className="Card">
        <div className="CardTitle">Risk trend</div>
        <RiskTrendChart points={riskTrend} />
      </div>

      <div className="Card">
        <div className="CardTitle">Top triggered rules</div>
        {topRules.length === 0 ? (
          <div className="Empty">No rules have fired yet.</div>
        ) : (
          <BarList items={topRules} labelKey="ruleCode" valueKey="triggerCount" />
        )}
      </div>
    </div>
  );
}
