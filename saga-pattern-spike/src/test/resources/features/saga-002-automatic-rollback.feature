@saga @saga-002
Feature: SAGA-002 - Automatic Rollback on Failure
  As a customer
  I want my order to be automatically cancelled if any step fails
  So that I am not charged for items that cannot be fulfilled

  Background:
    Given the saga pattern service is running
    And the inventory service is available
    And the payment service is available
    And the shipping service is available

  @compensation @inventory-failure
  Scenario: Order fails at inventory step - no compensation needed
    Given I have a valid customer account
    And I have items in my cart that are out of stock
    And I have a valid payment method on file
    When I submit my order
    Then the inventory reservation step should fail
    And no compensation should be triggered
    And I should receive a failure notification
    And the failure reason should indicate "insufficient stock"
    And the order status should be "FAILED"

  @compensation @payment-failure
  Scenario: Order fails at payment step - inventory is compensated
    Given I have a valid customer account
    And I have items in my cart with available inventory
    And I have a payment method that will be declined
    And I have a valid shipping address
    When I submit my order
    Then the inventory reservation step should complete successfully
    And the payment authorization step should fail
    And the inventory reservation should be automatically released
    And I should receive a failure notification
    And the failure reason should indicate "payment declined"
    And the order status should be "COMPENSATED"
    And no inventory reservations should remain

  @compensation @shipping-failure
  Scenario: Order fails at shipping step - inventory and payment are compensated
    Given I have a valid customer account
    And I have items in my cart with available inventory
    And I have a valid payment method on file
    And I have an invalid shipping address
    When I submit my order
    Then the inventory reservation step should complete successfully
    And the payment authorization step should complete successfully
    And the shipping arrangement step should fail
    And the payment authorization should be automatically voided
    And the inventory reservation should be automatically released
    And I should receive a failure notification
    And the failure reason should indicate "invalid shipping address"
    And the order status should be "COMPENSATED"

  @compensation @rollback-order
  Scenario: Compensation executes in reverse order
    Given I have submitted an order that will fail at shipping
    When the shipping step fails
    Then compensation should execute in reverse order:
      | step                    | compensation_order |
      | Payment Processing      | 1                  |
      | Inventory Reservation   | 2                  |
    And each compensation step should be recorded

  @compensation @idempotent
  Scenario: Compensation is idempotent
    Given I have an order that failed and was compensated
    When compensation is triggered again
    Then no duplicate reversals should occur
    And the compensation result should indicate already compensated

  @notification
  Scenario: Failure notification includes actionable information
    Given I have an order that failed due to payment decline
    When I receive the failure notification
    Then the notification should include the order ID
    And the notification should include the failed step name
    And the notification should include a clear failure reason
    And the notification should include suggested next steps

  @no-partial-state
  Scenario: No partial charges remain after compensation
    Given I have an order that failed after payment authorization
    When compensation completes
    Then no payment charges should exist for the order
    And no pending authorizations should exist for the order
