Feature: OpenTelemetry Metrics
  As a system operator
  I want CDC events to produce OpenTelemetry metrics
  So that I can monitor throughput, latency, and error rates

  Background:
    Given the metrics infrastructure is initialized
    And the metric reader is cleared

  Scenario: Counter increments for processed insert event
    When a CDC insert event is processed for metrics
    Then the counter "cdc.messages.processed" should have value 1
    And the counter should have attribute "operation" with value "upsert"
    And the counter should have attribute "topic" with value "cdc.public.customer"

  Scenario: Counter increments for processed update event
    When a CDC update event is processed for metrics
    Then the counter "cdc.messages.processed" should have value 1
    And the counter should have attribute "operation" with value "upsert"

  Scenario: Counter increments for processed delete event
    When a CDC delete event is processed for metrics
    Then the counter "cdc.messages.processed" should have value 1
    And the counter should have attribute "operation" with value "delete"

  Scenario: Counter increments for tombstone event
    When a tombstone event is processed for metrics
    Then the counter "cdc.messages.processed" should have value 1
    And the counter should have attribute "operation" with value "ignore"

  Scenario: Latency histogram records processing time
    When a CDC event is processed with simulated latency
    Then the histogram "cdc.processing.latency" should have recorded a value
    And the histogram should have attribute "topic" with value "cdc.public.customer"

  Scenario: Error counter increments on processing failure
    When a CDC event processing fails for metrics
    Then the counter "cdc.messages.errors" should have value 1
    And the counter should have attribute "topic" with value "cdc.public.customer"

  Scenario: Database upsert counter increments
    When a CDC upsert operation is recorded
    Then the counter "cdc.db.upserts" should have value 1

  Scenario: Database delete counter increments
    When a CDC delete operation is recorded
    Then the counter "cdc.db.deletes" should have value 1

  Scenario: Multiple events increment counters correctly
    When 3 CDC insert events are processed for metrics
    Then the counter "cdc.messages.processed" should have total value 3

  Scenario: Partition attribute is recorded correctly
    When a CDC event is processed on partition 5 for metrics
    Then the counter "cdc.messages.processed" should have attribute "partition" with value "5"
