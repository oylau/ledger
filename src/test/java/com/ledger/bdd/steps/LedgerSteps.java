package com.ledger.bdd.steps;

import com.ledger.exception.CreditLimitExceededException;
import com.ledger.exception.CurrencyMismatchException;
import com.ledger.exception.InsufficientFundsException;
import com.ledger.exception.LoanOverpaymentException;
import com.ledger.model.AccountType;
import com.ledger.repository.AccountRepository;
import com.ledger.repository.TransactionRepository;
import com.ledger.service.AccountDataFetchService;
import com.ledger.service.AccountManagerService;
import com.ledger.service.TransactionManagerService;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cucumber step definitions binding Gherkin phrases to service-layer calls.
 *
 * <p>Steps work directly with the Spring service beans — no HTTP, just real
 * business logic — keeping scenarios fast and easy for QA to trace to
 * acceptance criteria.
 *
 * <p>In Cucumber, {@code @Given}, {@code @When}, {@code @Then} are all aliases
 * for the same step registry, so each pattern is registered only once and
 * matches regardless of which keyword appears in the feature file.
 *
 * <p><b>Account naming convention used in feature files:</b>
 * <ul>
 *   <li>CA-xxx — Current account</li>
 *   <li>LA-xxx — Loan account</li>
 * </ul>
 */
public class LedgerSteps {

    @Autowired
    private AccountManagerService accountManagerService;

    @Autowired
    private TransactionManagerService transactionManagerService;

    @Autowired
    private AccountDataFetchService accountDataFetchService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    /** Holds the last exception thrown by a "try to" step so Then can inspect it. */
    private Exception lastException;

    /** Holds the result of the most recent transaction-history query. */
    private List<com.ledger.dto.TransactionResponse> lastHistory;

    /**
     * Wipe the in-memory stores and reset exception state before each scenario.
     * This prevents state leaking between scenarios that share the same account
     * numbers via the Background clause.
     */
    @Before
    public void resetScenarioState() {
        accountRepository.clearAll();
        transactionRepository.clearAll();
        lastException = null;
        lastHistory = null;
    }

    // -------------------------------------------------------------------------
    // Account setup
    // -------------------------------------------------------------------------

    @Given("a Current account {string} exists with currency {string}")
    public void aCurrentAccountExistsWithCurrency(String accountNumber, String currency) {
        accountManagerService.createAccount(accountNumber, currency, AccountType.CURRENT, BigDecimal.ZERO);
    }

    @Given("a Loan account {string} exists with currency {string} and credit limit {bigdecimal}")
    public void aLoanAccountExistsWithCurrencyAndCreditLimit(
            String accountNumber, String currency, BigDecimal creditLimit) {
        accountManagerService.createAccount(accountNumber, currency, AccountType.LOAN, creditLimit);
    }

    // -------------------------------------------------------------------------
    // Transactions expected to succeed
    // Step keyword in feature file (Given/When/And) does not matter —
    // Cucumber matches on the pattern text, not the keyword.
    // -------------------------------------------------------------------------

    @Given("I deposit {bigdecimal} {word} into account {string}")
    public void iDeposit(BigDecimal amount, String currency, String accountNumber) {
        transactionManagerService.applyTransaction(accountNumber, amount, currency);
    }

    @Given("I withdraw {bigdecimal} {word} from account {string}")
    public void iWithdraw(BigDecimal amount, String currency, String accountNumber) {
        transactionManagerService.applyTransaction(accountNumber, amount.negate(), currency);
    }

    // -------------------------------------------------------------------------
    // Transactions that may fail — exception is captured for Then to inspect
    // -------------------------------------------------------------------------

    @When("I try to deposit {bigdecimal} {word} into account {string}")
    public void iTryToDeposit(BigDecimal amount, String currency, String accountNumber) {
        try {
            transactionManagerService.applyTransaction(accountNumber, amount, currency);
        } catch (InsufficientFundsException | CreditLimitExceededException
                | LoanOverpaymentException | CurrencyMismatchException e) {
            lastException = e;
        }
    }

    @When("I try to withdraw {bigdecimal} {word} from account {string}")
    public void iTryToWithdraw(BigDecimal amount, String currency, String accountNumber) {
        try {
            transactionManagerService.applyTransaction(accountNumber, amount.negate(), currency);
        } catch (InsufficientFundsException | CreditLimitExceededException
                | LoanOverpaymentException | CurrencyMismatchException e) {
            lastException = e;
        }
    }

    // -------------------------------------------------------------------------
    // Assertions
    // -------------------------------------------------------------------------

    @Then("the balance of account {string} should be {bigdecimal} {word}")
    public void theBalanceShouldBe(String accountNumber, BigDecimal expected, String currency) {
        BigDecimal actual = accountDataFetchService.getBalance(accountNumber, null).balance();
        assertThat(actual).isEqualByComparingTo(expected);
    }

    // -------------------------------------------------------------------------
    // Timestamped transactions for history / as-of scenarios
    // -------------------------------------------------------------------------

    @Given("I deposit {bigdecimal} {word} into account {string} at {string}")
    public void iDepositAt(BigDecimal amount, String currency, String accountNumber, String iso8601) {
        transactionManagerService.applyTransaction(accountNumber, amount, currency, Instant.parse(iso8601));
    }

    @Given("I withdraw {bigdecimal} {word} from account {string} at {string}")
    public void iWithdrawAt(BigDecimal amount, String currency, String accountNumber, String iso8601) {
        transactionManagerService.applyTransaction(accountNumber, amount.negate(), currency, Instant.parse(iso8601));
    }

    @When("I request the transaction history of account {string} from {string} to {string}")
    public void iRequestTransactionHistory(String accountNumber, String fromIso8601, String toIso8601) {
        lastHistory = accountDataFetchService.getTransactionHistory(
                accountNumber, Instant.parse(fromIso8601), Instant.parse(toIso8601));
    }

    @When("I request the transaction history of account {string} from {string}")
    public void iRequestTransactionHistoryNoTo(String accountNumber, String fromIso8601) {
        lastHistory = accountDataFetchService.getTransactionHistory(
                accountNumber, Instant.parse(fromIso8601), Instant.now());
    }

    @When("I try to request the transaction history of account {string} from {string} to {string}")
    public void iTryToRequestTransactionHistory(String accountNumber, String fromIso8601, String toIso8601) {
        try {
            lastHistory = accountDataFetchService.getTransactionHistory(
                    accountNumber, Instant.parse(fromIso8601), Instant.parse(toIso8601));
        } catch (IllegalArgumentException e) {
            lastException = e;
        }
    }

    @Then("the transaction history should contain {int} transaction(s)")
    public void theHistoryShouldContain(int expectedCount) {
        assertThat(lastHistory)
                .as("Expected %d transaction(s) in history but got %d", expectedCount, lastHistory.size())
                .hasSize(expectedCount);
    }

    @Then("the transaction history should include a deposit of {bigdecimal} {word}")
    public void theHistoryShouldIncludeDeposit(BigDecimal amount, String currency) {
        assertThat(lastHistory)
                .as("Expected a deposit of %s %s in history", amount, currency)
                .anyMatch(tx -> tx.amount().compareTo(amount) == 0);
    }

    @Then("the transaction history should include a withdrawal of {bigdecimal} {word}")
    public void theHistoryShouldIncludeWithdrawal(BigDecimal amount, String currency) {
        assertThat(lastHistory)
                .as("Expected a withdrawal of %s %s in history", amount, currency)
                .anyMatch(tx -> tx.amount().compareTo(amount.negate()) == 0);
    }

    @Then("the history request should be rejected with {string}")
    public void theHistoryRequestShouldBeRejectedWith(String reason) {
        assertThat(lastException)
                .as("Expected a rejected history request but no exception was captured")
                .isNotNull()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(reason);
    }

    @Then("the transaction should be rejected with {string}")
    public void theTransactionShouldBeRejectedWith(String reason) {
        assertThat(lastException)
                .as("Expected a rejected transaction but no exception was captured")
                .isNotNull();

        switch (reason) {
            case "insufficient funds" ->
                assertThat(lastException).isInstanceOf(InsufficientFundsException.class);
            case "credit limit exceeded" ->
                assertThat(lastException).isInstanceOf(CreditLimitExceededException.class);
            case "loan overpayment" ->
                assertThat(lastException).isInstanceOf(LoanOverpaymentException.class);
            case "currency mismatch" ->
                assertThat(lastException).isInstanceOf(CurrencyMismatchException.class);
            default ->
                throw new IllegalArgumentException("Unknown rejection reason in feature file: " + reason);
        }
    }
}
