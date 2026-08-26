# ImpulseLock

Rule-based transaction risk evaluation system. A Spring Boot backend evaluates financial
transactions against user-defined behavioral preferences and returns a decision (`ALLOW` /
`DELAY` / `BLOCK`) with a risk score and explanation; a React frontend provides a dashboard,
transaction history, and (for admins) rule/user/audit-log management. This is V2 — a full rebuild
of the original demo (see [`docs/v1/`](docs/v1/) for the original design and
[`docs/v2/`](docs/v2/) for this version's architecture, security design, and roadmap) adding real
authentication, RBAC, persistence via Spring Data JPA, audit logging, and Docker/CI packaging.

## Quickstart (Docker)

Requires Docker and Docker Compose.

```bash
git clone <this-repo-url>
cd ImpulseLock
cp .env.example .env
# edit .env: set MYSQL_ROOT_PASSWORD and JWT_SECRET to real values
docker compose up --build
```

This brings up MySQL (with the schema created automatically by Flyway on backend startup), the
backend on `http://localhost:8080`, and the frontend on `http://localhost:3000`.

1. Open `http://localhost:3000` and register an account (Register tab) — this always creates a
   regular `ROLE_USER` account, never an admin (see [security design](docs/v2/security-design.md)).
2. Set your preferences (daily limit, night spending, sensitivity, restricted categories), then
   evaluate a transaction and watch the dashboard/history populate.
3. **To get an admin account** (for the Admin tab: user management, rule-config tuning, audit
   log): there is no self-service or seeded admin. Register normally, then promote that account
   directly in the database:
   ```sql
   INSERT INTO user_roles (user_id, role_id)
   SELECT u.id, r.id FROM users u, roles r
   WHERE u.username = 'your-username' AND r.name = 'ROLE_ADMIN';
   ```
   Log out and back in afterward so the new role is reflected in a fresh access token.

Swagger UI (interactive API docs) is available at `http://localhost:8080/swagger-ui.html` in the
default (non-`prod`) setup.

## API overview

All endpoints are under `/api/v2`. Full request/response shapes: [`docs/v2/api-design.md`](docs/v2/api-design.md); interactive docs via Swagger UI above.

| Area | Endpoints |
|---|---|
| Auth | `POST /auth/register`, `/auth/login`, `/auth/refresh`, `/auth/logout` |
| Users (self) | `GET/PUT /users/me`, `/users/me/preferences`, `/users/me/restricted-categories` |
| Transactions | `POST /transactions/evaluate`, `GET /transactions/{publicId}`, `/transactions/history`, `/transactions/history/export` |
| Dashboard | `GET /dashboard/summary`, `/spending-by-category`, `/risk-trend`, `/top-triggered-rules` |
| Admin (`ROLE_ADMIN`) | `/admin/users`, `/admin/rule-configs`, `/admin/audit-logs` |

Every endpoint except `/auth/**` requires `Authorization: Bearer <accessToken>` from `/auth/login`
or `/auth/register`; the refresh token travels only as an httpOnly cookie.

## Manual setup (without Docker)

Useful for local development on the app itself (hot reload, debugging).

### 1. Database
```sql
CREATE DATABASE impulselock;
```
Schema is created automatically by Flyway migrations (`src/main/resources/db/migration/`) the
first time the backend starts — no manual DDL needed.

### 2. Backend
`src/main/resources/application.properties` already defaults to `jdbc:mysql://localhost:3306/impulselock`
with `root`/`password` and a dev-only JWT secret — override any of these via env vars instead of
editing the file, if your local MySQL uses different credentials:
```bash
export SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/impulselock
export SPRING_DATASOURCE_USERNAME=root
export SPRING_DATASOURCE_PASSWORD=your_password
export JWT_SECRET=some-long-random-development-secret
```
Then run:
```bash
./mvnw spring-boot:run       # mvnw.cmd on native Windows shells
```
Server runs at `http://localhost:8080`.

### 3. Frontend
```bash
cd frontend
npm install
npm start
```
Runs at `http://localhost:3000`, proxying API calls to `localhost:8080` (see the `"proxy"` field
in `frontend/package.json`).

### 4. Tests
```bash
./mvnw test                              # backend - Testcontainers spins up a real MySQL for repository/integration tests
cd frontend && npm test -- --watchAll=false
```

## Architecture

- **Rule engine** (`rules/`, `engine/`): pluggable `SpendingRule` strategy classes, each backed by
  a DB-configurable weight/enabled flag/params (`rule_configs` table, tunable via the Admin UI
  without a deploy). `DecisionEngine` sums triggered rules' weights against configurable
  BLOCK/DELAY thresholds.
- **Auth**: stateless JWT access tokens (15 min, held in memory on the frontend) + rotating opaque
  refresh tokens (7 days, httpOnly cookie). See [`docs/v2/security-design.md`](docs/v2/security-design.md).
- **Persistence**: Spring Data JPA + Hibernate, schema owned by Flyway migrations
  (`src/main/resources/db/migration/`) — see [`docs/v2/database-design.md`](docs/v2/database-design.md).
- **Audit logging**: every register/login/preference-change/transaction-evaluation/admin action is
  recorded to an append-only `audit_log` table, queryable via `/admin/audit-logs`.
- **Frontend**: plain Create React App (no router, no state library) — tab-based navigation,
  `AuthContext` for session state, `api.js` for a bearer-token-aware fetch wrapper with automatic
  401-triggered token refresh.

Full documentation: [`docs/v2/architecture.md`](docs/v2/architecture.md),
[`docs/v2/tasks.md`](docs/v2/tasks.md) (phase-by-phase build log with implementation notes).

## Tech stack

| Layer | Technology |
|---|---|
| Backend | Java 17, Spring Boot 4, Spring Security, Spring Data JPA, Flyway |
| Frontend | React 19 (Create React App) |
| Database | MySQL 8 |
| Build | Maven |
| Testing | JUnit 5, Mockito, AssertJ, Testcontainers (backend); React Testing Library, MSW (frontend) |
| CI/CD | GitHub Actions, Docker, GHCR |

## Known limitations

- No seeded/CLI admin provisioning — see the manual SQL step above.
- No rate limiting beyond `/auth/login`'s brute-force guard (see
  [`docs/v2/security-design.md`](docs/v2/security-design.md#whats-explicitly-out-of-scope-for-v2)
  for what's deliberately out of scope).
- Single-instance deployment shape (no horizontal scaling, no distributed cache) — appropriate at
  this project's scale; see [`docs/v2/deployment-plan.md`](docs/v2/deployment-plan.md).
