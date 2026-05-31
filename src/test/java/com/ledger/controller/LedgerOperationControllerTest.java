package com.ledger.controller;

import tools.jackson.databind.ObjectMapper;
import com.ledger.dto.BalanceResponse;
import com.ledger.dto.TransactionRequest;
import com.ledger.dto.TransactionResponse;
import com.ledger.exception.AccountNotFoundException;
import com.ledger.exception.CreditLimitExceededException;
import com.ledger.exception.CurrencyMismatchException;
import com.ledger.exception.InsufficientFundsException;
import com.ledger.exception.LoanOverpaymentException;
import com.ledger.service.AccountDataFetchService;
import com.ledger.service.TransactionManagerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LedgerOperationController.class)
class LedgerOperationControllerTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @MockitoBean
    TransactionManagerService transactionManagerService;
    @MockitoBean
    AccountDataFetchService accountDataFetchService;

    private static final Instant NOW = Instant.parse("2024-06-01T12:00:00Z");

    private TransactionResponse dummyTx(BigDecimal amount) {
        return new TransactionResponse("tx-1", "ACC001", amount, NOW, NOW, Instant.MAX);
    }

    @Test
    void deposit_returns200() throws Exception {
        when(transactionManagerService.applyTransaction("ACC001", new BigDecimal("100.00"), "USD"))
                .thenReturn(dummyTx(new BigDecimal("100.00")));

        mockMvc.perform(post("/api/transactions/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new TransactionRequest("ACC001", new BigDecimal("100.00"), "USD"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value("tx-1"))
                .andExpect(jsonPath("$.amount").value(100.00));
    }

    @Test
    void withdraw_negatesAmount_andReturns200() throws Exception {
        when(transactionManagerService.applyTransaction("ACC001", new BigDecimal("-50.00"), "USD"))
                .thenReturn(dummyTx(new BigDecimal("-50.00")));

        mockMvc.perform(post("/api/transactions/withdraw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new TransactionRequest("ACC001", new BigDecimal("50.00"), "USD"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(-50.00));
    }

    @Test
    void deposit_missingFields_returns400() throws Exception {
        mockMvc.perform(post("/api/transactions/deposit").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deposit_accountNotFound_returns404() throws Exception {
        when(transactionManagerService.applyTransaction(any(), any(), any()))
                .thenThrow(new AccountNotFoundException("ACC999"));

        mockMvc.perform(post("/api/transactions/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new TransactionRequest("ACC999", new BigDecimal("10.00"), "USD"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void deposit_currencyMismatch_returns400() throws Exception {
        when(transactionManagerService.applyTransaction(any(), any(), any()))
                .thenThrow(new CurrencyMismatchException("USD", "EUR"));

        mockMvc.perform(post("/api/transactions/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new TransactionRequest("ACC001", new BigDecimal("10.00"), "EUR"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void withdraw_insufficientFunds_returns400() throws Exception {
        when(transactionManagerService.applyTransaction(any(), any(), any()))
                .thenThrow(new InsufficientFundsException("ACC001", new BigDecimal("10"), new BigDecimal("50")));

        mockMvc.perform(post("/api/transactions/withdraw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new TransactionRequest("ACC001", new BigDecimal("50.00"), "USD"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void withdraw_creditLimitExceeded_returns400() throws Exception {
        when(transactionManagerService.applyTransaction(any(), any(), any()))
                .thenThrow(new CreditLimitExceededException("LOAN001", new BigDecimal("-1000.00"),
                        new BigDecimal("-800.00"), new BigDecimal("300.00")));

        mockMvc.perform(post("/api/transactions/withdraw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new TransactionRequest("LOAN001", new BigDecimal("300.00"), "GBP"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deposit_loanOverpayment_returns400() throws Exception {
        when(transactionManagerService.applyTransaction(any(), any(), any()))
                .thenThrow(new LoanOverpaymentException("LOAN001", new BigDecimal("-200.00"), new BigDecimal("500.00")));

        mockMvc.perform(post("/api/transactions/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new TransactionRequest("LOAN001", new BigDecimal("500.00"), "GBP"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getBalance_noAsOf_returns200() throws Exception {
        when(accountDataFetchService.getBalance(eq("ACC001"), isNull()))
                .thenReturn(new BalanceResponse("ACC001", "USD", new BigDecimal("250.00"), NOW));

        mockMvc.perform(get("/api/transactions/balance/ACC001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(250.00))
                .andExpect(jsonPath("$.currency").value("USD"));
    }

    @Test
    void getBalance_withAsOf_passes() throws Exception {
        Instant asOf = Instant.parse("2024-01-01T00:00:00Z");
        when(accountDataFetchService.getBalance(eq("ACC001"), eq(asOf)))
                .thenReturn(new BalanceResponse("ACC001", "USD", new BigDecimal("100.00"), asOf));

        mockMvc.perform(get("/api/transactions/balance/ACC001").param("asOf", "2024-01-01T00:00:00Z"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.balance").value(100.00));
    }

    @Test
    void getHistory_returns200() throws Exception {
        when(accountDataFetchService.getTransactionHistory(any(), any(), any()))
                .thenReturn(List.of(dummyTx(new BigDecimal("100.00"))));

        mockMvc.perform(get("/api/transactions/history/ACC001")
                        .param("from", "2024-01-01T00:00:00Z")
                        .param("to", "2024-12-31T23:59:59Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].amount").value(100.00));
    }
}
