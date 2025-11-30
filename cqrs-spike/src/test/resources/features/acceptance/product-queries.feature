@product-queries
Feature: Product Query Operations
  As a product catalog user
  I want to query products with various filters and options
  So that I can find the products I need

  Background:
    Given the system is running
    And the product catalog is empty

  @happy-path
  Scenario: Retrieve a product by ID
    Given a product with SKU "QUERY-001" and name "Query Test Product" exists
    When I retrieve the product by ID
    Then the response status should be OK
    And I should receive the product details
    And the returned product should have ID matching the created product

  @error-handling
  Scenario: Retrieve a non-existent product
    When I retrieve a product with non-existent ID
    Then the response status should be NOT_FOUND

  @happy-path
  Scenario: List all products with default pagination
    Given there are 5 products in the catalog
    When I list all products
    Then the response status should be OK
    And I should receive 5 products
    And the total count should be 5

  @happy-path
  Scenario: List products with custom pagination
    Given there are 25 products in the catalog
    When I list products with page 0 and size 10
    Then the response status should be OK
    And I should receive 10 products
    And the total count should be 25
    And I should be on page 0
    And there should be 3 total pages

  @happy-path
  Scenario: Navigate to second page of products
    Given there are 25 products in the catalog
    When I list products with page 1 and size 10
    Then the response status should be OK
    And I should receive 10 products
    And I should be on page 1

  @happy-path
  Scenario: Filter products by ACTIVE status
    Given there are 3 active products in the catalog
    And there are 2 draft products in the catalog
    When I list products filtered by status "ACTIVE"
    Then the response status should be OK
    And I should receive 3 products
    And all returned products should have status "ACTIVE"

  @happy-path
  Scenario: Filter products by DRAFT status
    Given there are 3 active products in the catalog
    And there are 2 draft products in the catalog
    When I list products filtered by status "DRAFT"
    Then the response status should be OK
    And I should receive 2 products
    And all returned products should have status "DRAFT"

  @happy-path
  Scenario: Search products by name
    Given products with names containing "Widget" exist
    When I search for products with query "Widget"
    Then the response status should be OK
    And all returned products should contain "Widget" in their name or description

  @happy-path
  Scenario: Sort products by name ascending
    Given there are 5 products in the catalog
    When I list products sorted by "name" in "ASC" order
    Then the response status should be OK
    And the products should be sorted by "name" in "ASC" order

  @happy-path
  Scenario: Sort products by name descending
    Given there are 5 products in the catalog
    When I list products sorted by "name" in "DESC" order
    Then the response status should be OK
    And the products should be sorted by "name" in "DESC" order

  @happy-path
  Scenario: Sort products by price ascending
    Given there are 5 products in the catalog
    When I list products sorted by "price" in "ASC" order
    Then the response status should be OK
    And the products should be sorted by "price" in "ASC" order

  @edge-case
  Scenario: Search with no matching results
    Given a product with SKU "NOMATCH-001" and name "Test Product" exists
    When I search for products with query "xyz123nonexistent"
    Then the response status should be OK
    And no products should be returned

  @edge-case
  Scenario: Empty catalog returns empty list
    When I list all products
    Then the response status should be OK
    And no products should be returned
    And the total count should be 0
