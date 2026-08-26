# Rule Engine

The rule engine is the core domain logic of ImpulseLock. It is a textbook **Strategy pattern**: a fixed evaluation algorithm (`DecisionEngine`) runs an arbitrary, injected list of interchangeable strategies (`SpendingRule` implementations) and aggregates their results.

## Core abstractions

### `SpendingRule` (interface, `rules/SpendingRule.java`)
```java
public interface SpendingRule {
    double evaluate(Transaction transaction, UserProfile userProfile); // risk score, 0–100
    String getExplanation();                                          // human-readable reason, if it fired
}
```

### `AbstractSpendingRule` (`rules/AbstractSpendingRule.java`)
Base class every concrete rule extends. Holds two constructor-supplied fields:
- `riskWeight` — the fixed score this rule contributes when it fires (rules in this codebase are **all-or-nothing**: they return either `0` or the full weight, never a graduated value).
- `explanation` — a static, fixed string describing what the rule checks (not parameterized with actual transaction values).

`getExplanation()` is implemented once in the base class and returns the fixed string; only `evaluate(...)` is left abstract for subclasses.

### `DecisionEngine` (`engine/DecisionEngine.java`)
A plain class (registered as a Spring bean via `RuleEngineConfig`, but with no dependencies of its own). Its single method:

```java
public Decision evaluate(Transaction transaction, UserProfile userProfile, List<SpendingRule> rules)
```

Algorithm:
1. Iterate every rule in the supplied list, in list order (which is Spring's bean-registration order from `RuleEngineConfig`, i.e. `HighAmountRule → NightSpendingRule → FrequentTransactionRule → CategoryRestrictionRule → SensitivityLevelRule`).
2. For each rule, call `evaluate(...)`. If the returned risk is `> 0`, add it to a running `totalRisk` and append `rule.getExplanation() + "; "` to a `StringBuilder`.
3. Set `decision.riskScore = totalRisk` and `decision.explanation` to the concatenated string (rules that didn't fire contribute nothing to the explanation — if no rule fires, `explanation` is an empty string, not `"No risk detected"` or similar).
4. Threshold `totalRisk` into a `DecisionType`:
   - `>= 80` → `BLOCK`
   - `>= 40` (and `< 80`) → `DELAY`
   - otherwise → `ALLOW`

These thresholds (`80`, `40`) are hardcoded literals inside `DecisionEngine.evaluate` — not configurable via `application.properties` or any config class.

## The five rules

| Rule | Risk weight | Fires when | Explanation string |
|---|---|---|---|
| `HighAmountRule` | 70.0 | `transaction.amount > userProfile.dailyLimit` | "Transaction exceeds daily limit" |
| `NightSpendingRule` | 40.0 | `!userProfile.nightSpendingAllowed` AND transaction hour is `< 6` or `>= 23` | "Spending attempted during restricted night hours" |
| `FrequentTransactionRule` | 30.0 | `transaction.amount > 1000` | "Multiple rapid transactions detected" |
| `CategoryRestrictionRule` | 25.0 | see below | "Restricted spending category used" |
| `SensitivityLevelRule` | 20.0 | `userProfile.sensitivityLevel >= 8` | "High user sensitivity level applied" |

### `HighAmountRule`
Simplest rule: single-transaction comparison against the user's `dailyLimit`. Despite the name "daily limit," this is **not actually a running daily total** — it compares the single transaction's amount directly against the limit. A user could make ten transactions just under the limit in one day with no cumulative check.

### `NightSpendingRule`
Reads `transaction.getTimestamp().getHour()` directly — **will throw a `NullPointerException` if `timestamp` is null**. In practice `TransactionService` always backfills `timestamp` with `LocalDateTime.now()` before the engine runs, so this path is not currently reachable via the API, but the rule itself has no null-guard.

Night window is hardcoded as hour `< 6` or `>= 23` (i.e. 11:00 PM–5:59 AM).

### `FrequentTransactionRule`
The name and explanation ("Multiple rapid transactions detected") describe frequency/velocity checking, but the implementation only checks `transaction.amount > 1000` — a single-transaction amount threshold, unrelated to transaction frequency or rate. There is no lookback at transaction history for this rule (nor does `TransactionRepository.getTransactionsByUserId` get called anywhere in the evaluation path). The class comment marks this explicitly: `// Simplified logic for Phase 1 demo`.

### `CategoryRestrictionRule`
Two-tier behavior:
- If the `UserProfile` has a non-empty `restrictedCategories` list, the transaction's `category` is checked case-insensitively against that list.
- If `restrictedCategories` is null/empty (e.g. the `restricted_categories` DB column doesn't exist yet, or the user never set any), it falls back to a hardcoded default: only `"LUXURY"` (case-insensitive) is treated as restricted.

This means user-configured restricted categories fully replace the default — a user who sets `restrictedCategories: ["gaming"]` no longer gets "luxury" restricted for free; the two lists are not merged.

### `SensitivityLevelRule`
Single threshold check: user's `sensitivityLevel >= 8` (scale is 1–10 per the frontend UI) adds a flat 20-point risk penalty to *every* transaction from that user, regardless of the transaction's own attributes. This is a blanket per-user risk adjustment rather than a modifier on other rules' outputs — a high-sensitivity user's transactions aren't scored more strictly by the *other* rules, they simply get +20 added on top.

## Adding a new rule

Because of Spring's collection-injection via `RuleEngineConfig` (see [architecture.md](./architecture.md#dependency-injection--bean-wiring)):
1. Create a new class extending `AbstractSpendingRule`, implementing `evaluate(...)`.
2. Add a `@Bean` method for it in `RuleEngineConfig`.

No changes are needed to `DecisionEngine`, `TransactionService`, or any controller — the new rule is automatically included in the `List<SpendingRule>` injected into `TransactionService`.

## Score aggregation caveat

Risk scores are **purely additive** with no cap — the `Decision.riskScore` returned to the client can exceed 100 if multiple high-weight rules fire simultaneously (e.g. `HighAmountRule` (70) + `NightSpendingRule` (40) = 110), even though individual rules are documented as returning "a risk score (0–100)" in the `SpendingRule` interface's Javadoc. The `BLOCK` threshold of 80 is a floor, not a cap, so scores of 110, 165 (all five rules), etc. are all reported as-is with `decisionType = BLOCK`.
