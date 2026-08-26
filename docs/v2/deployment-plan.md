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

### CD workflow (`.github/workflows/cd.yml`) — on push/tag to `main` (or a release tag), after CI passes
```
job: build-and-push-images
  - checkout
  - docker/login-action against the chosen registry (GHCR is the natural default — no extra
    account needed beyond the GitHub repo itself)
  - docker build + push for both backend and frontend images, tagged with the git SHA and
    (on a release tag) the semantic version
job: deploy (optional, environment-dependent)
  - if a real target host/PaaS is chosen (see "Where this actually runs" below), this job
    SSHes/uses the platform's CLI to pull the new images and restart the compose stack /
    redeploy the service. Left as a documented placeholder until a concrete hosting choice
    is made — see roadmap.md phase 6.
```

Separating CI (always runs, gates merges) from CD (only runs on `main`/tags, actually ships something) keeps every PR fast and keeps deployment an explicit, reviewable event rather than something that happens on every commit to a feature branch.

## Where this actually runs

This document intentionally does not commit to a specific cloud provider — the compose-based shape (backend + frontend + MySQL, three containers, no orchestration platform needed) runs identically on a single small VM (e.g. a $5–10/mo droplet/VPS with Docker + Compose installed), a platform with native compose/container support (Railway, Render, Fly.io), or a local machine for demo purposes. The choice is a hosting-cost/convenience decision for whoever runs this, not an architectural one — nothing in the app design assumes a specific provider. If/when a target is chosen, the CD workflow's `deploy` job is filled in against that target; everything upstream of it (images, compose file, migrations) is provider-agnostic already.

## Database migrations in deployment

Flyway runs automatically on backend container startup (`spring.flyway.enabled=true`, baked into the default Spring Boot + Flyway integration) — no manual `CREATE TABLE` step by an operator, which is a direct fix for V1's fully-manual, under-documented schema setup (see [v1/database.md](../v1/database.md#schema-code-mismatch-restricted_categories)). A failed migration fails the container's startup loudly rather than the app booting against a mismatched schema.

## Observability in deployment

- Structured (JSON) logs in `docker`/`prod` profiles, written to stdout — captured by `docker logs` / whatever the hosting platform's log aggregation is, without requiring the app itself to know about a specific log backend (see [architecture.md](./architecture.md#logging)).
- A Spring Boot Actuator `/actuator/health` endpoint (already available once `spring-boot-starter-actuator` is added) backs the compose healthcheck for the `backend` service, mirroring the `mysql` service's own healthcheck.
- Log/metrics shipping to an external system (ELK, Grafana/Loki, etc.) is explicitly out of scope for V2 — flagged as a future enhancement once there's a real deployment generating volume worth aggregating, not built speculatively now.

## Rollback

Since images are tagged by git SHA (see CD workflow above), a rollback is "redeploy the previous known-good tag" — no separate rollback tooling needed. Flyway migrations are additive/forward-only by convention (no down-migrations authored), so a rollback that would require reversing a schema change is treated as a new forward migration, not a database rollback — standard practice for this style of migration tool.
