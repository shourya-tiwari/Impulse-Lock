# ImpulseLock V2 — Database Design

Engine: MySQL, accessed via Spring Data JPA/Hibernate (replacing V1's hand-written `JdbcTemplate`). Schema is managed by Flyway migrations under `src/main/resources/db/migration/`: `V1__init_schema.sql` (full schema), `V2__seed_roles_and_rule_configs.sql` (seeds `ROLE_USER`/`ROLE_ADMIN` and `rule_configs` with V1-equivalent weights), `V3__add_decision_thresholds.sql` (adds the `decision_thresholds` table, seeded 80/40), `V4__tighten_transaction_decision_columns.sql` (backfills then tightens `transactions.decision_type`/`risk_score`/`explanation`/`triggered_rules` to `NOT NULL`, and moves `decision_type` from `ENUM` to `VARCHAR(10)` + `CHECK` — see the `transactions` table below), `V5__update_frequent_transaction_rule_params.sql` (real velocity params for `FREQUENT_TRANSACTION`). `spring.jpa.hibernate.ddl-auto` is set to `validate`, never `update`/`create`, so the schema is only ever changed by a reviewed migration file, never silently by Hibernate. This directly replaces V1's `spring.sql.init.mode=never` (manual, undocumented setup — see [v1/database.md](../v1/database.md#schema-mismatch-restricted_categories)) with a versioned, reproducible schema history.

## Entity-relationship overview

```
users ──1───────*── refresh_tokens
  │ 1
  │
  *
user_roles ──*───1── roles

users ──1───────*── restricted_categories
users ──1───────*── transactions
users ──1───────*── audit_log  (actor_user_id, nullable for system-initiated entries)
rule_configs (global only - no per-user override was built)
decision_thresholds (global, single-row - no per-user override, no FK to any other table)
```

## Tables

### `users`
| Column | Type | Notes |
|---|---|---|
| `id` | `BIGINT` PK, auto-increment | Replaces V1's client-chosen `user_id VARCHAR` as primary key — internal IDs are never client-supplied. |
| `username` | `VARCHAR(50)` UNIQUE NOT NULL | Login identifier. |
| `email` | `VARCHAR(255)` UNIQUE NOT NULL | |
| `password_hash` | `VARCHAR(255)` NOT NULL | BCrypt hash; never the plaintext password. |
| `daily_limit` | `DECIMAL(12,2)` NOT NULL DEFAULT 0 | `DOUBLE` → `DECIMAL` fixes float-rounding risk on monetary values (V1 used `DOUBLE` throughout). |
| `night_spending_allowed` | `BOOLEAN` NOT NULL DEFAULT false | |
| `sensitivity_level` | `TINYINT` NOT NULL DEFAULT 5 | `CHECK (sensitivity_level BETWEEN 1 AND 10)` — V1 had no DB or API-level bound (see [v1/design-decisions.md](../v1/design-decisions.md), item 8). |
| `enabled` | `BOOLEAN` NOT NULL DEFAULT true | Account disable switch, for admins — didn't exist in V1. |
| `created_at` | `DATETIME` NOT NULL | Set by Hibernate auditing (`@CreatedDate`). |
| `updated_at` | `DATETIME` NOT NULL | Set by Hibernate auditing (`@LastModifiedDate`). |

`user_id` as a free-text client-chosen string (V1) is gone. External API responses expose `id`, and `username` is the human-facing identifier used for login.

### `roles`
| Column | Type | Notes |
|---|---|---|
| `id` | `BIGINT` PK | |
| `name` | `VARCHAR(30)` UNIQUE NOT NULL | Seed values: `ROLE_USER`, `ROLE_ADMIN`. |

### `user_roles` (join table)
| Column | Type | Notes |
|---|---|---|
| `user_id` | `BIGINT` FK → `users.id` | Composite PK `(user_id, role_id)`. |
| `role_id` | `BIGINT` FK → `roles.id` | |

Many-to-many, even though V1 has no role concept at all — designed for future extensibility (e.g. an `AUDITOR` read-only role) without a schema change; V2 ships with every user assigned exactly `ROLE_USER` at registration, and `ROLE_ADMIN` assigned manually/via a seed script (no self-service admin signup).

### `restricted_categories`
| Column | Type | Notes |
|---|---|---|
| `id` | `BIGINT` PK | |
| `user_id` | `BIGINT` FK → `users.id`, NOT NULL, ON DELETE CASCADE | |
| `category` | `VARCHAR(50)` NOT NULL | Stored as provided (trimmed, not case-normalized) — `CategoryRestrictionRule` and `UserService.addRestrictedCategory`'s duplicate check both compare via `equalsIgnoreCase` rather than relying on a stored canonical case. |

Unique constraint `(user_id, category)`. This replaces V1's comma-separated `restricted_categories` string column (which wasn't even in the documented DDL — see [v1/database.md](../v1/database.md#schema-code-mismatch-restricted_categories)) with a normalized one-to-many relationship: no string parsing, no silent truncation at a `VARCHAR` length limit, and categories can be queried/indexed directly.

**Behavioral fix**: V1's `CategoryRestrictionRule` fell back to a hardcoded `"LUXURY"` default whenever a user had zero restricted categories, and a user-supplied list fully *replaced* that default rather than adding to it. In V2, the "LUXURY default" becomes a literal seeded row per new user at registration (an explicit, visible, editable/deletable `restricted_categories` row) rather than an implicit code fallback — removing the hidden special-case from the rule and making default behavior inspectable/auditable like any other data.

### `transactions`
| Column | Type | Notes |
|---|---|---|
| `id` | `BIGINT` PK, auto-increment | Replaces V1's client-suppliable `transaction_id VARCHAR` as primary key. |
| `public_id` | `CHAR(36)` UNIQUE NOT NULL | UUID, server-generated, this is what's exposed over the API (avoids exposing sequential internal IDs — a minor but standard hardening step now that auth exists). |
| `user_id` | `BIGINT` FK → `users.id` NOT NULL | **Real foreign key** — V1 had no FK constraint at all on `transactions.user_id` (see [v1/database.md](../v1/database.md#notable-absences)). |
| `amount` | `DECIMAL(12,2)` NOT NULL, `CHECK (amount >= 0)` | |
| `category` | `VARCHAR(50)` NULL | |
| `merchant` | `VARCHAR(100)` NULL | |
| `occurred_at` | `DATETIME(3)` NOT NULL | Renamed from `timestamp` (reserved-word friction in V1 required backtick-escaping everywhere — see [v1/database.md](../v1/database.md)); millisecond precision to support tie-breaking in "rapid succession" velocity checks. |
| `decision_type` | `VARCHAR(10)` NOT NULL, `CHECK (decision_type IN ('ALLOW','DELAY','BLOCK'))` | Persisted (V1 computed but never stored the decision on the transaction row itself — it returned it to the client and separately wrote only the transaction fields). Storing it enables real transaction-history filtering by decision outcome. Started as a MySQL `ENUM` in `V1__init_schema.sql` (and nullable, since the write path didn't exist yet); `V4` backfilled existing rows and switched to `VARCHAR` + `CHECK` — Hibernate's `@Enumerated(EnumType.STRING)` mapping validates more reliably under `ddl-auto=validate` against a plain `VARCHAR` than against a vendor-specific `ENUM` type. |
| `risk_score` | `DECIMAL(5,2)` NOT NULL, `CHECK (risk_score BETWEEN 0 AND 100)` | Capped at 100 at write time (see engine change in [architecture.md](./architecture.md)) — fixes V1's uncapped/unbounded additive score (see [v1/rule-engine.md](../v1/rule-engine.md#score-aggregation-caveat)). |
| `explanation` | `TEXT` NOT NULL | Same semantic as V1, but see structured alternative below. |
| `triggered_rules` | `JSON` NOT NULL | New: a structured array of `{ruleCode, weight, message}` for every rule that fired — powers dashboard breakdowns ("which rules fire most often") without parsing the `explanation` string. `explanation` is kept for human-readable display/back-compat; `triggered_rules` is the machine-readable source of truth. |
| `created_at` | `DATETIME` NOT NULL | Hibernate auditing. |

Indexes: `(user_id, occurred_at DESC)` — the single most important index, since both transaction history and the daily-limit/velocity rules query "this user's recent transactions" constantly; `(user_id, decision_type)` for dashboard aggregation; `(user_id, category)` for category-based filtering/aggregation.

### `rule_configs`
| Column | Type | Notes |
|---|---|---|
| `id` | `BIGINT` PK | |
| `rule_code` | `VARCHAR(50)` UNIQUE NOT NULL | e.g. `HIGH_AMOUNT`, `NIGHT_SPENDING`, `FREQUENT_TRANSACTION`, `CATEGORY_RESTRICTION`, `SENSITIVITY_LEVEL`. |
| `weight` | `DECIMAL(5,2)` NOT NULL | Replaces the hardcoded `riskWeight` constructor literals in V1's `AbstractSpendingRule` subclasses. |
| `enabled` | `BOOLEAN` NOT NULL DEFAULT true | Lets an admin disable a rule without a deployment. |
| `params` | `JSON` NULL | Rule-specific tunables, e.g. `{"nightStartHour": 23, "nightEndHour": 6}` for `NIGHT_SPENDING`, `{"velocityWindowMinutes": 10, "velocityCountThreshold": 3}` for the real `FREQUENT_TRANSACTION` rule, `{"sensitivityThreshold": 8}` for `SENSITIVITY_LEVEL`. |
| `updated_at` | `DATETIME` | |

This directly resolves the "hardcoded thresholds, not configurable" limitation called out in both [v1/architecture.md](../v1/architecture.md#dependency-injection--bean-wiring) and [v1/rule-engine.md](../v1/rule-engine.md) for rule weights; see `decision_thresholds` below for the BLOCK/DELAY thresholds themselves.

### `decision_thresholds`
| Column | Type | Notes |
|---|---|---|
| `id` | `BIGINT` PK, auto-increment | |
| `block_threshold` | `DECIMAL(5,2)` NOT NULL | |
| `delay_threshold` | `DECIMAL(5,2)` NOT NULL, `CHECK (block_threshold > delay_threshold)` | |
| `updated_at` | `DATETIME(3)` NOT NULL | |

A single-row table (added in `V3__add_decision_thresholds.sql`) replacing `DecisionEngine`'s hardcoded `80`/`40` `BLOCK`/`DELAY` literals from V1 — seeded with those exact values so V2's default behavior matches V1 on day one; `RuleContextFactory` reads the row fresh on every evaluation (`decisionThresholdsRepository.findTopByOrderByIdAsc()`), so an admin change takes effect on the very next transaction, no deploy needed. See [v1/rule-engine.md](../v1/rule-engine.md) for the V1 baseline being replaced.

### `refresh_tokens`
| Column | Type | Notes |
|---|---|---|
| `id` | `BIGINT` PK | |
| `user_id` | `BIGINT` FK → `users.id`, ON DELETE CASCADE | |
| `token_hash` | `VARCHAR(255)` UNIQUE NOT NULL | SHA-256 hash of the refresh token — the raw token is never stored, only its hash (mirrors password-hash practice; see [security-design.md](./security-design.md)). |
| `expires_at` | `DATETIME` NOT NULL | |
| `revoked_at` | `DATETIME` NULL | Set on logout / rotation / admin-forced revocation. |
| `created_at` | `DATETIME` NOT NULL | |

Supports refresh-token rotation and server-side revocation (logout, "log out of all devices," admin-disables-account) — see [security-design.md](./security-design.md) for the full token lifecycle.

### `audit_log`
| Column | Type | Notes |
|---|---|---|
| `id` | `BIGINT` PK | |
| `actor_user_id` | `BIGINT` FK → `users.id`, NULL | Null for unauthenticated events (e.g. a failed login attempt against a username that doesn't map to a user). |
| `action` | `VARCHAR(60)` NOT NULL | e.g. `USER_REGISTERED`, `LOGIN_SUCCESS`, `LOGIN_FAILURE`, `PREFERENCES_UPDATED`, `TRANSACTION_EVALUATED`, `ADMIN_RULE_CONFIG_CHANGED`, `ADMIN_USER_DISABLED`. |
| `entity_type` | `VARCHAR(50)` NULL | e.g. `TRANSACTION`, `USER`, `RULE_CONFIG`. |
| `entity_id` | `VARCHAR(50)` NULL | Stored as string since it may reference a `public_id` (UUID) or a numeric ID depending on entity type. |
| `metadata` | `JSON` NULL | Structured detail specific to the action (e.g. `{"before": {...}, "after": {...}}` for a preferences update). Never stores raw passwords/tokens. |
| `ip_address` | `VARCHAR(45)` NULL | IPv4/IPv6. |
| `correlation_id` | `CHAR(36)` NULL | Ties an audit row back to the request's log lines via the correlation-ID filter (see [architecture.md](./architecture.md#logging)). |
| `created_at` | `DATETIME(3)` NOT NULL | |

Indexes: `(actor_user_id, created_at DESC)`, `(action, created_at DESC)`. This table is intentionally append-only — no update/delete API is ever exposed for it, including to admins (an audit trail that can be edited isn't one). Retention/archival policy is an operational decision left to [deployment-plan.md](./deployment-plan.md), not encoded in the schema.

## Design principles applied across the schema

1. **Every table gets `created_at`** (and `updated_at` where the row is mutable) via Hibernate auditing — this alone closes a V1 gap ([v1/database.md](../v1/database.md#notable-absences): "no audit columns").
2. **Every foreign key is a real `FOREIGN KEY` constraint** — V1's `transactions.user_id` had none.
3. **Monetary values use `DECIMAL`, never `DOUBLE`/`FLOAT`** — V1 used `DOUBLE` for `amount`/`daily_limit`, which risks floating-point rounding artifacts on money.
4. **No comma-separated "lists in a string" columns** — `restricted_categories` becomes a real child table.
5. **Server-generated surrogate keys** (`BIGINT AUTO_INCREMENT`) for all internal PKs; externally-exposed identifiers are separate UUID (`public_id`) columns, not the internal PK — avoids exposing sequential IDs and avoids letting a client dictate a primary key (V1 let the client supply `transaction_id`/`user_id` directly as PK values).
6. **Schema changes only via Flyway migrations**, never `ddl-auto=update` — makes the schema reproducible across dev/CI/prod and gives V2 a real migration history (a prerequisite for the Docker/CI work in [deployment-plan.md](./deployment-plan.md)).

## Migration from V1 data (if preserving existing local data)

For anyone with existing V1 data to carry forward: a one-time migration script (not part of Flyway's normal versioned migrations, run manually) would need to: (1) create a `users` row per legacy `user_id`, generating a placeholder password hash and forcing a password reset on first V2 login; (2) split the legacy comma-separated `restricted_categories` string into rows in the new child table; (3) map legacy `transaction_id` values into the new `transactions.public_id` column, letting `id` auto-generate; (4) backfill `decision_type`/`risk_score` only if they were captured historically (V1 did not persist the decision on the transaction row — see the `transactions.decision_type` note above — so this data may not exist for old rows and would need to be left null or re-derived where possible). This is a one-off operational task, not a recurring migration, and is scoped out of the V2 build itself unless the user has real V1 data they need carried over.
