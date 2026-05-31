# Session 3 Summary — BDD Tests with Cucumber

**Date:** 2026-05-30
**Branch:** `feature/ledger-initial`

---

## Session Prompt

> Implement some initial BDD tests with a few scenarios for both CURRENT and LOAN account types, with instructions intended on how a business user or QA team member.
> Log what you have done in a new Session3_Summary.md file under docs.

---

## Objective

Add Behaviour-Driven Development (BDD) tests using [Cucumber](https://cucumber.io/) so that business users and QA team members can read — and potentially author — test scenarios without needing to understand Java code. Scenarios are written in plain English Gherkin syntax and cover the key business rules for both CURRENT and LOAN account types.

---

## What Was Added

### 1. Cucumber Dependencies (`pom.xml`)

Four new `test`-scoped dependencies were added:

| Dependency | Purpose |
|---|---|
| `io.cucumber:cucumber-java:7.22.2` | Core Cucumber runtime + Gherkin parser |
| `io.cucumber:cucumber-spring:7.22.2` | Injects Spring beans into step definition classes |
| `io.cucumber:cucumber-junit-platform-engine:7.22.2` | Plugs Cucumber into the JUnit 5 platform |
| `org.junit.platform:junit-platform-suite` | Provides `@Suite` to drive the Cucumber engine |

### 2. Feature Files

Feature files are written in **Gherkin** — structured plain English that describes system behaviour. They live under `src/test/resources/features/` and are the primary artefact for QA teams and business stakeholders.

#### `src/test/resources/features/current_account.feature`

Covers 7 scenarios for CURRENT accounts:

| Scenario | What it verifies |
|---|---|
| Depositing money increases the balance | Happy path deposit |
| Withdrawing money decreases the balance | Happy path withdrawal |
| Multiple deposits accumulate correctly | Additive deposit behaviour |
| Withdrawing the exact available balance leaves zero | Boundary: withdraw to exactly zero |
| Withdrawal fails when there are insufficient funds | Business rule: no overdraft on CURRENT |
| Withdrawal on an empty account is rejected immediately | Edge case: zero balance before first transaction |
| A deposit using the wrong currency is rejected | Currency guard: GBP account rejects USD transaction |

#### `src/test/resources/features/loan_account.feature`

Covers 8 scenarios for LOAN accounts:

| Scenario | What it verifies |
|---|---|
| A fresh loan account starts with a zero balance | Initial state |
| Drawing down creates a negative balance within the credit limit | Happy path drawdown |
| Partial repayment reduces the outstanding loan balance | Happy path repayment |
| Full repayment brings the loan balance back to zero | Boundary: repay to exactly zero |
| Drawing down to the exact credit limit is permitted | Boundary: use full credit limit |
| Withdrawal that would breach the credit limit is rejected | Business rule: credit limit enforcement |
| Overpayment that would push balance above zero is rejected | Business rule: no positive balance on LOAN |
| A deposit using the wrong currency is rejected on a loan account | Currency guard: GBP account rejects USD transaction |

### 3. Spring Integration (`CucumberSpringConfiguration.java`)

`src/test/java/com/ledger/bdd/CucumberSpringConfiguration.java` — a small configuration class that tells Cucumber to boot the full Spring application context for each test run. This means all step definitions receive real Spring beans (`AccountManagerService`, `TransactionManagerService`, `AccountDataFetchService`) through `@Autowired`, so scenarios exercise actual business logic rather than mocks.

```java
@CucumberContextConfiguration
@SpringBootTest(classes = LedgerApplication.class)
public class CucumberSpringConfiguration { }
```

### 4. Test Runner (`CucumberRunnerTest.java`)

`src/test/java/com/ledger/bdd/CucumberRunnerTest.java` — the JUnit Platform suite entry point that Maven's Surefire plugin discovers. It tells Cucumber where to find feature files (`classpath:features`) and step definitions (`com.ledger.bdd` glue package), and configures the HTML report output.

### 5. Step Definitions (`LedgerSteps.java`)

`src/test/java/com/ledger/bdd/steps/LedgerSteps.java` — maps every Gherkin phrase to a Java method call. Step definitions are deliberately thin: they call service-layer methods and delegate all assertions to AssertJ. No HTTP, no mocking.

**Key design decisions in step definitions:**

- In Cucumber, `@Given`, `@When`, `@Then`, `@And` are all aliases for the same step registry — a step registered with `@Given` will also match the `When` keyword in a feature file. Each pattern is therefore registered only once.
- **"Try to" steps** (`I try to deposit…`, `I try to withdraw…`) catch expected business exceptions and store them in a `lastException` field so the next `Then` assertion can inspect which exception was thrown.
- **`@Before` hook** clears both in-memory repository stores and resets `lastException` before every scenario. Because the repositories are Spring singletons, without this step data from one scenario would bleed into the next.

### 6. Repository Clear Methods

`AccountRepository.clearAll()` and `TransactionRepository.clearAll()` were added to support the `@Before` hook. Both methods call `CopyOnWriteArrayList.clear()`. They are only called from the BDD test setup — no production code path uses them.

### 7. Cucumber Configuration (`junit-platform.properties`)

`src/test/resources/junit-platform.properties` suppresses the Cucumber publish prompt that would otherwise appear in the console on every run.

---

## How to Read a Feature File (Guide for Business Users and QA)

Feature files are located at `src/test/resources/features/`. You do not need Java knowledge to read or author them.

**Anatomy of a scenario:**

```gherkin
Scenario: Withdrawal fails when there are insufficient funds
    Given I deposit 50.00 GBP into account "CA-001"   ← set up initial state
    When I try to withdraw 100.00 GBP from account "CA-001"  ← perform the action
    Then the transaction should be rejected with "insufficient funds"  ← verify outcome
```

**Keywords:**
- `Feature` — names the capability being tested (one per file).
- `Background` — steps that run before every scenario in the file (used here to create the account).
- `Scenario` — a single test case with a name readable by anyone.
- `Given` — context / precondition.
- `When` — the action being tested.
- `Then` — the expected outcome.
- `And` — continues a Given, When, or Then without repeating the keyword.

**Accepted rejection reasons** (used in `Then the transaction should be rejected with "…"`):

| Phrase | Meaning |
|---|---|
| `"insufficient funds"` | CURRENT account withdrawal would make balance negative |
| `"credit limit exceeded"` | LOAN account withdrawal would breach the agreed credit limit |
| `"loan overpayment"` | LOAN account deposit would push balance above zero |
| `"currency mismatch"` | Transaction currency does not match the account's currency |

**Adding a new scenario:** Copy an existing scenario block, change the name and the amounts/accounts, and run `mvn test`. Cucumber will report whether the scenario passes or what went wrong.

---

## How to Run the BDD Tests

```bash
# Run all tests (unit, integration, and BDD) together:
mvn test

# Run only the BDD suite:
mvn test -Dtest=CucumberRunnerTest

# View the HTML report after a run:
open target/cucumber-reports/bdd-report.html
```

---

## Test Count

| Test suite | Tests before | Tests after |
|---|---|---|
| Unit / integration (JUnit) | 34 | 34 |
| BDD (Cucumber) | 0 | **15** |
| **Total** | **34** | **49** |

---

## Files Changed

### New Files
- `src/test/resources/features/current_account.feature`
- `src/test/resources/features/loan_account.feature`
- `src/test/java/com/ledger/bdd/CucumberSpringConfiguration.java`
- `src/test/java/com/ledger/bdd/CucumberRunnerTest.java`
- `src/test/java/com/ledger/bdd/steps/LedgerSteps.java`
- `src/test/resources/junit-platform.properties`
- `docs/audit/Session3_Summary.md`

### Modified Files
- `pom.xml` — four Cucumber dependencies added
- `src/main/java/com/ledger/repository/AccountRepository.java` — `clearAll()` added
- `src/main/java/com/ledger/repository/TransactionRepository.java` — `clearAll()` added

---

## Key Design Decisions

### Why service layer, not HTTP?
BDD tests could drive the application through its REST API (using `MockMvc` or `RestAssured`). However, doing so would require mocking Spring beans or starting a real server, adding latency and complexity. Calling the service layer directly keeps scenarios fast (the full BDD suite runs in under one second) and keeps step definitions simple — each step is one or two lines. The existing controller tests already cover the HTTP layer.

### Why one step definition class?
With 15 scenarios across two features, a single `LedgerSteps` class is easy to navigate. If the number of features grows significantly, steps can be split by domain (e.g. `CurrentAccountSteps`, `LoanAccountSteps`) without changing the feature files at all.

### Why `clearAll()` on the repository rather than restarting the Spring context?
Restarting the Spring context (`@DirtiesContext`) between scenarios is the Cucumber-Spring idiom for full isolation, but it is slow — each restart takes several seconds. Clearing the two `CopyOnWriteArrayList` stores is O(1) and keeps the full suite under one second. The trade-off is that `clearAll()` is a test-support method on a production class; it is clearly documented and not called from any production code path.
