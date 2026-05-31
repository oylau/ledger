package com.ledger.controller;

import com.ledger.dto.BalanceResponse;
import com.ledger.dto.ErrorResponse;
import com.ledger.dto.TransactionRequest;
import com.ledger.dto.TransactionResponse;
import com.ledger.service.AccountDataFetchService;
import com.ledger.service.TransactionManagerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/transactions")
@Tag(name = "Transactions", description = "Deposit, withdraw, query balance and history")
public class LedgerOperationController {

    private final TransactionManagerService transactionManagerService;
    private final AccountDataFetchService accountDataFetchService;

    public LedgerOperationController(TransactionManagerService transactionManagerService,
            AccountDataFetchService accountDataFetchService) {
        this.transactionManagerService = transactionManagerService;
        this.accountDataFetchService = accountDataFetchService;
    }

    @Operation(summary = "Deposit funds into an account", responses = {
            @ApiResponse(responseCode = "200", description = "Transaction recorded"),
            @ApiResponse(responseCode = "400", description = "Validation error or currency mismatch", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Account not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class))) })
    @PostMapping(value = "/deposit", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TransactionResponse> deposit(@Valid @RequestBody TransactionRequest request) {
        return ResponseEntity.ok(transactionManagerService.applyTransaction(request.accountNumber(), request.amount(),
                request.currency()));
    }

    @Operation(summary = "Withdraw funds from an account", responses = {
            @ApiResponse(responseCode = "200", description = "Transaction recorded"),
            @ApiResponse(responseCode = "400", description = "Insufficient funds, validation error, or currency mismatch", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Account not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class))) })
    @PostMapping(value = "/withdraw", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TransactionResponse> withdraw(@Valid @RequestBody TransactionRequest request) {
        return ResponseEntity.ok(transactionManagerService.applyTransaction(request.accountNumber(),
                request.amount().negate(), request.currency()));
    }

    @Operation(summary = "Get account balance", description = "Returns the balance at the current time when asOf is omitted, "
            + "or the historical balance at the given UTC instant.")
    @GetMapping(value = "/balance/{accountNumber}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BalanceResponse> getBalance(
            @Parameter(description = "Account number", required = true) @PathVariable String accountNumber,
            @Parameter(description = "Optional point-in-time (ISO-8601 UTC), e.g. 2024-01-01T00:00:00Z") @RequestParam(required = false) Instant asOf) {
        return ResponseEntity.ok(accountDataFetchService.getBalance(accountNumber, asOf));
    }

    @Operation(summary = "Get transaction history for an account", description = "Returns all transactions whose transactionTimeUtc falls within [from, to].")
    @GetMapping(value = "/history/{accountNumber}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<TransactionResponse>> getHistory(
            @Parameter(description = "Account number", required = true) @PathVariable String accountNumber,
            @Parameter(description = "Range start (ISO-8601 UTC)", required = true) @RequestParam Instant from,
            @Parameter(description = "Range end (ISO-8601 UTC)", required = true) @RequestParam Instant to) {
        return ResponseEntity.ok(accountDataFetchService.getTransactionHistory(accountNumber, from, to));
    }
}
