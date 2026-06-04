package com.ledger.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Standard error response body")
public record ErrorResponse(@Schema(description = "UTC timestamp when the error occurred") Instant timestamp,
        @Schema(description = "HTTP status code") int status,
        @Schema(description = "Error detail message") String error) {
}
