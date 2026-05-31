package com.ledger.repository;

import com.ledger.model.Account;
import com.ledger.model.AccountType;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

@Repository
public class AccountRepository {

    // CopyOnWriteArrayList gives thread-safe iteration and atomic single-element
    // writes.
    // Compound balance-check + write operations are serialised by the lock in
    // TransactionManagerService.
    private final CopyOnWriteArrayList<Account> store = new CopyOnWriteArrayList<>();

    private Account save(Account account) {
        store.add(account);
        return account;
    }

    /**
     * Return the active account for the given number at the requested point in
     * time.
     *
     * @param accountNumber the account number
     * @param asOf the instant to check
     * @return optional containing the active account
     */
    public Optional<Account> findActiveAt(String accountNumber, Instant asOf) {
        return store.stream().filter(a -> a.accountNumber().equals(accountNumber) && a.isActiveAt(asOf)).findFirst();
    }

    /**
     * Create and persist a brand-new account active from now.
     *
     * @param accountNumber the account number
     * @param currency the currency code
     * @param accountType the account type
     * @param lowerBound the minimum allowed balance (0 for CURRENT, negative credit limit for LOAN)
     * @return the created account
     */
    public Account createAccount(String accountNumber, String currency, AccountType accountType,
            BigDecimal lowerBound) {
        Instant now = Instant.now();
        return save(new Account(accountNumber, currency, accountType, lowerBound, now, Instant.MAX));
    }

    /**
     * Convenience method to find the account active right now.
     *
     * @param accountNumber the account number
     * @return optional containing the currently active account
     */
    public Optional<Account> findActive(String accountNumber) {
        return findActiveAt(accountNumber, Instant.now());
    }

    /**
     * Return all account records including temporal history.
     *
     * @return list of all account records
     */
    public List<Account> findAll() {
        return new ArrayList<>(store);
    }

    //=================================================================
    // FIXME - this is only use for testing!
    /** Remove all account records. Intended for use in test tear-down only. */
    public void clearAll() {
        store.clear();
    }
    //=================================================================
}
