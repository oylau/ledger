package com.ledger.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Account balance at a given point in time")
public record BalanceResponse(@Schema(description = "Account number") String accountNumber,
        @Schema(description = "ISO 4217 currency code") String currency,
        @Schema(description = "Sum of all transactions up to asOfUtc") BigDecimal balance,
        @Schema(description = "The UTC instant the balance was evaluated at") Instant asOfUtc) {
}
