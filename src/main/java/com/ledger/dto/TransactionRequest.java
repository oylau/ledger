package com.ledger.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

@Schema(description = "Request body for deposit or withdrawal")
public record TransactionRequest(
        @Schema(description = "Account number", example = "ACC001")
            @NotBlank String accountNumber,
        @Schema(description = "Positive amount (must be > 0); the withdraw endpoint negates this internally", example = "100.00")
            @NotNull @DecimalMin(value = "0.01", message = "Amount must be positive") BigDecimal amount,
        @Schema(description = "ISO 4217 currency code — must match the account's currency", example = "USD")
            @NotBlank String currency) {
}
