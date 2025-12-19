Feature: CDC Event Processing
  As a system administrator
  I want the application to process CDC events from Kafka
  So that customer data changes are captured and handled correctly

  Background:
    Given the application is running

  Scenario: Process customer insert event
    When a customer insert CDC event is received
    Then the event should be identified as an upsert operation
    And the event should be acknowledged

  Scenario: Process customer update event
    When a customer update CDC event is received
    Then the event should be identified as an upsert operation
    And the event should be acknowledged

  Scenario: Process customer delete event with __deleted flag
    When a customer delete CDC event with __deleted flag is received
    Then the event should be identified as a delete operation
    And the event should be acknowledged

  Scenario: Process customer delete event with operation code
    When a customer delete CDC event with operation code "d" is received
    Then the event should be identified as a delete operation
    And the event should be acknowledged

  Scenario: Handle Kafka tombstone message
    When a Kafka tombstone message is received
    Then the tombstone should be handled gracefully
    And the message should be acknowledged
