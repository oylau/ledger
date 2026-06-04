Feature: Transaction History As-Of Queries
    As a bank customer or QA team member
    I want to retrieve the transaction history of an account for different time windows
    So that I can audit what transactions were recorded at any point in time

    Background:
        Given a Current account "CA-HIST" exists with currency "GBP"

    Scenario: History window that covers all transactions returns everything
        Given I deposit 100.00 GBP into account "CA-HIST" at "2024-01-01T09:00:00Z"
        And I deposit 200.00 GBP into account "CA-HIST" at "2024-01-01T12:00:00Z"
        And I withdraw 50.00 GBP from account "CA-HIST" at "2024-01-01T15:00:00Z"
        When I request the transaction history of account "CA-HIST" from "2024-01-01T00:00:00Z" to "2024-01-01T23:59:59Z"
        Then the transaction history should contain 3 transactions

    Scenario: History window ending before the second transaction excludes it
        Given I deposit 100.00 GBP into account "CA-HIST" at "2024-01-01T09:00:00Z"
        And I deposit 200.00 GBP into account "CA-HIST" at "2024-01-01T12:00:00Z"
        When I request the transaction history of account "CA-HIST" from "2024-01-01T00:00:00Z" to "2024-01-01T10:00:00Z"
        Then the transaction history should contain 1 transaction
        And the transaction history should include a deposit of 100.00 GBP

    Scenario: History window starting after the first transaction excludes it
        Given I deposit 100.00 GBP into account "CA-HIST" at "2024-01-01T09:00:00Z"
        And I deposit 200.00 GBP into account "CA-HIST" at "2024-01-01T12:00:00Z"
        When I request the transaction history of account "CA-HIST" from "2024-01-01T11:00:00Z" to "2024-01-01T23:59:59Z"
        Then the transaction history should contain 1 transaction
        And the transaction history should include a deposit of 200.00 GBP

    Scenario: History window exactly on a transaction timestamp includes that transaction (inclusive bounds)
        Given I deposit 300.00 GBP into account "CA-HIST" at "2024-06-15T08:30:00Z"
        When I request the transaction history of account "CA-HIST" from "2024-06-15T08:30:00Z" to "2024-06-15T08:30:00Z"
        Then the transaction history should contain 1 transaction
        And the transaction history should include a deposit of 300.00 GBP

    Scenario: History window that contains no transactions returns an empty list
        Given I deposit 100.00 GBP into account "CA-HIST" at "2024-01-01T09:00:00Z"
        When I request the transaction history of account "CA-HIST" from "2024-01-02T00:00:00Z" to "2024-01-02T23:59:59Z"
        Then the transaction history should contain 0 transactions

    Scenario: Withdrawals appear in history with a negative amount
        Given I deposit 500.00 GBP into account "CA-HIST" at "2024-03-01T10:00:00Z"
        And I withdraw 150.00 GBP from account "CA-HIST" at "2024-03-01T11:00:00Z"
        When I request the transaction history of account "CA-HIST" from "2024-03-01T10:30:00Z" to "2024-03-01T23:59:59Z"
        Then the transaction history should contain 1 transaction
        And the transaction history should include a withdrawal of 150.00 GBP

    Scenario: Transactions on different days are separated by date-range queries
        Given I deposit 100.00 GBP into account "CA-HIST" at "2024-02-01T09:00:00Z"
        And I deposit 200.00 GBP into account "CA-HIST" at "2024-02-02T09:00:00Z"
        And I deposit 300.00 GBP into account "CA-HIST" at "2024-02-03T09:00:00Z"
        When I request the transaction history of account "CA-HIST" from "2024-02-02T00:00:00Z" to "2024-02-02T23:59:59Z"
        Then the transaction history should contain 1 transaction
        And the transaction history should include a deposit of 200.00 GBP

    Scenario: Omitting the 'to' timestamp returns transactions up to now
        Given I deposit 75.00 GBP into account "CA-HIST" at "2025-12-01T10:00:00Z"
        And I deposit 25.00 GBP into account "CA-HIST" at "2025-12-15T10:00:00Z"
        When I request the transaction history of account "CA-HIST" from "2025-12-01T00:00:00Z"
        Then the transaction history should contain 2 transactions

    Scenario: 'from' older than 12 months before 'to' is rejected
        When I try to request the transaction history of account "CA-HIST" from "2022-01-01T00:00:00Z" to "2024-12-31T23:59:59Z"
        Then the history request should be rejected with "'from' must not be older than 12 months"

    Scenario: 'from' exactly 12 months (365 days) before 'to' is accepted
        When I request the transaction history of account "CA-HIST" from "2024-01-01T00:00:00Z" to "2024-12-31T00:00:00Z"
        Then the transaction history should contain 0 transactions
