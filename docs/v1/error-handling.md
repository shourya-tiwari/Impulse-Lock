# Error Handling

All error handling is centralized in `GlobalExceptionHandler` (`@RestControllerAdvice`, `exception/` package) — controllers themselves contain no try/catch or status-code logic.

## Exception hierarchy

| Exception | Extends | Thrown by | Mapped HTTP status |
|---|---|---|---|
| `UserNotFoundException` | `RuntimeException` | `TransactionService.evaluateAndSave` when `userRepository.getUserById` returns empty | `404 Not Found` |
| `DatabaseOperationException` | `RuntimeException` | `TransactionService.evaluateAndSave` — wraps a `DataAccessException` caught around `transactionRepository.saveTransaction(...)` | `500 Internal Server Error` |
| `IllegalArgumentException` (built-in) | — | Manual validation in `TransactionService`, `UserRepository`, `TransactionRepository` (null/blank `userId`/`transactionId` checks) | `400 Bad Request` |
| `MethodArgumentNotValidException` (built-in) | — | Would be thrown by `@Valid`-annotated controller parameters — **but no controller method in this codebase uses `@Valid`**, and `spring-boot-starter-validation` is not a dependency, so this handler branch is currently unreachable dead code | `400 Bad Request` |
| Any other `Exception` | — | Catch-all (e.g. an unwrapped `DataAccessException` from `UserRepository.upsertUser`, which has no local try/catch) | `500 Internal Server Error`, generic message `"Unexpected server error"` |

## Response shape (`ErrorResponse` DTO)

Every error handler builds the same shape via `buildErrorResponse(status, message, path)`:

```json
{
  "timestamp": "2026-08-26T10:15:30",
  "status": 404,
  "error": "Not Found",
  "message": "User not found for userId: U999",
  "path": "/transaction/evaluate"
}
```

| Field | Source |
|---|---|
| `timestamp` | `LocalDateTime.now()` at the moment the handler runs (not when the request arrived) |
| `status` | `HttpStatus.value()` |
| `error` | `HttpStatus.getReasonPhrase()` (e.g. `"Not Found"`, `"Bad Request"`) |
| `message` | The specific exception's message — for the generic catch-all, always the fixed string `"Unexpected server error"` (the real exception message/type is not leaked to the client) |
| `path` | `HttpServletRequest.getRequestURI()` |

## Notable gaps

- **`UserRepository.upsertUser` does not catch `DataAccessException`.** Unlike `TransactionRepository.saveTransaction` (which is caught and rewrapped as `DatabaseOperationException` inside `TransactionService`), a DB failure during a `POST /users` upsert (e.g. missing `restricted_categories` column, connection failure, constraint violation) propagates as a raw `DataAccessException`, which falls into the generic `Exception` handler — the client sees a generic `"Unexpected server error"` 500 with no indication it was a database problem, whereas the equivalent failure on the transaction-evaluate path gets a more specific (though still generic-worded) `DatabaseOperationException` message: `"Failed to save transaction in database"`.
- **No request validation framework.** All input checks are hand-written `if (x == null || x.isBlank())` calls scattered across `TransactionService`, `UserRepository`, and `TransactionRepository`, rather than declarative `@Valid`/`@NotNull` annotations. Numeric fields (`amount`, `dailyLimit`, `sensitivityLevel`) have no bounds checking anywhere in the backend — a negative `dailyLimit` or `sensitivityLevel` of `-5` or `999` would be accepted.
- **No handler for `HttpMessageNotReadableException`** (malformed JSON body) — falls through to the generic catch-all, again yielding the generic `"Unexpected server error"` message rather than something like "malformed request body."
- **Stack traces are not returned to the client** for the generic handler (good from a security standpoint) but are also not logged anywhere in `GlobalExceptionHandler` itself — if the caller doesn't log elsewhere (only `TransactionRepository` logs, at `error` level, its own specific failure), an unexpected exception on, say, the `/users` path leaves no server-side trace beyond whatever the servlet container's default error logging captures.
