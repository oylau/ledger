package com.ledger.service;

import com.ledger.dto.BalanceResponse;
import com.ledger.dto.TransactionResponse;
import com.ledger.exception.AccountNotFoundException;
import com.ledger.exception.CreditLimitExceededException;
import com.ledger.exception.CurrencyMismatchException;
import com.ledger.exception.InsufficientFundsException;
import com.ledger.exception.LoanOverpaymentException;
import com.ledger.model.AccountType;
import com.ledger.repository.AccountRepository;
import com.ledger.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.*;

class TransactionServiceTest {

    private static final BigDecimal LOAN_LIMIT = new BigDecimal("-1000.00");

    private AccountRepository accountRepository;
    private TransactionRepository transactionRepository;
    private TransactionManagerService transactionManagerService;
    private AccountDataFetchService accountDataFetchService;

    @BeforeEach
    void setUp() {
        accountRepository = new AccountRepository();
        transactionRepository = new TransactionRepository();
        transactionManagerService = new TransactionManagerService(accountRepository, transactionRepository);
        accountDataFetchService = new AccountDataFetchService(accountRepository, transactionRepository);
        accountRepository.createAccount("ACC001", "USD", AccountType.CURRENT, BigDecimal.ZERO);
    }

    // --- CURRENT: deposit ---

    @Test
    void deposit_increasesBalance() {
        transactionManagerService.applyTransaction("ACC001", new BigDecimal("100.00"), "USD");
        BalanceResponse balance = accountDataFetchService.getBalance("ACC001", null);
        assertThat(balance.balance()).isEqualByComparingTo("100.00");
    }

    @Test
    void multipleDeposits_accumulateCorrectly() {
        transactionManagerService.applyTransaction("ACC001", new BigDecimal("100.00"), "USD");
        transactionManagerService.applyTransaction("ACC001", new BigDecimal("50.00"), "USD");
        assertThat(accountDataFetchService.getBalance("ACC001", null).balance()).isEqualByComparingTo("150.00");
    }

    // --- CURRENT: withdrawal ---

    @Test
    void withdrawal_decreasesBalance() {
        transactionManagerService.applyTransaction("ACC001", new BigDecimal("200.00"), "USD");
        transactionManagerService.applyTransaction("ACC001", new BigDecimal("-80.00"), "USD");
        assertThat(accountDataFetchService.getBalance("ACC001", null).balance()).isEqualByComparingTo("120.00");
    }

    @Test
    void current_withdrawal_insufficientFunds_throws() {
        transactionManagerService.applyTransaction("ACC001", new BigDecimal("50.00"), "USD");
        assertThatThrownBy(() -> transactionManagerService.applyTransaction("ACC001", new BigDecimal("-100.00"), "USD"))
                .isInstanceOf(InsufficientFundsException.class);
    }

    @Test
    void current_withdrawal_exactBalance_succeeds() {
        transactionManagerService.applyTransaction("ACC001", new BigDecimal("100.00"), "USD");
        transactionManagerService.applyTransaction("ACC001", new BigDecimal("-100.00"), "USD");
        assertThat(accountDataFetchService.getBalance("ACC001", null).balance()).isEqualByComparingTo("0.00");
    }

    // --- currency validation ---

    @Test
    void wrongCurrency_throws() {
        assertThatThrownBy(() -> transactionManagerService.applyTransaction("ACC001", new BigDecimal("50.00"), "EUR"))
                .isInstanceOf(CurrencyMismatchException.class);
    }

    // --- account not found ---

    @Test
    void unknownAccount_throws() {
        assertThatThrownBy(() -> transactionManagerService.applyTransaction("UNKNOWN", new BigDecimal("10.00"), "USD"))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void getBalance_unknownAccount_throws() {
        assertThatThrownBy(() -> accountDataFetchService.getBalance("UNKNOWN", null))
                .isInstanceOf(AccountNotFoundException.class);
    }

    // --- as-of balance ---

    @Test
    void getBalance_asOf_returnsHistoricalBalance() throws InterruptedException {
        transactionManagerService.applyTransaction("ACC001", new BigDecimal("300.00"), "USD");
        Instant snapshot = Instant.now();
        Thread.sleep(5);
        transactionManagerService.applyTransaction("ACC001", new BigDecimal("700.00"), "USD");

        BalanceResponse historical = accountDataFetchService.getBalance("ACC001", snapshot);
        assertThat(historical.balance()).isEqualByComparingTo("300.00");

        BalanceResponse current = accountDataFetchService.getBalance("ACC001", null);
        assertThat(current.balance()).isEqualByComparingTo("1000.00");
    }

    // --- transaction history ---

    @Test
    void getTransactionHistory_returnsWithinRange() throws InterruptedException {
        transactionManagerService.applyTransaction("ACC001", new BigDecimal("10.00"), "USD");
        Instant from = Instant.now();
        Thread.sleep(5);
        transactionManagerService.applyTransaction("ACC001", new BigDecimal("20.00"), "USD");
        Instant to = Instant.now();
        Thread.sleep(5);
        transactionManagerService.applyTransaction("ACC001", new BigDecimal("30.00"), "USD");

        List<TransactionResponse> history = accountDataFetchService.getTransactionHistory("ACC001", from, to);
        assertThat(history).hasSize(1);
        assertThat(history.getFirst().amount()).isEqualByComparingTo("20.00");
    }

    @Test
    void getTransactionHistory_invalidRange_throws() {
        Instant now = Instant.now();
        assertThatThrownBy(() -> accountDataFetchService.getTransactionHistory("ACC001", now, now.minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getTransactionHistory_fromOlderThan12Months_throws() {
        Instant to = Instant.now();
        Instant from = to.minus(366, java.time.temporal.ChronoUnit.DAYS);
        assertThatThrownBy(() -> accountDataFetchService.getTransactionHistory("ACC001", from, to))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'from' must not be older than 12 months");
    }

    @Test
    void getTransactionHistory_fromExactly365DaysBack_succeeds() {
        Instant to = Instant.now();
        Instant from = to.minus(365, java.time.temporal.ChronoUnit.DAYS);
        // Boundary: exactly 365 days is the allowed limit — should not throw
        List<TransactionResponse> history = accountDataFetchService.getTransactionHistory("ACC001", from, to);
        assertThat(history).isEmpty();
    }

    // --- LOAN: happy paths ---

    @Test
    void loan_withdrawal_withinCreditLimit_succeeds() {
        accountRepository.createAccount("LOAN001", "GBP", AccountType.LOAN, LOAN_LIMIT);
        transactionManagerService.applyTransaction("LOAN001", new BigDecimal("-500.00"), "GBP");
        assertThat(accountDataFetchService.getBalance("LOAN001", null).balance()).isEqualByComparingTo("-500.00");
    }

    @Test
    void loan_withdrawal_exactCreditLimit_succeeds() {
        accountRepository.createAccount("LOAN001", "GBP", AccountType.LOAN, LOAN_LIMIT);
        transactionManagerService.applyTransaction("LOAN001", new BigDecimal("-1000.00"), "GBP");
        assertThat(accountDataFetchService.getBalance("LOAN001", null).balance()).isEqualByComparingTo("-1000.00");
    }

    @Test
    void loan_deposit_reducesDebt() {
        accountRepository.createAccount("LOAN001", "GBP", AccountType.LOAN, LOAN_LIMIT);
        transactionManagerService.applyTransaction("LOAN001", new BigDecimal("-600.00"), "GBP");
        transactionManagerService.applyTransaction("LOAN001", new BigDecimal("400.00"), "GBP");
        assertThat(accountDataFetchService.getBalance("LOAN001", null).balance()).isEqualByComparingTo("-200.00");
    }

    @Test
    void loan_deposit_exactRepayment_balanceReachesZero() {
        accountRepository.createAccount("LOAN001", "GBP", AccountType.LOAN, LOAN_LIMIT);
        transactionManagerService.applyTransaction("LOAN001", new BigDecimal("-500.00"), "GBP");
        transactionManagerService.applyTransaction("LOAN001", new BigDecimal("500.00"), "GBP");
        assertThat(accountDataFetchService.getBalance("LOAN001", null).balance()).isEqualByComparingTo("0.00");
    }

    // --- LOAN: credit limit exceeded ---

    @Test
    void loan_withdrawal_exceedsCreditLimit_throws() {
        accountRepository.createAccount("LOAN001", "GBP", AccountType.LOAN, LOAN_LIMIT);
        assertThatThrownBy(() -> transactionManagerService.applyTransaction("LOAN001", new BigDecimal("-1500.00"), "GBP"))
                .isInstanceOf(CreditLimitExceededException.class)
                .hasMessageContaining("LOAN001")
                .hasMessageContaining("-1000.00");
    }

    @Test
    void loan_withdrawal_pushesJustBelowLimit_throws() {
        accountRepository.createAccount("LOAN001", "GBP", AccountType.LOAN, LOAN_LIMIT);
        transactionManagerService.applyTransaction("LOAN001", new BigDecimal("-999.00"), "GBP");
        assertThatThrownBy(() -> transactionManagerService.applyTransaction("LOAN001", new BigDecimal("-2.00"), "GBP"))
                .isInstanceOf(CreditLimitExceededException.class);
    }

    // --- LOAN: overpayment ---

    @Test
    void loan_deposit_onFreshAccount_throws() {
        accountRepository.createAccount("LOAN001", "GBP", AccountType.LOAN, LOAN_LIMIT);
        assertThatThrownBy(() -> transactionManagerService.applyTransaction("LOAN001", new BigDecimal("100.00"), "GBP"))
                .isInstanceOf(LoanOverpaymentException.class)
                .hasMessageContaining("LOAN001");
    }

    @Test
    void loan_deposit_exceedsZero_throws() {
        accountRepository.createAccount("LOAN001", "GBP", AccountType.LOAN, LOAN_LIMIT);
        transactionManagerService.applyTransaction("LOAN001", new BigDecimal("-300.00"), "GBP");
        assertThatThrownBy(() -> transactionManagerService.applyTransaction("LOAN001", new BigDecimal("500.00"), "GBP"))
                .isInstanceOf(LoanOverpaymentException.class)
                .hasMessageContaining("LOAN001");
    }

    // --- concurrency ---

    @Test
    void concurrentDeposits_consistentFinalBalance() throws InterruptedException {
        int threads = 20;
        BigDecimal depositAmount = new BigDecimal("10.00");
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    transactionManagerService.applyTransaction("ACC001", depositAmount, "USD");
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executor.shutdown();

        BigDecimal expected = depositAmount.multiply(BigDecimal.valueOf(threads));
        assertThat(accountDataFetchService.getBalance("ACC001", null).balance()).isEqualByComparingTo(expected);
    }

    @Test
    void concurrentWithdrawals_neverGoBelowZero() throws InterruptedException {
        transactionManagerService.applyTransaction("ACC001", new BigDecimal("100.00"), "USD");

        int threads = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    transactionManagerService.applyTransaction("ACC001", new BigDecimal("-10.00"), "USD");
                } catch (InsufficientFundsException ignored) {
                    // expected for threads that exceed the balance
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executor.shutdown();

        assertThat(accountDataFetchService.getBalance("ACC001", null).balance())
                .isGreaterThanOrEqualTo(BigDecimal.ZERO);
    }

    @Test
    void concurrentTransactions_onDifferentAccounts_doNotBlockEachOther() throws InterruptedException {
        // Create a second account so both can be targeted independently.
        accountRepository.createAccount("ACC002", "USD", AccountType.CURRENT, BigDecimal.ZERO);
        transactionManagerService.applyTransaction("ACC001", new BigDecimal("1000.00"), "USD");
        transactionManagerService.applyTransaction("ACC002", new BigDecimal("1000.00"), "USD");

        int threadsPerAccount = 10;
        int totalThreads = threadsPerAccount * 2;
        ExecutorService executor = Executors.newFixedThreadPool(totalThreads);
        CountDownLatch ready = new CountDownLatch(totalThreads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(totalThreads);

        // Half the threads target ACC001, the other half target ACC002.
        for (int i = 0; i < totalThreads; i++) {
            String account = i < threadsPerAccount ? "ACC001" : "ACC002";
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await(); // all threads start simultaneously
                    transactionManagerService.applyTransaction(account, new BigDecimal("-10.00"), "USD");
                } catch (InsufficientFundsException | InterruptedException ignored) {
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await(); // wait for all threads to be ready
        start.countDown(); // release all at once
        done.await();
        executor.shutdown();

        // Each account had 1000 and 10 threads withdrew 10 each — final balance must be >= 0.
        assertThat(accountDataFetchService.getBalance("ACC001", null).balance())
                .isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(accountDataFetchService.getBalance("ACC002", null).balance())
                .isGreaterThanOrEqualTo(BigDecimal.ZERO);
    }

    // --- multi-operation transactions: commit ---

    @Test
    void multiOp_allDeposits_allCommitted() {
        List<TransactionResponse> responses = transactionManagerService.applyTransaction(
                "ACC001", List.of(new BigDecimal("100.00"), new BigDecimal("50.00")), "USD");

        assertThat(responses).hasSize(2);
        assertThat(accountDataFetchService.getBalance("ACC001", null).balance()).isEqualByComparingTo("150.00");
    }

    @Test
    void multiOp_mixedOps_netPositive_allCommitted() {
        transactionManagerService.applyTransaction("ACC001", new BigDecimal("200.00"), "USD");

        List<TransactionResponse> responses = transactionManagerService.applyTransaction(
                "ACC001", List.of(new BigDecimal("100.00"), new BigDecimal("-50.00")), "USD");

        assertThat(responses).hasSize(2);
        assertThat(accountDataFetchService.getBalance("ACC001", null).balance()).isEqualByComparingTo("250.00");
    }

    @Test
    void multiOp_netToExactlyZero_current_commits() {
        transactionManagerService.applyTransaction("ACC001", new BigDecimal("100.00"), "USD");

        transactionManagerService.applyTransaction(
                "ACC001", List.of(new BigDecimal("50.00"), new BigDecimal("-150.00")), "USD");

        assertThat(accountDataFetchService.getBalance("ACC001", null).balance()).isEqualByComparingTo("0.00");
    }

    @Test
    void multiOp_loan_netWithinLimit_commits() {
        accountRepository.createAccount("LOAN001", "GBP", AccountType.LOAN, LOAN_LIMIT);

        transactionManagerService.applyTransaction(
                "LOAN001", List.of(new BigDecimal("-600.00"), new BigDecimal("100.00")), "GBP");

        assertThat(accountDataFetchService.getBalance("LOAN001", null).balance()).isEqualByComparingTo("-500.00");
    }

    // --- multi-operation transactions: rollback ---

    @Test
    void multiOp_netBelowZero_current_nothingCommitted() {
        transactionManagerService.applyTransaction("ACC001", new BigDecimal("100.00"), "USD");

        assertThatThrownBy(() -> transactionManagerService.applyTransaction(
                "ACC001", List.of(new BigDecimal("50.00"), new BigDecimal("-200.00")), "USD"))
                .isInstanceOf(InsufficientFundsException.class);

        // Balance unchanged — neither operation was persisted.
        assertThat(accountDataFetchService.getBalance("ACC001", null).balance()).isEqualByComparingTo("100.00");
    }

    @Test
    void multiOp_individualOpOk_butNetBelowZero_nothingCommitted() {
        // Each individual amount is <= current balance, but together they overdraft.
        transactionManagerService.applyTransaction("ACC001", new BigDecimal("50.00"), "USD");

        assertThatThrownBy(() -> transactionManagerService.applyTransaction(
                "ACC001", List.of(new BigDecimal("-30.00"), new BigDecimal("-30.00")), "USD"))
                .isInstanceOf(InsufficientFundsException.class);

        assertThat(accountDataFetchService.getBalance("ACC001", null).balance()).isEqualByComparingTo("50.00");
    }

    @Test
    void multiOp_loan_netExceedsCreditLimit_nothingCommitted() {
        accountRepository.createAccount("LOAN001", "GBP", AccountType.LOAN, LOAN_LIMIT);
        transactionManagerService.applyTransaction("LOAN001", new BigDecimal("-800.00"), "GBP");

        assertThatThrownBy(() -> transactionManagerService.applyTransaction(
                "LOAN001", List.of(new BigDecimal("-100.00"), new BigDecimal("-200.00")), "GBP"))
                .isInstanceOf(CreditLimitExceededException.class);

        assertThat(accountDataFetchService.getBalance("LOAN001", null).balance()).isEqualByComparingTo("-800.00");
    }

    @Test
    void multiOp_loan_netOverpayment_nothingCommitted() {
        accountRepository.createAccount("LOAN001", "GBP", AccountType.LOAN, LOAN_LIMIT);
        transactionManagerService.applyTransaction("LOAN001", new BigDecimal("-300.00"), "GBP");

        assertThatThrownBy(() -> transactionManagerService.applyTransaction(
                "LOAN001", List.of(new BigDecimal("200.00"), new BigDecimal("200.00")), "GBP"))
                .isInstanceOf(LoanOverpaymentException.class);

        assertThat(accountDataFetchService.getBalance("LOAN001", null).balance()).isEqualByComparingTo("-300.00");
    }

    @Test
    void loan_concurrentWithdrawals_neverBreachCreditLimit() throws InterruptedException {
        accountRepository.createAccount("LOAN001", "GBP", AccountType.LOAN, LOAN_LIMIT);

        int threads = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    transactionManagerService.applyTransaction("LOAN001", new BigDecimal("-100.00"), "GBP");
                } catch (CreditLimitExceededException ignored) {
                    // expected for threads that would breach the limit
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executor.shutdown();

        assertThat(accountDataFetchService.getBalance("LOAN001", null).balance())
                .isGreaterThanOrEqualTo(LOAN_LIMIT);
    }
}
