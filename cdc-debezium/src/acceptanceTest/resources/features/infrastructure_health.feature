Feature: Infrastructure Health Checks
  As a system administrator
  I want to verify that all CDC infrastructure components are healthy
  So that I can be confident the system is operational before running tests

  Scenario: PostgreSQL database is accessible
    Given PostgreSQL is running and accessible
    Then PostgreSQL should be healthy

  Scenario: Kafka broker is accessible
    Given Kafka is running and accessible
    Then Kafka should be healthy

  Scenario: Kafka Connect is accessible
    Given Kafka Connect is running and accessible
    Then Kafka Connect should be healthy

  Scenario: All CDC infrastructure components are healthy
    Given the CDC infrastructure is healthy
    Then PostgreSQL should be healthy
    And Kafka should be healthy
    And Kafka Connect should be healthy

  @requires-mongodb
  Scenario: MongoDB is accessible
    Given MongoDB is running and accessible
    Then MongoDB should be healthy

  @requires-connector
  Scenario: Debezium PostgreSQL connector is running
    Given Kafka Connect is running and accessible
    And the Debezium connector "postgres-cdc-connector" is registered
    Then Kafka Connect should be healthy
