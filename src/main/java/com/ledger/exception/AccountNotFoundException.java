package com.ledger.exception;

import java.io.Serial;

public class AccountNotFoundException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public AccountNotFoundException(String accountNumber) {
        super("Account not found: " + accountNumber);
    }
}
