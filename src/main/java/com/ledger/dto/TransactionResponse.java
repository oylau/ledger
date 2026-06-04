package com.ledger.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Recorded transaction detail")
public record TransactionResponse(@Schema(description = "UUID of the transaction") String transactionId,
        @Schema(description = "Account number") String accountNumber,
        @Schema(description = "Amount (positive = deposit, negative = withdrawal)") BigDecimal amount,
        @Schema(description = "Business time of the transaction (UTC)") Instant transactionTimeUtc,
        @Schema(description = "Temporal record valid-from (UTC)") Instant validFromUtc,
        @Schema(description = "Temporal record valid-to; Instant.MAX means not reversed") Instant validToUtc) {
}
