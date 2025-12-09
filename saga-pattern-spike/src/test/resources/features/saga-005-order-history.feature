@saga @saga-005
Feature: SAGA-005 - Order History Includes Saga Details
  As a customer
  I want to see the full history of my order processing
  So that I understand what happened if something went wrong

  Background:
    Given the saga pattern service is running
    And the inventory service is available
    And the payment service is available
    And the shipping service is available

  @history @timeline
  Scenario: View complete order history timeline
    Given I have a completed order
    When I view the order history
    Then I should see a timeline of all processing steps
    And the timeline should be ordered chronologically
    And each entry should have a timestamp

  @history @successful-order
  Scenario: History shows all steps for successful order
    Given I have a successfully completed order
    When I view the order history
    Then I should see the following timeline entries:
      | title                | status    |
      | Order Placed         | SUCCESS   |
      | Inventory Reserved   | SUCCESS   |
      | Payment Processed    | SUCCESS   |
      | Shipping Arranged    | SUCCESS   |
      | Order Complete       | SUCCESS   |

  @history @failed-order
  Scenario: History shows failure and compensation details
    Given I have an order that failed at payment and was compensated
    When I view the order history
    Then I should see the following timeline entries:
      | title                        | status      |
      | Order Placed                 | SUCCESS     |
      | Inventory Reserved           | COMPENSATED |
      | Payment Failed               | FAILED      |
      | Inventory Released           | COMPENSATED |
      | Order Could Not Be Completed | FAILED      |

  @history @failure-reason
  Scenario: Failed steps include the reason for failure
    Given I have an order that failed due to payment decline
    When I view the order history
    Then the failed step should include an error section
    And the error should include a code
    And the error should include a user-friendly message
    And the error should include a suggested action

  @history @step-outcomes
  Scenario: Each step shows its outcome clearly
    Given I have an order with mixed step outcomes
    When I view the order history
    Then each step should show one of these outcomes:
      | outcome     | description                    |
      | SUCCESS     | Step completed successfully    |
      | FAILED      | Step execution failed          |
      | COMPENSATED | Step was successfully reversed |
      | SKIPPED     | Step was not executed          |

  @history @retry-visibility
  Scenario: History shows retry attempts
    Given I have an order that was retried after initial failure
    When I view the order history
    Then I should see the original execution attempt
    And I should see the retry attempt
    And each attempt should be clearly labeled
    And the final successful attempt should be highlighted

  @history @multiple-executions
  Scenario: History shows all saga execution attempts
    Given I have an order with 2 saga execution attempts
    When I view the order history
    Then I should see execution summaries for each attempt
    And each execution should show:
      | field           |
      | executionId     |
      | attemptNumber   |
      | outcome         |
      | stepsCompleted  |

  @history @human-readable
  Scenario: History entries have human-readable descriptions
    Given I have a completed order
    When I view the order history
    Then each timeline entry should have a title
    And each timeline entry should have a description
    And descriptions should be customer-friendly, not technical

  @history @api
  Scenario: History API returns proper response structure
    Given I have a completed order
    When I request the order history via API
    Then the response should include:
      | field          | type    |
      | orderId        | UUID    |
      | orderNumber    | String  |
      | createdAt      | ISO8601 |
      | completedAt    | ISO8601 |
      | finalStatus    | String  |
      | timeline       | Array   |
      | executionCount | Integer |

  @history @events
  Scenario: Raw events are available for detailed inspection
    Given I have a completed order
    When I request the raw order events via API
    Then I should receive all recorded events
    And each event should include:
      | field      |
      | id         |
      | eventType  |
      | timestamp  |
      | stepName   |
      | details    |
