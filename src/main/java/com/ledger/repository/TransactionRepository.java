package com.ledger.repository;

import com.ledger.model.Transaction;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Repository
public class TransactionRepository {

    private final CopyOnWriteArrayList<Transaction> store = new CopyOnWriteArrayList<>();

    public Transaction save(Transaction transaction) {
        store.add(transaction);
        return transaction;
    }

    /**
     * Sum all valid transaction amounts for an account where transactionTimeUtc <=
     * asOf. Only includes records whose temporal validity window covers asOf.
     */
    public BigDecimal sumBalanceAsOf(String accountNumber, Instant asOf) {
        return store.stream().filter(t -> t.accountNumber().equals(accountNumber)).filter(t -> t.isValidAt(asOf))
                .filter(t -> !t.transactionTimeUtc().isAfter(asOf)).map(Transaction::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Return all valid transactions for an account whose transactionTimeUtc falls
     * within [from, to] (inclusive) evaluated at the current wall-clock time.
     */
    public List<Transaction> findByAccountAndTimeRange(String accountNumber, Instant from, Instant to) {
        Instant now = Instant.now();
        return store.stream().filter(t -> t.accountNumber().equals(accountNumber)).filter(t -> t.isValidAt(now))
                .filter(t -> !t.transactionTimeUtc().isBefore(from) && !t.transactionTimeUtc().isAfter(to)).toList();
    }

    //=================================================================
    // FIXME - this is only use for testing!
    /** Remove all transaction records. Intended for use in test tear-down only. */
    public void clearAll() {
        store.clear();
    }
    //=================================================================
}
