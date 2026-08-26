# Frontend

Create React App (`react-scripts` 5.0.1), React 19. No router (single view with tab-based switching, no URL routing), no external state management library (component-local `useState`/`useMemo` only), no CSS framework (hand-written `App.css`).

## Structure

```
frontend/src/
├── App.js                       # top-level shell: tab switcher (Preferences / Transaction) + result panel
├── App.css                      # all styling (Card/Field/Pill/etc. class names, dark fintech theme)
├── api.js                       # fetch wrapper: base URL resolution, timeout/abort, error normalization
├── index.js                     # ReactDOM root render
├── components/
│   ├── TransactionForm.js       # POST /transaction/evaluate
│   ├── UserPreferencesForm.js   # POST /users
│   └── ResultCard.js            # renders loading / error / empty / decision result states
```

## `api.js` — HTTP client

Exports:
- `getApiBaseUrl()` — returns `process.env.REACT_APP_API_BASE_URL` (trailing slashes stripped) or `''`. When empty, `postJson` builds a relative URL, which CRA's dev-server proxy (`"proxy": "http://localhost:8080"` in `package.json`) forwards to the backend — this is the default dev setup and is why `CorsConfig` allowing only `localhost:3000` still works: the browser only ever talks to `localhost:3000`, and CRA's own dev server proxies server-to-server.
- `postJson(pathOrUrl, body, { timeoutMs = 15000 })` — wraps `fetch` with:
  - An `AbortController` timeout (default 15s), producing a friendly "Request timed out. Please try again." error.
  - Content-type-aware response parsing (JSON if `content-type` includes `application/json`, otherwise falls back to raw text).
  - On non-`ok` responses, throws an `Error` combining the HTTP status and the server's `message`/`error` field (matching the backend's `ErrorResponse` shape — see [error-handling.md](./error-handling.md)) or raw text if the body wasn't JSON.
  - A heuristic `isProbablyCorsOrNetworkError` (matches `"failed to fetch"`, `"networkerror"`, `"load failed"`, `"cors"` in the error message) that rewrites low-level fetch failures into an actionable multi-line message telling the user to check that the backend is running and CORS/proxy is configured correctly.

This is the only place HTTP logic lives — components call `postJson` directly with a relative path (`/transaction/evaluate`, `/users`) and never construct URLs themselves.

## `App.js` — shell

- Holds `active` state (`'preferences' | 'transaction'`, defaulting to `'transaction'`) controlling which form renders on the left.
- Holds a single `view` state object (`{ loading, error, result, title, emptyHint }`) shared by **both** forms and the `ResultCard` on the right — switching tabs resets `title`/`emptyHint` to match the newly active form, but note `result`/`error`/`loading` are not explicitly cleared on tab switch (only reset when a form's `onResult` callback next fires), so switching tabs can briefly show the previous tab's stale result under the new tab's title until a new submission happens.
- `apiHint` (`useMemo`) displays either the configured `REACT_APP_API_BASE_URL` or a static string `"Using CRA proxy → http://localhost:8080"` in the header, purely informational.
- Sets `document.title = 'ImpulseLock'` once on mount.
- Both `TransactionForm` and `UserPreferencesForm` receive an `onResult` callback prop through which they push `{ loading, error, result }` updates up into `App`'s shared `view` state, which `ResultCard` then renders.

## `TransactionForm.js`

- Local form state: `userId`, `amount`, `category`, `merchant` (all strings in state, even `amount` — coerced to `Number(...)` only at submit time).
- **Persists form input to `localStorage`** under key `impulselock:lastTransactionForm` on every change (via a `useEffect` keyed on `form`), and rehydrates from it on mount — so a user's last-entered transaction values survive a page reload. Both the read and write are wrapped in empty `try/catch` (silently ignored) to tolerate `localStorage` being unavailable (private browsing, quota, etc.).
- On submit: builds the payload (trimming strings, `Number(...)` on amount), calls `postJson('/transaction/evaluate', payload)`, and reports `{ loading, error, result }` both to local state (for the inline `Alert`) and up via `onResult`.
- All four fields are HTML-`required`; `amount` uses `type="number"` with `min="0"` `step="0.01"` for basic browser-level input constraints (not a substitute for server-side validation, of which there is very little — see [api-reference.md](./api-reference.md)).

## `UserPreferencesForm.js`

- Local form state includes a `restrictedCategoriesText` string (comma-separated raw input, e.g. `"luxury,gaming"`) rather than an array — `parseRestrictedCategories(value)` splits on `,`, trims, and filters blanks only at submit time to build the actual `restrictedCategories` array sent to the API.
- Default initial state: `sensitivityLevel: 5`, `restrictedCategoriesText: 'luxury,gaming'`, `nightSpendingAllowed: false`.
- Same `localStorage` persistence pattern as `TransactionForm`, under key `impulselock:lastPreferences`.
- `sensitivityLevel` is a `<input type="range" min="1" max="10">` — the frontend enforces the 1–10 range via the browser widget; the backend does not re-validate this range (see [api-reference.md](./api-reference.md)).
- On submit: posts to `/users`, same loading/error/result reporting pattern as `TransactionForm`.

## `ResultCard.js`

Presentational only — no state, no side effects. Renders one of four mutually exclusive states based on props (`loading`, `error`, `result`, `emptyHint`):
1. **Loading** — spinner + "Waiting for API…".
2. **Error** — red `Alert` box with the error message (already human-formatted by `api.js`/the form's catch block).
3. **Empty** — the `emptyHint` placeholder text (form-specific, passed from `App.js`).
4. **Result** — two "pill" badges (`Decision`, color-coded via `decisionTone`: `ALLOW`→success/green, `DELAY`→warn/yellow, `BLOCK`→danger/red, anything else→neutral; and `Risk score`, formatted to 2 decimal places if numeric) plus an `Explanation` block rendering the raw `explanation` string from the backend as-is (including its trailing `"; "` from the join in `DecisionEngine`, and no line-breaking between individual rule explanations).

## Styling

`App.css` (289 lines) implements a dark "fintech" visual theme (per the README's "UI Features" section) using plain CSS class names (`Card`, `Field`, `Pill`, `Tab`, `Alert`, etc.) referenced directly by class name across components — no CSS Modules, no styled-components, no Tailwind.

## Tests

`App.test.js` contains a single smoke test: renders `<App />` and asserts the header text, "Transaction evaluation" (the default active form's title), and "Evaluation result" (the default `ResultCard` title) are present. No component-specific test coverage exists for `TransactionForm`, `UserPreferencesForm`, `ResultCard`, or `api.js` in isolation, and no test covers the preferences tab or an actual API call/mock.
