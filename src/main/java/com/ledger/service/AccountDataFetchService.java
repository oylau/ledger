package com.ledger.service;

import com.ledger.dto.BalanceResponse;
import com.ledger.dto.TransactionResponse;
import com.ledger.exception.AccountNotFoundException;
import com.ledger.model.Account;
import com.ledger.model.Transaction;
import com.ledger.repository.AccountRepository;
import com.ledger.repository.TransactionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Read-only service for querying account balances and transaction history. */
@Service
public class AccountDataFetchService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public AccountDataFetchService(AccountRepository accountRepository, TransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    /**
     * Get the balance for an account as of a given UTC instant.
     *
     * @param accountNumber
     *            the account number
     * @param asOf
     *            point-in-time (uses current time when null)
     * @return the balance response
     */
    public BalanceResponse getBalance(String accountNumber, Instant asOf) {
        Instant effectiveAsOf = asOf != null ? asOf : Instant.now();
        Account account = accountRepository.findActiveAt(accountNumber, effectiveAsOf)
                .orElseThrow(() -> new AccountNotFoundException(accountNumber));

        BigDecimal balance = transactionRepository.sumBalanceAsOf(accountNumber, effectiveAsOf);
        return new BalanceResponse(accountNumber, account.currency(), balance, effectiveAsOf);
    }

    /**
     * Return transactions for an account whose transactionTimeUtc falls within
     * [from, to].
     *
     * @param accountNumber
     *            the account number
     * @param from
     *            range start (inclusive)
     * @param to
     *            range end (inclusive)
     * @return list of transaction responses
     */
    public List<TransactionResponse> getTransactionHistory(String accountNumber, Instant from, Instant to) {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("'from' must not be after 'to'");
        }
        accountRepository.findActive(accountNumber).orElseThrow(() -> new AccountNotFoundException(accountNumber));

        return transactionRepository.findByAccountAndTimeRange(accountNumber, from, to).stream().map(this::toResponse)
                .toList();
    }

    /** Return all account records including temporal history. */
    public List<Account> findAllAccounts() {
        return accountRepository.findAll();
    }

    private TransactionResponse toResponse(Transaction tx) {
        return new TransactionResponse(tx.transactionId(), tx.accountNumber(), tx.amount(), tx.transactionTimeUtc(),
                tx.validFromUtc(), tx.validToUtc());
    }
}
