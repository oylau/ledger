# Session 2 Summary — Refactoring, Formatting & LOAN Account Rules

**Date:** 2026-05-30  
**Branch:** `feature/ledger-initial`

---

## Session Prompts

### Prompt 1 — Records, Service Split, Controller Renames, Spotless

> Please do the followings and make sure you have tests accordingly.
> 1. Use Java records for the DTOs and the other POJOs in the model directory please? They need to be immutable.
> 2. Refactor the TransactionService based on these responsibilities:
> 3. TransactionManagerService to only manage write operations that deal with transactions.
> 3.1. AccountDataFetchService for read only operations
> 3.2. AccountManagerService for write operations that deal with accounts
> 4. Rename the TransactionController to LedgerOperationController, and TestController to DemoSetupController.
> 5. Add spotless maven plugin with 4 whitespace formatting for java classes. Also run spotless apply to reformat the files.
> 6. Update the README and create a new Session2_Summary.md file to log what's done in this session.

### Prompt 2 — Remove External Formatter Config

> Can you remove the usage of the formatter.xml files in the project? Use inline configuration in the pom file if possible please?

### Prompt 3 — Checkstyle Explanation

> What does the checkstyle.xml file do?

### Prompt 4 — AccountType Enum Changes

> In AccountType, remove INVESTMENT. Replace CHECKING with CURRENT and then replace SAVING with LOAN.

### Prompt 5 — LOAN Balance Rules

> I need some enhancements. Make sure you have tests.
> * Now that we have an account type called LOAN for borrowing money, need to ensure when each transaction happens the balance doesn't go below the agreed lower bound and above 0. Throw new exceptions accordingly.
> * Keep the insufficient fund check for the CURRENT account type.

### Prompt 6 — Documentation Update

> Please update README and Session2_Summary thank you.

### Prompt 7 — Add Prompts to Session Summary

> Can you add the prompts in this session to doc/Session2_Summary.md, use Session1_Summary.md as example.

### Prompt 8 — Per-Account Locking

> Is it possible to update the in memory data store locking to be per account base please?

### Prompt 9 — Update Session Summary

> Update Session2 summary with the last prompt please.

---

## Objective

Refactor and harden the ledger application built in Session 1: convert model classes to immutable Java records, decompose the monolithic `TransactionService` into focused services, rename controllers to better reflect their purpose, add automated code formatting via Spotless, introduce LOAN account semantics with credit-limit enforcement, and keep all documentation current throughout.

---

## Changes Made

### 1. Java Records for Model Classes

Converted `Account` and `Transaction` from plain classes to Java records, making them structurally immutable. All five DTO classes (`CreateAccountRequest`, `TransactionRequest`, `TransactionResponse`, `BalanceResponse`, `ErrorResponse`) were already records from Session 1 and remain unchanged.

Key detail: `Transaction` previously auto-generated its `transactionId` inside the constructor. Since records require all components to be supplied at construction time, a static factory method `Transaction.create(...)` was introduced to generate the UUID, keeping the canonical constructor clean.

All usages updated to record accessor syntax (e.g. `account.currency()` instead of `account.getCurrency()`).

### 2. Service Layer Split

`TransactionService` was dissolved and its responsibilities redistributed across three focused services:

| Service | Responsibility |
|---|---|
| `TransactionManagerService` | Write operations — deposit and withdrawal; holds the `ReentrantLock` |
| `AccountManagerService` | Write operations — account creation |
| `AccountDataFetchService` | Read-only — balance queries, transaction history, list all accounts |

### 3. Controller Renames

| Old Name | New Name | Path |
|---|---|---|
| `TransactionController` | `LedgerOperationController` | `/api/transactions` |
| `TestController` | `DemoSetupController` | `/api/test` |

`LedgerOperationController` now injects `TransactionManagerService` (for deposits/withdrawals) and `AccountDataFetchService` (for balance and history queries). `DemoSetupController` now injects all three services instead of using `AccountRepository` directly.

### 4. Spotless Maven Plugin

Added `spotless-maven-plugin 2.43.0` to `pom.xml`. The initial approach used the Eclipse formatter with an external XML config file (`src/spotless/eclipse-formatter.xml`), which was subsequently removed in favour of fully inline built-in Spotless steps: trim trailing whitespace, end with newline, and 4-space indentation.

`spotless:check` runs as part of the build lifecycle. `spotless:apply` was executed to reformat all Java source files.

**Note on formatter compatibility:** Both `googleJavaFormat` and `palantirJavaFormat` were attempted but are incompatible with the Java 26 compiler — both depend on `com.sun.tools.javac.util.Log$DeferredDiagnosticHandler.getDiagnostics()`, an internal JDK API removed in JDK 25+. The built-in Spotless steps have no such dependency and work on any JDK version.

### 5. AccountType Enum Changes

Removed `INVESTMENT`. Renamed `CHECKING` → `CURRENT` and `SAVINGS` → `LOAN` to better reflect the account types supported. All usages in controllers, services, repositories, and tests updated accordingly.

### 6. Account Lower Bound and LOAN Balance Rules

Added a `lowerBound` (`BigDecimal`) field to the `Account` record representing the minimum allowed balance:

- **CURRENT** accounts — `lowerBound = 0`. A withdrawal that would make the balance negative throws `InsufficientFundsException` (unchanged behaviour from Session 1).
- **LOAN** accounts — `lowerBound` is a negative value representing the agreed credit limit, set at account creation time. Two new constraints are enforced on every transaction:
  - A withdrawal that would push the balance below `lowerBound` throws `CreditLimitExceededException` → HTTP 400.
  - A deposit that would push the balance above 0 (overpayment) throws `LoanOverpaymentException` → HTTP 400.

`lowerBound` is an optional field on `CreateAccountRequest`, validated as `≤ 0` when provided, defaulting to `BigDecimal.ZERO` in `AccountManagerService` if omitted. The balance validation in `TransactionManagerService` was restructured from a single amount-sign check into an account-type-aware block covering all three exception paths. Both new exceptions are registered in `GlobalExceptionHandler` and map to HTTP 400.

### 7. Test Updates

- `TransactionControllerTest` renamed to `LedgerOperationControllerTest`; updated to mock `TransactionManagerService` + `AccountDataFetchService` instead of `TransactionService`.
- `TransactionServiceTest` updated to wire up all three new services sharing the same repository instances.
- 11 new tests added (8 service, 2 controller slice, 1 concurrency):
  - LOAN happy paths: within-limit withdrawal, exact-limit withdrawal, deposit reduces debt, exact repayment to zero.
  - LOAN error paths: credit limit breach, just-below-limit breach, overpayment on fresh account, overpayment after partial draw.
  - LOAN concurrency: concurrent withdrawals never breach the credit limit.
  - Controller: `withdraw_creditLimitExceeded_returns400`, `deposit_loanOverpayment_returns400`.

**Total tests: 33** (up from 22 at the start of the session).

### 8. Per-Account Locking

Replaced the single global `ReentrantLock` in `TransactionManagerService` with a `ConcurrentHashMap<String, ReentrantLock>` keyed by account number. A lock for a given account is created lazily via `computeIfAbsent` on first use and reused for the lifetime of the service.

Before:
```java
private final ReentrantLock lock = new ReentrantLock();
```

After:
```java
private final ConcurrentHashMap<String, ReentrantLock> accountLocks = new ConcurrentHashMap<>();

ReentrantLock lock = accountLocks.computeIfAbsent(accountNumber, k -> new ReentrantLock());
```

`computeIfAbsent` is atomic on `ConcurrentHashMap`, so two threads racing to create a lock for the same new account always receive the same instance — no additional synchronisation is required. Transactions on different accounts now proceed concurrently with zero contention between them, while same-account transactions remain fully serialised.

A new concurrency test `concurrentTransactions_onDifferentAccounts_doNotBlockEachOther` was added to verify two accounts can be written to simultaneously without interference. ADR-0002 (`docs/adr/0002-per-account-locking.md`) documents the decision; ADR-0001 updated to mark its single-lock assumption as retired.

**Total tests: 34**.

---

## File Inventory

### New Files
- `src/main/java/com/ledger/service/TransactionManagerService.java`
- `src/main/java/com/ledger/service/AccountManagerService.java`
- `src/main/java/com/ledger/service/AccountDataFetchService.java`
- `src/main/java/com/ledger/controller/LedgerOperationController.java`
- `src/main/java/com/ledger/controller/DemoSetupController.java`
- `src/main/java/com/ledger/exception/CreditLimitExceededException.java`
- `src/main/java/com/ledger/exception/LoanOverpaymentException.java`
- `src/test/java/com/ledger/controller/LedgerOperationControllerTest.java`
- `docs/audit/Session2_Summary.md`
- `docs/adr/0000-adr-template.md`
- `docs/adr/0001-in-memory-temporal-ledger-design.md`
- `docs/adr/0002-per-account-locking.md`

### Deleted Files
- `src/main/java/com/ledger/service/TransactionService.java`
- `src/main/java/com/ledger/controller/TransactionController.java`
- `src/main/java/com/ledger/controller/TestController.java`
- `src/test/java/com/ledger/controller/TransactionControllerTest.java`
- `src/spotless/eclipse-formatter.xml` — replaced by inline pom.xml configuration

### Modified Files
- `src/main/java/com/ledger/model/Account.java` — converted to record; added `lowerBound` field
- `src/main/java/com/ledger/model/AccountType.java` — removed `INVESTMENT`; renamed `CHECKING` → `CURRENT`, `SAVINGS` → `LOAN`
- `src/main/java/com/ledger/model/Transaction.java` — converted to record with `Transaction.create(...)` factory
- `src/main/java/com/ledger/dto/CreateAccountRequest.java` — added optional `lowerBound` field with `@DecimalMax("0.00")`
- `src/main/java/com/ledger/repository/AccountRepository.java` — updated to record accessors; `createAccount` accepts `lowerBound`
- `src/main/java/com/ledger/repository/TransactionRepository.java` — updated to record accessors
- `src/main/java/com/ledger/service/AccountManagerService.java` — `createAccount` accepts `lowerBound`; defaults null to zero
- `src/main/java/com/ledger/service/TransactionManagerService.java` — account-type-aware balance validation; global lock replaced with per-account `ConcurrentHashMap` lock registry
- `src/main/java/com/ledger/exception/GlobalExceptionHandler.java` — registered `CreditLimitExceededException` and `LoanOverpaymentException`
- `src/main/java/com/ledger/controller/DemoSetupController.java` — passes `lowerBound`; seed uses realistic LOAN credit limits
- `src/test/java/com/ledger/service/TransactionServiceTest.java` — updated existing tests; 12 new tests added (11 LOAN + 1 per-account concurrency)
- `src/test/java/com/ledger/controller/LedgerOperationControllerTest.java` — 2 new exception tests added
- `pom.xml` — Spotless plugin added with inline built-in steps
- `README.md` — updated throughout

---

## API Endpoints (End of Session)

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/transactions/deposit` | Deposit funds |
| POST | `/api/transactions/withdraw` | Withdraw funds |
| GET | `/api/transactions/balance/{accountNumber}` | Current or as-of balance |
| GET | `/api/transactions/history/{accountNumber}` | Transaction history for a time range |
| POST | `/api/test/seed` | Seed 3 demo accounts with sample transactions |
| POST | `/api/test/accounts` | Create a single account |
| GET | `/api/test/accounts` | List all account records |

---

## Key Design Decisions

### Why three services instead of one?
The original `TransactionService` conflated reads and writes. Separating them by responsibility makes it clear which paths require locking (`TransactionManagerService` holds the `ReentrantLock`) and which are safely lock-free reads (`AccountDataFetchService`). `AccountManagerService` encapsulates account creation, leaving the door open to add validation or eventing there independently of transaction logic.

### Why `lowerBound` on `Account` rather than a separate entity?
The credit limit is an intrinsic property of a LOAN account — it is fixed at creation and does not change over time in this model. Embedding it directly on `Account` keeps the design simple and the balance rule check in `TransactionManagerService` self-contained: it reads `account.lowerBound()` and validates without any additional lookups.

### Why not `googleJavaFormat` or `palantirJavaFormat` for Spotless?
Both formatters call `com.sun.tools.javac.util.Log$DeferredDiagnosticHandler.getDiagnostics()`, an internal JDK API that was removed in JDK 25. Since this environment runs Java 26, neither formatter could execute. The built-in Spotless steps (`trimTrailingWhitespace`, `endWithNewline`, `indent`) enforce the required 4-space style without any JDK-internal dependency.

### Why `ConcurrentHashMap` over `Striped<Lock>` for per-account locking?
Guava's `Striped` pre-allocates a fixed number of buckets and maps keys via hashing, meaning unrelated accounts can share a bucket (false contention). Since the number of accounts in this application is expected to remain small, a 1:1 map gives strictly better isolation with no memory concern. `computeIfAbsent` on `ConcurrentHashMap` is atomic, so no additional synchronisation is needed to create locks safely under concurrency. Full rationale in ADR-0002.

---

## Next Steps (Suggested)

- Add Spring Security for endpoint protection
- Implement transaction reversal (set `validToUtc` on the existing record, append a compensating entry)
- Persist to a real database using temporal table support or a custom bitemporal schema
- Add account amendment support (new `validFrom`/`validTo` record pair when currency or type changes)
- If account counts grow very large, consider replacing the `ConcurrentHashMap` lock registry with Guava `Striped<ReentrantLock>` to bound memory usage at the cost of occasional false contention (see ADR-0002)
- If the application moves to a multi-node deployment, replace per-JVM locking with a distributed lock (Redis, ZooKeeper, or a database advisory lock)
