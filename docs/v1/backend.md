# Backend

Package root: `com.impulselock.impulselock`. Spring Boot 4.0.2, Java 17, built with Maven (`pom.xml`).

## Dependencies (`pom.xml`)

| Dependency | Purpose |
|---|---|
| `spring-boot-starter-web` | REST controllers, embedded servlet container |
| `spring-boot-starter-jdbc` | `JdbcTemplate` + `DataSource` management |
| `mysql-connector-j` (runtime) | MySQL JDBC driver |
| `spring-boot-starter-test` (test) | JUnit 5, Mockito, Spring Test |

No Spring Security, no Spring Data JPA, no validation starter (`spring-boot-starter-validation` is not present — see [design-decisions.md](./design-decisions.md) regarding `@Valid`/`MethodArgumentNotValidException`).

## Application entry point

`ImpulseLockApplication` (`src/main/java/.../ImpulseLockApplication.java`) is the `@SpringBootApplication` class. Beyond `SpringApplication.run(...)`, its `main` method also runs a hardcoded console demo (constructs a sample user + transaction, runs them through a manually-`new`-ed `DecisionEngine`, prints the result) on every startup. This bypasses the Spring container and has no effect on the HTTP API — it's leftover scaffolding from early development. See [architecture.md](./architecture.md#notable-startup-behavior).

## Controllers

### `TransactionController` (`/transaction`)
- `POST /transaction/evaluate` — accepts a `Transaction` JSON body, delegates to `TransactionService.evaluateAndSave`, returns the resulting `Decision` as `200 OK`.
- Single dependency: `TransactionService` (constructor injection).
- No input validation annotations (`@Valid`) on the request body — validation happens imperatively inside the service layer instead.

### `UserController` (`/users`)
- `POST /users` — accepts a `UserProfile` JSON body, calls `UserRepository.upsertUser` directly (no service layer), returns the saved profile as `200 OK`.
- This is the only place in the codebase where a controller talks directly to a repository, skipping the service layer — appropriate here since an upsert has no orchestration logic beyond the SQL itself.

Both controllers are intentionally thin: no try/catch, no status-code logic — all of that is handled by `GlobalExceptionHandler` (see [error-handling.md](./error-handling.md)).

## Service layer

### `TransactionService`
The only service class in the codebase. Constructor-injects `UserRepository`, `TransactionRepository`, `DecisionEngine`, and `List<SpendingRule>`.

`evaluateAndSave(Transaction)` responsibilities, in order:
1. Null/blank-`userId` validation → throws `IllegalArgumentException` (mapped to 400).
2. Look up the user's profile → throws `UserNotFoundException` (mapped to 404) if not found.
3. Backfill `transactionId` with a random `UUID` if the client didn't supply one.
4. Backfill `timestamp` with `LocalDateTime.now()` if the client didn't supply one.
5. Run the decision engine over all injected rules.
6. Persist the transaction — wraps `DataAccessException` in `DatabaseOperationException` (mapped to 500).
7. Return the `Decision` — note the transaction is saved **after** the decision is computed, and is saved **regardless of the decision** (a `BLOCK`ed transaction is still written to the `transactions` table).

## Configuration classes

### `RuleEngineConfig`
Registers `DecisionEngine` and every `SpendingRule` implementation (`HighAmountRule`, `NightSpendingRule`, `FrequentTransactionRule`, `CategoryRestrictionRule`, `SensitivityLevelRule`) as individual `@Bean`s, all of static return type `SpendingRule` (except `DecisionEngine`). This is what lets Spring auto-collect them into a single injectable `List<SpendingRule>`. See [rule-engine.md](./rule-engine.md).

### `CorsConfig`
Implements `WebMvcConfigurer`, allows `http://localhost:3000` on all paths (`/**`), all common HTTP methods, all headers, with a 1-hour (`3600`s) preflight cache. Hardcoded — no environment-based configuration for other origins (e.g. a deployed frontend URL).

## Configuration file (`application.properties`)

```properties
spring.application.name=ImpulseLock

spring.datasource.url=jdbc:mysql://localhost:3306/impulselock
spring.datasource.username=root
spring.datasource.password=password
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

spring.sql.init.mode=never
```

- Datasource credentials are plaintext in a committed file (`root`/`password`) — fine for local dev, not appropriate as-is for any shared/deployed environment.
- `spring.sql.init.mode=never` means Spring will **not** run any `schema.sql`/`data.sql` on startup — the schema must be created manually (see [database.md](./database.md)).

## Testing

`src/test/java/.../ImpulseLockApplicationTests.java` contains a single `@SpringBootTest` context-load smoke test (`contextLoads()`, empty body). There are no unit tests for rules, the decision engine, services, or repositories in the current codebase.
