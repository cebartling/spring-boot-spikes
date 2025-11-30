@business-rules
Feature: Business Rules Validation
  As a product administrator
  I want the system to enforce business rules
  So that product data integrity is maintained

  Background:
    Given the system is running
    And the product catalog is empty

  # AC9: Product name validation
  @validation @business-rule
  Scenario: Reject product with empty name
    When I try to create a product with empty name
    Then the response status should be BAD_REQUEST
    And the product should be rejected with validation error
    And the validation error should mention "Name is required"

  @validation @business-rule
  Scenario: Reject product with name exceeding 255 characters
    When I try to create a product with name longer than 255 characters
    Then the response status should be BAD_REQUEST
    And the product should be rejected with validation error
    And the validation error should mention "255 characters"

  # AC9: SKU validation
  @validation @business-rule
  Scenario: Reject product with empty SKU
    When I try to create a product with empty SKU
    Then the response status should be BAD_REQUEST
    And the product should be rejected with validation error
    And the validation error should mention "SKU is required"

  @validation @business-rule
  Scenario: Reject product with SKU shorter than 3 characters
    When I try to create a product with SKU shorter than 3 characters
    Then the response status should be BAD_REQUEST
    And the product should be rejected with validation error
    And the validation error should mention "3-50 characters"

  @validation @business-rule
  Scenario: Reject product with SKU longer than 50 characters
    When I try to create a product with SKU longer than 50 characters
    Then the response status should be BAD_REQUEST
    And the product should be rejected with validation error
    And the validation error should mention "3-50 characters"

  @validation @business-rule
  Scenario: Reject product with SKU containing special characters
    When I try to create a product with SKU containing special characters
    Then the response status should be BAD_REQUEST
    And the product should be rejected with validation error
    And the validation error should mention "alphanumeric"

  # AC9: Price validation
  @validation @business-rule
  Scenario: Reject product with negative price
    When I try to create a product with negative price
    Then the response status should be BAD_REQUEST
    And the product should be rejected with validation error
    And the validation error should mention "positive"

  @validation @business-rule
  Scenario: Reject product with zero price
    When I try to create a product with zero price
    Then the response status should be BAD_REQUEST
    And the product should be rejected with validation error
    And the validation error should mention "positive"

  # AC9: Description validation
  @validation @business-rule
  Scenario: Reject product with description exceeding 5000 characters
    When I try to create a product with description longer than 5000 characters
    Then the response status should be BAD_REQUEST
    And the product should be rejected with validation error
    And the validation error should mention "5000 characters"

  # AC9: Status transitions
  @status-transition @business-rule
  Scenario: Activate a draft product succeeds
    Given I have a draft product
    When I try to activate a draft product
    Then the response status should be OK
    And the product should be activated

  @status-transition @business-rule
  Scenario: Discontinue an active product succeeds
    Given I have an active product with price 1000 cents
    When I try to discontinue an active product
    Then the response status should be OK
    And the product should be discontinued

  @status-transition @business-rule
  Scenario: Reactivating a discontinued product fails
    Given I have a discontinued product
    When I try to reactivate a discontinued product
    Then the status transition should be rejected
    And the error should indicate invalid status transition

  # AC9: Price change confirmation for >20% changes
  @price-change @business-rule
  Scenario: Large price change without confirmation is rejected
    Given I have an active product with price 1000 cents
    When I try to change the price by more than 20% without confirmation
    Then the price change should require confirmation

  @price-change @business-rule
  Scenario: Large price change with confirmation succeeds
    Given I have an active product with price 1000 cents
    When I try to change the price by more than 20% with confirmation
    Then the price change should be accepted

  @price-change @business-rule
  Scenario: Small price change does not require confirmation
    Given I have an active product with price 1000 cents
    When I try to change the price by less than 20%
    Then the price change should be accepted

  # AC9: Concurrent modification handling
  @concurrency @business-rule
  Scenario: Concurrent modification conflict returns HTTP 409
    Given a product with SKU "CONFLICT-001" and name "Original" exists
    When I try to update with an outdated version
    Then the response should indicate concurrent modification conflict
    And the response should include retry guidance
