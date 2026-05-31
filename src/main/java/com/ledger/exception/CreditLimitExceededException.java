package com.ledger.exception;

import java.io.Serial;
import java.math.BigDecimal;

/** Thrown when a withdrawal on a LOAN account would breach its agreed credit limit. */
public class CreditLimitExceededException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public CreditLimitExceededException(String accountNumber, BigDecimal creditLimit, BigDecimal currentBalance,
            BigDecimal requested) {
        super("Credit limit exceeded on account " + accountNumber + ": limit=" + creditLimit + ", balance="
                + currentBalance + ", requested=" + requested);
    }
}
