# ADR-0003 — Multi-Operation Atomic Transactions

**Status:** Accepted  
**Date:** 2026-06-04  
**Author(s):** irenelau-gds

---

## Context

The original `applyTransaction` API accepted exactly one amount per call. Each call was independently validated against the account's balance constraint — CURRENT accounts must not go below zero; LOAN accounts must stay within `[lowerBound, 0]`.

A requirement emerged for transactions that contain **multiple operations** (a mix of deposits and withdrawals) which must be treated as a single atomic unit: either all operations are recorded or none are. The constraint check must be evaluated against the **final net balance** after all operations are applied, not against each operation individually.

Example: a CURRENT account with a balance of £100 should be able to accept `[−£80, +£50, −£60]` (net: −£90, final balance £10) even though the intermediate state after the first withdrawal would be £20, which is above zero throughout — but should reject `[+£50, −£200]` (net: −£150, final balance −£50) as a whole.

Two alternative approaches were considered:

1. **Net the amounts before calling the existing single-operation path.** Simple, but loses the individual operation records from the audit trail.
2. **Validate on the net delta; persist each operation individually.** Preserves a full per-operation audit trail while enforcing the all-or-nothing constraint on the final state.

---

## Assumptions

- [ ] All operations within a single multi-operation transaction share the same account, currency, and business timestamp. Cross-account or cross-currency atomic transactions are out of scope.
- [ ] Intermediate balance states during a multi-operation transaction are not subject to constraint checking. Only the final net balance matters.
- [ ] Each operation is still worth preserving as a distinct record in the transaction log (e.g. for downstream reconciliation or auditing).
- [ ] The application remains a single JVM. The existing per-account `ReentrantLock` (ADR-0002) is sufficient to serialise multi-operation transactions on the same account.

---

## Decision

We will add two new overloads to `TransactionManagerService.applyTransaction` that accept a `List<BigDecimal> amounts`:

```java
public List<TransactionResponse> applyTransaction(
        String accountNumber, List<BigDecimal> amounts, String currency)

public List<TransactionResponse> applyTransaction(
        String accountNumber, List<BigDecimal> amounts, String currency, Instant transactionTime)
```

The implementation:
1. Acquires the per-account lock (same as the single-operation path).
2. Computes `netDelta = sum(amounts)`.
3. Evaluates `currentBalance + netDelta` against the account type constraint. Throws the appropriate exception if the constraint is violated — no operations are persisted.
4. If the constraint is satisfied, persists each amount as its own `Transaction` record and returns one `TransactionResponse` per operation in input order.

The existing single-amount overloads delegate to the new multi-operation overloads via `List.of(amount)`, eliminating duplicated logic.

---

## Rationale

### Why validate on net delta, not per-operation?
The caller groups operations into a single transaction precisely because they are logically inseparable. Checking each operation individually could reject a transaction that is economically valid (e.g. a debit followed by an offsetting credit), or accept a sequence that is individually valid but leaves the account in a forbidden state.

### Why persist each operation as a separate record?
Collapsing the operations into a single netted record would lose the breakdown visible to auditors and downstream systems. Storing each operation individually preserves the full picture while the all-or-nothing guarantee is enforced at the validation step before any writes occur.

### Why delegate single-operation overloads to the multi-operation path?
It removes the duplication of the validation and persistence logic. The single-operation path had become a strict subset of the multi-operation path; the delegation makes this relationship explicit and ensures any future changes to the validation logic are applied in one place.

---

## Consequences

### Positive
- Callers can submit a group of related operations that succeed or fail together, without needing to implement rollback logic themselves.
- The audit trail retains individual operation granularity.
- The single-operation API is unchanged — all existing callers continue to work without modification.
- Validation logic exists in exactly one place.

### Negative / Trade-offs
- Introducing `List<BigDecimal>` as a second-parameter type alongside `BigDecimal` creates an overload pair that can be ambiguous in Mockito `any()` matchers. Test stubs must use typed matchers (`any(BigDecimal.class)`) to resolve the correct overload.
- The response type for the multi-operation path is `List<TransactionResponse>`, which is a different return type than the single-operation `TransactionResponse`. Callers must use the correct overload.

### Risks
- If a caller passes an empty list, `netDelta` is zero and the constraint check will pass vacuously, returning an empty response list. This is technically correct but may mask a caller bug. Consider adding a precondition guard if this becomes a concern.

---

## Notes / Follow-up

- If multi-operation transactions ever need to span **multiple accounts** (e.g. a transfer), the locking strategy in ADR-0002 must be revisited — acquiring two per-account locks introduces deadlock risk unless locks are always acquired in a canonical order (e.g. sorted by account number).
- Related ADRs: ADR-0001, ADR-0002
- Supersedes: —
- Superseded by: —
