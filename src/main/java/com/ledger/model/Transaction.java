package com.ledger.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Temporal transaction record. Positive amount = deposit, negative =
 * withdrawal. validToUtc is Instant.MAX for non-reversed transactions.
 */
@Schema(description = "Temporal transaction record — validToUtc is Instant.MAX while the transaction is not reversed")
public record Transaction(@Schema(description = "UUID of the transaction") String transactionId,
        @Schema(description = "Account number this transaction belongs to", example = "ACC001") String accountNumber,
        @Schema(description = "Amount: positive = deposit, negative = withdrawal") BigDecimal amount,
        @Schema(description = "Business time of the transaction (UTC)") Instant transactionTimeUtc,
        @Schema(description = "UTC instant from which this record is valid") Instant validFromUtc,
        @Schema(description = "UTC instant until which this record is valid; Instant.MAX means not reversed") Instant validToUtc) {

    /** Factory method that auto-generates a random transactionId. */
    public static Transaction create(String accountNumber, BigDecimal amount, Instant transactionTimeUtc,
            Instant validFromUtc, Instant validToUtc) {
        return new Transaction(UUID.randomUUID().toString(), accountNumber, amount, transactionTimeUtc, validFromUtc,
                validToUtc);
    }

    /**
     * Returns true if this transaction record is valid at the given point in time.
     */
    public boolean isValidAt(Instant asOf) {
        return !validFromUtc.isAfter(asOf) && validToUtc.isAfter(asOf);
    }
}
