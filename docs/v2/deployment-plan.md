# ImpulseLock V2 — Deployment Plan

V1 had no containerization and no CI/CD — "how to run locally" in the README was manual MySQL setup + `mvn spring-boot:run` + `npm start` (see [v1/README.md quick facts](../v1/README.md#quick-facts)). V2 adds Docker packaging and GitHub Actions automation on top of that same core stack, without introducing infrastructure the project doesn't need (no Kubernetes, no service mesh — a docker-compose-shaped deployment is the right scale here).

## Environments / profiles

Spring profiles: `dev` (local, verbose logging, Swagger enabled, relaxed CORS to `localhost:3000`), `docker` (used inside compose — DB host is the compose service name, not `localhost`), `prod` (structured JSON logs, Swagger admin-gated/disabled, CORS restricted to the real deployed frontend origin via `app.cors.allowed-origins`), `test` (used by the integration test tier, pointed at Testcontainers — see [testing-strategy.md](./testing-strategy.md)).

All secrets (`JWT_SECRET`, `SPRING_DATASOURCE_PASSWORD`, etc.) come from environment variables in every profile except local `dev`, where a git-ignored `.env` (with a committed `.env.example` template) supplies them — see [security-design.md](./security-design.md#secrets-management).

## Docker

### Backend `Dockerfile` (multi-stage)
```
Stage 1 (build):  maven:3.9-eclipse-temurin-17  → mvn -B package -DskipTests
Stage 2 (runtime): eclipse-temurin:17-jre-alpine → copy the built jar, run as a non-root user
```
Multi-stage keeps the shipped image to a JRE + jar (no Maven, no build cache, no source) — smaller image, smaller attack surface. Runtime stage runs as a dedicated non-root user (a one-line `Dockerfile` addition, standard hardening with no real cost).

### Frontend `Dockerfile` (multi-stage)
```
Stage 1 (build): node:20-alpine → npm ci && npm run build
Stage 2 (serve): nginx:alpine   → copy the CRA build/ output, serve as static files
```
Nginx serves the built static bundle and reverse-proxies `/api/v2/*` to the backend service (compose-internal DNS name), so the frontend container needs no runtime knowledge of where the backend "really" lives beyond the compose network — mirroring the dev-time convenience CRA's proxy already gave V1 (see [v1/frontend.md](../v1/frontend.md#apijs--http-client)), just for the containerized deployment shape instead of the dev server.

### `docker-compose.yml`
Services: `mysql` (official image, named volume for data persistence, healthcheck via `mysqladmin ping`), `backend` (depends_on `mysql` healthy, runs Flyway migrations on startup — see [database-design.md](./database-design.md)), `frontend` (depends_on `backend`, nginx on port 80/443 mapped to host). A `.env` file supplies `MYSQL_ROOT_PASSWORD`, `JWT_SECRET`, etc., referenced via compose variable substitution — never hardcoded in the compose file itself (fixing the exact class of mistake V1 made by committing `root`/`password` directly into `application.properties`).

Healthchecks matter here specifically because Flyway migrations must not race an unready MySQL — `backend`'s `depends_on` uses `condition: service_healthy`, not just "container started."

## GitHub Actions

### CI workflow (`.github/workflows/ci.yml`) — on every PR and push to `main`
```
job: backend
  - checkout
  - setup-java (17, temurin), cache ~/.m2
  - mvn -B verify   (runs unit + repository (Testcontainers) + integration tests, JaCoCo report)
  - upload coverage report as an artifact

job: frontend
  - checkout
  - setup-node (20), cache node_modules via actions/cache or npm's built-in cache
  - npm ci
  - npm test -- --watchAll=false
  - npm run build   (fails the job if the production build breaks)

job: lint (optional, can be folded into the above jobs)
  - backend: a Maven-bound formatter/linter check (e.g. Spotless or Checkstyle) if adopted
  - frontend: eslint (CRA's built-in eslint config, already present per v1/frontend.md)
```
`backend` and `frontend` jobs run in parallel (independent). Testcontainers-based tests work on GitHub-hosted `ubuntu-latest` runners out of the box (Docker is preinstalled). The workflow fails the PR check if either job fails — this is the actual "quality gate," not a suggestion.

### No CD workflow — deliberately

This plan originally called for a second `.github/workflows/cd.yml` that would push both images to GHCR on `main` and carry a `deploy` job stub. It was built, and has since been removed. Recording why, because "we shipped a CD workflow" reads as more mature than the arrangement that actually serves this project:

- **Nothing consumed the images.** Once Render + Vercel were chosen (see "Where this actually runs"), Render built the repo-root `Dockerfile` itself from the Git integration and Vercel built the CRA bundle itself. The GHCR images were published on every push to `main` and pulled by no environment — pure build minutes and package storage.
- **The `deploy` job was a permanent `if: false` stub.** Written before a hosting target existed, never filled in once one did. Every Actions run therefore displayed a *Skipped* deploy step for a deploy that had, in fact, already happened seconds earlier via the platforms' push webhooks. That is worse than no deploy step at all: it advertises a gate that does not exist.
- **The claimed CI→CD ordering was never real.** `cd.yml`'s trigger was a plain `on: push: branches: [main]` — no `workflow_run`, no `needs`. It started in parallel with CI on the same commit. "After CI passes" was a comment in the header, not a mechanism.

So the arrangement is now: **one workflow, `ci.yml`, which is a pure quality gate and deploys nothing**, and deployment is the platforms' own Git integration. One path that plainly does what it says beats two that only appear to be connected.

**The trade this leaves open, stated plainly:** deploys are not gated on tests. Render and Vercel fire on the push to `main`, not on CI's result, so a commit that lands on `main` ships whether CI goes green or red. CI gates the *merge* (a red run blocks the PR), which on a branch-and-PR workflow is most of the value, but it is not the same guarantee. Closing the gap would mean disabling platform auto-deploy and calling their deploy hooks from a job that `needs` the CI jobs — at the cost of owning deploy credentials in Actions secrets and losing the platforms' zero-config rollback UI. Worth doing if this project ever takes real traffic; not worth it for a portfolio deployment on free tiers.

## Where this actually runs

This project deploys to **Render (backend, Docker) + Aiven (MySQL) + Vercel (frontend)**. This is
the chosen path, not one option among several: earlier revisions of this document deliberately
stayed provider-agnostic and listed Railway / Fly.io / a generic VPS as equivalent alternatives,
but as of 2026 neither Railway nor Fly.io has a workable free tier, which removes the premise that
made the "pick any of these" framing useful. The app itself is still provider-agnostic — nothing
in the code names a provider, and every provider-specific value below arrives through an
environment variable — but the *documented* deployment is this one.

| Component | Platform | How it ships |
|---|---|---|
| Backend | Render, Docker runtime | Builds the repo-root `Dockerfile` (the same multi-stage build compose uses) |
| Database | Aiven for MySQL | Managed, TLS-only, external to Render |
| Frontend | Vercel | CRA static build, with a rewrite proxying `/api/v2/*` to Render |

The three-container compose topology still describes local development exactly. In the deployed
shape, compose's `mysql` service is replaced by Aiven and its `frontend` nginx container is
replaced by Vercel's edge — the `backend` container is the only piece that ships as-is.

### Why the frontend proxies instead of calling Render directly

The refresh token is delivered as an httpOnly, `Secure`, **`SameSite=Strict`** cookie scoped to
`/api/v2/auth` (see [security-design.md](./security-design.md)). `SameSite=Strict` means the
browser withholds that cookie on any request whose site differs from the page's site — so a
Vercel-hosted page calling `https://<app>.onrender.com/api/v2/auth/refresh` directly would send no
cookie, and the silent-refresh flow would fail on every hard page load. Relaxing the cookie to
`SameSite=None` would fix the symptom while giving up the CSRF protection that `Strict` buys.

`frontend/vercel.json` avoids the trade-off by keeping the browser same-origin:

```json
{
  "rewrites": [
    { "source": "/api/v2/:path*", "destination": "https://REPLACE-WITH-RENDER-URL.onrender.com/api/v2/:path*" }
  ]
}
```

Vercel proxies server-side, so as far as the browser is concerned every API call is a same-origin
request to the Vercel domain, and the `Strict` cookie is sent normally. This is the same trick
`frontend/nginx.conf` already plays for compose (`location /api/v2/` → `proxy_pass http://backend:8080/api/v2/`)
and CRA's `"proxy"` field plays in dev — three deployment shapes, one same-origin strategy.

`frontend/src/api.js` needs no change: `getApiBaseUrl()` returns `''` unless
`REACT_APP_API_BASE_URL` is set, so it already emits relative `/api/v2/...` URLs that the rewrite
picks up. **Do not set `REACT_APP_API_BASE_URL` on Vercel** — doing so switches the client to
absolute cross-origin URLs and reintroduces the exact cookie problem the rewrite exists to avoid.

### Render environment variables

Set these in the Render dashboard (Service → Environment). The order below is the order to fill
them in; the first six are required and the service will not work correctly without them.

| # | Variable | Value / format |
|---|---|---|
| 1 | `SPRING_DATASOURCE_URL` | `jdbc:mysql://<aiven-host>:<port>/impulselock?sslMode=REQUIRED` — host and port from Aiven's service overview. The `?sslMode=REQUIRED` suffix is **not optional**; see "Aiven TLS" below. |
| 2 | `SPRING_DATASOURCE_USERNAME` | Aiven's generated user — `avnadmin` unless you created another |
| 3 | `SPRING_DATASOURCE_PASSWORD` | From Aiven's service overview. Mark it secret in Render. |
| 4 | `JWT_SECRET` | **A real random value — never the committed dev default.** Minimum 32 characters (see below). Generate with `openssl rand -base64 48`. Mark it secret. |
| 5 | `APP_CORS_ALLOWED_ORIGINS` | Your Vercel origin, scheme included, **no trailing slash**: `https://<project>.vercel.app`. Comma-separated if more than one. |
| 6 | `SPRING_PROFILES_ACTIVE` | `prod` — activates the profile block that disables Swagger UI and the OpenAPI endpoint |
| 7 | `JWT_ACCESS_TOKEN_TTL_MINUTES` | Optional, defaults to `15` |
| 8 | `JWT_REFRESH_TOKEN_TTL_DAYS` | Optional, defaults to `7` |
| 9 | `LOGIN_RATE_LIMIT_MAX_ATTEMPTS` | Optional, defaults to `5` |
| 10 | `LOGIN_RATE_LIMIT_WINDOW_MINUTES` | Optional, defaults to `15` |

Deliberately **not** in that list:

- `PORT` — Render injects it. `application.properties` binds `server.port=${PORT:8080}` so the app
  follows whatever Render assigns, falling back to 8080 locally. Do not set it by hand.
- `MYSQL_ROOT_PASSWORD` — compose-only, for the local `mysql` container. Aiven has no equivalent.

**On `JWT_SECRET` specifically**: `JwtService` builds its signing key with
`Keys.hmacShaKeyFor(secret.getBytes(UTF_8))`, and jjwt rejects anything under 256 bits for HS256 —
so the value must be **at least 32 characters**, used raw (it is not base64-decoded first). A
shorter secret fails at startup with `WeakKeyException` rather than silently weakening anything.
The default in `application.properties` is committed and therefore public: it is for local dev
only, and shipping it to Render would let anyone mint valid access tokens.

**On `APP_CORS_ALLOWED_ORIGINS` and the rewrite**: with the Vercel rewrite in place, browser
traffic is same-origin and never triggers a CORS preflight, so this variable is not what makes the
app work day to day. It still matters — `CorsConfig` sets `allowCredentials(true)`, and Spring
rejects a wildcard origin in that mode — so set it correctly rather than leaving it at the
`localhost:3000` default, which would block any direct browser call to the Render URL.

### Swapping in the real Render URL

Render assigns the URL only after the first successful deploy, so the placeholder ships first:

1. Deploy the backend on Render, then copy the assigned URL (`https://<name>.onrender.com`).
2. Edit **`frontend/vercel.json`** — the `"destination"` field, the only line in the repo that
   carries the Render URL. Replace **only** the host `REPLACE-WITH-RENDER-URL.onrender.com`,
   keeping the `https://` prefix and the `/api/v2/:path*` suffix intact.
3. Commit and push — Vercel redeploys on push and the rewrite takes effect.
4. Set `APP_CORS_ALLOWED_ORIGINS` on Render to the Vercel URL, which by then is also known.

Steps 2 and 4 are a genuine chicken-and-egg: each platform needs the other's URL, so the first
deploy of each is expected to be non-functional end-to-end until both are filled in.

### GitHub Actions' role

There is exactly one workflow, `.github/workflows/ci.yml`, and it deploys nothing — it runs the
backend and frontend test suites and uploads the coverage report. Render and Vercel each deploy
from their own Git integration on push to `main`; that is the entire deploy mechanism. See
"No CD workflow — deliberately" above for why the GHCR image-publishing workflow that used to sit
alongside it was removed rather than kept.

## Database migrations in deployment

Flyway runs automatically on backend container startup (`spring.flyway.enabled=true`, baked into
the default Spring Boot + Flyway integration) — no manual `CREATE TABLE` step by an operator,
which is a direct fix for V1's fully-manual, under-documented schema setup (see
[v1/database.md](../v1/database.md#schema-code-mismatch-restricted_categories)). A failed
migration fails the container's startup loudly rather than the app booting against a mismatched
schema — reinforced by `spring.jpa.hibernate.ddl-auto=validate`, which independently fails startup
if the migrated schema and the JPA entities disagree.

### Against a fresh Aiven database

`V1`–`V5` run against Aiven exactly as they do against the compose `mysql:8.0` container. Nothing
in them needs elevated privileges: they are plain `CREATE TABLE` / `ALTER TABLE` / `INSERT` /
`UPDATE` with `CHECK` constraints, `JSON` columns and `DATETIME(3)` — no stored procedures, no
functions, no triggers, no `DEFINER` clauses, no `SET GLOBAL`, no `CREATE DATABASE`. That matters
because those are precisely the constructs a shared-tier managed MySQL withholds: Aiven's
`avnadmin` is not `SUPER`, so a migration defining a trigger or stored routine would fail against
`log_bin_trust_function_creators` with binary logging enabled. None of this project's migrations
touch that surface, so the shared tier is not a constraint here.

Aiven-specific things that *can* trip up the first deploy:

- **The database must already exist.** Flyway creates tables, not schemas, and the JDBC URL has to
  connect to a database that is already there. Aiven provisions a service with `defaultdb` only —
  create `impulselock` from the Aiven console (Databases tab) before the first Render deploy, or
  point `SPRING_DATASOURCE_URL` at `defaultdb` instead. A URL naming a non-existent database fails
  at connection time with `Unknown database`, before Flyway is ever reached.
- **The schema must be empty on first run.** Flyway refuses a non-empty schema that has no
  `flyway_schema_history` table (`Found non-empty schema ... without schema history table`). Use a
  clean database, not one you have already hand-created tables in.
- **TLS is mandatory** — see below. This surfaces as a connection failure, not a migration failure.
- **Connection ceiling.** Shared-tier Aiven plans cap concurrent connections (roughly 20–25 on the
  smallest ones). HikariCP's default maximum pool size is 10, which fits comfortably for a single
  Render instance, but is worth remembering before scaling to more than one.
- **Render free-tier spin-down.** An idle free service is stopped and cold-starts on the next
  request. Flyway re-runs on every start, but after the first deploy it only reads
  `flyway_schema_history` and finds nothing to apply — that costs a checksum validation, not a
  re-migration.

### Aiven TLS

Aiven refuses non-TLS MySQL connections. mysql-connector-j (9.7.0 here, version managed by the
Spring Boot parent) defaults to `sslMode=PREFERRED`, which does negotiate TLS when the server
offers it — but `PREFERRED` also *silently falls back to an unencrypted connection* if the
handshake fails, which is the wrong failure mode for a database reached over the public internet.
Appending **`?sslMode=REQUIRED`** to `SPRING_DATASOURCE_URL` makes a failed handshake an error
instead.

Use `sslMode`, not the older spelling. The driver still accepts `useSSL=true&requireSSL=true` and
translates that exact pair to `sslMode=REQUIRED`, but its own bundled property documentation marks
`useSSL` / `requireSSL` / `verifyServerCertificate` as **deprecated**, and it ignores all three
whenever `sslMode` is set explicitly — so mixing the two spellings is at best redundant and at
worst misleading to the next reader.

`REQUIRED` encrypts without validating the server certificate against a CA. `VERIFY_CA` and
`VERIFY_IDENTITY` add that validation but require Aiven's CA certificate loaded into a Java
truststore on the Render instance, a meaningfully larger setup step; `REQUIRED` is the
proportionate choice at this project's scale and is what Aiven's own quick-start documents.

The hostname never enters the repo — it arrives only through `SPRING_DATASOURCE_URL`, matching how
the username and password already work.

## Observability in deployment

- Structured (JSON) logs in `docker`/`prod` profiles, written to stdout — captured by `docker logs` / whatever the hosting platform's log aggregation is, without requiring the app itself to know about a specific log backend (see [architecture.md](./architecture.md#logging)).
- A Spring Boot Actuator `/actuator/health` endpoint (already available once `spring-boot-starter-actuator` is added) backs the compose healthcheck for the `backend` service, mirroring the `mysql` service's own healthcheck.
- Log/metrics shipping to an external system (ELK, Grafana/Loki, etc.) is explicitly out of scope for V2 — flagged as a future enhancement once there's a real deployment generating volume worth aggregating, not built speculatively now.

## Rollback

Rollback is the hosting platforms' own: Render → **Events** → *Rollback* to a previous deploy; Vercel → **Deployments** → *Promote to Production* on an older one. Both keep previous builds addressable, so no rollback tooling of this project's own is needed (this is one of the things given up by not publishing SHA-tagged images from Actions, and it turns out the platforms already covered it). Flyway migrations are additive/forward-only by convention (no down-migrations authored), so a rollback that would require reversing a schema change is treated as a new forward migration, not a database rollback — standard practice for this style of migration tool.
