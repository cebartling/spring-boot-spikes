@saga @saga-004
Feature: SAGA-004 - Retry Failed Orders
  As a customer
  I want to retry a failed order without re-entering all my information
  So that I can easily complete my purchase after resolving the issue

  Background:
    Given the saga pattern service is running
    And the inventory service is available
    And the payment service is available
    And the shipping service is available

  @retry @eligibility
  Scenario: Check retry eligibility for a failed order
    Given I have an order that failed due to payment decline
    When I check if the order is eligible for retry
    Then the order should be eligible for retry
    And the required action should be "UPDATE_PAYMENT_METHOD"

  @retry @not-eligible
  Scenario: Non-retryable failure is not eligible for retry
    Given I have an order that failed due to fraud detection
    When I check if the order is eligible for retry
    Then the order should not be eligible for retry
    And the reason should indicate "non-retryable failure"

  @retry @success
  Scenario: Successfully retry a failed order
    Given I have an order that failed due to payment decline
    And I have updated my payment method
    When I retry the order
    Then the retry should be initiated successfully
    And the order should complete successfully

  @retry @resume-from-failed-step
  Scenario: Retry resumes from the failed step
    Given I have an order that failed at the shipping step
    And I have corrected my shipping address
    When I retry the order
    Then the inventory step should be skipped
    And the payment step should be skipped
    And the shipping step should execute with the new address
    And the order should complete successfully

  @retry @step-validation
  Scenario: Retry validates previous step results
    Given I have an order that failed at payment
    And the original inventory reservation has expired
    When I retry the order
    Then a new inventory reservation should be created
    And the payment step should execute
    And the order should complete successfully

  @retry @limits
  Scenario: Retry limits are enforced
    Given I have an order that has been retried 3 times
    When I attempt to retry the order again
    Then the retry should be rejected
    And the reason should indicate "maximum retry attempts exceeded"

  @retry @cooldown
  Scenario: Retry cooldown period is enforced
    Given I have an order that just failed
    When I attempt to retry immediately
    Then the retry should be rejected
    And the reason should indicate "retry cooldown period not elapsed"
    And I should see when the next retry will be available

  @retry @history
  Scenario: Retry attempts are tracked
    Given I have an order with multiple retry attempts
    When I view the retry history
    Then I should see all retry attempts
    And each attempt should show:
      | field            |
      | attemptNumber    |
      | initiatedAt      |
      | resumedFromStep  |
      | outcome          |

  @retry @concurrent-prevention
  Scenario: Concurrent retries are prevented
    Given I have an order with a retry in progress
    When I attempt to start another retry
    Then the second retry should be rejected
    And the reason should indicate "retry already in progress"

  @retry @price-change
  Scenario: Price changes require acknowledgment before retry
    Given I have an order that failed at payment
    And the item prices have increased since the original order
    When I attempt to retry the order
    Then the retry should require acknowledgment of the price change
    And I should see the original and new prices

  @observability @retry-trace-linking
  Scenario: Retry creates a new trace linked to the original failed trace
    Given I have an order that failed due to payment decline
    And the original trace ID is recorded
    And I have updated my payment method
    When I retry the order
    Then a new trace should be created for the retry execution
    And the new trace should include a link to the original failed trace
    And the link should be visible in the observability platform
    And the retry trace should include an attribute "saga.original_trace_id"

  @observability @retry-metrics
  Scenario: Retry metrics are recorded for monitoring retry patterns
    Given I have an order that failed due to payment decline
    And I have updated my payment method
    When I retry the order
    Then the saga.retry.initiated counter should be incremented
    And the saga.retry.success counter should be incremented on success
    And the retry metrics should include tags for:
      | tag               |
      | failed_step       |
      | retry_attempt     |
      | original_order_id |

  @observability @retry-failure-tracking
  Scenario: Failed retry attempts are tracked in metrics
    Given I have an order that failed due to payment decline
    And I have not fixed the payment issue
    When I retry the order
    And the retry fails
    Then the saga.retry.failed counter should be incremented
    And the retry trace should include error attributes
    And both original and retry traces should be queryable together
