@saga @saga-001
Feature: SAGA-001 - Complete a Multi-Step Order Process
  As a customer
  I want my order to be processed through all required steps (inventory reservation, payment, shipping)
  So that I receive confirmation only when my entire order is successfully completed

  Background:
    Given the saga pattern service is running
    And the inventory service is available
    And the payment service is available
    And the shipping service is available

  @happy-path
  Scenario: Successfully complete an order through all saga steps
    Given I have a valid customer account
    And I have items in my cart with available inventory
    And I have a valid payment method on file
    And I have a valid shipping address
    When I submit my order
    Then the inventory reservation step should complete successfully
    And the payment authorization step should complete successfully
    And the shipping arrangement step should complete successfully
    And I should receive a single order confirmation
    And the order status should be "COMPLETED"
    And all saga execution records should reflect the completed state

  @happy-path
  Scenario: Order confirmation includes expected details
    Given I have placed a successful order
    When I receive the order confirmation
    Then the confirmation should include an order ID
    And the confirmation should include a confirmation number
    And the confirmation should include the total amount charged
    And the confirmation should include an estimated delivery date

  @integration
  Scenario Outline: Process orders with different item quantities
    Given I have a valid customer account
    And I have <quantity> items in my cart with available inventory
    And I have a valid payment method on file
    And I have a valid shipping address
    When I submit my order
    Then the order should complete successfully
    And the total amount should reflect <quantity> items

    Examples:
      | quantity |
      | 1        |
      | 5        |
      | 10       |

  @saga-state
  Scenario: Saga execution state is properly tracked
    Given I have submitted an order
    When the saga execution begins
    Then a saga execution record should be created
    And the saga should progress through steps in order:
      | step                    | order |
      | Inventory Reservation   | 1     |
      | Payment Processing      | 2     |
      | Shipping Arrangement    | 3     |
    And each step result should be recorded in the database
