@event-sourcing
Feature: Event Sourcing Verification
  As a system auditor
  I want to verify that events are properly captured
  So that I can trust the event sourcing implementation

  Background:
    Given the system is running
    And the product catalog is empty

  @event-sourcing @happy-path
  Scenario: ProductCreated event is emitted on product creation
    When I create a product with SKU "EVENT-001", name "Event Test Product", and price 1999 cents
    Then the response status should be CREATED
    And the product should be created successfully
    And the product version should be 1

  @event-sourcing @happy-path
  Scenario: ProductUpdated event increments version
    Given a product with SKU "EVENT-002" and name "Original Name" exists
    And I use expected version 1
    When I update the product name to "Updated Name"
    Then the response status should be OK
    And the product version should be 2

  @event-sourcing @happy-path
  Scenario: ProductActivated event increments version
    Given a product with SKU "EVENT-003" exists
    And I use expected version 1
    When I activate the product
    Then the response status should be OK
    And the product version should be 2

  @event-sourcing @happy-path
  Scenario: ProductPriceChanged event increments version
    Given a product with SKU "EVENT-004" and price 1000 cents exists
    And I use expected version 1
    When I change the product price to 1200 cents
    Then the response status should be OK
    And the product version should be 2

  @event-sourcing @happy-path
  Scenario: Multiple events increment version correctly
    Given a product with SKU "EVENT-005" exists
    And I use expected version 1
    When I activate the product
    Then the product version should be 2
    When I use expected version 2
    # Price change within 20% threshold (1999 to 2300 = ~15%)
    And I change the product price to 2300 cents
    Then the product version should be 3
    When I use expected version 3
    And I discontinue the product
    Then the product version should be 4

  @event-sourcing @happy-path
  Scenario: Read model reflects latest state after events
    Given a product with SKU "EVENT-006", name "Initial Name", and price 1000 cents exists
    And I use expected version 1
    When I update the product name to "Final Name"
    And I retrieve the product by ID
    Then the product should have name "Final Name"
    And the product version should be 2
