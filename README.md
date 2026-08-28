# ImpulseLock

[![CI](https://github.com/shourya-tiwari/Impulse-Lock/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/shourya-tiwari/Impulse-Lock/actions/workflows/ci.yml)
[![CD](https://github.com/shourya-tiwari/Impulse-Lock/actions/workflows/cd.yml/badge.svg?branch=main)](https://github.com/shourya-tiwari/Impulse-Lock/actions/workflows/cd.yml)
![Java](https://img.shields.io/badge/Java-17-b07219)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0-6DB33F?logo=springboot&logoColor=white)
![React](https://img.shields.io/badge/React-19-149ECA?logo=react&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1?logo=mysql&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?logo=docker&logoColor=white)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

**ImpulseLock** is a JWT-authenticated, rule-based transaction risk evaluation system. A Spring
Boot backend evaluates financial transactions against each user's own behavioral preferences —
daily spending limit, night-spending policy, category restrictions, sensitivity level — through a
pluggable, DB-configurable rule engine, and returns a decision (`ALLOW` / `DELAY` / `BLOCK`) with a
numeric risk score and a human-readable explanation. A React frontend provides registration/login,
a personal dashboard, filterable transaction history with CSV export, and (for admins) user
management, rule-weight tuning, and a full audit-log viewer.

This is **V2**, a full security- and persistence-focused rebuild of an original unauthenticated
demo. See [`docs/v1/`](docs/v1/) for the original design and [`docs/v2/`](docs/v2/) for this
version's architecture, security design, database design, testing strategy, deployment plan, and
phase-by-phase build log — every design decision summarized in this README is backed by a
document in that folder, cross-referenced throughout.

---

## Table of contents

- [Key features](#key-features)
- [Tech stack](#tech-stack)
- [Architecture](#architecture)
- [The rule engine](#the-rule-engine)
- [Getting started](#getting-started)
  - [Prerequisites](#prerequisites)
  - [Quickstart with Docker Compose](#quickstart-with-docker-compose)
  - [Creating an admin account](#creating-an-admin-account)
  - [Manual setup (without Docker)](#manual-setup-without-docker)
- [Configuration reference](#configuration-reference)
- [API reference](#api-reference)
- [Database schema](#database-schema)
- [Security model](#security-model)
- [Testing](#testing)
- [CI/CD](#cicd)
- [Project structure](#project-structure)
- [Known limitations](#known-limitations)
- [Documentation index](#documentation-index)
- [License](#license)

---

## Key features

- **Explainable, deterministic risk scoring** — five independent rules each contribute a weighted
  score to a transaction; no black-box ML, every decision comes with a plain-English explanation
  and a structured `triggeredRules` array naming exactly which rules fired.
- **JWT authentication with rotation** — short-lived (15 min) signed access tokens held in memory
  on the frontend, long-lived (7 day) opaque refresh tokens delivered as an `httpOnly`/`Secure`
  cookie and rotated on every use.
- **Role-based access control** — `ROLE_USER` / `ROLE_ADMIN`, enforced at the URL level
  (`SecurityConfig`) and again in service code for anything finer-grained (ownership checks are
  always resolved from the authenticated principal, never from a client-supplied ID).
- **DB-configurable rule engine** — rule weights, enabled/disabled flags, and per-rule parameters
  (night-spending window, velocity thresholds, sensitivity threshold) live in the `rule_configs`
  table and are editable by an admin at runtime, with no redeploy required.
- **Full audit trail** — every registration, login (success and failure), preference change,
  transaction evaluation, and admin action is recorded to an append-only `audit_log` table,
  correlated to structured request logs via a per-request correlation ID.
- **Normalized, migration-managed schema** — Flyway owns every schema change (`V1`–`V5`); Hibernate
  runs in `validate`-only mode and is never allowed to create or alter a table.
- **Dashboard and reporting** — spend/decision summaries, spending-by-category breakdowns, a
  risk-score trend over time, and a "which rules fire most often" view, plus paginated/filterable
  transaction history with CSV export.
- **Production-shaped packaging** — multi-stage Docker builds for both services, a three-container
  Compose stack with real healthchecks, and separate CI (test-gate every PR) and CD (build/push
  images to GHCR) GitHub Actions pipelines.

## Tech stack

| Layer | Technology |
|---|---|
| Backend language/runtime | Java 17 |
| Backend framework | Spring Boot 4 (Spring Web, Spring Security, Spring Data JPA, Spring AOP/AspectJ, Spring Boot Actuator) |
| Schema migrations | Flyway (`flyway-core`, `flyway-mysql`, `spring-boot-flyway`) |
| ORM | Hibernate via Spring Data JPA (`ddl-auto=validate` — schema owned exclusively by Flyway) |
| Auth | JWT access tokens (`io.jsonwebtoken` / jjwt, HS256) + opaque, hashed, rotating refresh tokens |
| Object mapping | MapStruct (compile-time entity ↔ DTO mappers) |
| API docs | springdoc-openapi (OpenAPI 3 + Swagger UI) |
| Logging | SLF4J + Logback; JSON (Logstash encoder) in `docker`/`prod`, human-readable pattern otherwise |
| Frontend | React 19 (Create React App, no router/state library — tab-based navigation + `AuthContext`) |
| Database | MySQL 8 |
| Build tools | Maven (backend), npm (frontend) |
| Testing | JUnit 5, Mockito, AssertJ, Testcontainers (backend); React Testing Library, MSW (frontend); JaCoCo coverage reporting |
| Containerization | Docker (multi-stage builds), Docker Compose, Nginx (frontend static serving + API reverse proxy) |
| CI/CD | GitHub Actions (separate CI and CD workflows), GitHub Container Registry (GHCR) |

## Architecture

Two-tier system — a single Spring Boot service and a single React SPA, backed by one MySQL
instance. No message queue, no cache layer, no microservices: at this project's scale, splitting
the backend further would add operational complexity (service discovery, distributed tracing,
network partition handling) with no corresponding benefit.

```
┌──────────────────────┐   HTTPS/JSON (JWT bearer)   ┌────────────────────────────────────────┐   JPA/Hibernate   ┌───────────┐
│    React frontend     │ ───────────────────────────▶ │           Spring Boot backend           │ ──────────────────▶ │   MySQL   │
│  dashboard · history   │ ◀─────────────────────────── │              (port 8080)                │ ◀────────────────── │ impulselock│
│  auth · admin panel    │                              │  Security filter chain → Controllers →  │                    └───────────┘
└──────────────────────┘                              │  Services → Rule Engine → Repositories   │
                                                        │  cross-cutting: audit · logging ·         │
                                                        │  validation · exception handling · OpenAPI│
                                                        └────────────────────────────────────────┘
```

**Request flow — evaluating a transaction:**

```
Client                     POST /api/v2/transactions/evaluate   Authorization: Bearer <access-jwt>
  │
  ▼
JwtAuthenticationFilter    validates the JWT, loads the principal, sets the SecurityContext
  │                        → 401 on a missing/invalid/expired token
  ▼
TransactionController      Bean-Validates the request body
  │                        → 400 with field-level errors on validation failure
  ▼
TransactionService
  1. resolve the acting user from the SecurityContext (never a client-supplied userId)
  2. load the user's profile + effective rule configuration
  3. load the recent transaction window (for velocity / rolling-daily-total rules)
  4. DecisionEngine.evaluate(transaction, profile, rules, history)
  5. persist the transaction (decision, risk score, and structured triggeredRules JSON)
  6. AuditLogService records TRANSACTION_EVALUATED (isolated, never fails the request)
  ▼
200 OK — TransactionResponseDto (decision, riskScore, explanation, triggeredRules[])
```

**Backend package layout:**

```
com.impulselock.impulselock
├── config/       SecurityConfig, JpaAuditingConfig, OpenApiConfig, CorsConfig, RuleEngineConfig,
│                 FilterRegistrationConfig, PasswordEncoderConfig, JacksonConfig
├── security/     JwtService, JwtAuthenticationFilter, SecurityUserDetailsService, SecurityUser,
│                 RestAuthenticationEntryPoint, RestAccessDeniedHandler, SecurityErrorResponseWriter,
│                 RefreshTokenService, LoginRateLimiter
├── controller/   AuthController, UserController, TransactionController, DashboardController,
│                 AdminUserController, AdminRuleConfigController, AdminAuditLogController
├── service/      AuthService, UserService, TransactionService, DashboardService,
│                 AdminUserService, RuleConfigService, AuditLogService, DatabaseOperations
├── engine/       DecisionEngine, RuleContext, RuleContextFactory
├── rules/        SpendingRule (interface), AbstractSpendingRule, and five concrete rules
├── entity/       JPA entities — User, Role, RestrictedCategory, Transaction, AuditLog,
│                 RefreshToken, RuleConfig, DecisionThresholds
├── repository/   Spring Data JPA repositories + JPA Specifications for history filtering
├── dto/          Request/response DTOs — entities never leave the service layer
├── mapper/       MapStruct entity ↔ DTO mappers
├── audit/        AuditAspect — Spring AOP advice for @Auditable service methods
├── exception/    Domain exceptions + GlobalExceptionHandler (@RestControllerAdvice)
└── logging/      Correlation-ID filter, MDC setup
```

The **DTO boundary is mandatory**: controllers only ever see `*RequestDto`/`*ResponseDto` types —
JPA entities (with their relationships, audit columns, and security fields) never appear in a
request or response body. This is what makes Swagger schemas, Bean Validation, and structural
exclusion of `passwordHash` from every response possible without relying on anyone remembering to
omit a field by hand.

## The rule engine

Five independent `SpendingRule` strategies, each contributing `0` or its full configured weight
(all-or-nothing per rule, not graduated) to a running total. `DecisionEngine` sums every rule that
fires, caps the total at 100, and thresholds it: **`≥ 80` → `BLOCK`**, **`≥ 40` → `DELAY`**,
otherwise **`ALLOW`**.

| Rule code | Default weight | What it checks |
|---|---|---|
| `HIGH_AMOUNT` | 70 | Today's cumulative spend (including this transaction) exceeds the user's `dailyLimit`. |
| `NIGHT_SPENDING` | 40 | Transaction occurs during the configured night window (default 23:00–06:00) and the user hasn't opted in to night spending. |
| `FREQUENT_TRANSACTION` | 30 | A velocity check — this many transactions (default: 3) within a short rolling window (default: 10 minutes), including the one being evaluated. |
| `CATEGORY_RESTRICTION` | 25 | Transaction's category matches one of the user's own restricted categories. |
| `SENSITIVITY_LEVEL` | 20 | User's configured sensitivity level (1–10) meets or exceeds a threshold (default: 8), making otherwise-borderline transactions stricter for cautious users. |

All five weights, thresholds, and per-rule parameters live in the `rule_configs` table (see
[Database schema](#database-schema)) and are editable from the Admin UI / `PUT
/admin/rule-configs/{ruleCode}` — changing a weight takes effect on the very next transaction
evaluation, with no deploy. Adding a brand-new rule requires only a new class extending
`AbstractSpendingRule` plus one `@Bean` registration in `RuleEngineConfig` — `DecisionEngine` and
every existing rule are untouched (classic Strategy pattern).

## Getting started

### Prerequisites

- **Docker + Docker Compose** (recommended path — no local Java/Node/MySQL needed), **or**
- **Java 17**, **Node.js 20** + npm, and a local **MySQL 8** instance for the manual setup path.

### Quickstart with Docker Compose

```bash
git clone https://github.com/shourya-tiwari/Impulse-Lock.git
cd Impulse-Lock
cp .env.example .env
# edit .env: set MYSQL_ROOT_PASSWORD and JWT_SECRET to real values
docker compose up --build
```

This brings up three containers:

| Service | Image | Exposed at |
|---|---|---|
| `mysql` | `mysql:8.0` | `localhost:3306` |
| `backend` | built from the root `Dockerfile` (Maven → JRE-Alpine multi-stage) | `localhost:8080` |
| `frontend` | built from `frontend/Dockerfile` (Node → Nginx multi-stage) | `localhost:3000` |

`backend` waits for MySQL's healthcheck before starting, and runs the Flyway migrations
automatically on startup — the schema is created for you, no manual `CREATE TABLE` step. Once
`backend` reports healthy (`GET /actuator/health` → `{"status":"UP"}`), `frontend` starts.

1. Open `http://localhost:3000` and register an account (**Register** tab) — this always creates a
   plain `ROLE_USER` account, never an admin.
2. Set your preferences (daily limit, night spending, sensitivity level, restricted categories),
   then submit a transaction on the evaluation form and watch the dashboard and history populate.
3. Explore the interactive API docs at `http://localhost:8080/swagger-ui.html` (enabled by default;
   disabled entirely under the `prod` profile).

### Creating an admin account

There is no self-service or seeded admin account, and no CLI provisioning tool. Register a normal
account, then grant it `ROLE_ADMIN` directly in the database:

```sql
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r
WHERE u.username = 'your-username' AND r.name = 'ROLE_ADMIN';
```

Log out and back in afterward, so the new role is reflected in a freshly-issued access token. Once
promoted, the **Admin** tab exposes user management (enable/disable accounts), rule-weight tuning,
and the full audit log.

### Manual setup (without Docker)

Useful for local development on the app itself — hot reload, debugging, IDE-driven test runs.

**1. Database**

```sql
CREATE DATABASE impulselock;
```

That's the only manual step — the schema is created automatically by the Flyway migrations under
`src/main/resources/db/migration/` the first time the backend starts.

**2. Backend**

`src/main/resources/application.properties` already defaults to
`jdbc:mysql://localhost:3306/impulselock` with `root`/`password` and a dev-only JWT secret.
Override via environment variables rather than editing the file if your local MySQL differs:

```bash
export SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/impulselock
export SPRING_DATASOURCE_USERNAME=root
export SPRING_DATASOURCE_PASSWORD=your_password
export JWT_SECRET=some-long-random-development-secret
```

```bash
./mvnw spring-boot:run       # mvnw.cmd on native Windows shells (PowerShell/cmd)
```

Server runs at `http://localhost:8080`.

**3. Frontend**

```bash
cd frontend
npm install
npm start
```

Runs at `http://localhost:3000`, proxying API calls to `localhost:8080` via the `"proxy"` field in
`frontend/package.json` (set `REACT_APP_API_BASE_URL` to override with an absolute URL instead).

**4. Tests**

```bash
./mvnw test                              # backend — Testcontainers spins up a real MySQL
cd frontend && npm test -- --watchAll=false
```

## Configuration reference

All configuration is environment-variable-driven with local-dev defaults baked into
`application.properties` — nothing sensitive is ever hardcoded or committed. `.env.example` is
committed as the authoritative template; copy it to `.env` (git-ignored) for Docker Compose.

| Variable | Default (local dev) | Purpose |
|---|---|---|
| `MYSQL_ROOT_PASSWORD` | — (required, no default) | MySQL root password, shared by the `mysql` and `backend` services. |
| `SPRING_DATASOURCE_URL` | `jdbc:mysql://localhost:3306/impulselock` | JDBC connection string (Compose overrides the host to the `mysql` service name). |
| `SPRING_DATASOURCE_USERNAME` | `root` | Database user. |
| `SPRING_DATASOURCE_PASSWORD` | `password` | Database password. |
| `JWT_SECRET` | dev-only placeholder (committed, **must** be overridden outside local dev) | HS256 signing secret for access tokens. |
| `JWT_ACCESS_TOKEN_TTL_MINUTES` | `15` | Access token lifetime. |
| `JWT_REFRESH_TOKEN_TTL_DAYS` | `7` | Refresh token lifetime. |
| `APP_CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | Comma-separated list of allowed CORS origins. |
| `LOGIN_RATE_LIMIT_MAX_ATTEMPTS` | `5` | Failed `/auth/login` attempts allowed per window, per username. |
| `LOGIN_RATE_LIMIT_WINDOW_MINUTES` | `15` | Rate-limit window size. |

## API reference

Base path: **`/api/v2`**. All request/response bodies are JSON. Every endpoint except
`/auth/register` and `/auth/login` requires an `Authorization: Bearer <accessToken>` header; the
refresh token travels only as an `httpOnly` cookie. Full request/response schemas are authored as
the live OpenAPI spec itself (via springdoc annotations) — browse them interactively at
`/swagger-ui.html`, or see [`docs/v2/api-design.md`](docs/v2/api-design.md) for the complete
written contract and conventions.

| Area | Endpoints |
|---|---|
| Auth (public) | `POST /auth/register`, `POST /auth/login`, `POST /auth/refresh`, `POST /auth/logout` |
| Users (self) | `GET/PUT /users/me`, `PUT /users/me/preferences`, `GET/POST /users/me/restricted-categories`, `DELETE /users/me/restricted-categories/{category}` |
| Transactions | `POST /transactions/evaluate`, `GET /transactions/{publicId}`, `GET /transactions/history`, `GET /transactions/history/export` |
| Dashboard | `GET /dashboard/summary`, `GET /dashboard/spending-by-category`, `GET /dashboard/risk-trend`, `GET /dashboard/top-triggered-rules` |
| Admin (`ROLE_ADMIN` only) | `GET /admin/users`, `GET /admin/users/{id}`, `PATCH /admin/users/{id}/status`, `GET/PUT /admin/rule-configs`, `GET /admin/audit-logs` |

**Example — `POST /transactions/evaluate` response:**

```json
{
  "publicId": "b3f1c2d4-5678-4abc-9def-0123456789ab",
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

**Error shape** (identical across every endpoint):

```json
{
  "timestamp": "2026-08-26T10:15:30.512",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/v2/transactions/evaluate",
  "correlationId": "b7e2c1a4-1234-4a5b-8c9d-0e1f2a3b4c5d",
  "fieldErrors": [
    { "field": "amount", "message": "must be greater than or equal to 0" }
  ]
}
```

| Status | When |
|---|---|
| `400` | Bean Validation failure, malformed JSON body |
| `401` | Missing/invalid/expired JWT, bad login credentials |
| `403` | Valid JWT but caller lacks the required role |
| `404` | Referenced resource doesn't exist — or belongs to another user without `ROLE_ADMIN` access (deliberately not `403`, so callers can't probe for a resource's existence) |
| `409` | Duplicate `username`/`email` on register, or a DB unique-constraint conflict |
| `429` | Too many failed `/auth/login` attempts for the same username |
| `500` | Unexpected server error (message never leaks internals) |

## Database schema

MySQL, accessed via Spring Data JPA/Hibernate. Every schema change ships as a reviewed Flyway
migration under `src/main/resources/db/migration/`; `spring.jpa.hibernate.ddl-auto=validate` means
Hibernate only ever checks the schema against the entity mappings at startup — it can never create
or alter a table itself.

```
users ──1───────*── refresh_tokens
  │ 1
  │
  *
user_roles ──*───1── roles

users ──1───────*── restricted_categories
users ──1───────*── transactions
users ──1───────*── audit_log        (actor_user_id nullable — system/unauthenticated events)
rule_configs           (global only — no per-user override)
decision_thresholds    (global, single-row — no FK to any other table)
```

| Table | Purpose |
|---|---|
| `users` | Account + preferences (`daily_limit`, `night_spending_allowed`, `sensitivity_level`, `enabled`). Monetary values are `DECIMAL`, never `FLOAT`/`DOUBLE`. |
| `roles` / `user_roles` | Many-to-many RBAC — ships with `ROLE_USER`/`ROLE_ADMIN`, extensible without a schema change. |
| `restricted_categories` | Normalized one-to-many (replaces a comma-separated string) — one seeded row per new user at registration. |
| `transactions` | `public_id` (UUID) is the externally-exposed identifier, never the internal `BIGINT` PK. Persists `decision_type`, `risk_score` (capped 0–100), `explanation`, and a structured `triggered_rules` JSON array. Indexed on `(user_id, occurred_at DESC)`, `(user_id, decision_type)`, `(user_id, category)`. |
| `rule_configs` | Per-rule `weight`, `enabled` flag, and JSON `params` — read fresh on every evaluation. |
| `decision_thresholds` | Single-row table holding the global `BLOCK`/`DELAY` cutoffs (seeded 80/40). |
| `refresh_tokens` | Only the SHA-256 hash of the token is stored, never the raw value. |
| `audit_log` | Append-only — no update/delete API exists for it, including for admins. Indexed on `(actor_user_id, created_at DESC)` and `(action, created_at DESC)`. |

Full column-level design, rationale, and index choices: [`docs/v2/database-design.md`](docs/v2/database-design.md).

## Security model

- **Authentication**: HS256-signed JWT access tokens (15 min, held in memory on the frontend —
  never `localStorage`, never a cookie) + opaque random refresh tokens (7 days, `httpOnly` /
  `Secure` / `SameSite=Strict` cookie, rotated on every use, only their SHA-256 hash persisted).
  This is the standard "access token in memory, refresh token in an httpOnly cookie" pattern:
  immune to `localStorage`-targeting XSS for the long-lived credential, and requires no CSRF
  defense on the API itself since the access token is never cookie-delivered.
- **Authorization**: two roles (`ROLE_USER`, `ROLE_ADMIN`), enforced at the URL level in
  `SecurityConfig` and again via manual ownership checks in service code for anything
  finer-grained. Ownership is always resolved from the authenticated `SecurityContext`, never from
  a client-supplied ID — the core fix over the original unauthenticated design.
- **Password storage**: `BCryptPasswordEncoder`. The password hash is structurally excluded from
  every DTO/mapper — it is not possible for it to leak through a mapping mistake.
- **Rate limiting**: an in-memory, fixed-window brute-force guard on `/auth/login`, keyed by
  username (not IP), configurable via `LOGIN_RATE_LIMIT_MAX_ATTEMPTS` /
  `LOGIN_RATE_LIMIT_WINDOW_MINUTES`.
- **Secrets**: JWT signing secret and DB credentials are environment-variable-driven everywhere —
  never committed. `.env` is git-ignored; `.env.example` documents every variable a new contributor
  needs to set.
- **Scope**: this targets real authentication, per-user data isolation, and defense against the
  standard OWASP API Top 10 concerns at a level appropriate for a portfolio/learning-scale
  fintech-flavored demo — it does not target PCI-DSS/SOC2 compliance or HSM-backed key management.

Full threat model and design rationale: [`docs/v2/security-design.md`](docs/v2/security-design.md).

## Testing

```
                     ▲
                    /  \        E2E / manual exploratory
                   /────\
                  / API  \      Controller + security integration tests (MockMvc)
                 /────────\
                / Service  \    Service-layer tests (Mockito-mocked repositories)
               /────────────\
              /  Repository  \  JPA repository tests (Testcontainers — real MySQL)
             /────────────────\
            /   Unit (rules,   \ Rule engine, JWT, mappers, validators
           /   engine, utils)   \
          /──────────────────────\
```

Most tests sit at the bottom of the pyramid (fast, isolated, no Spring context) and thin out
toward the top. Backend repository/integration tests run against a **real MySQL via
Testcontainers**, running the actual Flyway migrations — not an in-memory approximation with
different SQL dialect behavior. JaCoCo produces a coverage report on every build (`target/site/jacoco/`, uploaded as a CI artifact).

```bash
./mvnw test                              # backend
cd frontend && npm test -- --watchAll=false   # frontend
```

Frontend tests use React Testing Library for component behavior and MSW (Mock Service Worker) to
intercept `fetch` calls, including a dedicated auth-flow test covering login, protected-route
rendering, and 401-triggered silent token refresh.

Full test-tier breakdown: [`docs/v2/testing-strategy.md`](docs/v2/testing-strategy.md).

## CI/CD

Two independent GitHub Actions workflows:

- **`ci.yml`** — runs on every push/PR to `main`. `backend` job: sets up JDK 17, runs
  `./mvnw -B verify` (unit + Testcontainers-backed repository/integration tests), uploads the
  JaCoCo report. `frontend` job: sets up Node 20, runs `npm ci`, `npm test`, and `npm run build`.
  The two jobs run in parallel; either failing blocks the merge.
- **`cd.yml`** — runs on push to `main` and on version tags. Builds and pushes both the backend and
  frontend Docker images to **GitHub Container Registry (GHCR)**, tagged by commit SHA and (on a
  release tag) semantic version. Its `deploy` job stays disabled (`if: false`) — Render and Vercel
  each deploy from their own Git integration, so a third path here would duplicate them.

```bash
docker compose up --build     # exactly what CD packages, runnable locally
```

Step-by-step deployment runbook: [`DEPLOYMENT.md`](DEPLOYMENT.md).
Full CI/CD and hosting rationale: [`docs/v2/deployment-plan.md`](docs/v2/deployment-plan.md).

## Project structure

```
Impulse-Lock/
├── src/main/java/com/impulselock/impulselock/   Backend source (see Architecture above)
├── src/main/resources/
│   ├── application.properties
│   ├── db/migration/                            Flyway migrations (V1–V5)
│   └── logback-spring.xml
├── src/test/java/                                Unit, service, repository (Testcontainers), and
│                                                  controller/integration tests
├── frontend/
│   ├── src/
│   │   ├── api.js                                Fetch wrapper: bearer auth, timeout/abort,
│   │   │                                          automatic 401 → refresh → retry
│   │   ├── auth/AuthContext.js                    Session state (access token, current user)
│   │   ├── components/                            TransactionForm, UserPreferencesForm,
│   │   │                                          ResultCard, Dashboard, TransactionHistory,
│   │   │                                          LoginForm, RegisterForm, admin/*
│   │   └── mocks/                                 MSW handlers for API-mocked tests
│   ├── Dockerfile                                 Node build stage → Nginx runtime stage
│   └── nginx.conf                                 Static serving + /api/v2/* reverse proxy
├── docs/
│   ├── v1/                                        Original design documentation
│   └── v2/                                        Architecture, security, database, API,
│                                                   testing, and deployment design docs
├── .github/workflows/                             ci.yml, cd.yml
├── Dockerfile                                      Maven build stage → JRE-Alpine runtime stage
├── docker-compose.yml
├── .env.example
└── pom.xml
```

## Known limitations

- No seeded or CLI-based admin provisioning — see [Creating an admin account](#creating-an-admin-account) for the manual step.
- No rate limiting beyond `/auth/login`'s brute-force guard (deliberately scoped — see
  [`docs/v2/security-design.md`](docs/v2/security-design.md#whats-explicitly-out-of-scope-for-v2)).
- Single-instance deployment shape: no horizontal scaling, no distributed cache/rate-limit store —
  appropriate at this project's current scale; see
  [`docs/v2/deployment-plan.md`](docs/v2/deployment-plan.md) for what would need to change first.
- No multi-factor authentication, OAuth2/social login, or ML-based fraud/anomaly scoring — the
  engine is deliberately deterministic and explainable rather than a black box.

## Documentation index

| Document | Covers |
|---|---|
| [`DEPLOYMENT.md`](DEPLOYMENT.md) | Step-by-step runbook for deploying to Aiven + Render + Vercel |
| [`docs/v2/architecture.md`](docs/v2/architecture.md) | System overview, package structure, request flow, cross-cutting concerns |
| [`docs/v2/security-design.md`](docs/v2/security-design.md) | Full JWT/RBAC/rate-limiting threat model and design |
| [`docs/v2/database-design.md`](docs/v2/database-design.md) | Column-level schema, indexes, migration history |
| [`docs/v2/api-design.md`](docs/v2/api-design.md) | Full endpoint contracts and API conventions |
| [`docs/v2/testing-strategy.md`](docs/v2/testing-strategy.md) | Test pyramid, tooling, and coverage approach |
| [`docs/v2/deployment-plan.md`](docs/v2/deployment-plan.md) | Docker, CI/CD, environments, observability, rollback |
| [`docs/v2/roadmap.md`](docs/v2/roadmap.md) / [`docs/v2/tasks.md`](docs/v2/tasks.md) | Phase-by-phase build history from V1 to V2 |
| [`docs/v1/`](docs/v1/) | Original (V1) design and the specific gaps V2 closes |

## License

Licensed under the [MIT License](LICENSE) — see the `LICENSE` file for the full text.
