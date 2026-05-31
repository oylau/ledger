package com.ledger.controller;

import com.ledger.dto.CreateAccountRequest;
import com.ledger.dto.TransactionResponse;
import com.ledger.model.Account;
import com.ledger.model.AccountType;
import com.ledger.service.AccountDataFetchService;
import com.ledger.service.AccountManagerService;
import com.ledger.service.TransactionManagerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Demo / testing endpoint — not for production use. Provides convenience
 * operations to seed accounts and transactions.
 */
@RestController
@RequestMapping("/api/test")
@Tag(name = "Test & Demo", description = "Seed data and account creation helpers for demo purposes")
public class DemoSetupController {

    private final AccountManagerService accountManagerService;
    private final AccountDataFetchService accountDataFetchService;
    private final TransactionManagerService transactionManagerService;

    public DemoSetupController(AccountManagerService accountManagerService,
            AccountDataFetchService accountDataFetchService, TransactionManagerService transactionManagerService) {
        this.accountManagerService = accountManagerService;
        this.accountDataFetchService = accountDataFetchService;
        this.transactionManagerService = transactionManagerService;
    }

    @Operation(summary = "Create a single account")
    @PostMapping(value = "/accounts", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Account> createAccount(@Valid @RequestBody CreateAccountRequest request) {
        Account account = accountManagerService.createAccount(request.accountNumber(), request.currency(),
                request.accountType(), request.lowerBound());
        return ResponseEntity.ok(account);
    }

    @Operation(summary = "List all account records (including temporal history)")
    @GetMapping(value = "/accounts", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Account>> listAccounts() {
        return ResponseEntity.ok(accountDataFetchService.findAllAccounts());
    }

    @Operation(summary = "Seed demo data",
            description = "Creates ACC001 (USD/CURRENT, limit=0), ACC002 (USD/LOAN, limit=-5000), "
                    + "ACC003 (EUR/LOAN, limit=-10000) with a set of sample transactions.")
    @PostMapping(value = "/seed", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> seed() {
        accountManagerService.createAccount("ACC001", "USD", AccountType.CURRENT, BigDecimal.ZERO);
        accountManagerService.createAccount("ACC002", "USD", AccountType.LOAN, new BigDecimal("-5000.00"));
        accountManagerService.createAccount("ACC003", "EUR", AccountType.LOAN, new BigDecimal("-10000.00"));

        List<TransactionResponse> acc001Txns = List.of(
                transactionManagerService.applyTransaction("ACC001", new BigDecimal("1000.00"), "USD"),
                transactionManagerService.applyTransaction("ACC001", new BigDecimal("500.00"), "USD"),
                transactionManagerService.applyTransaction("ACC001", new BigDecimal("-200.00"), "USD"));

        List<TransactionResponse> acc002Txns = List.of(
                transactionManagerService.applyTransaction("ACC002", new BigDecimal("-1000.00"), "USD"),
                transactionManagerService.applyTransaction("ACC002", new BigDecimal("500.00"), "USD"));

        List<TransactionResponse> acc003Txns =
                List.of(transactionManagerService.applyTransaction("ACC003", new BigDecimal("-2500.00"), "EUR"));

        return ResponseEntity.ok(Map.of("message", "Seed data created successfully",
                "accounts", List.of("ACC001 (USD/CURRENT)", "ACC002 (USD/LOAN)", "ACC003 (EUR/LOAN)"),
                "ACC001_transactions", acc001Txns,
                "ACC002_transactions", acc002Txns,
                "ACC003_transactions", acc003Txns));
    }
}
