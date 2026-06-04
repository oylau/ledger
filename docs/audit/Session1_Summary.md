# Session 1 Summary — Ledger Application Bootstrap

**Date:** 2026-05-29  
**Branch:** `feature/ledger-initial`

---

## Session Prompts

### Prompt 1 — Initial Build

> cd into /Users/air/Develop/ledger
>
> First create a feature branch called feature/ledger-initial.
>
> Then Implement a brand new maven Springboot ledger application with persistence layer where all the data is kept in memory in a thread safe manner. Here are the requirements:
>
> Data structures that must be temporal, using Instant type for validFromUtc and validToUtc as the temporal columns. ValidToUtc is set to an infinite time in the future for running as of queries.
> 1. Account - accountNumber, currency, accountType
> 2. Transaction - transactionId, accountNumber, amount (where we'll use positive and negative values to denote deposit or withdrawal), transactionTimeUtc
>
> A "TransactionController" with API endpoints to
> 1. Deposit amount for a given currency
> 2. Withdraw amount for a given currency
> 3. Get account balance with an optional as-of time in UTC, if as of time is not specified, get the current balance.
> 4. Get transaction history for an account with a given time range.
>
> A "TransactionService" with thread safe methods to
> 1. Handle both deposit and withdrawal in a single method (i.e. the deposit endpoint call will add, the withdrawal call to deduct). It needs to validate the currency matches the account setup.
> 2. Handle fetching account balance with an as-of time.
> 3. Handle fetching transaction history with a given time range.
>
> I also need an TestController to allow me to create some test accounts with some test transactions for demo purposes.
>
> Make sure you have good unit tests in place, add a README on the application with instructions. Once done, create a docs directory and write a summary of this session to a Session1_Summary.md file.

### Prompt 2 — Improvements

> Please execute the followings:
> 1. Update springboot to the latest version that has addressed the major vulnerabilities?
> 2. Change the group ID in the pom to io.github.irenelau-gds.
> 3. Integrate swagger to the project and update the README with instructions.
> 4. Include the prompts in this session to the Session1_Summary.md file.

### Prompt 3 — Spring Boot 4.0.6 Upgrade

> Upgrade to SpringBoot 4.0.6 and rewrite the tests to not use the deprecated @MockBean. Update the session summary file accordingly after all test updates pass.

### Prompt 4 — Warning & Code Smell Cleanup

> Check for warnings and code smells and fix them accordingly. Update session summary once you verify everything passes.

---

## Objective

Bootstrap a brand-new Maven Spring Boot ledger application with a fully in-memory, thread-safe persistence layer, REST API, unit tests, and documentation. Then progressively harden it: patched Spring Boot, corrected group ID, Swagger docs, and finally a full upgrade to Spring Boot 4.0.6 / Spring Framework 7 / Jackson 3.

---

## What Was Built

### Project Structure

```
ledger/
├── pom.xml
├── README.md
├── docs/
│   └── Session1_Summary.md
└── src/
    ├── main/java/com/ledger/
    │   ├── LedgerApplication.java
    │   ├── config/
    │   │   └── OpenApiConfig.java        — OpenAPI metadata bean
    │   ├── model/
    │   │   ├── Account.java              — temporal account entity
    │   │   ├── Transaction.java          — temporal transaction entity
    │   │   └── AccountType.java          — CHECKING / SAVINGS / INVESTMENT enum
    │   ├── repository/
    │   │   ├── AccountRepository.java
    │   │   └── TransactionRepository.java
    │   ├── service/
    │   │   └── TransactionService.java
    │   ├── controller/
    │   │   ├── TransactionController.java
    │   │   └── TestController.java
    │   ├── dto/
    │   │   ├── TransactionRequest.java
    │   │   ├── TransactionResponse.java
    │   │   ├── BalanceResponse.java
    │   │   └── CreateAccountRequest.java
    │   └── exception/
    │       ├── AccountNotFoundException.java
    │       ├── CurrencyMismatchException.java
    │       ├── InsufficientFundsException.java
    │       └── GlobalExceptionHandler.java
    ├── main/resources/
    │   └── application.properties
    └── test/java/com/ledger/
        ├── service/TransactionServiceTest.java       (13 tests)
        └── controller/TransactionControllerTest.java  (9 tests)
```

---

## Final Library Versions

| Component | Version |
|-----------|---------|
| Spring Boot | **4.0.6** |
| Spring Framework | 7.0.7 |
| Jackson | 3.1.2 (`tools.jackson`) |
| springdoc-openapi | **3.0.3** (Spring Boot 4 compatible) |
| Java | 21+ (tested on Java 26) |

---

## Key Design Decisions

### Temporal Data Model
Both `Account` and `Transaction` carry `validFromUtc` and `validToUtc` as `java.time.Instant`. Active records are open-ended with `validToUtc = Instant.MAX`. This enables:
- Point-in-time (as-of) balance queries
- Non-destructive history (append-only)
- Future support for account amendments and transaction reversals without data loss

### Thread Safety Strategy
- `CopyOnWriteArrayList` in both repositories provides lock-free concurrent reads.
- A `ReentrantLock` in `TransactionService.applyTransaction` makes the balance-read + transaction-write sequence atomic, preventing double-spend in concurrent withdrawal scenarios.

### Unified Deposit/Withdrawal
`TransactionService.applyTransaction` handles both operations via the sign of the `amount` argument (positive = deposit, negative = withdrawal). The `TransactionController` normalises the sign before delegating — deposits pass the amount as-is, withdrawals negate it.

### Currency Validation
Every deposit/withdrawal validates that the request currency matches the account's configured currency, throwing `CurrencyMismatchException` (HTTP 400) if not.

---

## API Endpoints

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

## Swagger / OpenAPI

Integrated via `springdoc-openapi-starter-webmvc-ui` **3.0.3** (Spring Boot 4.0 / Spring Framework 7 compatible).

| URL | Description |
|-----|-------------|
| http://localhost:8080/swagger-ui.html | Interactive Swagger UI |
| http://localhost:8080/v3/api-docs | OpenAPI 3.0 JSON spec |
| http://localhost:8080/v3/api-docs.yaml | OpenAPI 3.0 YAML spec |

Annotations used:
- `@Tag` on controllers to group endpoints by domain
- `@Operation` + `@ApiResponse` on each method
- `@Parameter` on path/query variables
- `@Schema` on all DTO fields with descriptions and examples

---

## Test Coverage

**22 tests, all passing** (Java 26, Spring Boot 4.0.6):

### Service Tests (13)
- Deposit increases balance
- Multiple deposits accumulate correctly
- Withdrawal decreases balance
- Withdrawal with insufficient funds throws `InsufficientFundsException`
- Withdrawal of exact balance succeeds (zero result)
- Wrong currency throws `CurrencyMismatchException`
- Unknown account throws `AccountNotFoundException` (deposit and balance)
- Historical as-of balance returns correct value
- Transaction history returns only transactions within the given range
- Invalid time range (from > to) throws `IllegalArgumentException`
- Concurrent deposits produce consistent final balance (20 threads)
- Concurrent withdrawals never produce a negative balance (20 threads)

### Controller Tests (9)
- Deposit returns 200 with correct response body
- Withdraw negates the amount and returns 200
- Missing request fields return 400
- Account not found returns 404
- Currency mismatch returns 400
- Insufficient funds returns 400
- Balance without asOf returns 200
- Balance with asOf passes the instant correctly
- History returns correct transaction list

---

## Issues Encountered & Resolved

### Issue 1 — Mockito / Java 26 incompatibility
Mockito's default byte-buddy inline mock maker cannot instrument classes on Java 26 without explicit JVM flags. Resolved by:
1. Adding `-XX:+EnableDynamicAgentLoading` to the Surefire plugin `argLine`.
2. Adding `src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker` with `mock-maker-subclass` to use the subclass strategy instead of byte-buddy instrumentation.

### Issue 2 — Spring Boot 3.3.0 → 3.5.14
Intermediate upgrade to 3.5.14 (latest stable 3.x, 2026-04-23). Straightforward drop-in replacement — all 22 tests passed without changes.

### Issue 3 — Spring Boot 4.0.6 upgrade (3 cascading breaking changes)

**Breaking change 1 — `@MockBean` removed.**  
`org.springframework.boot.test.mock.mockito.MockBean` was deprecated in 3.4 and removed in 4.0.  
Fix: replaced with `@MockitoBean` from `org.springframework.test.context.bean.override.mockito`.

**Breaking change 2 — `@WebMvcTest` moved to a new module.**  
In Spring Boot 4.0 the web-layer test slice was extracted to a dedicated module. The annotation no longer lives in `spring-boot-test-autoconfigure`; it moved to:  
- **Module:** `org.springframework.boot:spring-boot-webmvc-test`  
- **Package:** `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`  
Fix: added `spring-boot-webmvc-test` as a test-scope dependency and updated the import.

**Breaking change 3 — Jackson 3 removed `WRITE_DATES_AS_TIMESTAMPS`.**  
Spring Boot 4.0 ships Jackson 3.x (`tools.jackson`). Jackson 3 removed `SerializationFeature.WRITE_DATES_AS_TIMESTAMPS` and bundled `java.time` support directly into `jackson-databind` (no separate JSR-310 module needed). The `spring.jackson.serialization.write-dates-as-timestamps=false` property in `application.properties` caused `JacksonProperties` binding to fail at context startup.  
Fix: removed the property from `application.properties` (Jackson 3 serialises `Instant` as ISO-8601 strings by default).  
Side-effect fix: the test's `@Autowired ObjectMapper` also failed — Spring Boot 4 registers a `tools.jackson.databind.ObjectMapper` bean, not `com.fasterxml.jackson.databind.ObjectMapper`. Updated import accordingly.

---

## Warning & Code Smell Fixes (Prompt 4)

Ran `javac -Xlint:all` and performed a full manual review. Five categories of issues were identified and resolved. Zero compiler warnings remain; all 22 tests continue to pass.

### Fix 1 — Missing `serialVersionUID` (compiler warnings)
All `RuntimeException` subclasses are `Serializable` by inheritance. Without a `serialVersionUID` the compiler emits a `[serial]` warning and JVM deserialization becomes unreliable across recompilations.  
**Added** `private static final long serialVersionUID = 1L;` to:
- `AccountNotFoundException`
- `CurrencyMismatchException`
- `InsufficientFundsException`

### Fix 2 — Stale / misleading comment in `AccountRepository`
The field comment said *"mutations are protected by the synchronized block in save()"* — no synchronized block has ever existed in that method. `CopyOnWriteArrayList.add()` is inherently atomic; compound operations are serialised by the `ReentrantLock` in `TransactionService`.  
**Updated** the comment to accurately describe the actual concurrency contract.

### Fix 3 — Wildcard imports in production controllers
`import org.springframework.web.bind.annotation.*` was used in both `TransactionController` and `TestController`. Wildcard imports hide which symbols are actually in use, making the code harder to navigate and grep.  
**Replaced** with explicit per-annotation imports in both files.

### Fix 4 — Missing `@Schema` annotations on model classes
`Account` and `Transaction` are returned directly from `TestController` endpoints (`POST /api/test/accounts`, `GET /api/test/accounts`) but carried no OpenAPI documentation. This left those response schemas undocumented in Swagger UI.  
**Added** `@Schema` at class level and on every field in both model classes.

### Fix 5 — `@Schema(implementation = Object.class)` replaced with `ErrorResponse`
Error `@ApiResponse` content schemas in `TransactionController` referenced `Object.class`, which produces meaningless `{}` in the generated spec. `GlobalExceptionHandler` was also returning a raw `Map<String, Object>`.  
**Created** `dto/ErrorResponse.java` (a typed record with `timestamp`, `status`, `error`), updated `GlobalExceptionHandler` to return `ResponseEntity<ErrorResponse>`, and updated all `@ApiResponse` error content annotations to `@Schema(implementation = ErrorResponse.class)`.

---

## Next Steps (Suggested)

- Add Spring Security for endpoint protection
- Implement transaction reversal (set `validToUtc` on the existing record, append a compensating entry)
- Persist to a real database (H2 for dev, PostgreSQL for prod) using temporal table support or a custom bitemporal schema
- Add account amendment support (new `validFrom`/`validTo` record pair when currency or type changes)
