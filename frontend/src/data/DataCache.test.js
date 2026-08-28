import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { useCallback, useState } from 'react';
import { DataCacheProvider, useCachedResource } from './DataCache';

/**
 * Covers the behaviour the cache exists for: a second visit to an already-loaded view must not
 * blank the screen, while still revalidating in the background so freshness is unchanged.
 */

function Consumer({ fetcher, cacheKey = 'k' }) {
  const { loading, refreshing, error, data } = useCachedResource(cacheKey, fetcher);
  return (
    <div>
      {loading ? <span>SPINNER</span> : null}
      {refreshing ? <span>REFRESHING</span> : null}
      {error ? <span>ERROR:{error}</span> : null}
      {data ? <span>DATA:{data}</span> : null}
    </div>
  );
}

/** Mounts and unmounts Consumer on demand, mimicking a tab switching away and back. */
function TabHost({ fetcher }) {
  const [mounted, setMounted] = useState(true);
  return (
    <div>
      <button type="button" onClick={() => setMounted((m) => !m)}>
        toggle
      </button>
      {mounted ? <Consumer fetcher={fetcher} /> : <span>HIDDEN</span>}
    </div>
  );
}

test('first load shows a spinner and no cached data', async () => {
  const fetcher = jest.fn().mockResolvedValue('one');

  render(
    <DataCacheProvider>
      <Consumer fetcher={fetcher} />
    </DataCacheProvider>
  );

  expect(screen.getByText('SPINNER')).toBeInTheDocument();
  expect(await screen.findByText('DATA:one')).toBeInTheDocument();
  expect(screen.queryByText('SPINNER')).not.toBeInTheDocument();
});

test('remounting renders cached data immediately instead of a spinner, and still refetches', async () => {
  const fetcher = jest.fn().mockResolvedValue('one');

  render(
    <DataCacheProvider>
      <TabHost fetcher={fetcher} />
    </DataCacheProvider>
  );

  expect(await screen.findByText('DATA:one')).toBeInTheDocument();
  expect(fetcher).toHaveBeenCalledTimes(1);

  // Switch away, then back - the remount is what used to blank the screen.
  fireEvent.click(screen.getByText('toggle'));
  expect(await screen.findByText('HIDDEN')).toBeInTheDocument();
  fireEvent.click(screen.getByText('toggle'));

  // The cached value is on screen synchronously; no spinner is ever rendered this time.
  expect(await screen.findByText('DATA:one')).toBeInTheDocument();
  expect(screen.queryByText('SPINNER')).not.toBeInTheDocument();

  // Freshness is unchanged: the fetch still fired on this mount, just invisibly.
  await waitFor(() => expect(fetcher).toHaveBeenCalledTimes(2));
});

test('a failed refresh keeps the last good data on screen alongside the error', async () => {
  const fetcher = jest
    .fn()
    .mockResolvedValueOnce('one')
    .mockRejectedValueOnce(new Error('network down'));

  render(
    <DataCacheProvider>
      <TabHost fetcher={fetcher} />
    </DataCacheProvider>
  );

  expect(await screen.findByText('DATA:one')).toBeInTheDocument();

  fireEvent.click(screen.getByText('toggle'));
  expect(await screen.findByText('HIDDEN')).toBeInTheDocument();
  fireEvent.click(screen.getByText('toggle'));

  expect(await screen.findByText('ERROR:network down')).toBeInTheDocument();
  // The point of the assertion: the view did not collapse to a bare error page.
  expect(screen.getByText('DATA:one')).toBeInTheDocument();
});

test('different cache keys do not share an entry', async () => {
  function TwoKeys() {
    const first = useCallback(() => Promise.resolve('alpha'), []);
    const second = useCallback(() => Promise.resolve('beta'), []);
    return (
      <>
        <Consumer fetcher={first} cacheKey="a" />
        <Consumer fetcher={second} cacheKey="b" />
      </>
    );
  }

  render(
    <DataCacheProvider>
      <TwoKeys />
    </DataCacheProvider>
  );

  expect(await screen.findByText('DATA:alpha')).toBeInTheDocument();
  expect(await screen.findByText('DATA:beta')).toBeInTheDocument();
});

test('works without a provider, falling back to no cross-mount caching', async () => {
  const fetcher = jest.fn().mockResolvedValue('one');

  render(<Consumer fetcher={fetcher} />);

  expect(screen.getByText('SPINNER')).toBeInTheDocument();
  expect(await screen.findByText('DATA:one')).toBeInTheDocument();
});
