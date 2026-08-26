import './App.css';
import { useEffect, useMemo, useState } from 'react';
import { AuthProvider, useAuth } from './auth/AuthContext';
import ResultCard from './components/ResultCard';
import TransactionForm from './components/TransactionForm';
import UserPreferencesForm from './components/UserPreferencesForm';
import Dashboard from './components/Dashboard';
import TransactionHistory from './components/TransactionHistory';
import LoginForm from './components/LoginForm';
import RegisterForm from './components/RegisterForm';
import AdminPanel from './components/admin/AdminPanel';

const TABS = [
  { key: 'dashboard', label: 'Dashboard' },
  { key: 'transaction', label: 'Transaction' },
  { key: 'history', label: 'History' },
  { key: 'preferences', label: 'Preferences' },
];

const VIEW_DEFAULTS = {
  transaction: {
    title: 'Evaluation result',
    emptyHint: 'Evaluate a transaction to see decision type, risk score, and explanation.',
  },
  preferences: {
    title: 'Saved preferences',
    emptyHint: 'Save preferences to store your behavior settings.',
  },
};

function UnauthenticatedShell() {
  const [mode, setMode] = useState('login'); // 'login' | 'register'

  return (
    <div className="Shell">
      <div className="Header">
        <div>
          <div className="Title">ImpulseLock</div>
          <div className="Subtitle">Fintech behavior + transaction risk</div>
        </div>
      </div>
      <div className="AuthGrid">
        {mode === 'login' ? (
          <LoginForm onSwitchToRegister={() => setMode('register')} />
        ) : (
          <RegisterForm onSwitchToLogin={() => setMode('login')} />
        )}
      </div>
    </div>
  );
}

function AuthenticatedShell() {
  const { user, isAdmin, logout } = useAuth();
  const apiHint = useMemo(() => {
    const base = process.env.REACT_APP_API_BASE_URL;
    if (base) return base;
    return 'Using CRA proxy → http://localhost:8080';
  }, []);

  const [active, setActive] = useState('dashboard');
  const [view, setView] = useState({ loading: false, error: '', result: null, ...VIEW_DEFAULTS.transaction });

  const tabs = isAdmin ? [...TABS, { key: 'admin', label: 'Admin' }] : TABS;

  // Fixes docs/v1/design-decisions.md item 11 ("tab-switch state bleed"): switching tabs must
  // clear the previous form's result/error immediately, not just update the title - otherwise a
  // stale transaction result could briefly appear under the Preferences tab (or vice versa)
  // until the newly active form is submitted.
  function switchTab(key) {
    setActive(key);
    if (VIEW_DEFAULTS[key]) {
      setView({ loading: false, error: '', result: null, ...VIEW_DEFAULTS[key] });
    }
  }

  return (
    <div className="Shell">
      <div className="Header">
        <div>
          <div className="Title">ImpulseLock</div>
          <div className="Subtitle">Fintech behavior + transaction risk</div>

          <div className="Tabs" role="tablist" aria-label="Sections">
            {tabs.map((tab) => (
              <button
                key={tab.key}
                type="button"
                role="tab"
                aria-selected={active === tab.key}
                className={`Tab ${active === tab.key ? 'Tab--active' : ''}`}
                onClick={() => switchTab(tab.key)}
              >
                {tab.label}
              </button>
            ))}
          </div>
        </div>
        <div className="Meta">
          <div className="MetaLabel">Signed in as</div>
          <div className="MetaValue" title={user?.username}>
            {user?.username}
          </div>
          <button type="button" className="LinkButton" onClick={logout}>
            Log out
          </button>
          <div className="MetaLabel" style={{ marginTop: 10 }}>
            API
          </div>
          <div className="MetaValue" title={apiHint}>
            {apiHint}
          </div>
        </div>
      </div>

      {active === 'dashboard' ? <Dashboard /> : null}
      {active === 'history' ? <TransactionHistory /> : null}
      {active === 'admin' && isAdmin ? <AdminPanel /> : null}

      {active === 'transaction' || active === 'preferences' ? (
        <div className="Grid">
          <div>
            {active === 'preferences' ? (
              <UserPreferencesForm
                onResult={(next) => setView({ ...VIEW_DEFAULTS.preferences, ...next })}
              />
            ) : (
              <TransactionForm
                onResult={(next) => setView({ ...VIEW_DEFAULTS.transaction, ...next })}
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
      ) : null}
    </div>
  );
}

function AppShell() {
  const { isInitializing, isAuthenticated } = useAuth();

  if (isInitializing) {
    return (
      <div className="Shell">
        <div className="Inline">
          <div className="Spinner" aria-label="Loading" />
          <div className="Empty">Loading…</div>
        </div>
      </div>
    );
  }

  return isAuthenticated ? <AuthenticatedShell /> : <UnauthenticatedShell />;
}

function App() {
  useEffect(() => {
    document.title = 'ImpulseLock';
  }, []);

  return (
    <div className="App">
      <AuthProvider>
        <AppShell />
      </AuthProvider>
    </div>
  );
}

export default App;
