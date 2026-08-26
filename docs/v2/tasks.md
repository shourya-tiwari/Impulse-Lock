# ImpulseLock V2 — Detailed Tasks

Granular, checkbox-level breakdown of [roadmap.md](./roadmap.md). Organized by phase, in dependency order. No code is written against this list yet — it's the implementation checklist for when building starts. Each item references the design doc that justifies it, so "why" is always one click away.

---

## Phase 0 — Foundation: JPA migration + normalized schema ✅ implemented

- [x] Add `flyway-core` + `flyway-mysql` dependencies to `pom.xml` (also swapped `spring-boot-starter-jdbc` for `spring-boot-starter-data-jpa`, and added `spring-security-crypto` for password hashing — see note below).
- [x] Set `spring.jpa.hibernate.ddl-auto=validate` in every profile (never `update`/`create`) — see [database-design.md](./database-design.md). (Only one profile exists as of Phase 0; per-profile split arrives in Phase 6.) Also set `spring.jpa.open-in-view=false`.
- [x] Author `src/main/resources/db/migration/V1__init_schema.sql`: `users`, `roles`, `user_roles`, `restricted_categories`, `transactions`, `rule_configs`, `refresh_tokens`, `audit_log` — full DDL per [database-design.md](./database-design.md). **Deviation**: `transactions.decision_type` / `risk_score` / `explanation` / `triggered_rules` are nullable in this migration, not `NOT NULL` — the JPA entity intentionally doesn't map them yet (see next item), so the DB constraint is loosened until Phase 2 wires the write path and a follow-up migration tightens it.
- [x] Author `V2__seed_roles_and_rule_configs.sql`: seed `ROLE_USER`/`ROLE_ADMIN`, seed `rule_configs` rows with V1-equivalent weights (70/40/30/25/20). **Note**: BLOCK/DELAY decision thresholds (80/40) are *not* seeded anywhere yet — `DecisionEngine` keeps them as hardcoded literals in Phase 0 (unchanged from V1); sourcing them from config is explicit Phase 2 work per this file's own Phase 2 section.
- [x] Create JPA entities: `User`, `Role`, `RestrictedCategory`, `Transaction`, `RuleConfig` (`RefreshToken`, `AuditLog` tables exist in the migration but have no entity yet, per plan). `Transaction` deliberately omits `decisionType`/`riskScore`/`explanation`/`triggeredRules` fields — Phase 0 matches V1's actual behavior (Decision computed and returned, not persisted on the row); Phase 2 adds those fields.
- [x] Add `@EntityListeners(AuditingEntityListener.class)` + `@CreatedDate`/`@LastModifiedDate` fields; enable via `@EnableJpaAuditing` in a `JpaAuditingConfig`.
- [x] Replace `UserRepository`/`TransactionRepository` (hand-written `JdbcTemplate`) with Spring Data JPA interfaces — see [v1/database.md](../v1/database.md) for the code replaced. Also added `RoleRepository`, `RestrictedCategoryRepository`, `RuleConfigRepository` (all needed once entities exist, trivial to add now).
- [x] Add `TransactionSpecifications` (`byUser`, `occurredBetween`, `byCategory`, `byMerchant`, `amountBetween`). **Deviation**: no `byDecisionType` yet — that field isn't mapped on the entity until Phase 2 (see above); a comment in the class marks where it goes.
- [x] Re-point `DecisionEngine`/rules/`TransactionService` at the new entities. `SpendingRule`/`AbstractSpendingRule`/all five rule classes now take `entity.Transaction`/`entity.User` (amount comparisons moved from primitive `double` to `BigDecimal.compareTo`). Old unauthenticated endpoints were kept working, but reshaped: `TransactionEvaluateRequest`/`UserUpsertRequest`/`UserProfileResponse` DTOs (in `dto/`) replace binding directly to `model.Transaction`/`model.UserProfile` (which are deleted — identity moved from a client-chosen `userId` string to a server-generated `id` + unique `username`, so byte-for-byte contract preservation wasn't possible or desirable). A new transitional `UserService` hashes passwords (`spring-security-crypto`'s `BCryptPasswordEncoder`) and seeds a default `"LUXURY"` restricted-category row per new user, replacing `CategoryRestrictionRule`'s old hardcoded fallback now that the association is a real child table. Also removed `ImpulseLockApplication`'s leftover startup demo block (see [v1/design-decisions.md](../v1/design-decisions.md) item 9) since it directly `new`-ed up the now-deleted model classes.
- [x] Repository-level tests: Testcontainers MySQL (`AbstractIntegrationTest` base class using `@ServiceConnection`), covering unique constraints (`username`, `email`), cascade delete (`restricted_categories`), auditing timestamps, and `TransactionSpecifications` filtering. **Not run in this environment** (no Docker available here) — verified to compile; needs a Docker-capable machine or CI to actually execute (see [deployment-plan.md](./deployment-plan.md)).
- [x] Verify: same transaction inputs produce the same `Decision` output as documented in [v1/rule-engine.md](../v1/rule-engine.md) — `DecisionEngineTest` and per-rule boundary tests (fire/no-fire at the exact threshold) added and passing; confirms the persistence swap didn't change rule/engine behavior (including V1's uncapped-above-100 quirk, deliberately preserved until Phase 2).

## Phase 1 — Security: JWT auth, Spring Security, RBAC

- [ ] Add `spring-boot-starter-security` + a JWT library (`jjwt` or `nimbus-jose-jwt`) to `pom.xml`.
- [ ] `User` entity: add `passwordHash`, `enabled`; `Role` many-to-many relationship (already scaffolded in Phase 0).
- [ ] `SecurityUser` (implements `UserDetails`) + `SecurityUserDetailsService` (implements `UserDetailsService`, loads by username).
- [ ] `JwtService`: `generateAccessToken(user)`, `generateRefreshToken()` (opaque random, hashed before storage), `validateAccessToken(token)`, `parseClaims(token)`.
- [ ] `JwtAuthenticationFilter` (extends `OncePerRequestFilter`): reads `Authorization: Bearer`, validates, populates `SecurityContext`.
- [ ] `RefreshToken` entity + `RefreshTokenRepository`; `RefreshTokenService`: issue/rotate/revoke/revoke-all-for-user.
- [ ] `SecurityConfig`: stateless session policy, CSRF disabled (documented reasoning inline — see [security-design.md](./security-design.md#spring-security-configuration-shape)), URL-level authorization rules (`/api/v2/auth/**` public, `/api/v2/admin/**` → `ROLE_ADMIN`, everything else → authenticated), `BCryptPasswordEncoder` bean.
- [ ] `RestAuthenticationEntryPoint` (401 JSON) + `RestAccessDeniedHandler` (403 JSON) — matching [api-design.md](./api-design.md#error-format).
- [ ] `AuthController` + `AuthService`: `register`, `login`, `refresh`, `logout` — request/response DTOs with Bean Validation (see Phase 4 for the validation annotations themselves, but the DTO classes are created here).
- [ ] Refresh-token cookie handling: httpOnly/`Secure`/`SameSite=Strict`, set on login/refresh, cleared on logout.
- [ ] Update `CorsConfig` to read allowed origins from `app.cors.allowed-origins` config property instead of the V1 hardcoded `localhost:3000` (see [v1/backend.md](../v1/backend.md#corsconfig)).
- [ ] Retrofit `TransactionController`/`UserController` (or their Phase 3 replacements) to resolve the acting user via `@AuthenticationPrincipal`/`SecurityContextHolder`, removing any client-supplied `userId` request field — closes [v1/design-decisions.md](../v1/design-decisions.md) item 7.
- [ ] Security integration tests: register→login→call protected endpoint→expire→refresh→retry (full lifecycle, see [testing-strategy.md](./testing-strategy.md)); wrong-role → 403; missing/expired/tampered token → 401.

## Phase 2 — Domain improvements: configurable rules, real daily-limit/frequency logic

- [ ] `RuleConfig` entity + `RuleConfigRepository` (scaffolded in Phase 0; add service layer now).
- [ ] `RuleConfigService`: load-all, get-by-code, update (admin-only, wired to Phase 3's admin endpoint).
- [ ] Refactor `AbstractSpendingRule` (or its replacement) to accept a resolved `RuleConfig` (weight, enabled, params) instead of hardcoded constructor literals — see [v1/rule-engine.md](../v1/rule-engine.md) for the code being replaced.
- [ ] `HighAmountRule` → rolling-daily-total: query the user's transactions for the current calendar day (or rolling 24h — decide and document the choice in code comments/ADR at implementation time), sum `amount`, compare against `dailyLimit`. Requires a repository query (`sumAmountByUserAndOccurredAtBetween`).
- [ ] `FrequentTransactionRule` → real velocity check: query transactions within `params.velocityWindowMinutes`, compare count against `params.velocityCountThreshold`.
- [ ] `NightSpendingRule`: move `nightStartHour`/`nightEndHour` from hardcoded `6`/`23` into `RuleConfig.params`.
- [ ] `SensitivityLevelRule`: move the `>= 8` threshold into `RuleConfig.params`.
- [ ] `CategoryRestrictionRule`: read from the new `restricted_categories` child table (already modeled in Phase 0); remove the V1 "LUXURY" hardcoded fallback (see [v1/rule-engine.md](../v1/rule-engine.md#categoryrestrictionrule)) — replace with the seeded-default-row approach.
- [ ] Registration flow (Phase 1's `AuthService.register`): seed a default `restricted_categories` row (e.g. `"LUXURY"`) for every new user, replacing the removed code-level fallback.
- [ ] `DecisionEngine`: cap `totalRisk` at 100 before returning; source BLOCK/DELAY thresholds from `RuleConfig` (or a small dedicated `decision_thresholds` config) instead of the literals `80`/`40`.
- [ ] `Transaction` entity: add `decisionType`, `riskScore`, `triggeredRules` (JSON) columns/fields (already in Phase 0's schema; wire up the write path here).
- [ ] Unit tests per rule at exact fire/no-fire boundaries (see [testing-strategy.md](./testing-strategy.md#unit-tests-no-spring-context-fastest-tier)); `DecisionEngine` cap/threshold tests.

## Phase 3 — API surface: advanced transaction history, dashboard, admin endpoints

- [ ] `TransactionController`: `POST /api/v2/transactions/evaluate` (evolved from V1), `GET /api/v2/transactions/{publicId}` (with ownership/404-not-403 check per [api-design.md](./api-design.md#error-format)).
- [ ] `GET /api/v2/transactions/history`: pagination (`page`/`size`/`sort`), filters (`from`, `to`, `category`, `merchant`, `decisionType`, `minAmount`, `maxAmount`) via the `TransactionSpecifications` from Phase 0.
- [ ] `GET /api/v2/transactions/history/export`: streamed CSV response, row cap, audit-logged as a data-export action (ties into Phase 4's audit aspect).
- [ ] `UserController`: `GET /users/me`, `PUT /users/me/preferences`, `GET/POST/DELETE /users/me/restricted-categories`.
- [ ] `DashboardController` + `DashboardService`: `summary`, `spending-by-category`, `risk-trend`, `top-triggered-rules` — aggregation queries against `transactions` (including the new `triggered_rules` JSON column for the last one).
- [ ] `AdminUserController`: `GET /admin/users`, `GET /admin/users/{id}`, `PATCH /admin/users/{id}/status`.
- [ ] `AdminRuleConfigController`: `GET /admin/rule-configs`, `PUT /admin/rule-configs/{ruleCode}` (wired to Phase 2's `RuleConfigService`).
- [ ] `AdminAuditLogController` (endpoint shell now; backed fully once Phase 4 lands the `AuditLog` writer): `GET /admin/audit-logs` with filters.
- [ ] `PageResponseDto<T>` envelope class, reused by every paginated endpoint.
- [ ] MapStruct mappers for every entity↔DTO pair introduced this phase.
- [ ] Controller integration tests (MockMvc + real security chain) for every endpoint above, per [testing-strategy.md](./testing-strategy.md#controller--integration-tests-springboottest--mockmvc-full-security-filter-chain-active).

## Phase 4 — Cross-cutting: validation, global exception handling, logging, audit logging

- [ ] Add Bean Validation annotations to every request DTO from Phases 1 and 3 (`RegisterRequestDto`, `UpdatePreferencesRequestDto`, `EvaluateTransactionRequestDto`, rule-config update DTO, etc.) per [security-design.md](./security-design.md#validation).
- [ ] Custom validator: `@ValidSensitivityLevel` (or equivalent) if a plain `@Min`/`@Max` pair isn't expressive enough once combined with other constraints.
- [ ] Expand `GlobalExceptionHandler`: `MethodArgumentNotValidException`/`ConstraintViolationException` → 400 with `fieldErrors`; `AuthenticationException`/`BadCredentialsException` → 401; `AccessDeniedException` → 403; `ExpiredJwtException`/`JwtException` → 401; `DataIntegrityViolationException` → 409; generic catch-all retained. Full matrix in [api-design.md](./api-design.md#error-format).
- [ ] Audit every remaining service method that touches the DB for consistent `DataAccessException` wrapping — close the specific V1 `UserRepository` vs. `TransactionRepository` asymmetry (see [v1/error-handling.md](../v1/error-handling.md#notable-gaps)).
- [ ] `CorrelationIdFilter`: generate/propagate `X-Request-Id`, store in MDC.
- [ ] Logback config: pattern encoder for `dev`, JSON encoder for `docker`/`prod` (see [architecture.md](./architecture.md#logging)).
- [ ] `AuditLog` entity + `AuditLogRepository` + `AuditLogService.record(action, entityType, entityId, metadata)` — writes on a separate transaction/thread so a failure never rolls back or throws into the calling business operation (see [architecture.md](./architecture.md#audit-logging)).
- [ ] `@Auditable(action = "...")` annotation + AOP `@Around` aspect (`AuditAspect`) that calls `AuditLogService` after a successful method invocation, capturing actor (from `SecurityContext`), correlation ID (from MDC), and IP (from the request, via a request-scoped holder).
- [ ] Annotate: `AuthService.register`/`login` (success and failure), `UserService` preference/category changes, `TransactionService.evaluateAndSave`, every admin service method (status toggle, rule-config update).
- [ ] Test: simulated audit-write failure does not fail the calling operation (see [testing-strategy.md](./testing-strategy.md#service-layer-tests-mockito-mocked-repositories-no-db-no-spring-context)).
- [ ] Wire `AdminAuditLogController` (shell from Phase 3) to the now-real `AuditLogRepository`.

## Phase 5 — Documentation & developer experience: OpenAPI/Swagger

- [ ] Add `springdoc-openapi-starter-webmvc-ui` dependency.
- [ ] `OpenApiConfig`: API title/version/description, `bearerAuth` security scheme, tag definitions matching [api-design.md](./api-design.md)'s section headers (Auth, Users, Transactions, Dashboard, Admin).
- [ ] Annotate controllers/DTOs with `@Operation`/`@Schema` where the generated defaults aren't self-explanatory (error responses, auth requirements per endpoint).
- [ ] Profile-gate: enabled in `dev`/`docker`, disabled or `ROLE_ADMIN`-gated in `prod` (see [deployment-plan.md](./deployment-plan.md#observability-in-deployment) profile list).
- [ ] Manual pass: exercise every endpoint from Swagger UI with a real issued token, confirm request/response schemas match reality.

## Phase 6 — Packaging & delivery: Docker, GitHub Actions, deployment

- [ ] Backend multi-stage `Dockerfile` (maven build stage → JRE runtime stage, non-root user) per [deployment-plan.md](./deployment-plan.md#backend-dockerfile-multi-stage).
- [ ] Frontend multi-stage `Dockerfile` (node build stage → nginx serve stage), nginx config reverse-proxying `/api/v2/*` to the backend service.
- [ ] `docker-compose.yml`: `mysql` (volume, healthcheck), `backend` (`depends_on: condition: service_healthy`, Flyway auto-run), `frontend`.
- [ ] `.env.example` with every required variable (`JWT_SECRET`, `MYSQL_ROOT_PASSWORD`, `SPRING_DATASOURCE_*`, `app.cors.allowed-origins`, etc.); `.env` git-ignored.
- [ ] Add `spring-boot-starter-actuator`; expose `/actuator/health` for the compose healthcheck.
- [ ] `.github/workflows/ci.yml`: `backend` job (setup-java, `mvn -B verify` incl. Testcontainers tests, JaCoCo artifact upload), `frontend` job (setup-node, `npm ci`, `npm test -- --watchAll=false`, `npm run build`), running in parallel on every PR/push to `main`.
- [ ] `.github/workflows/cd.yml`: on push/tag to `main` after CI passes — build+push backend and frontend images to GHCR, tagged by git SHA (+ semver on release tags); a documented placeholder `deploy` job.
- [ ] Verify from a clean checkout: `docker compose up` (with a filled-in `.env`) brings up the full stack and the frontend can reach the backend.
- [ ] Mark CI as a required status check on `main` in repo branch protection settings.

## Phase 7 — Frontend: improved dashboard, advanced history UI, auth flows

- [ ] `AuthContext`/hook: holds the access token in memory, exposes `login`/`logout`/`register`, current user + roles.
- [ ] `LoginForm`, `RegisterForm` components.
- [ ] Evolve `api.js`: attach `Authorization` header automatically; on a 401, transparently call `/auth/refresh` (cookie-based) and retry once before surfacing an error — per [security-design.md](./security-design.md#token-lifecycle).
- [ ] Route/view gating by role (a plain conditional render is sufficient given V1/V2 have no router — introducing a router here is a reasonable point to reconsider, given multiple distinct views now exist: dashboard, history, preferences, admin).
- [ ] `Dashboard` view: summary tiles, spending-by-category chart, risk-trend chart, top-triggered-rules list — consuming Phase 3's dashboard endpoints.
- [ ] `TransactionHistory` view: filterable/sortable/paginated table (reuse the filter params from [api-design.md](./api-design.md#transaction-endpoints-apiv2transactions--authenticated)) + CSV export button.
- [ ] Update `TransactionForm`/`UserPreferencesForm` (or their replacements) to drop the `userId` field entirely (server resolves it from the token) and to use the granular restricted-category endpoints instead of the V1 whole-list overwrite (see [v1/frontend.md](../v1/frontend.md#userpreferencesformjs)).
- [ ] `AdminPanel` view(s): user list + status toggle, rule-config editor, audit-log viewer — rendered only for `ROLE_ADMIN` users.
- [ ] Component tests (React Testing Library) + MSW-mocked API tests for every new/changed component, per [testing-strategy.md](./testing-strategy.md#frontend).

## Phase 8 — Hardening & polish

- [ ] Review JaCoCo coverage report against the floor set in [testing-strategy.md](./testing-strategy.md#coverage-target); add tests for any real gap (not padding for the number).
- [ ] Decide and implement (or explicitly defer with a written reason) rate limiting on `/auth/login` per [security-design.md](./security-design.md#whats-explicitly-out-of-scope-for-v2).
- [ ] Rewrite the top-level `README.md`: Docker-first quickstart (`docker compose up`) as the primary path, manual setup kept as a documented fallback for contributors who want to run components individually.
- [ ] Final pass: re-read `architecture.md`, `security-design.md`, `database-design.md`, `api-design.md` against the actual built code; correct any drift before calling V2 complete.
- [ ] Confirm every documented V1 limitation in [v1/design-decisions.md](../v1/design-decisions.md) has either been fixed (list which) or explicitly deferred with a stated reason (e.g. rate limiting) — this is the acceptance check for "V2 addressed what V1 documented as known gaps."
