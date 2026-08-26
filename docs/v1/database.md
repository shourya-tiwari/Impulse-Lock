# Database

Engine: MySQL. Access via Spring's `JdbcTemplate` — no ORM (no JPA/Hibernate), no entity annotations, no migration tool (Flyway/Liquibase not present). `spring.sql.init.mode=never` means Spring Boot will not auto-run any `schema.sql`/`data.sql` — schema setup is entirely manual.

## Connection configuration

From `src/main/resources/application.properties`:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/impulselock
spring.datasource.username=root
spring.datasource.password=password
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
```

Single environment/profile — no `application-dev.properties`, `application-prod.properties`, or environment-variable overrides defined in the codebase.

## Schema (as documented in `README.md`)

```sql
CREATE DATABASE impulselock;
USE impulselock;

CREATE TABLE users (
    user_id VARCHAR(50) PRIMARY KEY,
    daily_limit DOUBLE,
    night_spending_allowed BOOLEAN,
    sensitivity_level INT
);

CREATE TABLE transactions (
    transaction_id VARCHAR(50) PRIMARY KEY,
    user_id VARCHAR(50),
    amount DOUBLE,
    category VARCHAR(50),
    merchant VARCHAR(100),
    timestamp DATETIME
);
```

### Schema/code mismatch: `restricted_categories`

The README's DDL for `users` does **not** include a `restricted_categories` column, but:
- `UserRepository.getUserById` selects `restricted_categories` explicitly in its `SELECT` statement.
- `UserRepository.upsertUser` inserts into it explicitly in its `INSERT` statement.

If the column doesn't exist, `upsertUser`'s `INSERT` will fail outright (unhandled `DataAccessException`, surfaces as a generic 500 — see [error-handling.md](./error-handling.md)). `getUserById`'s row mapper wraps the read of that column in a `try/catch (Exception ignored)`, so a missing column there degrades gracefully rather than failing the whole query — but this only helps reads, not the upsert. **In practice, the column must exist for the app to function**, meaning the actual required DDL is:

```sql
ALTER TABLE users ADD COLUMN restricted_categories VARCHAR(500);
```

(or equivalent, added directly to the `CREATE TABLE users` statement above). This is a documentation gap in `README.md` rather than a deliberate optional-column design — the code was written assuming the column exists, and the fallback logic exists only for backward compatibility with a database created before the column was added, not to support running without it.

### Table: `users`

| Column | Type | Notes |
|---|---|---|
| `user_id` | `VARCHAR(50)` | Primary key; matches `Transaction.userId` / `UserProfile.userId`. |
| `daily_limit` | `DOUBLE` | Used by `HighAmountRule`. |
| `night_spending_allowed` | `BOOLEAN` | Used by `NightSpendingRule`. |
| `sensitivity_level` | `INT` | Used by `SensitivityLevelRule`; app-level convention is 1–10, not DB-enforced. |
| `restricted_categories` | `VARCHAR` (not in README DDL, but required — see above) | Comma-separated string; used by `CategoryRestrictionRule`. |

### Table: `transactions`

| Column | Type | Notes |
|---|---|---|
| `transaction_id` | `VARCHAR(50)` | Primary key; client-supplied or server-generated UUID. |
| `user_id` | `VARCHAR(50)` | No `FOREIGN KEY` constraint defined in the DDL — referential integrity to `users` is enforced only by application logic (`TransactionService` looks up the user before saving), not by the database schema. |
| `amount` | `DOUBLE` | |
| `category` | `VARCHAR(50)` | |
| `merchant` | `VARCHAR(100)` | |
| `timestamp` | `DATETIME` | Column name is backtick-escaped (`` `timestamp` ``) in every query since `TIMESTAMP`/`timestamp` is a MySQL reserved-ish keyword context. |

## Data access layer

### `UserRepository`
- `getUserById(String userId) -> Optional<UserProfile>` — `SELECT ... WHERE user_id = ?`, mapped via a `RowMapper` lambda; returns `Optional.empty()` if no row matches (`.stream().findFirst()` over the query result list).
- `upsertUser(UserProfile) -> UserProfile` — single `INSERT ... ON DUPLICATE KEY UPDATE` statement; `restrictedCategories` list is joined with `,` before insertion (empty string if null/empty). Validates `userProfile != null` and non-blank `userId` before executing, throwing `IllegalArgumentException` otherwise.
- No password/credential handling — `UserProfile` has no auth-related fields.

### `TransactionRepository`
- `saveTransaction(Transaction)` — validates non-null transaction and non-blank `transactionId`, converts `LocalDateTime` → `java.sql.Timestamp`, logs the attempt at `debug`, executes a parameterized `INSERT`. On `DataAccessException`, logs at `error` (including the full transaction's stack trace) and **re-throws** — the wrapping into `DatabaseOperationException` happens one layer up, in `TransactionService`.
- `getTransactionsByUserId(String userId) -> List<Transaction>` — fully implemented (`SELECT ... WHERE user_id = ? ORDER BY timestamp DESC`) but **never called from any controller** — dead code from an API-surface perspective in V1 (see [api-reference.md](./api-reference.md#endpoints-defined-in-code-but-not-exposed)).

## Notable absences

- No indexes beyond each table's primary key (e.g. no explicit index on `transactions.user_id`, which is the only column `getTransactionsByUserId` filters on).
- No foreign key from `transactions.user_id` to `users.user_id`.
- No soft-delete, audit columns (`created_at`/`updated_at`), or row versioning.
- No connection pool tuning in `application.properties` (relies on Spring Boot / HikariCP defaults).
