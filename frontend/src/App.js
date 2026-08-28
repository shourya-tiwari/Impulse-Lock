import './App.css';
import { useEffect, useMemo, useRef, useState } from 'react';
import { AuthProvider, useAuth } from './auth/AuthContext';
import { DataCacheProvider, useDataCache } from './data/DataCache';
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
    // With no absolute base URL the client issues relative /api/v2/... requests, which something
    // in front of it proxies to the backend: CRA's dev-server "proxy" field under `npm start`,
    // frontend/nginx.conf under Docker Compose, and frontend/vercel.json's rewrite on Vercel. In
    // all three the browser is talking to its own origin, so the old "→ http://localhost:8080"
    // text was wrong everywhere except local development - and actively misleading in production,
    // where it implied the deployed app was calling a machine-local backend.
    return process.env.NODE_ENV === 'development'
      ? 'Using same-origin API (CRA proxy → localhost:8080)'
      : 'Using same-origin API';
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
  const { isInitializing, isAuthenticated, user } = useAuth();
  const { clear } = useDataCache();

  // Drop every cached payload when the signed-in identity changes - logging out, or a different
  // account signing in on the same tab without a page reload. Without this the next user would
  // briefly see the previous user's dashboard and history before their own data arrived.
  const previousUserId = useRef(user?.id ?? null);
  useEffect(() => {
    const currentUserId = user?.id ?? null;
    if (previousUserId.current !== currentUserId) {
      clear();
      previousUserId.current = currentUserId;
    }
  }, [user, clear]);

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
        <DataCacheProvider>
          <AppShell />
        </DataCacheProvider>
      </AuthProvider>
    </div>
  );
}

export default App;
