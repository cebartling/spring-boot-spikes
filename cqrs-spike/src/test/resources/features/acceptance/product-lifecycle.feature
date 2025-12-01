@product-lifecycle @smoke
Feature: Product Lifecycle Management
  As a product administrator
  I want to manage products through their lifecycle
  So that I can maintain an accurate product catalog

  Background:
    Given the system is running
    And the product catalog is empty

  @happy-path
  Scenario: Create a new product with minimal attributes
    When I create a product with SKU "TEST-001", name "Test Product", and price 1999 cents
    Then the response status should be CREATED
    And the product should be created successfully
    And the product should have SKU "TEST-001"
    And the product should have name "Test Product"
    And the product should have price 1999 cents
    And the product status should be "DRAFT"
    And the product version should be 1

  @happy-path
  Scenario: Create a product with full attributes
    When I create a product with SKU "FULL-001", name "Full Product", description "A complete product with all attributes", and price 2999 cents
    Then the response status should be CREATED
    And the product should be created successfully
    And the product should have SKU "FULL-001"
    And the product should have name "Full Product"

  @happy-path
  Scenario: Update product details
    Given a product with SKU "UPDATE-001" and name "Original Name" exists
    And I use expected version 1
    When I update the product name to "Updated Name"
    Then the response status should be OK
    And the product should be updated successfully
    And the product should have name "Updated Name"

  @happy-path
  Scenario: Change product price
    Given a product with SKU "PRICE-001" and price 1000 cents exists
    And I use expected version 1
    When I change the product price to 1100 cents
    Then the response status should be OK
    And the product should have price 1100 cents

  @happy-path
  Scenario: Activate a draft product
    Given a product with SKU "ACTIVATE-001" exists
    And I use expected version 1
    When I activate the product
    Then the response status should be OK
    And the product should be activated

  @happy-path
  Scenario: Discontinue an active product
    Given an active product with SKU "DISCONTINUE-001" exists
    And I use expected version 2
    When I discontinue the product
    Then the response status should be OK
    And the product should be discontinued

  @happy-path
  Scenario: Discontinue a product with reason
    Given an active product with SKU "DISCONTINUE-002" exists
    And I use expected version 2
    When I discontinue the product with reason "End of life"
    Then the response status should be OK
    And the product should be discontinued

  @happy-path
  Scenario: Delete a product
    Given a product with SKU "DELETE-001" exists
    And I use expected version 1
    When I delete the product
    Then the response status should be OK
    And the product should be deleted

  @error-handling
  Scenario: Attempt to create product with duplicate SKU
    Given a product with SKU "DUPLICATE-001" exists
    When I try to create a product with duplicate SKU "DUPLICATE-001"
    Then the response status should be CONFLICT
    And the response should contain error message "already exists"
