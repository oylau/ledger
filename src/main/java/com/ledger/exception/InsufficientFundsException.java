package com.ledger.exception;

import java.io.Serial;
import java.math.BigDecimal;

public class InsufficientFundsException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InsufficientFundsException(String accountNumber, BigDecimal balance, BigDecimal requested) {
        super("Insufficient funds on account " + accountNumber + ": balance=" + balance + ", requested=" + requested);
    }
}
