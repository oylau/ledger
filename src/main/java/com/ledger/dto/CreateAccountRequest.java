package com.ledger.dto;

import com.ledger.model.AccountType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** Request body for creating a new account. */
@Schema(description = "Request body for creating a new account")
public record CreateAccountRequest(
        @Schema(description = "Unique account number", example = "MY001") @NotBlank String accountNumber,
        @Schema(description = "ISO 4217 currency code", example = "GBP") @NotBlank String currency,
        @Schema(description = "Account type") @NotNull AccountType accountType,
        @Schema(description = "Minimum allowed balance. Must be zero or negative. "
                        + "Defaults to 0 when omitted (appropriate for CURRENT accounts). "
                        + "Set to a negative value to define the credit limit for LOAN accounts, e.g. -5000.00")
                @DecimalMax(value = "0.00", message = "lowerBound must be zero or negative")
                BigDecimal lowerBound) {
}
