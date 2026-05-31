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
     * Apply a transaction (deposit or withdrawal) to an account.
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
        ReentrantLock lock = accountLocks.computeIfAbsent(accountNumber, k -> new ReentrantLock());
        lock.lock();
        try {
            Instant now = Instant.now();
            Account account = accountRepository.findActive(accountNumber)
                    .orElseThrow(() -> new AccountNotFoundException(accountNumber));

            if (!account.currency().equalsIgnoreCase(currency)) {
                throw new CurrencyMismatchException(account.currency(), currency);
            }

            BigDecimal currentBalance = transactionRepository.sumBalanceAsOf(accountNumber, now);
            BigDecimal newBalance = currentBalance.add(amount);

            if (account.accountType() == AccountType.CURRENT) {
                if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
                    throw new InsufficientFundsException(accountNumber, currentBalance, amount.negate());
                }
            } else if (account.accountType() == AccountType.LOAN) {
                if (newBalance.compareTo(account.lowerBound()) < 0) {
                    throw new CreditLimitExceededException(accountNumber, account.lowerBound(), currentBalance,
                            amount.negate());
                }
                if (newBalance.compareTo(BigDecimal.ZERO) > 0) {
                    throw new LoanOverpaymentException(accountNumber, currentBalance, amount);
                }
            }

            Transaction tx = Transaction.create(accountNumber, amount, now, now, Instant.MAX);
            transactionRepository.save(tx);
            return toResponse(tx);
        } finally {
            lock.unlock();
        }
    }

    private TransactionResponse toResponse(Transaction tx) {
        return new TransactionResponse(tx.transactionId(), tx.accountNumber(), tx.amount(), tx.transactionTimeUtc(),
                tx.validFromUtc(), tx.validToUtc());
    }
}
