@observability
Feature: Observability Platform
  As a system operator
  I want comprehensive observability capabilities
  So that I can monitor, diagnose, and troubleshoot the system effectively

  Background:
    Given the system is running

  # ========== Health Endpoints ==========

  @smoke @observability
  Scenario: Health endpoint returns system status
    When I check the health endpoint
    Then the response status should be OK
    And the health status should be "UP"

  @observability
  Scenario: Health endpoint includes component details
    When I check the health endpoint
    Then the response status should be OK
    And the health response should include component "db"
    And the health response should include component "diskSpace"

  # ========== Prometheus Metrics Endpoint ==========

  @smoke @observability
  Scenario: Prometheus metrics endpoint is accessible
    When I request the prometheus metrics endpoint
    Then the response status should be OK
    And the response should contain prometheus metrics

  @observability
  Scenario: Prometheus metrics include JVM metrics
    When I request the prometheus metrics endpoint
    Then the response status should be OK
    And the prometheus metrics should include "jvm_memory"
    And the prometheus metrics should include "jvm_threads"
    And the prometheus metrics should include "jvm_gc"

  @observability
  Scenario: Prometheus metrics include HTTP server metrics
    When I request the prometheus metrics endpoint
    Then the response status should be OK
    And the prometheus metrics should include "http_server_requests"

  # ========== Custom Product Metrics ==========

  @observability
  Scenario: Product command metrics are recorded after creating a product
    Given the product catalog is empty
    When I create a product with SKU "METRICS-TEST-001", name "Metrics Test Product", and price 1999 cents
    Then the response status should be CREATED
    When I request the prometheus metrics endpoint
    Then the prometheus metrics should include "product_command"

  @observability
  Scenario: Product query metrics are recorded after querying products
    Given a product with SKU "QUERY-METRICS-001" and name "Query Metrics Test" exists
    When I retrieve the created product
    Then the response status should be OK
    When I request the prometheus metrics endpoint
    Then the prometheus metrics should include "product_query"

  # ========== Metrics Endpoint ==========

  @observability
  Scenario: Metrics endpoint lists available metrics
    When I request the metrics endpoint
    Then the response status should be OK
    And the metrics list should not be empty

  @observability
  Scenario: Individual metric details can be retrieved
    When I request the metric "jvm.memory.used"
    Then the response status should be OK
    And the metric response should contain measurements

  # ========== Correlation ID Propagation ==========

  @observability
  Scenario: Correlation ID is propagated in responses
    Given the product catalog is empty
    When I create a product with correlation ID "test-correlation-123"
    Then the response status should be CREATED
    And the response should include correlation ID header

  @observability
  Scenario: Correlation ID is included in error responses
    When I retrieve a product with non-existent ID using correlation ID "error-correlation-456"
    Then the response status should be NOT_FOUND
    And the response should include correlation ID header

  # ========== System Information ==========

  @observability
  Scenario: Info endpoint returns application information
    When I request the info endpoint
    Then the response status should be OK
