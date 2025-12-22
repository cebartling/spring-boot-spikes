Feature: Order Idempotent Upsert/Delete Processing with Embedded Items
  As a CDC consumer
  I want to process order CDC events idempotently with embedded order items
  So that duplicate or out-of-order events do not corrupt the materialized order data

  Background:
    Given PostgreSQL is running and accessible
    And the order materialized table is empty

  Scenario: INSERT event creates new order in materialized table
    Given an order does not exist with id "770e8400-e29b-41d4-a716-446655440001"
    When a CDC insert event is processed for order:
      | id                                   | customerId                           | status  | totalAmount | sourceTimestamp |
      | 770e8400-e29b-41d4-a716-446655440001 | 550e8400-e29b-41d4-a716-446655440001 | pending | 100.00      | 1000            |
    Then an order should exist in the materialized table with id "770e8400-e29b-41d4-a716-446655440001"
    And the order should have status "PENDING"
    And the order should have total amount "100.00"
    And the order should have 0 items

  Scenario: UPDATE event modifies existing order in materialized table
    Given an order exists in the materialized table:
      | id                                   | customerId                           | status  | totalAmount | sourceTimestamp |
      | 770e8400-e29b-41d4-a716-446655440002 | 550e8400-e29b-41d4-a716-446655440001 | pending | 100.00      | 1000            |
    When a CDC update event is processed for order:
      | id                                   | customerId                           | status    | totalAmount | sourceTimestamp |
      | 770e8400-e29b-41d4-a716-446655440002 | 550e8400-e29b-41d4-a716-446655440001 | confirmed | 150.00      | 2000            |
    Then the order should have status "CONFIRMED"
    And the order should have total amount "150.00"

  Scenario: DELETE event removes order from materialized table
    Given an order exists in the materialized table:
      | id                                   | customerId                           | status  | totalAmount | sourceTimestamp |
      | 770e8400-e29b-41d4-a716-446655440003 | 550e8400-e29b-41d4-a716-446655440001 | pending | 50.00       | 1000            |
    When a CDC delete event is processed for order id "770e8400-e29b-41d4-a716-446655440003"
    Then an order should not exist in the materialized table with id "770e8400-e29b-41d4-a716-446655440003"

  Scenario: Out-of-order event with older timestamp is skipped
    Given an order exists in the materialized table:
      | id                                   | customerId                           | status    | totalAmount | sourceTimestamp |
      | 770e8400-e29b-41d4-a716-446655440004 | 550e8400-e29b-41d4-a716-446655440001 | confirmed | 200.00      | 2000            |
    When a CDC update event is processed for order:
      | id                                   | customerId                           | status  | totalAmount | sourceTimestamp |
      | 770e8400-e29b-41d4-a716-446655440004 | 550e8400-e29b-41d4-a716-446655440001 | pending | 100.00      | 1000            |
    Then the order should have status "CONFIRMED"
    And the order should have total amount "200.00"

  Scenario: Order item is embedded in order document
    Given an order exists in the materialized table:
      | id                                   | customerId                           | status  | totalAmount | sourceTimestamp |
      | 770e8400-e29b-41d4-a716-446655440005 | 550e8400-e29b-41d4-a716-446655440001 | pending | 0.00        | 1000            |
    When a CDC insert event is processed for order item:
      | id                                   | orderId                              | productSku | productName | quantity | unitPrice | lineTotal | sourceTimestamp |
      | 880e8400-e29b-41d4-a716-446655440001 | 770e8400-e29b-41d4-a716-446655440005 | PROD-001   | Widget Pro  | 2        | 29.99     | 59.98     | 2000            |
    Then the order should have 1 items
    And the order should contain item with sku "PROD-001"

  Scenario: Multiple items are properly embedded
    Given an order exists in the materialized table:
      | id                                   | customerId                           | status  | totalAmount | sourceTimestamp |
      | 770e8400-e29b-41d4-a716-446655440006 | 550e8400-e29b-41d4-a716-446655440001 | pending | 0.00        | 1000            |
    When a CDC insert event is processed for order item:
      | id                                   | orderId                              | productSku | productName | quantity | unitPrice | lineTotal | sourceTimestamp |
      | 880e8400-e29b-41d4-a716-446655440002 | 770e8400-e29b-41d4-a716-446655440006 | PROD-001   | Widget Pro  | 2        | 29.99     | 59.98     | 2000            |
    And a CDC insert event is processed for order item:
      | id                                   | orderId                              | productSku | productName | quantity | unitPrice | lineTotal | sourceTimestamp |
      | 880e8400-e29b-41d4-a716-446655440003 | 770e8400-e29b-41d4-a716-446655440006 | PROD-002   | Gadget Plus | 1        | 49.99     | 49.99     | 2001            |
    Then the order should have 2 items
    And the order should contain item with sku "PROD-001"
    And the order should contain item with sku "PROD-002"

  Scenario: Order item update updates embedded document
    Given an order exists in the materialized table:
      | id                                   | customerId                           | status  | totalAmount | sourceTimestamp |
      | 770e8400-e29b-41d4-a716-446655440007 | 550e8400-e29b-41d4-a716-446655440001 | pending | 0.00        | 1000            |
    And an order item exists embedded in the order:
      | id                                   | orderId                              | productSku | productName | quantity | unitPrice | lineTotal | sourceTimestamp |
      | 880e8400-e29b-41d4-a716-446655440004 | 770e8400-e29b-41d4-a716-446655440007 | PROD-001   | Widget Pro  | 1        | 29.99     | 29.99     | 2000            |
    When a CDC update event is processed for order item:
      | id                                   | orderId                              | productSku | productName | quantity | unitPrice | lineTotal | sourceTimestamp |
      | 880e8400-e29b-41d4-a716-446655440004 | 770e8400-e29b-41d4-a716-446655440007 | PROD-001   | Widget Pro  | 5        | 29.99     | 149.95    | 3000            |
    Then the order should have 1 items
    And the order item "880e8400-e29b-41d4-a716-446655440004" should have quantity 5

  Scenario: Order item delete removes from embedded array
    Given an order exists in the materialized table:
      | id                                   | customerId                           | status  | totalAmount | sourceTimestamp |
      | 770e8400-e29b-41d4-a716-446655440008 | 550e8400-e29b-41d4-a716-446655440001 | pending | 0.00        | 1000            |
    And an order item exists embedded in the order:
      | id                                   | orderId                              | productSku | productName | quantity | unitPrice | lineTotal | sourceTimestamp |
      | 880e8400-e29b-41d4-a716-446655440005 | 770e8400-e29b-41d4-a716-446655440008 | PROD-001   | Widget Pro  | 2        | 29.99     | 59.98     | 2000            |
    When a CDC delete event is processed for order item "880e8400-e29b-41d4-a716-446655440005" in order "770e8400-e29b-41d4-a716-446655440008"
    Then the order should have 0 items

  Scenario: Out-of-order item events are handled correctly
    Given an order exists in the materialized table:
      | id                                   | customerId                           | status  | totalAmount | sourceTimestamp |
      | 770e8400-e29b-41d4-a716-446655440009 | 550e8400-e29b-41d4-a716-446655440001 | pending | 0.00        | 1000            |
    And an order item exists embedded in the order:
      | id                                   | orderId                              | productSku | productName | quantity | unitPrice | lineTotal | sourceTimestamp |
      | 880e8400-e29b-41d4-a716-446655440006 | 770e8400-e29b-41d4-a716-446655440009 | PROD-001   | Widget Pro  | 5        | 29.99     | 149.95    | 3000            |
    When a CDC update event is processed for order item:
      | id                                   | orderId                              | productSku | productName | quantity | unitPrice | lineTotal | sourceTimestamp |
      | 880e8400-e29b-41d4-a716-446655440006 | 770e8400-e29b-41d4-a716-446655440009 | PROD-001   | Widget Pro  | 1        | 29.99     | 29.99     | 2000            |
    Then the order item "880e8400-e29b-41d4-a716-446655440006" should have quantity 5

  Scenario: Order update preserves embedded items
    Given an order exists in the materialized table:
      | id                                   | customerId                           | status  | totalAmount | sourceTimestamp |
      | 770e8400-e29b-41d4-a716-446655440010 | 550e8400-e29b-41d4-a716-446655440001 | pending | 59.98       | 1000            |
    And an order item exists embedded in the order:
      | id                                   | orderId                              | productSku | productName | quantity | unitPrice | lineTotal | sourceTimestamp |
      | 880e8400-e29b-41d4-a716-446655440007 | 770e8400-e29b-41d4-a716-446655440010 | PROD-001   | Widget Pro  | 2        | 29.99     | 59.98     | 1500            |
    When a CDC update event is processed for order:
      | id                                   | customerId                           | status    | totalAmount | sourceTimestamp |
      | 770e8400-e29b-41d4-a716-446655440010 | 550e8400-e29b-41d4-a716-446655440001 | confirmed | 59.98       | 2000            |
    Then the order should have status "CONFIRMED"
    And the order should have 1 items
    And the order should contain item with sku "PROD-001"
