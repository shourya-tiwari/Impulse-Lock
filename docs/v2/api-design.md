# ImpulseLock V2 — API Design

Base path: `/api/v2` (V1 had no version prefix at all — `/transaction/evaluate`, `/users`; V2 introduces `/api/v2/...` so a future breaking change can live alongside it at `/api/v3`). All bodies are JSON. All endpoints except `/api/v2/auth/register` and `/api/v2/auth/login` require a `Authorization: Bearer <access-token>` header.

Full request/response contracts are authored as the OpenAPI spec itself (via springdoc annotations on DTOs/controllers) — this document describes the endpoint surface and the conventions the spec follows, not a hand-duplicated copy of every field.

## Conventions

- **Identifiers over the wire are never raw DB primary keys.** Users are referenced by `username` (login) or an opaque profile; transactions are referenced by `publicId` (UUID). See [database-design.md](./database-design.md).
- **Ownership, not client-supplied `userId`.** V1's `/transaction/evaluate` and `/users` took a free-text `userId` in the body with zero ownership check (see [v1/design-decisions.md](../v1/design-decisions.md), item 7). In V2, a `ROLE_USER` caller always acts on *their own* authenticated identity (resolved from the JWT) — there is no `userId` field in the transaction-evaluate or preferences-update request bodies at all. Only `ROLE_ADMIN` endpoints accept an explicit target user identifier, and only under `/api/v2/admin/...`.
- **Pagination**: any endpoint returning a collection uses `page`/`size` query params and returns a `PageResponseDto<T>` envelope (`content`, `page`, `size`, `totalElements`, `totalPages`).
- **Filtering**: query params, not a request body, on `GET` endpoints (e.g. `?category=luxury&decisionType=BLOCK&from=2026-01-01&to=2026-02-01`).
- **Consistent error shape** across every endpoint — see [Error format](#error-format).

## Auth endpoints (`/api/v2/auth`) — public

| Method & path | Purpose |
|---|---|
| `POST /auth/register` | Create a new user account (`ROLE_USER` only — no self-service admin creation). Seeds a default `restricted_categories` row (see [database-design.md](./database-design.md#restricted_categories)). |
| `POST /auth/login` | Authenticate with `username`/`password`; returns an access token (short-lived JWT) + a refresh token (long-lived, returned as an httpOnly cookie — see [security-design.md](./security-design.md)). |
| `POST /auth/refresh` | Exchange a valid refresh token (from the cookie) for a new access token; rotates the refresh token. |
| `POST /auth/logout` | Revokes the caller's current refresh token. |

## User/preferences endpoints (`/api/v2/users`) — authenticated

| Method & path | Purpose |
|---|---|
| `GET /users/me` | Current user's profile + preferences + restricted categories. |
| `PUT /users/me/preferences` | Update `dailyLimit`, `nightSpendingAllowed`, `sensitivityLevel`. Bean-Validated (`@DecimalMin("0")`, `@Min(1)`/`@Max(10)`). |
| `GET /users/me/restricted-categories` | List. |
| `POST /users/me/restricted-categories` | Add one category. |
| `DELETE /users/me/restricted-categories/{category}` | Remove one. |

Replaces V1's single all-in-one `POST /users` upsert (which silently overwrote `restrictedCategories` wholesale on every call — see [v1/rule-engine.md](../v1/rule-engine.md#categoryrestrictionrule)) with granular endpoints so adding one restricted category doesn't require re-sending the entire list.

## Transaction endpoints (`/api/v2/transactions`) — authenticated

| Method & path | Purpose |
|---|---|
| `POST /transactions/evaluate` | Evaluate + persist a transaction for the current user. Direct evolution of V1's `POST /transaction/evaluate` — see [v1/api-reference.md](../v1/api-reference.md#post-transactionevaluate). No `userId` in the body (see Conventions above); `transactionId` is never client-suppliable either (server always generates `publicId`). |
| `GET /transactions/{publicId}` | Fetch one transaction (must belong to the caller, or caller is `ROLE_ADMIN`). |
| `GET /transactions/history` | **New** — paginated, filterable transaction history. Query params: `page`, `size`, `sort` (e.g. `occurredAt,desc`), `from`, `to`, `category`, `merchant`, `decisionType`, `minAmount`, `maxAmount`. Implements the "advanced transaction history" goal and finally exposes what V1 built but never wired up (`TransactionRepository.getTransactionsByUserId` — see [v1/database.md](../v1/database.md#notable-absences) / [v1/api-reference.md](../v1/api-reference.md#endpoints-defined-in-code-but-not-exposed)). |
| `GET /transactions/history/export` | **New** — CSV export of the same filtered set (streamed response, capped row count, audit-logged as a data-export action). |

### `POST /transactions/evaluate` response shape (evolution of V1's `Decision`)

```json
{
  "publicId": "b3f1...",
  "amount": 1000.00,
  "category": "luxury",
  "merchant": "Example Store",
  "occurredAt": "2026-08-26T23:45:00.123",
  "decisionType": "BLOCK",
  "riskScore": 100.00,
  "explanation": "Transaction exceeds daily limit; Spending attempted during restricted night hours; ",
  "triggeredRules": [
    { "ruleCode": "HIGH_AMOUNT", "weight": 70.0, "message": "Transaction exceeds daily limit" },
    { "ruleCode": "NIGHT_SPENDING", "weight": 40.0, "message": "Spending attempted during restricted night hours" }
  ]
}
```
This exact shape (`TransactionResponseDto`) is reused verbatim for `GET /transactions/{publicId}` and every row of `GET /transactions/history` — one entity, one response DTO, three call sites.

`riskScore` is capped at 100 (see [database-design.md](./database-design.md#transactions) — V1's engine summed weights uncapped, see [v1/rule-engine.md](../v1/rule-engine.md#score-aggregation-caveat)). `triggeredRules` is new — a structured array replacing the need to parse the semicolon-joined `explanation` string V1 returned as the only machine-readable signal.

## Dashboard endpoints (`/api/v2/dashboard`) — authenticated, new in V2

| Method & path | Purpose |
|---|---|
| `GET /dashboard/summary` | Current period (default: last 30 days) totals: transaction count, total spend, decision breakdown (ALLOW/DELAY/BLOCK counts), average risk score. |
| `GET /dashboard/spending-by-category` | Aggregated spend + count per category, for the same period. |
| `GET /dashboard/risk-trend` | Daily/weekly risk-score and decision-count time series, for charting. |
| `GET /dashboard/top-triggered-rules` | Which `rule_code`s fire most often for this user, derived from `transactions.triggered_rules` (see [database-design.md](./database-design.md#transactions)). |

All dashboard queries are scoped to the authenticated user by default; `ROLE_ADMIN` callers may pass a `userId` query param to view another user's dashboard (audit-logged as an admin cross-user view).

## Admin endpoints (`/api/v2/admin`) — `ROLE_ADMIN` only

| Method & path | Purpose |
|---|---|
| `GET /admin/users` | Paginated list of all users. |
| `GET /admin/users/{id}` | One user's full profile. |
| `PATCH /admin/users/{id}/status` | Enable/disable an account (sets `users.enabled`). |
| `GET /admin/rule-configs` | List all `rule_configs` rows (weights, thresholds, enabled flags, params). |
| `PUT /admin/rule-configs/{ruleCode}` | Update one rule's `weight`/`enabled`/`params` — this is what makes the previously-hardcoded per-rule weights configurable (see [database-design.md](./database-design.md#rule_configs)). **Note**: this does not cover the global BLOCK/DELAY `decision_thresholds` row — there is no admin endpoint to view or change those; they can only be edited directly in the database. |
| `GET /admin/audit-logs` | Paginated, filterable audit trail — by `action` (exact match) and `from`/`to` date range. No `actor`/`entityType` filter params exist on this endpoint as-built. |

## Error format

All errors share one shape (evolution of V1's `ErrorResponse` — see [v1/error-handling.md](../v1/error-handling.md)):

```json
{
  "timestamp": "2026-08-26T10:15:30.512",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/v2/transactions/evaluate",
  "correlationId": "b7e2c1a4-...",
  "fieldErrors": [
    { "field": "amount", "message": "must be greater than or equal to 0" }
  ]
}
```

`fieldErrors` is new (populated only for validation failures — `MethodArgumentNotValidException`/`ConstraintViolationException`) and is what V1 was missing entirely (V1 collapsed every 400 into a single opaque `message` string). `correlationId` ties the error response to the structured server logs and any audit entry for the same request (see [architecture.md](./architecture.md#logging)).

| Status | When |
|---|---|
| `400` | Bean Validation failure, malformed JSON body |
| `401` | Missing/invalid/expired JWT, bad login credentials |
| `403` | Valid JWT, but caller lacks the required role, or accesses another user's resource without `ROLE_ADMIN` |
| `404` | Referenced resource doesn't exist (or exists but caller has no access — see note below) |
| `409` | Duplicate `username`/`email` on register, or a DB unique-constraint conflict |
| `429` | Too many failed `/auth/login` attempts for the same username in a short window (see [security-design.md](./security-design.md#rate-limiting-authlogin)) |
| `500` | Unexpected server error (message never leaks internals — matches V1's existing generic-catch-all behavior, kept deliberately) |

**Access-control note**: fetching another user's transaction by `publicId` without `ROLE_ADMIN` returns `404`, not `403` — this avoids confirming to an unauthorized caller that a given `publicId` exists at all (a standard information-disclosure precaution; see [security-design.md](./security-design.md)).

This closes the specific asymmetry noted in [v1/error-handling.md](../v1/error-handling.md#notable-gaps): every service method that touches the database now goes through the same `DataIntegrityViolationException`/`DataAccessException` → typed-exception translation, so there's no longer a difference in error quality between the transaction path and the user/preferences path.

## OpenAPI / Swagger

`springdoc-openapi-starter-webmvc-ui` exposes:
- `/v3/api-docs` — raw OpenAPI 3 JSON.
- `/swagger-ui.html` — interactive UI, with a configured `bearerAuth` security scheme so "Authorize" in the UI accepts a real access token and exercises protected endpoints directly.

Grouped by tag matching the sections above (Auth, Users, Transactions, Dashboard, Admin — audit-log endpoints are tagged Admin too, not a separate tag). Enabled by default (any profile other than `prod`); disabled outright — both `/v3/api-docs` and `/swagger-ui.html` — under the `prod` profile via `spring.config.activate.on-profile=prod` in `application.properties`. There is no role-gated middle ground (e.g. "visible only to `ROLE_ADMIN`") — a public interactive API explorer is a reasonable dev convenience but not something to expose unauthenticated in production, so it's simply off there.
