# API Reference

Base URL (local dev): `http://localhost:8080`. No API versioning prefix, no authentication/authorization on any endpoint. All request/response bodies are JSON.

## `POST /transaction/evaluate`

Evaluates a transaction against the owning user's profile and the full rule set, then persists the transaction.

**Controller**: `TransactionController.evaluateTransaction` → `TransactionService.evaluateAndSave`

### Request body (`Transaction`)

```json
{
  "userId": "U101",
  "transactionId": "optional-client-supplied-id",
  "amount": 1000,
  "category": "shopping",
  "merchant": "Amazon",
  "timestamp": "optional-ISO-8601-datetime"
}
```

| Field | Type | Required | Notes |
|---|---|---|---|
| `userId` | string | **yes** | Must be non-null/non-blank. Must reference an existing user (created via `POST /users`) or the request 404s. |
| `transactionId` | string | no | Auto-generated (`UUID.randomUUID()`) if omitted or blank. |
| `amount` | number | effectively yes | Defaults to `0.0` if omitted (primitive `double`, no null semantics) — a request with no `amount` is evaluated as a `$0` transaction rather than rejected. |
| `category` | string | no | Used by `CategoryRestrictionRule`; null/blank is treated as "no category," never restricted. |
| `merchant` | string | no | Stored but not used by any rule. |
| `timestamp` | ISO-8601 datetime string | no | Auto-set to server "now" if omitted. Used by `NightSpendingRule` for hour-of-day. |

### Response — `200 OK` (`Decision`)

```json
{
  "decisionType": "BLOCK",
  "riskScore": 120.0,
  "explanation": "Transaction exceeds daily limit; Spending attempted during restricted night hours; "
}
```

| Field | Type | Notes |
|---|---|---|
| `decisionType` | `"ALLOW"` \| `"DELAY"` \| `"BLOCK"` | See thresholds in [rule-engine.md](./rule-engine.md). |
| `riskScore` | number | Sum of all fired rules' weights. **Not capped at 100** — can exceed it if multiple rules fire. |
| `explanation` | string | Semicolon-and-space-joined concatenation of every fired rule's fixed explanation string. Empty string if no rule fired. |

### Error responses
See [error-handling.md](./error-handling.md) for the full mapping. Relevant to this endpoint:
- `400 Bad Request` — missing/blank `userId`, or a malformed request body.
- `404 Not Found` — `userId` doesn't correspond to a saved user profile.
- `500 Internal Server Error` — the transaction was evaluated successfully but the subsequent DB insert failed.

### Side effects
On success, the transaction (including its resolved `transactionId` and `timestamp`) is inserted into the `transactions` table — **even if the decision is `BLOCK`**. There is no separate "attempted but blocked" state; all evaluated transactions are recorded identically regardless of outcome.

---

## `POST /users`

Creates or updates (upserts) a user's behavioral preference profile.

**Controller**: `UserController.upsertUser` → `UserRepository.upsertUser` (no service layer)

### Request body (`UserProfile`)

```json
{
  "userId": "U101",
  "dailyLimit": 3000,
  "nightSpendingAllowed": false,
  "sensitivityLevel": 5,
  "restrictedCategories": ["luxury", "gaming"]
}
```

| Field | Type | Required | Notes |
|---|---|---|---|
| `userId` | string | **yes** | Non-null/non-blank; used as the upsert key (`PRIMARY KEY`). |
| `dailyLimit` | number | no | Defaults to `0.0` (primitive `double`) if omitted. |
| `nightSpendingAllowed` | boolean | no | Defaults to `false` (primitive `boolean`) if omitted. |
| `sensitivityLevel` | number | no | Defaults to `0` (primitive `int`) if omitted. Frontend UI restricts this to 1–10 but the API does not enforce any range. |
| `restrictedCategories` | string array | no | Stored as a comma-joined string in the DB (see [database.md](./database.md)); replaces (not merges with) any previous value on update. |

### Response — `200 OK` (`UserProfile`)

Echoes back exactly what was submitted (the repository returns the same object it was given, not a re-read from the database) — so client-side defaults applied above (e.g. `sensitivityLevel` defaulting to `0`) are reflected as-is in the response.

### Error responses
- `400 Bad Request` — missing/blank `userId`, or malformed body.
- `500 Internal Server Error` — any unhandled DB failure (note: `UserRepository.upsertUser` does **not** catch `DataAccessException` and wrap it as `DatabaseOperationException` the way `TransactionRepository.saveTransaction` does — a raw DB failure here falls through to the generic `Exception` handler, which returns a generic "Unexpected server error" message. See [error-handling.md](./error-handling.md)).

### Idempotency
This endpoint is a true upsert (`INSERT ... ON DUPLICATE KEY UPDATE` in a single SQL statement) — calling it twice with the same `userId` updates the existing row rather than erroring or creating a duplicate.

---

## Endpoints defined in code but not exposed

`TransactionRepository.getTransactionsByUserId(String userId)` exists and is fully implemented (fetches all transactions for a user, most recent first) but **no controller method calls it** — there is no `GET` endpoint to retrieve transaction history in V1.

---

## CORS

Only `http://localhost:3000` is an allowed origin (see `CorsConfig`, all endpoints, all common HTTP methods, all headers, 1-hour preflight cache). Any other origin calling these endpoints directly from a browser will be blocked by CORS.
