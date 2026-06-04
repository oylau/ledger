package com.ledger.service;

import com.ledger.dto.TransactionResponse;
import com.ledger.exception.AccountNotFoundException;
import com.ledger.exception.CreditLimitExceededException;
import com.ledger.exception.CurrencyMismatchException;
import com.ledger.exception.InsufficientFundsException;
import com.ledger.exception.LoanOverpaymentException;
import com.ledger.model.Account;
import com.ledger.model.AccountType;
import com.ledger.model.Transaction;
import com.ledger.repository.AccountRepository;
import com.ledger.repository.TransactionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Handles write operations that record financial transactions (deposits and
 * withdrawals).
 *
 * <p>Thread safety is provided by a per-account {@link ReentrantLock}. Locks are
 * created lazily via a {@link ConcurrentHashMap} and reused for the lifetime of
 * the service. This allows concurrent transactions on <em>different</em> accounts
 * to proceed without contention, while still serialising the balance-check + write
 * sequence for the <em>same</em> account.
 */
@Service
public class TransactionManagerService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    /**
     * Per-account lock registry. {@link ConcurrentHashMap#computeIfAbsent} is
     * atomic, so two threads racing to create a lock for the same account will
     * always receive the same {@link ReentrantLock} instance.
     */
    private final ConcurrentHashMap<String, ReentrantLock> accountLocks = new ConcurrentHashMap<>();

    public TransactionManagerService(AccountRepository accountRepository,
            TransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    /**
     * Apply a single-operation transaction (deposit or withdrawal) to an account.
     *
     * <p>Balance rules by account type:
     * <ul>
     *   <li><b>CURRENT</b> — balance must not drop below zero (insufficient funds).</li>
     *   <li><b>LOAN</b> — balance must stay within [{@code lowerBound}, 0]:
     *       withdrawals that breach the credit limit throw {@link CreditLimitExceededException};
     *       deposits that push the balance above zero throw {@link LoanOverpaymentException}.</li>
     * </ul>
     *
     * @param accountNumber the account number
     * @param amount positive for deposit, negative for withdrawal
     * @param currency ISO 4217 currency code
     * @return the recorded transaction response
     */
    public TransactionResponse applyTransaction(String accountNumber, BigDecimal amount, String currency) {
        return applyTransaction(accountNumber, List.of(amount), currency, Instant.now()).getFirst();
    }

    /**
     * Apply a single-operation transaction at an explicit business timestamp. Useful in tests that
     * need deterministic transaction times for as-of history queries.
     */
    public TransactionResponse applyTransaction(String accountNumber, BigDecimal amount, String currency,
            Instant transactionTime) {
        return applyTransaction(accountNumber, List.of(amount), currency, transactionTime).getFirst();
    }

    /**
     * Apply a multi-operation transaction atomically. All operations are recorded only if the
     * resulting balance satisfies the account type constraints. The constraint is evaluated on the
     * final net balance — intermediate states during the transaction are not checked.
     *
     * @param accountNumber the account number
     * @param amounts list of amounts to apply (positive = deposit, negative = withdrawal)
     * @param currency ISO 4217 currency code
     * @return the recorded transaction responses, one per operation, in input order
     */
    public List<TransactionResponse> applyTransaction(String accountNumber, List<BigDecimal> amounts, String currency) {
        return applyTransaction(accountNumber, amounts, currency, Instant.now());
    }

    /**
     * Apply a multi-operation transaction at an explicit business timestamp. Useful in tests that
     * need deterministic transaction times for as-of history queries.
     */
    public List<TransactionResponse> applyTransaction(String accountNumber, List<BigDecimal> amounts, String currency,
            Instant transactionTime) {
        ReentrantLock lock = accountLocks.computeIfAbsent(accountNumber, k -> new ReentrantLock());
        lock.lock();
        try {
            Instant now = transactionTime;
            Account account = accountRepository.findActive(accountNumber)
                    .orElseThrow(() -> new AccountNotFoundException(accountNumber));

            if (!account.currency().equalsIgnoreCase(currency)) {
                throw new CurrencyMismatchException(account.currency(), currency);
            }

            BigDecimal currentBalance = transactionRepository.sumBalanceAsOf(accountNumber, now);
            BigDecimal netDelta = amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal newBalance = currentBalance.add(netDelta);

            if (account.accountType() == AccountType.CURRENT) {
                if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
                    // Use netDelta for the error: withdrawal is the net negative effect
                    throw new InsufficientFundsException(accountNumber, currentBalance, netDelta.negate());
                }
            } else if (account.accountType() == AccountType.LOAN) {
                if (newBalance.compareTo(account.lowerBound()) < 0) {
                    throw new CreditLimitExceededException(accountNumber, account.lowerBound(), currentBalance,
                            netDelta.negate());
                }
                if (newBalance.compareTo(BigDecimal.ZERO) > 0) {
                    throw new LoanOverpaymentException(accountNumber, currentBalance, netDelta);
                }
            }

            List<TransactionResponse> responses = new ArrayList<>(amounts.size());
            for (BigDecimal amount : amounts) {
                Transaction tx = Transaction.create(accountNumber, amount, now, now, Instant.MAX);
                transactionRepository.save(tx);
                responses.add(toResponse(tx));
            }
            return responses;
        } finally {
            lock.unlock();
        }
    }

    private TransactionResponse toResponse(Transaction tx) {
        return new TransactionResponse(tx.transactionId(), tx.accountNumber(), tx.amount(), tx.transactionTimeUtc(),
                tx.validFromUtc(), tx.validToUtc());
    }
}
