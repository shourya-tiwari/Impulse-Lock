# ImpulseLock V2 — Roadmap

A phased plan for turning V1 (documented in [docs/v1](../v1/README.md)) into V2 (designed in this folder). Phases are ordered by dependency, not by importance — security and persistence come first because almost everything else (audit logging, dashboard, admin endpoints, testing) depends on having users, roles, and JPA entities in place first. Each phase should land as a working, mergeable increment; the granular checklist per phase is in [tasks.md](./tasks.md).

## Phase 0 — Foundation: JPA migration + normalized schema

Migrate off hand-written `JdbcTemplate` SQL onto Spring Data JPA, and land the normalized schema from [database-design.md](./database-design.md) via Flyway, *before* adding any new feature. Doing this first means every later phase (security, audit, dashboard) is built on the real target persistence model instead of being built twice.

- Add Flyway; author `V1__init.sql` matching the new schema.
- Convert `UserProfile`/`Transaction` into JPA entities (`User`, `Transaction`, plus new `Role`, `RestrictedCategory`, `RuleConfig` entities).
- Convert `UserRepository`/`TransactionRepository` to Spring Data JPA interfaces; introduce JPA Specifications for the transaction-history filtering that Phase 3 will expose over the API.
- Keep the existing (unauthenticated) V1 endpoint behavior working end-to-end against the new persistence layer, so this phase is independently verifiable before auth is layered on top.

**Exit criteria**: rule engine + decision flow work identically to V1 behavior (same inputs → same decisions), but reading/writing through JPA against the Flyway-managed schema, with repository-level tests (Testcontainers) in place.

## Phase 1 — Security: JWT auth, Spring Security, RBAC

Full design in [security-design.md](./security-design.md).

- `User`/`Role`/`user_roles` tables and entities (schema already landed in Phase 0; this phase adds registration/login).
- Spring Security filter chain, `BCryptPasswordEncoder`, `JwtService` (issue/validate), `JwtAuthenticationFilter`.
- `/api/v2/auth/register`, `/api/v2/auth/login`, `/api/v2/auth/refresh`, `/api/v2/auth/logout`.
- `refresh_tokens` table + rotation/revocation logic.
- Retrofit existing transaction/preferences endpoints to resolve the acting user from the authenticated principal, removing the client-supplied `userId` field entirely (closing [v1/design-decisions.md](../v1/design-decisions.md) item 7).
- `RestAuthenticationEntryPoint`/`RestAccessDeniedHandler` so 401/403 return the standard JSON error shape.

**Exit criteria**: every non-auth endpoint requires a valid JWT; a user can only ever act on their own data; `ROLE_ADMIN` exists as a concept (even if no admin-only endpoint exists yet).

## Phase 2 — Domain improvements: configurable rules, real daily-limit/frequency logic

Closes the behavioral gaps in [v1/rule-engine.md](../v1/rule-engine.md) and [v1/design-decisions.md](../v1/design-decisions.md) items 1–4.

- `rule_configs` table + `RuleConfigService`; rules read weight/threshold/params from DB instead of Java constructor literals.
- `HighAmountRule` becomes a rolling-daily-total check (aggregate today's transactions for the user, not a single-transaction comparison).
- `FrequentTransactionRule` becomes a real velocity check using actual transaction history (`velocityWindowMinutes`/`velocityCountThreshold` params).
- `DecisionEngine` caps `riskScore` at 100; BLOCK/DELAY thresholds move to `rule_configs`.
- `restricted_categories` becomes a normalized child table; the "LUXURY default" becomes a seeded row per new user instead of an implicit code fallback.
- Transactions persist `decision_type`, `risk_score`, and structured `triggered_rules` JSON (not just the raw fields V1 stored).

**Exit criteria**: rule behavior is admin-configurable without a deployment; the specific V1 quirks documented in `design-decisions.md` no longer reproduce.

## Phase 3 — API surface: advanced transaction history, dashboard, admin endpoints

Full design in [api-design.md](./api-design.md).

- `GET /transactions/history` with pagination/filtering (finally wires up what V1 built but never exposed — `TransactionRepository.getTransactionsByUserId`, see [v1/api-reference.md](../v1/api-reference.md#endpoints-defined-in-code-but-not-exposed)) + CSV export.
- Dashboard endpoints: summary, spending-by-category, risk-trend, top-triggered-rules.
- Admin endpoints: user management, rule-config management, audit-log viewing.
- Granular restricted-category endpoints replacing V1's overwrite-the-whole-list upsert.

**Exit criteria**: every endpoint in `api-design.md` exists, is authenticated/authorized correctly, and is covered by controller-level integration tests.

## Phase 4 — Cross-cutting: validation, global exception handling, logging, audit logging

- Bean Validation on every request DTO (see [security-design.md](./security-design.md#validation)).
- Expand `GlobalExceptionHandler` to the full matrix in [api-design.md](./api-design.md#error-format) (validation errors with field detail, 401/403/404/409 distinctions, consistent DB-error wrapping everywhere — closing the `UserRepository` vs. `TransactionRepository` asymmetry from [v1/error-handling.md](../v1/error-handling.md#notable-gaps)).
- Correlation-ID filter + MDC-based structured logging.
- `AuditLog` entity + AOP `@Auditable` aspect; wire it into auth events, preference changes, transaction evaluation, and every admin action.

**Exit criteria**: every error path returns the documented shape; every security-relevant action produces an audit-log row; logs are correlated by request ID end-to-end.

## Phase 5 — Documentation & developer experience: OpenAPI/Swagger

- springdoc-openapi wired in, grouped by tag, bearer-auth scheme configured in Swagger UI.
- Profile-gated exposure (open in `dev`, admin-gated/disabled in `prod` — see [deployment-plan.md](./deployment-plan.md)).

**Exit criteria**: every endpoint documented in `api-design.md` is discoverable and callable from Swagger UI with a real token.

## Phase 6 — Packaging & delivery: Docker, GitHub Actions, deployment

Full design in [deployment-plan.md](./deployment-plan.md).

- Multi-stage `Dockerfile`s for backend and frontend; `docker-compose.yml` wiring backend + frontend + MySQL with healthchecks.
- CI workflow (backend tests incl. Testcontainers, frontend tests + build) gating every PR.
- ~~CD workflow building/pushing tagged images on `main`/release tags; a placeholder `deploy` job filled in once a hosting target is chosen.~~ Built, then removed once the hosting target turned out to build its own images — GitHub Actions is now CI only. See [deployment-plan.md § No CD workflow](./deployment-plan.md).
- `.env.example`, secrets moved out of committed config (closing [v1/design-decisions.md](../v1/design-decisions.md) item 12).

**Exit criteria**: `docker compose up` runs the full stack from a clean checkout with only a `.env` filled in; CI is green and required on `main`; images are published on merge.

## Phase 7 — Frontend: improved dashboard, advanced history UI, auth flows

- Login/register screens; access-token-in-memory + silent-refresh-on-401 client wrapper (evolution of V1's `api.js`, see [v1/frontend.md](../v1/frontend.md#apijs--http-client)).
- Dashboard view: summary tiles, spending-by-category chart, risk-trend chart, top-triggered-rules — consuming Phase 3's dashboard endpoints.
- Transaction history view: filterable/sortable/paginated table + CSV export button, replacing the "no history view at all" gap in V1.
- Admin views (user list/status toggle, rule-config editor, audit-log viewer) gated on the logged-in user's role.
- Component + MSW-mocked API tests per [testing-strategy.md](./testing-strategy.md#frontend).

**Exit criteria**: a user can register, log in, set preferences, evaluate transactions, browse/filter their history, and view their dashboard entirely through the UI; an admin can additionally manage users and rule configs.

## Phase 8 — Hardening & polish

- Coverage review against the JaCoCo floor set in [testing-strategy.md](./testing-strategy.md#coverage-target); fill gaps.
- Rate limiting on `/auth/login` (flagged as a stretch item in [security-design.md](./security-design.md#whats-explicitly-out-of-scope-for-v2) — revisit once the backing-store decision is easy to make).
- README/docs pass: update the top-level `README.md` to describe V2 setup (Docker-first quickstart) rather than only V1's manual MySQL steps.
- Final architecture/security review pass against this doc set before calling V2 "done."

## Sequencing rationale (why this order)

Persistence (Phase 0) before security (Phase 1) because auth entities themselves need the JPA/schema foundation. Security before domain improvements (Phase 2) because rule-config admin endpoints need RBAC to exist first. Domain improvements before the API surface (Phase 3) because dashboard/history endpoints are more meaningful once transactions actually persist `decision_type`/`risk_score`/`triggered_rules`. Cross-cutting concerns (Phase 4) are threaded through everything above but are called out as their own phase for the pieces that don't naturally attach to a single feature (global exception handling, audit AOP). Documentation (Phase 5) and packaging (Phase 6) come once there's a stable API surface worth documenting and shipping. Frontend (Phase 7) consumes the by-then-stable backend API. Hardening (Phase 8) is last by definition.

Nothing prevents parallelizing within a phase (e.g. dashboard endpoints and admin endpoints in Phase 3 don't depend on each other) — the phase boundaries are about cross-phase dependencies, not a strict single-threaded schedule.
