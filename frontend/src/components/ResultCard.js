import '../App.css';

function decisionTone(decisionType) {
  const t = String(decisionType || '').toUpperCase();
  if (t === 'ALLOW') return 'success';
  if (t === 'DELAY') return 'warn';
  if (t === 'BLOCK') return 'danger';
  return 'neutral';
}

export default function ResultCard({
  title = 'Result',
  loading,
  error,
  result,
  emptyHint,
}) {
  const tone = decisionTone(result?.decisionType);

  return (
    <div className="Card">
      <div className="CardTitle">{title}</div>

      {loading ? (
        <div className="Inline">
          <div className="Spinner" aria-label="Loading" />
          <div className="Empty">Waiting for API…</div>
        </div>
      ) : null}

      {!loading && error ? <div className="Alert">{error}</div> : null}

      {!loading && !error && !result ? (
        <div className="Empty">{emptyHint}</div>
      ) : null}

      {!loading && !error && result ? (
        <div className="Result">
          <div className="Pills">
            <div className={`Pill Pill--${tone}`}>
              <div className="PillLabel">Decision</div>
              <div className="PillValue">
                {String(result.decisionType ?? 'N/A')}
              </div>
            </div>
            <div className="Pill">
              <div className="PillLabel">Risk score</div>
              <div className="PillValue">
                {typeof result.riskScore === 'number'
                  ? result.riskScore.toFixed(2)
                  : String(result.riskScore ?? 'N/A')}
              </div>
            </div>
          </div>

          <div className="Explanation">
            <div className="ExplanationLabel">Explanation</div>
            <div className="ExplanationValue">
              {String(result.explanation ?? 'N/A')}
            </div>
          </div>
        </div>
      ) : null}
    </div>
  );
}

