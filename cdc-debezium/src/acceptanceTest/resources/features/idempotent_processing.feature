Feature: Idempotent Upsert/Delete Processing
  As a CDC consumer
  I want to process CDC events idempotently
  So that duplicate or out-of-order events do not corrupt the materialized data

  Background:
    Given the customer materialized table is empty

  # Acceptance Criteria 3: INSERT events create new rows in materialized table
  Scenario: INSERT event creates new customer in materialized table
    Given a customer does not exist with id "550e8400-e29b-41d4-a716-446655440001"
    When a CDC insert event is processed for customer:
      | id                                   | email              | status | sourceTimestamp |
      | 550e8400-e29b-41d4-a716-446655440001 | insert@example.com | active | 1000            |
    Then a customer should exist in the materialized table with id "550e8400-e29b-41d4-a716-446655440001"
    And the customer should have email "insert@example.com"
    And the customer should have status "active"

  # Acceptance Criteria 4: UPDATE events modify existing rows
  Scenario: UPDATE event modifies existing customer in materialized table
    Given a customer exists in the materialized table:
      | id                                   | email                | status | sourceTimestamp |
      | 550e8400-e29b-41d4-a716-446655440002 | original@example.com | active | 1000            |
    When a CDC update event is processed for customer:
      | id                                   | email               | status   | sourceTimestamp |
      | 550e8400-e29b-41d4-a716-446655440002 | updated@example.com | inactive | 2000            |
    Then the customer should have email "updated@example.com"
    And the customer should have status "inactive"

  # Acceptance Criteria 5: DELETE events remove rows from materialized table
  Scenario: DELETE event removes customer from materialized table
    Given a customer exists in the materialized table:
      | id                                   | email              | status | sourceTimestamp |
      | 550e8400-e29b-41d4-a716-446655440003 | delete@example.com | active | 1000            |
    When a CDC delete event is processed for customer id "550e8400-e29b-41d4-a716-446655440003"
    Then a customer should not exist in the materialized table with id "550e8400-e29b-41d4-a716-446655440003"

  # Acceptance Criteria 6: Duplicate INSERT (same ID) does not create duplicate rows
  Scenario: Duplicate INSERT event updates existing row instead of creating duplicate
    Given a customer exists in the materialized table:
      | id                                   | email             | status | sourceTimestamp |
      | 550e8400-e29b-41d4-a716-446655440004 | first@example.com | active | 1000            |
    When a CDC insert event is processed for customer:
      | id                                   | email              | status   | sourceTimestamp |
      | 550e8400-e29b-41d4-a716-446655440004 | second@example.com | inactive | 2000            |
    Then the customer count in the materialized table should be 1
    And the customer should have email "second@example.com"

  # Acceptance Criteria 8: DELETE on non-existent record succeeds without error
  Scenario: DELETE event on non-existent customer succeeds without error
    Given a customer does not exist with id "550e8400-e29b-41d4-a716-446655440005"
    When a CDC delete event is processed for customer id "550e8400-e29b-41d4-a716-446655440005"
    Then no error should occur
    And a customer should not exist in the materialized table with id "550e8400-e29b-41d4-a716-446655440005"

  # Acceptance Criteria 9: Out-of-order events are handled via source timestamp comparison
  Scenario: Out-of-order event with older timestamp is skipped
    Given a customer exists in the materialized table:
      | id                                   | email             | status | sourceTimestamp |
      | 550e8400-e29b-41d4-a716-446655440006 | newer@example.com | active | 2000            |
    When a CDC update event is processed for customer:
      | id                                   | email             | status   | sourceTimestamp |
      | 550e8400-e29b-41d4-a716-446655440006 | older@example.com | inactive | 1000            |
    Then the customer should have email "newer@example.com"
    And the customer should have status "active"

  Scenario: Event with same timestamp as existing is skipped
    Given a customer exists in the materialized table:
      | id                                   | email                | status | sourceTimestamp |
      | 550e8400-e29b-41d4-a716-446655440007 | existing@example.com | active | 1000            |
    When a CDC update event is processed for customer:
      | id                                   | email            | status   | sourceTimestamp |
      | 550e8400-e29b-41d4-a716-446655440007 | same@example.com | inactive | 1000            |
    Then the customer should have email "existing@example.com"
    And the customer should have status "active"

  Scenario: Event with newer timestamp updates the record
    Given a customer exists in the materialized table:
      | id                                   | email             | status | sourceTimestamp |
      | 550e8400-e29b-41d4-a716-446655440008 | older@example.com | active | 1000            |
    When a CDC update event is processed for customer:
      | id                                   | email             | status   | sourceTimestamp |
      | 550e8400-e29b-41d4-a716-446655440008 | newer@example.com | inactive | 2000            |
    Then the customer should have email "newer@example.com"
    And the customer should have status "inactive"

  # Acceptance Criteria 7: Duplicate UPDATE (same ID, same data) is handled gracefully
  Scenario: Duplicate UPDATE event with same data is handled gracefully
    Given a customer exists in the materialized table:
      | id                                   | email              | status | sourceTimestamp |
      | 550e8400-e29b-41d4-a716-446655440009 | stable@example.com | active | 1000            |
    When a CDC update event is processed for customer:
      | id                                   | email              | status | sourceTimestamp |
      | 550e8400-e29b-41d4-a716-446655440009 | stable@example.com | active | 2000            |
    Then the customer should have email "stable@example.com"
    And the customer should have status "active"
    And no error should occur
