import './App.css';
import { useEffect, useMemo, useState } from 'react';
import ResultCard from './components/ResultCard';
import TransactionForm from './components/TransactionForm';
import UserPreferencesForm from './components/UserPreferencesForm';

function App() {
  const apiHint = useMemo(() => {
    const base = process.env.REACT_APP_API_BASE_URL;
    if (base) return base;
    return 'Using CRA proxy → http://localhost:8080';
  }, []);

  const [active, setActive] = useState('transaction'); // 'preferences' | 'transaction'
  const [view, setView] = useState({
    loading: false,
    error: '',
    result: null,
    title: 'Evaluation result',
    emptyHint:
      'Evaluate a transaction to see decision type, risk score, and explanation.',
  });

  useEffect(() => {
    document.title = 'ImpulseLock';
  }, []);

  return (
    <div className="App">
      <div className="Shell">
        <div className="Header">
          <div>
            <div className="Title">ImpulseLock</div>
            <div className="Subtitle">Fintech behavior + transaction risk</div>

            <div className="Tabs" role="tablist" aria-label="Sections">
              <button
                type="button"
                className={`Tab ${active === 'preferences' ? 'Tab--active' : ''}`}
                onClick={() => {
                  setActive('preferences');
                  setView((v) => ({
                    ...v,
                    title: 'Saved preferences',
                    emptyHint:
                      'Save preferences to store your behavior settings for a userId.',
                  }));
                }}
              >
                Preferences
              </button>
              <button
                type="button"
                className={`Tab ${active === 'transaction' ? 'Tab--active' : ''}`}
                onClick={() => {
                  setActive('transaction');
                  setView((v) => ({
                    ...v,
                    title: 'Evaluation result',
                    emptyHint:
                      'Evaluate a transaction to see decision type, risk score, and explanation.',
                  }));
                }}
              >
                Transaction
              </button>
            </div>
          </div>
          <div className="Meta">
            <div className="MetaLabel">API</div>
            <div className="MetaValue" title={apiHint}>
              {apiHint}
            </div>
          </div>
        </div>

        <div className="Grid">
          <div>
            {active === 'preferences' ? (
              <UserPreferencesForm
                onResult={(next) =>
                  setView({
                    title: 'Saved preferences',
                    emptyHint:
                      'Save preferences to store your behavior settings for a userId.',
                    ...next,
                  })
                }
              />
            ) : (
              <TransactionForm
                onResult={(next) =>
                  setView({
                    title: 'Evaluation result',
                    emptyHint:
                      'Evaluate a transaction to see decision type, risk score, and explanation.',
                    ...next,
                  })
                }
              />
            )}
          </div>

          <ResultCard
            title={view.title}
            loading={view.loading}
            error={view.error}
            result={view.result}
            emptyHint={view.emptyHint}
          />
        </div>
      </div>
    </div>
  );
}

export default App;
