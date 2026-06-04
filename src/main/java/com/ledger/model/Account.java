package com.ledger.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Temporal account record. validToUtc is Instant.MAX for currently active
 * accounts.
 */
@Schema(description = "Temporal account record — validToUtc is Instant.MAX while the account is active")
public record Account(
        @Schema(description = "Unique account identifier", example = "ACC001") String accountNumber,
        @Schema(description = "ISO 4217 currency code", example = "USD") String currency,
        @Schema(description = "Account type") AccountType accountType,
        @Schema(description = "Minimum allowed balance; zero for CURRENT accounts, negative credit limit for LOAN accounts")
                BigDecimal lowerBound,
        @Schema(description = "UTC instant from which this record is valid") Instant validFromUtc,
        @Schema(description = "UTC instant until which this record is valid; Instant.MAX means currently active")
                Instant validToUtc) {

    /** Returns true if this account is active at the given point in time. */
    public boolean isActiveAt(Instant asOf) {
        return !validFromUtc.isAfter(asOf) && validToUtc.isAfter(asOf);
    }
}
