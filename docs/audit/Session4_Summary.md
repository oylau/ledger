# Session 4 Summary — Transaction History As-Of Queries & History Endpoint Rules

**Date:** 2026-05-31
**Branch:** `feature/ledger-initial`

---

## Prompts

> **Prompt 1:** Add Cucumber tests for bringing back transaction history different as of timestamps.

> **Prompt 2:** Change the behaviour of the getHistory endpoint in LedgerOperationController to do the followings:
> 1. the to timestamp is now optional, when it's not specified then there's anything up to now
> 2. the from timestamp needs to have a limit which cannot be older than 12 months
> Update the unit tests and the Cucumber tests accordingly.

> **Prompt 3:** Hmm I think the new validation logic on the from time should live in the service. Move that and update the tests accordingly and make sure they all pass.

---

## Objectives

1. Extend the Cucumber BDD suite with scenarios that exercise transaction history queries across different time windows (as-of timestamps).
2. Evolve the `GET /api/transactions/history/{accountNumber}` endpoint so that `to` is optional (defaults to now) and `from` is limited to a maximum lookback of 12 months.
3. Ensure the 12-month validation lives in the service layer (not the controller), following the principle that business rules belong with the business logic.

---

## What Was Changed

### Prompt 1 — Cucumber History Scenarios

#### `TransactionManagerService.java` — new timestamp overload

Added a second `applyTransaction` overload that accepts an explicit `Instant transactionTime`. The original no-timestamp overload now delegates to it with `Instant.now()`. This allows Cucumber step definitions to record transactions at deterministic past timestamps without altering production behaviour.

```java
public TransactionResponse applyTransaction(
        String accountNumber, BigDecimal amount, String currency, Instant transactionTime)
```

#### `src/test/resources/features/transaction_history.feature` — new feature file

Seven scenarios covering the full range of as-of query behaviour:

| Scenario | What it verifies |
|---|---|
| History window covers all transactions | Full-range query returns all records |
| Window ends before the second transaction | Upper-bound exclusion |
| Window starts after the first transaction | Lower-bound exclusion |
| Window exactly on a transaction timestamp | Inclusive bounds at both ends |
| Window contains no transactions | Empty result set |
| Withdrawals appear in history | Negative-amount entries are included |
| Transactions on different days separated by date range | Day-level isolation |

#### `LedgerSteps.java` — new step definitions (Prompt 1)

| Step phrase | Purpose |
|---|---|
| `I deposit {amount} {currency} into account {id} at {iso8601}` | Timestamped deposit |
| `I withdraw {amount} {currency} from account {id} at {iso8601}` | Timestamped withdrawal |
| `I request the transaction history of account {id} from {iso} to {iso}` | Calls `getTransactionHistory` |
| `the transaction history should contain {n} transaction(s)` | Size assertion |
| `the transaction history should include a deposit of {amount} {currency}` | Positive-amount assertion |
| `the transaction history should include a withdrawal of {amount} {currency}` | Negative-amount assertion |

---

### Prompt 2 — Optional `to` and 12-Month `from` Limit

#### `LedgerOperationController.java`

- `to` parameter changed from `required = true` to `required = false`.
- Controller defaults `to` to `Instant.now()` when omitted before forwarding to the service.
- The 12-month validation was initially written here but moved to the service in Prompt 3 (see below).

#### `LedgerOperationControllerTest.java` — updated and extended

| Test | What it covers |
|---|---|
| `getHistory_withExplicitTo_returns200` | Renamed from `getHistory_returns200`; behaviour unchanged |
| `getHistory_omittingTo_defaultsToNow` | No `to` param → 200 OK |
| `getHistory_serviceRejectsFromTooOld_returns400` | Mock throws `IllegalArgumentException` → controller maps to 400 |
| `getHistory_fromExactly12MonthsBack_returns200` | Boundary: 365 days is accepted |

#### `transaction_history.feature` — three new scenarios added

| Scenario | What it verifies |
|---|---|
| Omitting `to` returns transactions up to now | Default-to-now behaviour |
| `from` older than 12 months before `to` is rejected | Lookback limit enforcement |
| `from` exactly 365 days before `to` is accepted | Boundary: exactly 365 days is allowed |

#### `LedgerSteps.java` — new step definitions (Prompt 2)

| Step phrase | Purpose |
|---|---|
| `I request the transaction history of account {id} from {iso}` | No `to`; passes `Instant.now()` |
| `I try to request the transaction history of account {id} from {iso} to {iso}` | Captures `IllegalArgumentException` |
| `the history request should be rejected with {string}` | Asserts message on captured exception |

---

### Prompt 3 — Move Validation to Service

#### `AccountDataFetchService.java`

The 12-month guard was moved from the controller into `getTransactionHistory`:

```java
Instant oldestAllowed = to.minus(365, ChronoUnit.DAYS);
if (from.isBefore(oldestAllowed)) {
    throw new IllegalArgumentException(
        "'from' must not be older than 12 months (earliest allowed: " + oldestAllowed + ")");
}
```

Placing the rule here means it is enforced regardless of how the service is called — via the REST controller, directly from tests, or by any future caller.

#### `TransactionServiceTest.java` — two new service-level tests

| Test | What it covers |
|---|---|
| `getTransactionHistory_fromOlderThan12Months_throws` | 366 days → `IllegalArgumentException` with expected message |
| `getTransactionHistory_fromExactly365DaysBack_succeeds` | 365 days → no exception, empty list returned |

#### `LedgerOperationControllerTest.java` — test renamed for clarity

`getHistory_fromOlderThan12Months_returns400` was renamed to `getHistory_serviceRejectsFromTooOld_returns400` to make clear that the controller test is verifying HTTP mapping behaviour (service throws → controller returns 400), not re-testing the business rule itself.

---

## Architecture Decision — Service vs Controller Validation

The 12-month limit is a **business rule** ("we only retain/surface 12 months of history"), not an HTTP API constraint. Business rules belong in the service layer so they are enforced consistently from all callers and remain testable at the service level without going through HTTP.

The controller's only responsibility for this endpoint is:

1. Parse and bind request parameters.
2. Default `to` to `Instant.now()` when omitted.
3. Propagate any `IllegalArgumentException` from the service as an HTTP 400 (handled by `GlobalExceptionHandler`).

This matches the layering used for all other business rules in the project (currency mismatch, insufficient funds, credit limit, etc.).

---

## How to Run

```bash
# All tests (unit + BDD):
mvn test

# BDD suite only:
mvn test -Dtest=CucumberRunnerTest

# Service tests only:
mvn test -Dtest=TransactionServiceTest

# Controller tests only:
mvn test -Dtest=LedgerOperationControllerTest
```

---

## Test Count

| Test suite | Before session | After session |
|---|---|---|
| Unit / integration (JUnit) | 34 | **39** (+5) |
| BDD (Cucumber) | 15 | **25** (+10) |
| **Total** | **49** | **64** |

---

## Files Changed

### New Files
- `src/test/resources/features/transaction_history.feature`
- `docs/audit/Session4_Summary.md`

### Modified Files
- `src/main/java/com/ledger/service/TransactionManagerService.java` — timestamp overload added
- `src/main/java/com/ledger/service/AccountDataFetchService.java` — 12-month guard added
- `src/main/java/com/ledger/controller/LedgerOperationController.java` — `to` made optional, defaults to now
- `src/test/java/com/ledger/bdd/steps/LedgerSteps.java` — 5 new step definitions, `lastHistory` field, reset in `@Before`
- `src/test/java/com/ledger/controller/LedgerOperationControllerTest.java` — 3 new tests, 1 renamed
- `src/test/java/com/ledger/service/TransactionServiceTest.java` — 2 new service-level validation tests
