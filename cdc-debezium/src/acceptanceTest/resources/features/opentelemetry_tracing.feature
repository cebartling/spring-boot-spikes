Feature: OpenTelemetry Tracing
  As a system operator
  I want CDC events to produce OpenTelemetry spans
  So that I can observe and debug the distributed system

  Background:
    Given the tracing infrastructure is initialized
    And the span exporter is cleared

  Scenario: Span created for customer insert event
    When a CDC insert event is processed for tracing
    Then a span should be created with name containing "cdc.public.customer"
    And the span should have kind "CONSUMER"
    And the span should have attribute "messaging.system" with value "kafka"
    And the span should have attribute "messaging.operation" with value "process"
    And the span should have attribute "db.operation" with value "upsert"
    And the span should have status "OK"

  Scenario: Span created for customer update event
    When a CDC update event is processed for tracing
    Then a span should be created with name containing "cdc.public.customer"
    And the span should have attribute "db.operation" with value "upsert"
    And the span should have status "OK"

  Scenario: Span created for customer delete event
    When a CDC delete event is processed for tracing
    Then a span should be created with name containing "cdc.public.customer"
    And the span should have attribute "db.operation" with value "delete"
    And the span should have status "OK"

  Scenario: Span includes Kafka metadata attributes
    When a CDC event is processed with partition 2 and offset 12345
    Then the span should have attribute "messaging.kafka.partition" with value "2"
    And the span should have attribute "messaging.kafka.message.offset" with value "12345"
    And the span should have attribute "messaging.kafka.consumer.group" with value "cdc-consumer-group"

  Scenario: Span includes customer ID attribute
    When a CDC event with customer ID "550e8400-e29b-41d4-a716-446655440001" is processed
    Then the span should have attribute "customer.id" with value "550e8400-e29b-41d4-a716-446655440001"

  Scenario: Tombstone event creates span with ignore operation
    When a tombstone event is processed for tracing
    Then a span should be created with name containing "cdc.public.customer"
    And the span should have attribute "db.operation" with value "ignore"
    And the span should have status "OK"

  Scenario: Processing error records exception on span
    When a CDC event processing fails with an error
    Then the span should have status "ERROR"
    And the span should have recorded an exception
