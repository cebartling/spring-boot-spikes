Feature: Observability Infrastructure
  As a system administrator
  I want to verify that observability components are operational
  So that I can monitor traces and metrics from the CDC consumer

  @requires-observability
  Scenario: Jaeger UI is accessible
    Given the observability infrastructure is running
    When I navigate to the Jaeger UI
    Then the Jaeger search page should be displayed
    And the service dropdown should be visible

  @requires-observability
  Scenario: Jaeger displays available services
    Given the observability infrastructure is running
    And traces have been sent to the collector
    When I navigate to the Jaeger UI
    Then the service dropdown should contain available services

  @requires-observability
  Scenario: Prometheus UI is accessible
    Given the observability infrastructure is running
    When I navigate to the Prometheus UI
    Then the Prometheus query page should be displayed
    And the query input should be visible

  @requires-observability
  Scenario: Prometheus targets are healthy
    Given the observability infrastructure is running
    When I navigate to the Prometheus targets page
    Then the "otel-collector" target should be UP
    And the "otel-collector-internal" target should be UP

  @requires-observability
  Scenario: Traces sent via OTLP appear in Jaeger
    Given the observability infrastructure is running
    When I send a test trace with service name "acceptance-test-service"
    And I navigate to the Jaeger UI
    And I search for traces from service "acceptance-test-service"
    Then at least one trace should be displayed

  @requires-observability
  Scenario: Prometheus can query OTel Collector metrics
    Given the observability infrastructure is running
    When I navigate to the Prometheus UI
    And I execute the query "otelcol_exporter_sent_spans"
    Then the query results should contain metrics data

  @requires-observability
  Scenario: Jaeger trace details are viewable
    Given the observability infrastructure is running
    And I send a test trace with service name "detail-test-service"
    When I navigate to the Jaeger UI
    And I search for traces from service "detail-test-service"
    And I click on the first trace
    Then the trace detail view should be displayed
    And the span timeline should be visible
