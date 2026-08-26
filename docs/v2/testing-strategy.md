# ImpulseLock V2 — Testing Strategy

V1's entire automated test suite was one empty Spring context-load test on the backend and one render-smoke test on the frontend (see [v1/backend.md](../v1/backend.md#testing) and [v1/frontend.md](../v1/frontend.md#tests)) — no rule, no service, no repository, no controller, no security path had any coverage. V2 treats "comprehensive testing" as a first-class goal, not an afterthought after features are built.

## Test pyramid

```
                     ▲
                    /  \        E2E / manual exploratory (a handful, high-value flows)
                   /────\
                  / API  \      Controller + security integration tests (MockMvc)
                 /────────\
                / Service  \    Service-layer tests (Mockito-mocked repositories)
               /────────────\
              /  Repository  \  JPA repository tests (Testcontainers MySQL)
             /────────────────\
            /   Unit (rules,   \ Rule engine, JWT util, mappers, validators — pure unit tests
           /   engine, utils)   \
          /──────────────────────\
```

Most tests live at the bottom (fast, isolated, no Spring context) and thin out toward the top (slower, fuller integration). This mirrors standard test-pyramid guidance and keeps the suite fast enough to run on every commit in CI.

## Backend

### Unit tests (no Spring context, fastest tier)
- **Every `SpendingRule` implementation**: table-driven tests covering the fire/no-fire boundary exactly (e.g. `HighAmountRule` at amount == limit, amount == limit + 0.01; `NightSpendingRule` at hour 22:59/23:00/05:59/06:00). This is the highest-value net-new coverage, since V1 shipped five rules with zero tests behind any of them.
- **`DecisionEngine`**: given a hand-built list of fake `SpendingRule`s (some firing, some not), assert `riskScore` sums correctly, is capped at 100 (see [database-design.md](./database-design.md#transactions)), and `decisionType` thresholds resolve correctly at and around the boundary values now sourced from `rule_configs` (see [database-design.md](./database-design.md#rule_configs)).
- **`JwtService`**: issues a token, parses it back, asserts claims round-trip; asserts an expired token fails validation; asserts a tampered-signature token fails validation.
- **Mappers** (MapStruct-generated entity↔DTO): assert field mapping is correct and that the password hash never appears on any `User`→DTO mapping path (a targeted regression test guarding the exact mistake that must never happen).
- **Custom validators** (e.g. sensitivity-level bound): valid/invalid boundary cases.

### Service-layer tests (Mockito-mocked repositories, no DB, no Spring context)
- `TransactionService.evaluateAndSave`: user-not-found → `UserNotFoundException`; DB failure on save → wrapped exception; verifies the audit-log call happens (mocked `AuditLogService`, verifying interaction not implementation); verifies ownership resolution comes from the passed-in authenticated user, never a request field.
- `UserService`: preference update persists correctly; restricted-category add/remove; duplicate-username registration surfaces as the correct typed exception (feeding the 409 mapping in [api-design.md](./api-design.md#error-format)).
- `RuleConfigService` / admin services: weight/threshold update validation (e.g. rejecting a negative weight).
- `AuditLogService`: an audit-write failure (simulated) must **not** propagate and fail the calling business operation — this is a specific, deliberately-authored test asserting the isolation guarantee described in [architecture.md](./architecture.md#audit-logging).

### Repository tests (Testcontainers — real MySQL, real schema via Flyway)
- Spin up a MySQL container per test class (or a shared container across the suite, reused for speed) via Testcontainers, run the actual Flyway migrations against it (so the tests validate the *real* schema, not an in-memory H2 approximation with different SQL dialect quirks — a real gap if V1 had used H2 for tests, given MySQL-specific things like the `` `timestamp` `` reserved-word escaping it needed).
- Cover: unique constraints (`username`, `email`, `(user_id, category)` on restricted categories) actually reject duplicates at the DB level; cascade deletes (deleting a user cascades to their restricted categories and refresh tokens, but transactions/audit_log are preserved per the retention stance in [database-design.md](./database-design.md#audit_log)); the JPA Specification-based transaction-history filters (date range, category, decision type, amount range, pagination, sorting) against seeded fixture data with known expected result sets.

### Controller / integration tests (`@SpringBootTest` + `MockMvc`, full security filter chain active)
- Full request-response cycle including the real `JwtAuthenticationFilter`, `@PreAuthorize`, and `GlobalExceptionHandler` — not `@WebMvcTest` with security mocked out, since the auth/authorization behavior *is* the thing being tested.
- Per endpoint: happy path; missing/expired/tampered token → 401; wrong role → 403; another user's resource → 404 (per the access-control note in [api-design.md](./api-design.md#error-format)); validation failure → 400 with correct `fieldErrors`.
- A dedicated auth-flow integration test: register → login → call a protected endpoint with the returned access token → let the access token expire (or force-expire in test) → refresh → retry — exercising the entire token lifecycle described in [security-design.md](./security-design.md#token-lifecycle) end-to-end.
- Admin-only endpoints: assert a `ROLE_USER` token is rejected (403) and a `ROLE_ADMIN` token succeeds.

### Coverage target
JaCoCo wired into the Maven build, with a CI-enforced minimum line/branch coverage on `service/`, `engine/`, `rules/`, and `security/` packages (a concrete number, e.g. 80%, is set once the codebase exists — this document fixes the *mechanism* and *scope*, not a number pulled out of thin air before any code is written). Coverage is a floor to catch untested logic, not a target to game with trivial tests — reviewed alongside the actual test list in code review, not merged on the percentage alone.

## Frontend

- **Component tests** (React Testing Library): `TransactionForm`, `UserPreferencesForm`, `ResultCard`, plus new components (login/register forms, dashboard charts, transaction history table with filters/pagination) — rendering, user interaction (typing, submitting), and conditional-state rendering (loading/error/empty/result, per [v1/frontend.md](../v1/frontend.md#resultcardjs)'s existing pattern).
- **API-mocking**: MSW (Mock Service Worker) intercepts `fetch` calls in tests, replacing the current total absence of any mocked-API test (V1's `App.test.js` only asserts static text is present — see [v1/frontend.md](../v1/frontend.md#tests) — it never exercises a submit-and-receive-response flow).
- **Auth-aware tests**: a login flow test (submit credentials → token stored in memory/context → protected route/component renders); a 401-triggers-refresh test against the API client wrapper (`api.js`'s evolution — see [architecture.md](./architecture.md)).
- **Dashboard/chart components**: snapshot or interaction tests asserting the right aggregate numbers render given known API-mock responses, and that empty/loading/error states are handled (extending the state-machine pattern `ResultCard` already established in V1).

## What "comprehensive" means here, concretely

Not "100% coverage as a vanity number" — specifically: every rule's fire/no-fire boundary, every auth/authz decision (401 vs 403 vs 404 vs 200), every validation constraint's pass/fail boundary, the full JWT lifecycle including rotation and revocation, and the audit-log isolation guarantee, all have an explicit test asserting the exact behavior described in this doc set — because these are precisely the areas where V1 had zero coverage and where a regression would be either a security hole or a silent business-logic error.

## CI integration

Every test tier above runs in GitHub Actions on every PR (see [deployment-plan.md](./deployment-plan.md)) — Testcontainers-based repository/integration tests run inside the CI runner (Docker-in-Docker is available on GitHub-hosted runners), so "tests pass in CI" means "tests pass against a real MySQL with the real schema," not an approximation.
