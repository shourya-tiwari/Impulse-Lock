# ImpulseLock V2 — Architecture

This document defines the target architecture for V2. It assumes the reader knows [V1](../v1/README.md); it calls out deltas explicitly rather than re-explaining what's unchanged.

## Goals driving the architecture

JWT authentication, Spring Security, role-based authorization, Spring Data JPA, an improved (normalized) database, structured logging, audit logging, request validation, comprehensive global exception handling, OpenAPI/Swagger docs, Docker packaging, GitHub Actions CI/CD, an improved dashboard, advanced transaction history, and comprehensive automated testing.

V2 also uses the migration as an opportunity to fix the behavioral gaps documented in [v1/design-decisions.md](../v1/design-decisions.md): the rule engine becomes DB-configurable instead of hardcoded, `HighAmountRule` becomes a true rolling-daily-total check, `FrequentTransactionRule` becomes a real velocity check backed by transaction history, risk scores are capped at 100, and `restricted_categories` becomes a proper normalized relationship instead of a comma-separated string with an undocumented required column.

## System overview

```
┌────────────────────┐     HTTPS/JSON (JWT bearer)     ┌───────────────────────────────────────────┐     JPA/Hibernate     ┌───────────┐
│   React frontend    │ ───────────────────────────────▶│           Spring Boot backend                │───────────────────────▶│   MySQL   │
│ (dashboard, auth,    │◀─────────────────────────────── │              (port 8080)                     │◀───────────────────────│ impulselock│
│  history, forms)     │                                 │  Security filter chain → Controllers →       │                        └───────────┘
└────────────────────┘                                 │  Services → Rule Engine → Repositories        │
                                                          │  Cross-cutting: audit, logging, validation,  │
                                                          │  global exception handling, OpenAPI           │
                                                          └───────────────────────────────────────────┘
```

Still a two-tier system (no message queue, no cache layer, no microservices) — V2 deepens the single Spring Boot service rather than decomposing it. This is a deliberate scope decision: splitting into microservices would add operational complexity (service discovery, distributed tracing, network failure modes) disproportionate to the project's actual size. Revisit only if a concrete driver emerges (e.g. a second consuming client, independent scaling needs).

## Package structure (backend)

```
com.impulselock.impulselock
├── config/            SecurityConfig, JpaAuditingConfig, OpenApiConfig, CorsConfig, RuleEngineConfig
├── security/           JwtService, JwtAuthenticationFilter, SecurityUserDetailsService, SecurityUser,
│                        RestAuthenticationEntryPoint, RestAccessDeniedHandler, RefreshTokenService
├── controller/          AuthController, UserController, TransactionController, DashboardController,
│                        AuditLogController, AdminUserController
├── service/              AuthService, UserService, TransactionService, DashboardService,
│                        RuleConfigService, AuditLogService
├── engine/               DecisionEngine (unchanged pattern, now weight/threshold-aware)
├── rules/                SpendingRule, AbstractSpendingRule, and concrete rules (now read
│                        DB-backed RuleConfig + real transaction history where needed)
├── entity/               JPA entities: User, Role, RestrictedCategory, Transaction, AuditLog,
│                        RefreshToken, RuleConfig
├── repository/            Spring Data JPA repositories (interfaces only, + JPA Specifications
│                        for transaction filtering)
├── dto/                  request/ and response/ DTOs — never expose entities directly over the API
├── mapper/                MapStruct mappers: entity ↔ DTO
├── validation/            custom Bean Validation annotations (e.g. @ValidSensitivityLevel)
├── audit/                 AOP aspect (or entity listener) that writes AuditLog rows
├── exception/             domain exceptions + GlobalExceptionHandler (expanded from V1)
└── logging/               correlation-ID filter, MDC setup
```

Key structural change from V1: **DTOs are introduced as a mandatory boundary.** V1 controllers accepted/returned domain model classes (`Transaction`, `UserProfile`, `Decision`) directly as request/response bodies. V2 controllers only ever see `*RequestDto`/`*ResponseDto` types; JPA entities never leave the service layer. This decouples the persistence model (which now includes JPA relationships, audit columns, security fields) from the wire format, and is what makes Swagger schemas, validation annotations, and password/security-field exclusion possible without leaking entity internals.

## Layering (unchanged shape, expanded content)

```
controller/  → thin; delegates to services; only DTOs in/out; @PreAuthorize for role checks
service/     → orchestration + business logic; transactional boundaries (@Transactional)
engine/      → DecisionEngine; pure evaluation logic given a rule list + resolved config
rules/       → SpendingRule strategy implementations, now DB-config-aware
repository/  → Spring Data JPA interfaces (no more hand-written SQL for CRUD; Specifications
               for dynamic transaction-history filtering)
entity/      → JPA-annotated persistence model
dto/         → API-facing shapes, validated with Bean Validation
mapper/      → entity <-> DTO translation (MapStruct, compile-time generated)
security/    → JWT issuance/validation, Spring Security integration
audit/       → audit trail writing, decoupled from business logic via AOP
exception/   → typed exceptions + centralized @RestControllerAdvice
```

`DecisionEngine` and the `SpendingRule` Strategy pattern are retained as-is architecturally (this was V1's strongest design decision — see [v1/design-decisions.md](../v1/design-decisions.md#deliberate-design-choices)) — V2 does not replace the pattern, it makes the rules' *inputs* richer (real transaction history, DB-backed thresholds) rather than changing the pattern itself.

## Request flow: evaluate a transaction (V2)

```
Client
  │  POST /api/v2/transactions/evaluate   Authorization: Bearer <access-jwt>
  ▼
JwtAuthenticationFilter                 → validates JWT, loads SecurityUser, sets SecurityContext
  ▼                                       401 if missing/invalid/expired token
TransactionController.evaluate           → @PreAuthorize("hasRole('USER')"); validates
  │                                          EvaluateTransactionRequestDto (Bean Validation)
  ▼                                       400 on validation failure (field-level errors)
TransactionService.evaluateAndSave
  │  1. resolve authenticated user (from SecurityContext, not a client-supplied userId —
  │     see security-design.md — a user can only transact as themselves; ADMIN can query others)
  │  2. load UserProfile (JPA) + effective RuleConfig set
  │  3. load recent transaction window for this user (for velocity/day-aggregate rules)
  │  4. DecisionEngine.evaluate(transaction, userProfile, rules, historyContext)
  │  5. persist Transaction (JPA)
  │  6. AuditLogService records "TRANSACTION_EVALUATED" (async, via AOP — see below)
  ▼
Mapper: Transaction/Decision entity → TransactionResponseDto
  ▼
200 OK JSON
```

## Cross-cutting concerns

### Security
Spring Security filter chain with a stateless JWT filter (no `HttpSession`), method-level `@PreAuthorize` for role checks, `BCryptPasswordEncoder` for credentials. Full design in [security-design.md](./security-design.md).

### Validation
Bean Validation (`jakarta.validation`) annotations on every request DTO (`@NotBlank`, `@Positive`, `@Min`/`@Max`, `@Email`, plus custom constraints where domain rules need it, e.g. sensitivity level 1–10). Validation failures are caught by the global exception handler and returned as structured field-level errors (not a single string message, unlike V1).

### Logging
SLF4J + Logback, structured (JSON encoder in the `docker`/`prod` profile, human-readable pattern in `dev`). A `CorrelationIdFilter` generates/propagates a request ID (`X-Request-Id` header in/out) stored in MDC, so every log line for a request can be correlated, including across the audit trail. Full design in [logging & audit section below] and referenced from [testing-strategy.md](./testing-strategy.md) for verifying it.

### Audit logging
Distinct from application logging: a persisted `AuditLog` table recording *who did what, to what, when* — user registration, login (success/failure), preference changes, transaction evaluation, and all admin actions. Implemented via a Spring AOP `@Around` aspect on service methods annotated `@Auditable(action = "...")`, keeping audit-writing out of business logic bodies. Audit writes are best-effort and must never fail or roll back the primary business transaction (see [database-design.md](./database-design.md#audit_log) for the isolation approach).

### Exception handling
`GlobalExceptionHandler` expands on V1's version (see [error-handling gaps in V1](../v1/error-handling.md#notable-gaps)) to add: `MethodArgumentNotValidException` → 400 with field errors, `AuthenticationException`/`BadCredentialsException` → 401, `AccessDeniedException` → 403, `ExpiredJwtException`/`JwtException` → 401, `DataIntegrityViolationException` → 409 (e.g. duplicate username/email), and a consistent catch-all. Every DB-touching service method is held to the same wrap-and-classify standard — the V1 asymmetry between `TransactionService` (wraps DB errors) and `UserRepository` (doesn't) is eliminated by moving all persistence error translation to one place. Full design in [api-design.md](./api-design.md#error-format) and rationale in [security-design.md](./security-design.md).

### API documentation
springdoc-openapi generates a live OpenAPI 3 spec + Swagger UI, secured behind a bearer-token scheme matching the real JWT auth, grouped by tag (Auth, Users, Transactions, Dashboard, Admin, Audit). Disabled (or admin-only) in the `prod` profile. Details in [api-design.md](./api-design.md).

### Persistence
Spring Data JPA replaces hand-written `JdbcTemplate` SQL. Entities use Hibernate's auditing support (`@CreatedDate`/`@LastModifiedDate` via `@EntityListeners(AuditingEntityListener.class)`) for `created_at`/`updated_at` on every table — closing the V1 gap noted in [v1/database.md](../v1/database.md#notable-absences). Dynamic transaction-history filtering (date range, category, decision type, amount range) uses JPA Specifications rather than hand-rolled SQL string-building. Full schema in [database-design.md](./database-design.md).

## What V2 deliberately does not change

- Still a monolith (see "System overview" above).
- Still MySQL (no move to a document store or a separate read-model DB) — normalization fixes the actual problems in V1's schema; a new datastore isn't warranted at this scale.
- Still no external message broker for audit/log events — synchronous-but-isolated (via a separate transaction/thread) is sufficient at current scale; revisit only if audit volume or latency becomes a measured problem.
- The Strategy-pattern rule engine shape is preserved, not rebuilt from scratch, per above.
