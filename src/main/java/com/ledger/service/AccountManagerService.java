package com.ledger.service;

import com.ledger.model.Account;
import com.ledger.model.AccountType;
import com.ledger.repository.AccountRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/** Handles write operations that create and manage accounts. */
@Service
public class AccountManagerService {

    private final AccountRepository accountRepository;

    public AccountManagerService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    /**
     * Create and persist a new account active from now.
     *
     * @param accountNumber unique account identifier
     * @param currency ISO 4217 currency code
     * @param accountType account classification
     * @param lowerBound minimum allowed balance; null defaults to zero (suitable for CURRENT accounts)
     * @return the created account record
     */
    public Account createAccount(String accountNumber, String currency, AccountType accountType,
            BigDecimal lowerBound) {
        BigDecimal effectiveLowerBound = lowerBound != null ? lowerBound : BigDecimal.ZERO;
        return accountRepository.createAccount(accountNumber, currency, accountType, effectiveLowerBound);
    }
}
