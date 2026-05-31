Feature: Loan Account Transactions
    As a bank customer or QA team member
    I want to draw down and repay a Loan account
    So that I can verify that credit limit and overpayment rules are enforced correctly

    Background:
        Given a Loan account "LA-001" exists with currency "GBP" and credit limit -1000.00

    Scenario: A fresh loan account starts with a zero balance
        Then the balance of account "LA-001" should be 0.00 GBP

    Scenario: Drawing down creates a negative balance within the credit limit
        When I withdraw 400.00 GBP from account "LA-001"
        Then the balance of account "LA-001" should be -400.00 GBP

    Scenario: Partial repayment reduces the outstanding loan balance
        Given I withdraw 600.00 GBP from account "LA-001"
        When I deposit 200.00 GBP into account "LA-001"
        Then the balance of account "LA-001" should be -400.00 GBP

    Scenario: Full repayment brings the loan balance back to zero
        Given I withdraw 750.00 GBP from account "LA-001"
        When I deposit 750.00 GBP into account "LA-001"
        Then the balance of account "LA-001" should be 0.00 GBP

    Scenario: Drawing down to the exact credit limit is permitted
        When I withdraw 1000.00 GBP from account "LA-001"
        Then the balance of account "LA-001" should be -1000.00 GBP

    Scenario: Withdrawal that would breach the credit limit is rejected
        Given I withdraw 800.00 GBP from account "LA-001"
        When I try to withdraw 300.00 GBP from account "LA-001"
        Then the transaction should be rejected with "credit limit exceeded"

    Scenario: Overpayment that would push balance above zero is rejected
        Given I withdraw 200.00 GBP from account "LA-001"
        When I try to deposit 500.00 GBP into account "LA-001"
        Then the transaction should be rejected with "loan overpayment"

    Scenario: A deposit using the wrong currency is rejected on a loan account
        When I try to deposit 100.00 USD into account "LA-001"
        Then the transaction should be rejected with "currency mismatch"
