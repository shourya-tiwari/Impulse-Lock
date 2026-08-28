import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';

/**
 * A tiny stale-while-revalidate cache shared by the tab-level views.
 *
 * Every tab used to fetch from scratch on mount, and switching tabs unmounts the previous one -
 * so returning to a tab you had already loaded blanked the screen and showed a full-card spinner
 * while re-requesting data that was usually identical. This keeps the last successful payload per
 * cache key so the second visit onward renders instantly.
 *
 * It deliberately does NOT change data freshness: a fetch still fires on every mount, exactly as
 * before. The only difference is what the user sees while it is in flight - previously a spinner
 * over an empty card, now the previous data with a quiet `refreshing` flag. The fresh response
 * replaces it when it lands, so what you end up looking at is always as current as it was before.
 *
 * The cache lives in a ref rather than state: writing to it must not re-render every consumer,
 * only the hook that asked for that key.
 */
const DataCacheContext = createContext(null);

export function DataCacheProvider({ children }) {
  const cacheRef = useRef(new Map());

  const read = useCallback((key) => cacheRef.current.get(key), []);
  const write = useCallback((key, value) => {
    cacheRef.current.set(key, value);
  }, []);

  /**
   * Drops everything. Called on logout - without it the next user to sign in on the same tab
   * would see the previous user's dashboard and history for the moment before their own data
   * arrived, which is a data-leak-shaped bug rather than a cosmetic one.
   */
  const clear = useCallback(() => {
    cacheRef.current.clear();
  }, []);

  const value = useMemo(() => ({ read, write, clear }), [read, write, clear]);

  return <DataCacheContext.Provider value={value}>{children}</DataCacheContext.Provider>;
}

/**
 * Returns the shared cache when a {@link DataCacheProvider} is above this component, and a private
 * per-component one when there is not.
 *
 * The fallback is deliberate rather than a missing guard. Caching here is an optimization, not a
 * correctness requirement - a view with no cache behaves exactly as it did before this module
 * existed: one fetch on mount, spinner while it runs. Throwing instead would make every component
 * that reads data un-renderable in isolation, which would force each existing component test to
 * grow a provider wrapper purely to satisfy a performance concern the test is not about.
 *
 * The fallback is per-hook-instance, never module-level. A module-level fallback would be shared
 * across every test in a file and leak one test's fetched data into the next.
 */
export function useDataCache() {
  const context = useContext(DataCacheContext);
  const fallbackRef = useRef(null);
  if (fallbackRef.current === null) {
    const map = new Map();
    fallbackRef.current = {
      read: (key) => map.get(key),
      write: (key, value) => map.set(key, value),
      clear: () => map.clear(),
    };
  }
  return context ?? fallbackRef.current;
}

/**
 * Fetches `fetcher()` and caches the result under `cacheKey`.
 *
 * Returns `{ loading, refreshing, error, data, reload }`:
 * - `loading` is true only when there is nothing cached to show - i.e. the genuine first load.
 *   Render the full-card spinner on this.
 * - `refreshing` is true while a background revalidation is in flight over already-rendered data.
 *   Render something unobtrusive on this, never a screen-blanking spinner.
 * - `error` on a failed refresh is surfaced while the last good `data` stays on screen, so a
 *   transient network blip does not destroy a working view.
 *
 * `cacheKey` must include every input the request varies on (page, sort, filters), otherwise two
 * different queries would share one cache entry and show each other's results.
 */
export function useCachedResource(cacheKey, fetcher, deps = []) {
  const { read, write } = useDataCache();
  const cached = read(cacheKey);

  const [state, setState] = useState(() => ({
    loading: cached === undefined,
    refreshing: cached !== undefined,
    error: '',
    data: cached,
  }));

  // `fetcher` is typically an inline closure, so it is a new function identity on every render.
  // Holding it in a ref keeps it out of the effect's dependency array - depending on it directly
  // would re-run the fetch on every render and defeat the entire point of caching.
  const fetcherRef = useRef(fetcher);
  useEffect(() => {
    fetcherRef.current = fetcher;
  });

  const [reloadToken, setReloadToken] = useState(0);
  const reload = useCallback(() => setReloadToken((n) => n + 1), []);

  useEffect(() => {
    let cancelled = false;
    const existing = read(cacheKey);

    setState({
      loading: existing === undefined,
      refreshing: existing !== undefined,
      error: '',
      data: existing,
    });

    (async () => {
      try {
        const data = await fetcherRef.current();
        if (cancelled) return;
        write(cacheKey, data);
        setState({ loading: false, refreshing: false, error: '', data });
      } catch (err) {
        if (cancelled) return;
        const message = err instanceof Error ? err.message : 'Request failed.';
        // Keep whatever was already on screen. Only a first load with nothing cached ends up
        // showing a bare error, which is what it did before this cache existed.
        setState((prev) => ({
          loading: false,
          refreshing: false,
          error: message,
          data: prev.data,
        }));
      }
    })();

    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [cacheKey, reloadToken, ...deps]);

  return { ...state, reload };
}
