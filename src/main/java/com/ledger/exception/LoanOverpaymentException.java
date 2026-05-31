package com.ledger.exception;

import java.io.Serial;
import java.math.BigDecimal;

/** Thrown when a deposit on a LOAN account would push the balance above zero. */
public class LoanOverpaymentException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public LoanOverpaymentException(String accountNumber, BigDecimal currentBalance, BigDecimal amount) {
        super("Overpayment on loan account " + accountNumber + ": depositing " + amount + " would exceed the maximum"
                + " balance of 0 (current balance=" + currentBalance + ")");
    }
}
