import { useEffect, useState } from 'react';
import { getJson, patchJson } from '../../api';

const PAGE_SIZE = 20;

export default function AdminUsers() {
  const [page, setPage] = useState(0);
  const [state, setState] = useState({ loading: true, error: '', data: null });
  const [pendingId, setPendingId] = useState(null);
  const [actionError, setActionError] = useState('');

  async function load() {
    setState((s) => ({ ...s, loading: true, error: '' }));
    try {
      const data = await getJson('/api/v2/admin/users', { page, size: PAGE_SIZE });
      setState({ loading: false, error: '', data });
    } catch (err) {
      setState({ loading: false, error: err instanceof Error ? err.message : 'Failed to load users.', data: null });
    }
  }

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page]);

  async function onToggleEnabled(user) {
    setActionError('');
    setPendingId(user.id);
    try {
      await patchJson(`/api/v2/admin/users/${user.id}/status`, { enabled: !user.enabled });
      await load();
    } catch (err) {
      setActionError(err instanceof Error ? err.message : 'Failed to update user.');
    } finally {
      setPendingId(null);
    }
  }

  if (state.loading) {
    return (
      <div className="Inline">
        <div className="Spinner" aria-label="Loading" />
        <div className="Empty">Loading users…</div>
      </div>
    );
  }

  if (state.error) {
    return <div className="Alert">{state.error}</div>;
  }

  return (
    <div>
      {actionError ? <div className="Alert">{actionError}</div> : null}
      <div className="TableWrap">
        <table className="Table">
          <thead>
            <tr>
              <th>ID</th>
              <th>Username</th>
              <th>Email</th>
              <th>Status</th>
              <th>Action</th>
            </tr>
          </thead>
          <tbody>
            {state.data.content.map((user) => (
              <tr key={user.id}>
                <td>{user.id}</td>
                <td>{user.username}</td>
                <td>{user.email}</td>
                <td>
                  <span className={`Badge ${user.enabled ? 'Badge--allow' : 'Badge--block'}`}>
                    {user.enabled ? 'Enabled' : 'Disabled'}
                  </span>
                </td>
                <td>
                  <button
                    type="button"
                    className="Button Button--secondary"
                    disabled={pendingId === user.id}
                    onClick={() => onToggleEnabled(user)}
                  >
                    {user.enabled ? 'Disable' : 'Enable'}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="Pagination">
        <button type="button" className="Button Button--secondary" disabled={page <= 0} onClick={() => setPage((p) => p - 1)}>
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
    </div>
  );
}
