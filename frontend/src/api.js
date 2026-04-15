function isProbablyCorsOrNetworkError(err) {
  const msg = String(err?.message || '').toLowerCase();
  return (
    msg.includes('failed to fetch') ||
    msg.includes('networkerror') ||
    msg.includes('load failed') ||
    msg.includes('cors')
  );
}

export function getApiBaseUrl() {
  // Prefer relative URLs when proxy is set in CRA (avoids CORS in dev).
  // If you set REACT_APP_API_BASE_URL, it will use absolute URLs.
  return (process.env.REACT_APP_API_BASE_URL || '').replace(/\/+$/, '');
}

export async function postJson(pathOrUrl, body, { timeoutMs = 15000 } = {}) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), timeoutMs);

  try {
    const url = pathOrUrl.startsWith('http')
      ? pathOrUrl
      : `${getApiBaseUrl()}${pathOrUrl}`;

    const res = await fetch(url, {
      method: 'POST',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(body ?? {}),
      signal: controller.signal,
    });

    const contentType = res.headers.get('content-type') || '';
    const isJson = contentType.includes('application/json');
    const data = isJson ? await res.json().catch(() => null) : null;
    const text = !isJson ? await res.text().catch(() => '') : '';

    if (!res.ok) {
      const serverMsg =
        (data && (data.message || data.error)) || text || 'Request failed.';
      throw new Error(`Request failed (${res.status}). ${serverMsg}`.trim());
    }

    return data;
  } catch (err) {
    if (err?.name === 'AbortError') {
      throw new Error('Request timed out. Please try again.');
    }
    if (isProbablyCorsOrNetworkError(err)) {
      throw new Error(
        [
          'Failed to reach the backend (network/CORS).',
          'Make sure Spring Boot is running on port 8080.',
          'If you are running React on 3000, ensure CORS is enabled in Spring OR use the CRA proxy.',
        ].join('\n')
      );
    }
    throw err instanceof Error ? err : new Error('Something went wrong.');
  } finally {
    clearTimeout(timeout);
  }
}

