package com.ledger.exception;

import java.io.Serial;

public class CurrencyMismatchException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public CurrencyMismatchException(String expected, String provided) {
        super("Currency mismatch: account currency is " + expected + " but request currency is " + provided);
    }
}
