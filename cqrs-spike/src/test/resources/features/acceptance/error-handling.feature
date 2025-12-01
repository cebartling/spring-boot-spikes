@error-handling
Feature: Error Handling and Resilience
  As a system administrator
  I want the system to handle errors gracefully
  So that users receive clear feedback and the system remains stable

  Background:
    Given the system is running

  @happy-path
  Scenario: System health check returns OK
    Given the database is available
    Then the response status should be OK

  @error-handling
  Scenario: Invalid product ID format returns BAD_REQUEST
    When I retrieve a product with ID "not-a-uuid"
    Then the response status should be BAD_REQUEST
    And the response should contain error message "UUID"

  @error-handling
  Scenario: Missing required field returns validation errors
    Given the product catalog is empty
    And I have product data with SKU ""
    When I create the product
    Then the response status should be BAD_REQUEST
    And the response should contain validation error for field "sku"

  @error-handling
  Scenario: Multiple validation errors are returned together
    Given the product catalog is empty
    And I have product data with SKU ""
    And the product has name ""
    And the product has price -100 cents
    When I create the product
    Then the response status should be BAD_REQUEST
    And the response should contain validation error for field "sku"
    And the response should contain validation error for field "name"
    And the response should contain validation error for field "priceCents"

  @error-handling
  Scenario: Operation on non-existent product returns NOT_FOUND
    When I retrieve a product with non-existent ID
    Then the response status should be NOT_FOUND
    And the response should contain error message "Product not found"

  @error-handling
  Scenario: Duplicate SKU returns CONFLICT
    Given a product with SKU "EXISTING-SKU" exists
    When I create a product with SKU "EXISTING-SKU", name "Duplicate Product", and price 1999 cents
    Then the response status should be CONFLICT
    And the response should contain error message "already exists"

  @error-handling
  Scenario: Concurrent modification returns CONFLICT with retry guidance
    Given a product with SKU "VERSION-TEST" and name "Version Test" exists
    When I try to update with an outdated version
    Then the response status should be CONFLICT
    And the response should contain error code "CONCURRENT_MODIFICATION"
    And the response should include retry guidance
