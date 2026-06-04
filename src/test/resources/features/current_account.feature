Feature: Current Account Transactions
    As a bank customer or QA team member
    I want to deposit and withdraw money from a Current account
    So that I can verify that balance rules are enforced correctly

    Background:
        Given a Current account "CA-001" exists with currency "GBP"

    Scenario: Depositing money increases the balance
        When I deposit 500.00 GBP into account "CA-001"
        Then the balance of account "CA-001" should be 500.00 GBP

    Scenario: Withdrawing money decreases the balance
        Given I deposit 300.00 GBP into account "CA-001"
        When I withdraw 100.00 GBP from account "CA-001"
        Then the balance of account "CA-001" should be 200.00 GBP

    Scenario: Multiple deposits accumulate correctly
        When I deposit 100.00 GBP into account "CA-001"
        And I deposit 250.00 GBP into account "CA-001"
        And I deposit 50.00 GBP into account "CA-001"
        Then the balance of account "CA-001" should be 400.00 GBP

    Scenario: Withdrawing the exact available balance leaves zero
        Given I deposit 200.00 GBP into account "CA-001"
        When I withdraw 200.00 GBP from account "CA-001"
        Then the balance of account "CA-001" should be 0.00 GBP

    Scenario: Withdrawal fails when there are insufficient funds
        Given I deposit 50.00 GBP into account "CA-001"
        When I try to withdraw 100.00 GBP from account "CA-001"
        Then the transaction should be rejected with "insufficient funds"

    Scenario: Withdrawal on an empty account is rejected immediately
        When I try to withdraw 10.00 GBP from account "CA-001"
        Then the transaction should be rejected with "insufficient funds"

    Scenario: A deposit using the wrong currency is rejected
        Given I deposit 100.00 GBP into account "CA-001"
        When I try to deposit 50.00 USD into account "CA-001"
        Then the transaction should be rejected with "currency mismatch"
