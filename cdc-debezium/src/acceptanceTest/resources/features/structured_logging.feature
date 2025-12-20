Feature: Structured Logging with Trace Correlation
  As a system operator
  I want CDC events to produce structured JSON logs with trace correlation
  So that I can query and correlate logs with distributed traces

  Background:
    Given the logging infrastructure is initialized
    And the log capture is cleared

  Scenario: Log output is in JSON format
    When a CDC event is processed for logging
    Then the log output should be valid JSON
    And the log should have field "@timestamp"
    And the log should have field "message"
    And the log should have field "level"
    And the log should have field "logger_name"

  Scenario: Logs include Kafka metadata in MDC
    When a CDC event with topic "cdc.public.customer" partition 3 offset 42 is processed
    Then the log should have field "kafka_topic" with value "cdc.public.customer"
    And the log should have field "kafka_partition" with value "3"
    And the log should have field "kafka_offset" with value "42"

  Scenario: Logs include message key in MDC
    When a CDC event with key "550e8400-e29b-41d4-a716-446655440001" is processed for logging
    Then the log should have field "message_key" with value "550e8400-e29b-41d4-a716-446655440001"

  Scenario: Logs include customer ID for non-tombstone events
    When a CDC event for customer "550e8400-e29b-41d4-a716-446655440002" is processed
    Then the log should have field "customer_id" with value "550e8400-e29b-41d4-a716-446655440002"

  Scenario: Successful processing logs include operation and outcome
    When a CDC upsert event is processed successfully
    Then the log should have field "db_operation" with value "upsert"
    And the log should have field "processing_outcome" with value "success"

  Scenario: Delete operation logs include correct operation type
    When a CDC delete event is processed for logging
    Then the log should have field "db_operation" with value "delete"
    And the log should have field "processing_outcome" with value "success"

  Scenario: Tombstone processing logs ignore operation
    When a tombstone event is processed for logging
    Then the log should have field "db_operation" with value "ignore"
    And the log should have field "processing_outcome" with value "success"

  Scenario: Error processing logs include error information
    When a CDC event processing fails with a logging error
    Then the log should have field "processing_outcome" with value "error"
    And the log should have field "error_type" with value "RuntimeException"

  Scenario: Logs include trace context from OpenTelemetry
    When a CDC event is processed within a trace context
    Then the log should have field "trace_id"
    And the trace_id field should not be empty
    And the log should have field "span_id"
    And the span_id field should not be empty
