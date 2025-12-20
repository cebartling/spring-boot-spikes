Feature: Failure and Recovery Testing
  As a CDC pipeline operator
  I want the system to handle failures gracefully
  So that data consistency is maintained during component restarts and errors

  Background:
    Given the CDC infrastructure is healthy
    And the Debezium connector "postgres-cdc-connector" is registered
    And the customer materialized table is empty

  @failure-recovery @kafka-connect-restart
  Scenario: Kafka Connect restart with database changes during downtime
    Given I record the initial customer count
    And I stop the Kafka Connect service
    And I wait for Kafka Connect to be unavailable
    When I insert customers while Kafka Connect is down:
      | email                    | status |
      | downtime-1@example.com   | active |
      | downtime-2@example.com   | active |
    And I start the Kafka Connect service
    And I wait for the Debezium connector to resume processing
    Then all CDC events should be processed within 60 seconds
    And the materialized customer count should increase by 2
    And a customer should exist with email "downtime-1@example.com"
    And a customer should exist with email "downtime-2@example.com"

  @failure-recovery @consumer-restart
  Scenario: Consumer restart with backlog of CDC events
    Given I generate 20 CDC events while consumer processing is paused:
      | email_prefix | status |
      | backlog      | active |
    And I verify Kafka consumer lag is greater than 0
    When I resume consumer processing
    And I wait for consumer to process all pending events within 60 seconds
    Then the Kafka consumer lag should be 0
    And 20 customers should exist with email prefix "backlog"

  @failure-recovery @kafka-restart
  Scenario: Kafka restart and recovery
    Given I record the current Kafka consumer group position
    When I stop the Kafka service
    And I insert a customer into the source table:
      | email                    | status |
      | kafka-down@example.com   | active |
    And I start the Kafka service
    And I wait for Kafka to become healthy within 60 seconds
    And I wait for the CDC pipeline to recover within 90 seconds
    Then a customer should exist with email "kafka-down@example.com"

  @failure-recovery @error-handling
  Scenario: Consumer continues processing after handling an error
    Given I have valid customers in the source table:
      | email                    | status |
      | before-error@example.com | active |
    When I wait for the customer to be processed
    And an invalid CDC message is injected into the topic
    And I insert a customer into the source table:
      | email                   | status |
      | after-error@example.com | active |
    And I wait for the customer to be processed
    Then a customer should exist with email "before-error@example.com"
    And a customer should exist with email "after-error@example.com"
    And the error metrics counter should have incremented

  @failure-recovery @schema-evolution
  Scenario: Schema evolution with new nullable column
    Given a customer exists in the source table:
      | email                      | status |
      | before-schema@example.com  | active |
    When I add a nullable column "phone" to the customer table
    And I insert a customer with the new column:
      | email                      | status | phone        |
      | new-schema@example.com     | active | +1-555-0100  |
    And I insert a customer without the new column:
      | email                      | status |
      | old-schema@example.com     | active |
    Then all customers should be processed without errors
    And a customer should exist with email "new-schema@example.com"
    And a customer should exist with email "old-schema@example.com"
