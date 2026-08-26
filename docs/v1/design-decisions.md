# Design Decisions, Trade-offs, and Known Limitations (V1)

This document collects the deliberate design choices visible in the code, and the limitations/quirks that come with V1 being an early-stage/demo-grade implementation ("Phase 1," per a code comment in `FrequentTransactionRule`).

## Deliberate design choices

### Strategy pattern for rules, via Spring collection-injection
Rather than a big `if/else` chain or a switch on transaction type, each risk check is its own class implementing `SpendingRule`, registered as a Spring bean, and collected into a list by type. This is the strongest architectural decision in the codebase — it makes adding/removing/reordering rules a matter of touching `RuleEngineConfig` and one new class, with zero changes to `DecisionEngine` or `TransactionService`. See [rule-engine.md](./rule-engine.md#adding-a-new-rule).

### Thin controllers, fat-ish service, dumb repositories
Controllers do no logic beyond delegation. `TransactionService` owns orchestration (lookup → evaluate → persist) and validation. Repositories own SQL only. `UserController` skips the service layer entirely since an upsert has no orchestration — a reasonable exception to the pattern rather than an inconsistency.

### Centralized error handling
A single `@RestControllerAdvice` maps a small, explicit set of exception types to HTTP statuses and a uniform JSON error shape, keeping error-response formatting out of every controller. See [error-handling.md](./error-handling.md) for where this is incomplete (the `UserRepository` DB-failure gap).

### CRA proxy instead of a hardcoded frontend API URL
The frontend defaults to relative API paths and lets Create React App's dev-server `"proxy"` field forward to `localhost:8080`, only switching to an absolute URL when `REACT_APP_API_BASE_URL` is explicitly set. This avoids needing broad CORS configuration for local development, while `CorsConfig` still exists (locked to `localhost:3000`) for the case where the frontend is served separately from its own origin.

### All-or-nothing rule scoring
Every rule returns either `0` or its fixed weight — no partial/graduated scoring (e.g. `HighAmountRule` doesn't scale risk with *how much* over the limit a transaction is). This keeps the engine simple and its output easy to reason about/explain, at the cost of not distinguishing a transaction that's $1 over the limit from one that's $10,000 over.

## Known limitations / rough edges

These are not necessarily bugs to fix reflexively — they're documented here as the actual current behavior, useful context for anyone extending V1 or planning a V2.

1. **"Daily limit" isn't a daily total.** `HighAmountRule` compares a single transaction's amount to `dailyLimit` — there's no aggregation of a user's spending over a rolling day. A user can make unlimited transactions each just under the limit. See [rule-engine.md](./rule-engine.md#highamountrule).
2. **"Frequent transaction" detection doesn't check frequency.** `FrequentTransactionRule` is a flat amount threshold (`> 1000`), explicitly marked in-code as simplified Phase-1 logic. `TransactionRepository.getTransactionsByUserId` exists and could support real velocity checks, but nothing calls it from the evaluation path. See [rule-engine.md](./rule-engine.md#frequenttransactionrule).
3. **Risk score is uncapped and additive.** The `SpendingRule` interface's Javadoc claims a "0–100" range per rule, but `DecisionEngine` simply sums whatever fires with no ceiling — a transaction can score well above 100. See [rule-engine.md](./rule-engine.md#score-aggregation-caveat).
4. **`restricted_categories` column is required but undocumented in the README's DDL.** The upsert path will fail without it; only the read path degrades gracefully. See [database.md](./database.md#schema-code-mismatch-restricted_categories).
5. **No GET endpoint for transaction history**, despite `TransactionRepository.getTransactionsByUserId` being fully implemented. See [api-reference.md](./api-reference.md#endpoints-defined-in-code-but-not-exposed).
6. **Inconsistent DB-failure handling between the two POST endpoints.** `/transaction/evaluate` wraps DB failures in a descriptive `DatabaseOperationException`; `/users` does not, and falls through to a generic 500. See [error-handling.md](./error-handling.md#notable-gaps).
7. **No authentication/authorization anywhere.** `userId` is a client-supplied, unauthenticated string — any caller can evaluate transactions or read/write preferences for any `userId` with no ownership check. Acceptable for a local demo, not for anything internet-facing.
8. **No numeric bounds validation.** Negative amounts, negative daily limits, out-of-range sensitivity levels (frontend clamps 1–10 via a range slider, backend does not) are all accepted as-is.
9. **`main()` runs a hardcoded demo scenario on every application startup**, independent of the Spring-managed beans (it `new`s up its own `DecisionEngine` and rules rather than using DI), printing a sample decision to stdout. Harmless to the running API but is leftover early-development scaffolding rather than intentional production behavior. See [architecture.md](./architecture.md#notable-startup-behavior).
10. **No automated test coverage for business logic.** The only backend test is a context-load smoke test; the only frontend test is a single top-level render/smoke test. No rule, `DecisionEngine`, service, or repository has a dedicated unit test in this codebase.
11. **Tab-switch state bleed in the frontend.** `App.js` keeps one shared `view` object for both forms' results; switching tabs updates the title/hint immediately but doesn't clear a previous form's in-flight `result`/`error`, so a stale result can briefly appear under the new tab's title until the newly active form submits. See [frontend.md](./frontend.md#appjs--shell).
12. **Plaintext DB credentials committed to `application.properties`** (`root`/`password`) — fine for the documented local-MySQL dev workflow, but there's no externalized-config mechanism (env vars, secrets manager) in place for anything beyond local use.
