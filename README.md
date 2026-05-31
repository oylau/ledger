# Ledger — In-Memory Thread-Safe Ledger Service

A Spring Boot application that exposes a REST API for managing accounts and financial transactions, using a fully in-memory persistence layer implemented in a thread-safe manner.

**Group ID:** `io.github.irenelau-gds`  
**Spring Boot:** 4.0.6

---

## Requirements

| Tool    | Version   |
|---------|-----------|
| Java    | 21+       |
| Maven   | 3.9+      |

---

## Running the Application

```bash
mvn spring-boot:run
```

The server starts on **http://localhost:8080**.

---

## Swagger / OpenAPI UI

Once the application is running, the interactive API documentation is available at:

| URL | Description |
|-----|-------------|
| http://localhost:8080/swagger-ui.html | Swagger UI — try endpoints directly in the browser |
| http://localhost:8080/v3/api-docs | Raw OpenAPI 3.0 JSON spec |
| http://localhost:8080/v3/api-docs.yaml | Raw OpenAPI 3.0 YAML spec |

All endpoints are grouped into two tags in the UI:

- **Transactions** — deposit, withdraw, balance, history
- **Test & Demo** — seed data, create/list accounts

### Using Swagger UI

1. Start the application with `mvn spring-boot:run`.
2. Open http://localhost:8080/swagger-ui.html in a browser.
3. Click **POST /api/test/seed** → **Try it out** → **Execute** to populate demo data.
4. Explore the **Transactions** endpoints using `ACC001`, `ACC002`, or `ACC003`.

---

## Running Tests

```bash
mvn test
```

49 tests cover the service layer (including concurrency and LOAN balance rules), the web layer via MockMvc, and BDD acceptance scenarios via Cucumber.

### BDD / Acceptance Tests (Cucumber)

The BDD tests are written in plain English [Gherkin](https://cucumber.io/docs/gherkin/) and are readable by business users and QA team members without any Java knowledge. Feature files live in `src/test/resources/features/`.

**Run only the BDD suite:**

```bash
mvn test -Dtest=CucumberRunnerTest
```

**View the HTML report** after any test run:

```bash
open target/cucumber-reports/bdd-report.html
```

**Feature files:**

| File | Account type | Scenarios |
|------|-------------|-----------|
| `current_account.feature` | CURRENT | 7 — deposit, withdraw, accumulation, insufficient funds, wrong currency |
| `loan_account.feature` | LOAN | 8 — drawdown, repayment, credit limit, overpayment, wrong currency |

**How to read a scenario:**

```gherkin
Scenario: Withdrawal fails when there are insufficient funds
    Given I deposit 50.00 GBP into account "CA-001"      # set up initial state
    When I try to withdraw 100.00 GBP from account "CA-001"  # perform the action
    Then the transaction should be rejected with "insufficient funds"  # verify outcome
```

**Adding a new scenario:** Open the relevant `.feature` file, copy an existing `Scenario` block, adjust the description and amounts, then run `mvn test`. Cucumber reports pass/fail per scenario with the full Gherkin text in the output.

---

## Code Formatting

The project uses [Spotless](https://github.com/diffplug/spotless) with built-in steps (trim trailing whitespace, end with newline, 4-space indentation). To check formatting:

```bash
mvn spotless:check
```

To auto-fix formatting:

```bash
mvn spotless:apply
```

---

## Data Model

All entities use **Java records** and are immutable. Both entities are **temporal** — every record carries `validFromUtc` and `validToUtc` (`Instant`) so the system supports point-in-time (as-of) queries. Active records have `validToUtc = Instant.MAX`.

### Account
| Field         | Type        | Notes                                                        |
|---------------|-------------|--------------------------------------------------------------|
| accountNumber | String      | Unique identifier                                            |
| currency      | String      | ISO 4217 code (e.g. USD, EUR)                               |
| accountType   | AccountType | CURRENT / LOAN                                               |
| lowerBound    | BigDecimal  | Minimum allowed balance; 0 for CURRENT, negative for LOAN   |
| validFromUtc  | Instant     | Record activation time                                       |
| validToUtc    | Instant     | Instant.MAX = currently active                               |

### AccountType
| Value   | Description                                                                 |
|---------|-----------------------------------------------------------------------------|
| CURRENT | Standard account; balance must not drop below zero                          |
| LOAN    | Credit account; balance must stay within `[lowerBound, 0]`                 |

### Transaction
| Field              | Type       | Notes                                                        |
|--------------------|------------|--------------------------------------------------------------|
| transactionId      | String     | UUID, generated via `Transaction.create(...)`               |
| accountNumber      | String     | References an Account                                        |
| amount             | BigDecimal | Positive = deposit, negative = withdrawal                    |
| transactionTimeUtc | Instant    | Business time of the transaction                             |
| validFromUtc       | Instant    | Record activation time                                       |
| validToUtc         | Instant    | Instant.MAX = not reversed                                   |

---

## Balance Rules by Account Type

### CURRENT
- Balance must remain **≥ 0** at all times.
- A withdrawal that would make the balance negative throws `InsufficientFundsException` → HTTP 400.

### LOAN
- Balance must remain within **[`lowerBound`, 0]**.
- A withdrawal that would push the balance below `lowerBound` (the credit limit) throws `CreditLimitExceededException` → HTTP 400.
- A deposit that would push the balance above 0 (overpayment) throws `LoanOverpaymentException` → HTTP 400.

---

## API Reference

### Transaction Endpoints

#### Deposit
```
POST /api/transactions/deposit
Content-Type: application/json

{
  "accountNumber": "ACC001",
  "amount": 100.00,
  "currency": "USD"
}
```

#### Withdraw
```
POST /api/transactions/withdraw
Content-Type: application/json

{
  "accountNumber": "ACC001",
  "amount": 50.00,
  "currency": "USD"
}
```
> Amount must be positive; the API negates it internally.

#### Get Balance (current or as-of)
```
GET /api/transactions/balance/{accountNumber}
GET /api/transactions/balance/{accountNumber}?asOf=2024-01-01T00:00:00Z
```

#### Get Transaction History
```
GET /api/transactions/history/{accountNumber}?from=2024-01-01T00:00:00Z&to=2024-12-31T23:59:59Z
```

---

### Test / Demo Endpoints

#### Seed demo data
```
POST /api/test/seed
```
Creates three accounts with sample transactions:
- `ACC001` — USD / CURRENT (lower bound: 0)
- `ACC002` — USD / LOAN (credit limit: -5000.00)
- `ACC003` — EUR / LOAN (credit limit: -10000.00)

#### Create a single account
```
POST /api/test/accounts
Content-Type: application/json

{
  "accountNumber": "MY001",
  "currency": "GBP",
  "accountType": "CURRENT"
}
```
```
POST /api/test/accounts
Content-Type: application/json

{
  "accountNumber": "LOAN001",
  "currency": "GBP",
  "accountType": "LOAN",
  "lowerBound": -5000.00
}
```
> `lowerBound` is optional and defaults to 0. For LOAN accounts it must be a negative number representing the credit limit.

#### List all accounts
```
GET /api/test/accounts
```

---

## Quick Demo (curl)

```bash
# 1. Seed demo data
curl -X POST http://localhost:8080/api/test/seed

# 2. Check current balance on CURRENT account
curl http://localhost:8080/api/transactions/balance/ACC001

# 3. Deposit to CURRENT account
curl -X POST http://localhost:8080/api/transactions/deposit \
  -H "Content-Type: application/json" \
  -d '{"accountNumber":"ACC001","amount":200.00,"currency":"USD"}'

# 4. Withdraw from CURRENT account
curl -X POST http://localhost:8080/api/transactions/withdraw \
  -H "Content-Type: application/json" \
  -d '{"accountNumber":"ACC001","amount":50.00,"currency":"USD"}'

# 5. Draw down on a LOAN account (withdrawal)
curl -X POST http://localhost:8080/api/transactions/withdraw \
  -H "Content-Type: application/json" \
  -d '{"accountNumber":"ACC002","amount":1000.00,"currency":"USD"}'

# 6. Repay part of a LOAN account (deposit)
curl -X POST http://localhost:8080/api/transactions/deposit \
  -H "Content-Type: application/json" \
  -d '{"accountNumber":"ACC002","amount":500.00,"currency":"USD"}'

# 7. Historical balance (replace with a past timestamp)
curl "http://localhost:8080/api/transactions/balance/ACC001?asOf=2024-06-01T00:00:00Z"

# 8. Transaction history
curl "http://localhost:8080/api/transactions/history/ACC001?from=2024-01-01T00:00:00Z&to=2099-12-31T23:59:59Z"
```

---

## Architecture Notes

- **Persistence** — `CopyOnWriteArrayList` inside `AccountRepository` and `TransactionRepository` provides thread-safe reads without locks. A `ReentrantLock` in `TransactionManagerService` serialises balance-check + write pairs to prevent race conditions on withdrawals.
- **Temporal model** — every write appends a new record; nothing is mutated or deleted, enabling full as-of query support.
- **Immutable model** — `Account` and `Transaction` are Java records; all DTOs are also records.
- **Service layer** — responsibilities are split across three services: `TransactionManagerService` (transaction writes with balance rule enforcement), `AccountManagerService` (account writes), and `AccountDataFetchService` (all reads).
- **Balance rules** — enforced inside `TransactionManagerService` by account type: CURRENT accounts guard against going below zero; LOAN accounts guard against breaching the credit limit and against overpayment above zero.
- **Error handling** — `GlobalExceptionHandler` maps domain exceptions to HTTP status codes: 404 for unknown accounts; 400 for currency mismatch, insufficient funds, credit limit exceeded, and loan overpayment.
- **OpenAPI** — `springdoc-openapi-starter-webmvc-ui` auto-generates the spec from annotations. `@Tag`, `@Operation`, `@ApiResponse`, and `@Schema` annotations on controllers and model types drive the documentation.
- **Code style** — Spotless enforces 4-space indentation and clean line endings on every build.
