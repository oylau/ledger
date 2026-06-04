# Session 5 Summary — Multi-Operation Atomic Transactions

**Date:** 2026-06-04  
**Branch:** `feature/ledger-initial`

---

## Prompts

> **Prompt 1:** Update applyTransaction to now take into account each trxn can have multiple operations for withdrawal and deposit. We need to only apply all operations if the end of the transaction comply to the account type (i.e. if it's CURRENT, it's not below 0)

> **Prompt 2:** I need you to add some tests to cover both commit and rollback cases

> **Prompt 3:** Add an ADR and session summary as well

---

## Objectives

1. Extend `applyTransaction` to accept a list of operations (deposits and withdrawals) that are applied atomically — all committed or none committed — based on the net final balance satisfying the account type constraint.
2. Add tests covering the commit path (all operations persisted) and the rollback path (violation detected, nothing persisted, balance unchanged).
3. Document the decision in an ADR and record the session in an audit summary.

---

## What Was Changed

### Prompt 1 — Multi-Operation `applyTransaction`

#### `TransactionManagerService.java`

Two new `applyTransaction` overloads added:

```java
public List<TransactionResponse> applyTransaction(
        String accountNumber, List<BigDecimal> amounts, String currency)

public List<TransactionResponse> applyTransaction(
        String accountNumber, List<BigDecimal> amounts, String currency, Instant transactionTime)
```

The core logic in the new overloads:

1. Acquires the per-account `ReentrantLock` (same as the single-operation path — see ADR-0002).
2. Computes `netDelta = sum(amounts)`.
3. Evaluates `currentBalance + netDelta` against the account type constraint:
   - **CURRENT:** throws `InsufficientFundsException` if `newBalance < 0`.
   - **LOAN:** throws `CreditLimitExceededException` if `newBalance < lowerBound`; throws `LoanOverpaymentException` if `newBalance > 0`.
4. If the constraint is satisfied, persists each amount as its own `Transaction` record and returns one `TransactionResponse` per operation in input order.

The existing single-amount overloads were refactored to delegate to the multi-operation path via `List.of(amount)`, removing duplicated validation and persistence logic.

#### `LedgerOperationControllerTest.java`

Mockito stubs that used bare `any()` matchers were updated to `any(BigDecimal.class)` to resolve the overload ambiguity introduced by the new `List<BigDecimal>` parameter type.

---

### Prompt 2 — Commit and Rollback Tests

Eight new tests added to `TransactionServiceTest.java` under two sections:

#### Commit cases — all operations are persisted

| Test | What it verifies |
|---|---|
| `multiOp_allDeposits_allCommitted` | Two deposits both appear in the final balance |
| `multiOp_mixedOps_netPositive_allCommitted` | Deposit + withdrawal with a net positive all commit |
| `multiOp_netToExactlyZero_current_commits` | Net of exactly zero on CURRENT is accepted |
| `multiOp_loan_netWithinLimit_commits` | LOAN withdrawal + partial repayment within credit limit commits |

#### Rollback cases — constraint violated, nothing persisted

| Test | What it verifies |
|---|---|
| `multiOp_netBelowZero_current_nothingCommitted` | Net overdraft → `InsufficientFundsException`, balance unchanged |
| `multiOp_individualOpOk_butNetBelowZero_nothingCommitted` | Each op is individually safe but together overdraft → nothing committed |
| `multiOp_loan_netExceedsCreditLimit_nothingCommitted` | LOAN net withdrawal past credit limit → `CreditLimitExceededException`, balance unchanged |
| `multiOp_loan_netOverpayment_nothingCommitted` | LOAN net deposit above zero → `LoanOverpaymentException`, balance unchanged |

The rollback tests explicitly assert the balance after the thrown exception to confirm zero partial writes occurred.

---

### Prompt 3 — Documentation

- `docs/adr/0003-multi-operation-atomic-transactions.md` — ADR recording the decision to validate on net delta and persist each operation individually. Covers rationale, trade-offs, and the future risk of multi-account transactions.
- `docs/audit/Session5_Summary.md` — this file.

---

## Architecture Note

Constraint validation is performed on the **net delta** of the entire operation list before any write occurs. This means:

- Intermediate balance states during the transaction are not checked.
- The all-or-nothing guarantee is enforced at the service layer within the existing per-account lock, so concurrent single- and multi-operation transactions on the same account remain fully serialised.

See [ADR-0003](../adr/0003-multi-operation-atomic-transactions.md) for the full decision record.

---

## How to Run

```bash
# All tests (unit + BDD):
mvn test

# Service tests only:
mvn test -Dtest=TransactionServiceTest

# Controller tests only:
mvn test -Dtest=LedgerOperationControllerTest
```

---

## Test Count

| Test suite | Before session | After session |
|---|---|---|
| Unit / integration (JUnit) | 39 | **47** (+8) |
| BDD (Cucumber) | 25 | **25** (unchanged) |
| **Total** | **64** | **72** |

---

## Files Changed

### New Files
- `docs/adr/0003-multi-operation-atomic-transactions.md`
- `docs/audit/Session5_Summary.md`

### Modified Files
- `src/main/java/com/ledger/service/TransactionManagerService.java` — multi-operation overloads added; single-operation overloads delegate to them
- `src/test/java/com/ledger/service/TransactionServiceTest.java` — 8 new multi-operation tests
- `src/test/java/com/ledger/controller/LedgerOperationControllerTest.java` — Mockito matchers typed to resolve overload ambiguity
