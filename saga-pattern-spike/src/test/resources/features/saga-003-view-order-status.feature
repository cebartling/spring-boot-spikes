@saga @saga-003
Feature: SAGA-003 - View Order Status During Processing
  As a customer
  I want to see the current status of my order while it is being processed
  So that I know which stage my order is in

  Background:
    Given the saga pattern service is running
    And the inventory service is available
    And the payment service is available
    And the shipping service is available

  @status @in-progress
  Scenario: View status while order is being processed
    Given I have placed an order that is currently processing
    When I check my order status
    Then I should see the overall status as "IN_PROGRESS"
    And I should see which step is currently in progress
    And I should see which steps have completed
    And I should see which steps are pending

  @status @completed-steps
  Scenario: View completed steps during processing
    Given I have an order where inventory and payment steps are complete
    And the shipping step is in progress
    When I check my order status
    Then I should see the following step statuses:
      | step                    | status      |
      | Inventory Reservation   | COMPLETED   |
      | Payment Processing      | COMPLETED   |
      | Shipping Arrangement    | IN_PROGRESS |

  @status @failed
  Scenario: View status when a step has failed
    Given I have an order where the payment step failed
    When I check my order status
    Then I should see the overall status as "FAILED"
    And I should see the payment step marked as "FAILED"
    And I should see the failure reason for the payment step

  @status @compensating
  Scenario: View status during compensation
    Given I have an order that is currently being compensated
    When I check my order status
    Then I should see the overall status as "ROLLING_BACK"
    And I should see which steps are being compensated
    And I should see which steps have been compensated

  @status @completed
  Scenario: View status of completed order
    Given I have a successfully completed order
    When I check my order status
    Then I should see the overall status as "COMPLETED"
    And all steps should show status "COMPLETED"
    And I should see the completion timestamp

  @status @api
  Scenario: Status API returns proper response structure
    Given I have placed an order
    When I request the order status via API
    Then the response should include:
      | field              | type    |
      | orderId            | UUID    |
      | overallStatus      | String  |
      | currentStep        | String  |
      | lastUpdated        | ISO8601 |
      | steps              | Array   |
    And each step in the response should include:
      | field      | type    |
      | name       | String  |
      | order      | Integer |
      | status     | String  |

  @status @timestamps
  Scenario: Status includes timing information
    Given I have an order in progress
    When I check my order status
    Then each completed step should show a startedAt timestamp
    And each completed step should show a completedAt timestamp
    And the in-progress step should show only a startedAt timestamp

  @observability @trace-id-availability
  Scenario: Trace ID is available in order status for observability correlation
    Given I have placed an order
    When I request the order status via API
    Then the response should include the trace ID
    And the trace ID should be in W3C trace context format
    And I should be able to use the trace ID to find the trace in observability tools

  @observability @status-api-tracing
  Scenario: Status API includes trace context in response headers
    Given I have placed an order
    When I request the order status via API
    Then the response headers should include "traceparent"
    And the response headers should include "tracestate"
    And the trace ID in the body should match the traceparent header
