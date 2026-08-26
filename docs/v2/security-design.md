# ImpulseLock V2 — Security Design

V1 had **no authentication or authorization at all** — any caller could evaluate transactions or read/write preferences for any `userId` string they typed in a request body (see [v1/design-decisions.md](../v1/design-decisions.md), item 7). This document is the full design for closing that gap.

## Threat model, scoped honestly

This is a portfolio/learning-scale fintech-flavored demo, not a regulated production banking system. The security design targets: real authentication, real per-user data isolation, defense against the standard web-API mistakes (broken auth, broken access control, injection, sensitive data exposure), and an auditable trail — matching OWASP API Top 10 concerns at a level appropriate to the project's scale. It does not target PCI-DSS/SOC2 compliance, HSM-backed key management, or fraud-detection-grade anomaly systems.

## Authentication: JWT

### Token types
- **Access token**: short-lived (15 minutes, `app.jwt.access-token-ttl-minutes`), JWT signed HS256 (`JwtService`, `io.jsonwebtoken`) — HS256 was the pragmatic default actually shipped; RS256 was considered but not built, since there's no second service that needs to verify tokens without the signing secret at this project's scale. Carries `sub` (username), `roles` (array), `iat`, `exp`, and a `jti` (random token ID). **The `jti` is not currently consulted anywhere** — there is no revoked-token/blacklist table, so it's present on the token but vestigial; `JwtAuthenticationFilter` only checks signature validity and expiry. Only refresh tokens are actually revocable (see below); an access token issued just before logout stays valid until its own 15-minute expiry.
- **Refresh token**: long-lived (7 days), opaque random value (not a JWT) — only its SHA-256 hash is persisted (`refresh_tokens.token_hash`, see [database-design.md](./database-design.md#refresh_tokens)). Delivered to the browser as an **httpOnly, `Secure`, `SameSite=Strict` cookie** — never accessible to JavaScript, closing off XSS-based token theft for the long-lived credential.

### Why the access token isn't also a cookie
The access token is returned in the JSON login response body and held in memory (a JS variable / React state) on the frontend, not `localStorage` and not a cookie — `localStorage` is readable by any XSS payload, and a cookie-based access token would require CSRF protection on every state-changing request. Keeping the access token in memory means it's gone on a hard refresh (mitigated by the silent-refresh flow below) but never persistently exposed to either attack class. This is the standard "access token in memory, refresh token in httpOnly cookie" pattern.

### Token lifecycle

```
POST /auth/login  (username + password)
  → SecurityUserDetailsService loads user, Spring Security's AuthenticationManager verifies
    password via BCryptPasswordEncoder
  → JwtService issues access token (15m) + refresh token (7d, random, hashed+stored)
  → response: { accessToken, user: {...} } in body; refresh token set as httpOnly cookie
  → AuditLogService records LOGIN_SUCCESS (or LOGIN_FAILURE on bad credentials, actor_user_id
    left null if the username didn't resolve to a real account, to avoid leaking existence)

Every subsequent request
  → JwtAuthenticationFilter reads Authorization: Bearer <accessToken>
  → validates signature + expiry (jti is not checked - see above; no revocation-list exists for
    access tokens), then loads the user fresh from the DB by username and checks isEnabled()
  → loads a SecurityUser (Spring Security principal), sets SecurityContext
  → 401 if missing/invalid/expired/disabled-user — caught by GlobalExceptionHandler, not a raw stack trace

Access token expires (15m)
  → frontend's fetch wrapper catches a 401, calls POST /auth/refresh (cookie sent automatically)
  → server validates refresh token hash against refresh_tokens, checks not revoked/expired
  → issues a new access token AND rotates the refresh token (old one marked revoked,
    new one stored) — rotation limits the blast radius of a leaked refresh token to one use
  → original request is retried once with the new access token

Logout
  → POST /auth/logout revokes the current refresh_tokens row (revoked_at = now)
  → cookie is cleared client-side
  → "log out of all devices": RefreshTokenService.revokeAllForUser(user) implements this (revokes
    every refresh_tokens row for that user_id), but **no controller endpoint calls it** - it's
    unreachable via the API as-built, not exposed to admins or self-service
```

### Password storage
`BCryptPasswordEncoder` (Spring Security default, cost factor 10+). Never logged, never included in any DTO leaving the service layer (enforced structurally — `User` entity's password hash field is excluded from every response DTO/mapper, not just "remembered to omit").

## Authorization: role-based access control

- Two roles at launch: `ROLE_USER`, `ROLE_ADMIN` (schema supports more — see [database-design.md](./database-design.md#roles)).
- Enforced at two layers, deliberately redundant:
  1. **URL-level** in `SecurityConfig`'s `SecurityFilterChain`: `/api/v2/admin/**` requires `ROLE_ADMIN`; everything else under `/api/v2/**` (except `/auth/**` and Swagger's own paths) requires an authenticated principal of any role.
  2. **Manual checks in service code** for anything with finer-grained rules than "is this URL prefix admin-only" — there is no `@PreAuthorize` anywhere in the codebase; instead, e.g. `DashboardService.resolveTargetUser` directly checks `principal.isAdmin()` (a plain method on `SecurityUser`, backed by the `ROLE_ADMIN` authority) and throws `AccessDeniedException` itself when a non-admin passes a `targetUserId`. `TransactionService.getByPublicId` does the equivalent ownership check for viewing another user's transaction.
- **Ownership checks are never inferred from a client-supplied ID.** Every "get/update my own X" endpoint resolves the acting user from the authenticated `SecurityContext`, never from a request parameter — this is the direct fix for V1's core gap (client-supplied `userId` with zero ownership check).
- New user registration always assigns exactly `ROLE_USER`. There is no API path to self-assign `ROLE_ADMIN`, and **no admin-provisioning mechanism was built** — no seed migration creates an admin user, and no CLI/script exists. In practice: register a normal account, then manually insert a `user_roles` row for `ROLE_ADMIN` directly in the database (see the top-level `README.md`'s quickstart for the exact SQL). There is also no `PATCH /admin/users/{id}` role-change endpoint — `AdminUserController` only exposes `PATCH /admin/users/{id}/status` (enable/disable), not a role editor.

## Spring Security configuration shape

- Stateless: `SessionCreationPolicy.STATELESS` — no `HttpSession`, matching the JWT-only design.
- CSRF disabled for the stateless JSON API (standard practice when there's no cookie-based session auth driving state changes) — the one cookie in the system (named `refreshToken`, scoped to path `/api/v2/auth`) is `httpOnly`/`Secure`/`SameSite=Strict` and only ever read by `/auth/refresh` and `/auth/logout`, which is itself a deliberate, narrow exception documented inline in the security config, not an oversight.
- `RestAuthenticationEntryPoint` / `RestAccessDeniedHandler` translate Spring Security's default HTML/redirect behavior into the same JSON `ErrorResponse` shape as the rest of the API (see [api-design.md](./api-design.md#error-format)) — a caller never sees a login-page redirect or an HTML 403 page.
- CORS: evolves V1's `CorsConfig` (hardcoded to `http://localhost:3000` — see [v1/backend.md](../v1/backend.md#corsconfig)) to read allowed origins from configuration (`app.cors.allowed-origins`), so a deployed frontend origin can be added per environment without a code change.

## Validation

Every request DTO carries Bean Validation constraints (`jakarta.validation`):
- `RegisterRequest`: `@NotBlank @Size(min=3, max=50) username`, `@NotBlank @Email email`, `@NotBlank @Size(min=8, max=100) password` (as-built, the minimum is length-only — there is no password/username-inequality or complexity check).
- `UserPreferencesUpdateRequest`: `@NotNull @DecimalMin("0.0") dailyLimit`, `@NotNull @Min(1) @Max(10) sensitivityLevel` — closes V1's complete absence of numeric bounds checking (see [v1/design-decisions.md](../v1/design-decisions.md), item 8). `restrictedCategories` is not part of this DTO — that's managed exclusively through the granular `/users/me/restricted-categories` endpoints below.
- `TransactionEvaluateRequest`: `@NotNull @DecimalMin("0.0") amount`, `@Size(max=50) category`, `@Size(max=100) merchant`.

Validation failures are caught centrally (see [api-design.md](./api-design.md#error-format)) and never reach a rule or a repository — the rules in `rules/` can now assume their inputs are already within valid domain ranges, simplifying the rule implementations relative to V1 (which had defensive-or-missing checks scattered across services and repositories — see [v1/error-handling.md](../v1/error-handling.md#notable-gaps)).

## Secrets management

- JWT signing secret, DB credentials, and any other secret are supplied via environment variables (`JWT_SECRET`, `SPRING_DATASOURCE_PASSWORD`, etc.), never committed to `application.properties`/`application.yml` — this directly fixes V1's plaintext committed DB password (see [v1/design-decisions.md](../v1/design-decisions.md), item 12).
- Local dev uses a `.env` file (git-ignored) consumed by Docker Compose; CI uses GitHub Actions encrypted secrets; see [deployment-plan.md](./deployment-plan.md).
- `.env.example` is committed with placeholder values so a new contributor knows what to set.

## Rate limiting (`/auth/login`)

Implemented via `LoginRateLimiter` (in `security/`) — an in-memory, fixed-window brute-force guard, wired into `AuthController.login`: `checkAllowed(username)` runs before delegating to `AuthService.login`, and the caller records a failure/success afterward depending on whether an `AuthenticationException` was thrown. Exceeding the limit throws `TooManyLoginAttemptsException`, mapped to **HTTP 429** by `GlobalExceptionHandler`.

- **Keyed by username alone** (case-folded), not by IP: this stops credential stuffing against one account regardless of how many source IPs an attacker rotates through, and avoids the false-positive risk of IP-based limiting locking out an entire NAT/shared-office network after a few genuine typos.
- **Default**: 5 failed attempts per 15-minute fixed window, configurable via `app.security.login-rate-limit.max-attempts` / `app.security.login-rate-limit.window-minutes` (env-overridable: `LOGIN_RATE_LIMIT_MAX_ATTEMPTS` / `LOGIN_RATE_LIMIT_WINDOW_MINUTES`).
- **In-memory, single instance**: a plain `ConcurrentHashMap`, no Redis or other shared store. This is sufficient because Phase 6 settled on a single-backend-instance deployment shape (see [deployment-plan.md](./deployment-plan.md)) — a distributed store would only be needed if the backend were ever horizontally scaled, and isn't built here since there's no concrete driver for it yet.
- A successful login clears the counter for that username; the counter itself, not the JWT/refresh-token issuance, is what's rate-limited — `/auth/register` and `/auth/refresh` are unaffected.

## What's explicitly out of scope for V2

- Multi-factor authentication (documented as a future enhancement, not built now — no concrete driver for it at this stage).
- OAuth2/social login.
- Fraud/anomaly ML scoring (the rule engine remains deterministic and explainable by design — see [architecture.md](./architecture.md)).
- Per-IP rate limiting, or rate limiting on any endpoint other than `/auth/login` (see above for what *is* built).
- Automated admin-account provisioning (seed migration or CLI) — see the Authorization section above for the actual manual process.
