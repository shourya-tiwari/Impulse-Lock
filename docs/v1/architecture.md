# Architecture

## System overview

ImpulseLock is a two-tier system: a stateless-per-request Spring Boot REST API backed by MySQL, and a React single-page frontend that talks to it over HTTP/JSON.

```
┌─────────────────────┐        HTTP/JSON        ┌──────────────────────────────┐        JDBC        ┌───────────┐
│   React frontend     │ ───────────────────────▶│      Spring Boot backend      │────────────────────▶│   MySQL   │
│  (CRA, port 3000)     │◀─────────────────────── │        (port 8080)            │◀────────────────────│ impulselock│
└─────────────────────┘                          └──────────────────────────────┘                     └───────────┘
```

There is no authentication layer, no session/user-identity concept beyond a client-supplied `userId` string, and no message queue or caching layer — every request is evaluated synchronously and fully in-process.

## Backend layering

The backend follows a conventional layered Spring MVC structure, all under `com.impulselock.impulselock`:

```
controller/   → REST endpoints (thin; delegate immediately)
service/      → orchestration/business logic (TransactionService only)
engine/       → DecisionEngine — pure evaluation logic, not a Spring bean
rules/        → SpendingRule strategy implementations
repository/   → JdbcTemplate-based data access
model/        → plain data classes (Transaction, UserProfile, Decision, DecisionType)
dto/          → API-facing shapes not tied to a domain model (ErrorResponse)
config/       → Spring @Configuration classes (CORS, rule engine bean wiring)
exception/    → domain exceptions + @RestControllerAdvice global handler
```

Notably, **there is no service layer for user preferences** — `UserController` calls `UserRepository` directly. Only transactions go through a service (`TransactionService`), because transaction evaluation requires orchestration (fetch user → run rules → persist) whereas a user upsert is a single repository call.

## End-to-end request flow: evaluate a transaction

```
Client (React)
  │  POST /transaction/evaluate  { userId, amount, category, merchant }
  ▼
TransactionController.evaluateTransaction
  │  delegates immediately, no logic in the controller
  ▼
TransactionService.evaluateAndSave(transaction)
  │  1. validate transaction != null, userId non-blank        → IllegalArgumentException (400) if invalid
  │  2. userRepository.getUserById(userId)                    → UserNotFoundException (404) if absent
  │  3. assign transactionId (UUID) if missing
  │  4. assign timestamp (now) if missing
  │  5. decisionEngine.evaluate(transaction, userProfile, rules)
  │  6. transactionRepository.saveTransaction(transaction)     → DatabaseOperationException (500) on DB failure
  │     (saved regardless of decision outcome — even a BLOCK is persisted)
  ▼
DecisionEngine.evaluate
  │  for each SpendingRule in the injected list:
  │    risk = rule.evaluate(transaction, userProfile)
  │    if risk > 0: totalRisk += risk; explanation += rule.getExplanation()
  │  threshold totalRisk → DecisionType (ALLOW / DELAY / BLOCK)
  ▼
Decision { decisionType, riskScore, explanation } returned as 200 OK JSON
```

See [rule-engine.md](./rule-engine.md) for what each rule checks and [error-handling.md](./error-handling.md) for the exception → HTTP status mapping.

## End-to-end request flow: save user preferences

```
Client (React)
  │  POST /users  { userId, dailyLimit, nightSpendingAllowed, sensitivityLevel, restrictedCategories }
  ▼
UserController.upsertUser
  ▼
UserRepository.upsertUser
  │  INSERT ... ON DUPLICATE KEY UPDATE (single SQL upsert, no read-then-write race)
  ▼
200 OK, echoes back the UserProfile that was submitted
```

## Dependency injection / bean wiring

- `RuleEngineConfig` (`config/`) registers `DecisionEngine` and each `SpendingRule` implementation as individual `@Bean`s of static type `SpendingRule`.
- Spring collects all `SpendingRule` beans into a single `List<SpendingRule>` and injects it into `TransactionService`'s constructor — this is standard Spring collection-injection, not custom wiring code.
- Consequence: **adding a new rule requires no changes to `DecisionEngine` or `TransactionService`** — only a new class + a new `@Bean` method in `RuleEngineConfig`. This is the main extensibility point in the system (see [rule-engine.md](./rule-engine.md)).
- `DecisionEngine` itself is a plain class with no dependencies, made a bean purely so it can be injected into `TransactionService`.

## Cross-cutting concerns

- **CORS**: `CorsConfig` allows only `http://localhost:3000` (see [frontend.md](./frontend.md) for how the frontend avoids needing CORS in dev via CRA's proxy).
- **Error handling**: centralized in `GlobalExceptionHandler` (`@RestControllerAdvice`) — see [error-handling.md](./error-handling.md).
- **Logging**: SLF4J via `TransactionRepository` (debug on insert, error on DB failure). No structured/correlation-ID logging.
- **Persistence model**: no JPA — repositories hand-write SQL and map `ResultSet` rows via `RowMapper` lambdas.

## Notable startup behavior

`ImpulseLockApplication.main` does two things: starts the Spring context (`SpringApplication.run`), then — **every time the application starts, in every environment** — constructs a hardcoded demo `UserProfile`/`Transaction`, runs them through a freshly-constructed `DecisionEngine` (bypassing Spring/DI entirely, `new`-ing up the rules directly), and prints the resulting decision to stdout. This is leftover demo/debug code from early development; it has no effect on the running API (it doesn't touch the database or the Spring-managed beans) but does add console noise on every boot. See [design-decisions.md](./design-decisions.md).
