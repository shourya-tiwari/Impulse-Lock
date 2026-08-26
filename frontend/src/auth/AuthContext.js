import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import * as api from '../api';

const AuthContext = createContext(null);

// Decoded purely for UI gating (which tabs to show) - the actual authorization boundary is
// enforced server-side via hasRole("ADMIN") on /api/v2/admin/** (see SecurityConfig). No
// signature verification happens here; that would be meaningless on the client anyway.
function decodeRoles(accessToken) {
  try {
    const [, payload] = accessToken.split('.');
    const json = atob(payload.replace(/-/g, '+').replace(/_/g, '/'));
    const claims = JSON.parse(json);
    return Array.isArray(claims.roles) ? claims.roles : [];
  } catch {
    return [];
  }
}

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [roles, setRoles] = useState([]);
  const [status, setStatus] = useState('initializing'); // 'initializing' | 'authenticated' | 'anonymous'

  const applySession = useCallback((data) => {
    setUser(data.user);
    setRoles(decodeRoles(data.accessToken));
    setStatus('authenticated');
  }, []);

  const clearSession = useCallback(() => {
    setUser(null);
    setRoles([]);
    setStatus('anonymous');
  }, []);

  useEffect(() => {
    api.registerSessionHandlers({
      onAuthUpdated: applySession,
      onSessionExpired: clearSession,
    });
  }, [applySession, clearSession]);

  useEffect(() => {
    let cancelled = false;
    // Access tokens live only in memory (see docs/v2/security-design.md) - a page reload always
    // needs to re-establish the session from the httpOnly refresh-token cookie, if any.
    api.refreshSession().then((data) => {
      if (cancelled) return;
      if (data) applySession(data);
      else clearSession();
    });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const login = useCallback(async (username, password) => {
    await api.login(username, password);
  }, []);

  const registerAccount = useCallback(async (username, email, password) => {
    await api.register(username, email, password);
  }, []);

  const logout = useCallback(async () => {
    await api.logout();
  }, []);

  const hasRole = useCallback((role) => roles.includes(role), [roles]);

  const value = useMemo(
    () => ({
      user,
      roles,
      status,
      isAuthenticated: status === 'authenticated',
      isInitializing: status === 'initializing',
      isAdmin: roles.includes('ROLE_ADMIN'),
      hasRole,
      login,
      register: registerAccount,
      logout,
    }),
    [user, roles, status, hasRole, login, registerAccount, logout]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within an AuthProvider');
  return ctx;
}
