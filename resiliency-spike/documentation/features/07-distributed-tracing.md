# Feature: Distributed Tracing and Observability

**Epic:** Platform Observability
**Status:** Implemented
**Priority:** High

## User Story

**As a** platform operator
**I want** complete visibility into request flows and performance
**So that** I can troubleshoot issues, optimize performance, and understand system behavior

## Description

Distributed tracing provides end-to-end visibility into request flows using OpenTelemetry and OTLP. All HTTP requests, database queries, messaging operations, and resilience pattern activations are automatically instrumented. Traces are exported to pluggable backends (Jaeger or SigNoz) with W3C trace context propagation.

## Acceptance Criteria

### Trace Generation
- [ ] Given an HTTP request, when it is processed, then a trace is created with a unique trace ID
- [ ] Given a request flow, when it executes, then all operations are captured as spans
- [ ] Given nested operations, when they execute, then parent-child span relationships are maintained
- [ ] Given trace context, when it propagates, then W3C Trace Context standard is used

### Automatic Instrumentation
- [ ] Given HTTP endpoints, when they are called, then request/response details are traced
- [ ] Given database operations, when they execute, then R2DBC queries are traced with parameters
- [ ] Given messaging operations, when they occur, then Pulsar publish/consume is traced
- [ ] Given resilience patterns, when they activate, then circuit breaker, retry, and rate limiter events are traced

### Trace Export
- [ ] Given trace data, when it is collected, then it is exported via OTLP protocol
- [ ] Given OTLP configuration, when backend is Jaeger, then traces are sent to Jaeger endpoint
- [ ] Given OTLP configuration, when backend is SigNoz, then traces are sent to SigNoz endpoint
- [ ] Given trace export, when it occurs, then compression is applied (gzip)

### Trace Context in Logs
- [ ] Given a log entry, when it is written, then trace ID and span ID are included
- [ ] Given trace context, when correlating logs, then all logs for a request share the same trace ID
- [ ] Given log patterns, when viewing logs, then trace context is clearly visible

### Trace Sampling
- [ ] Given sampling configuration, when set to 100%, then all requests are traced
- [ ] Given sampling configuration, when adjusted, then appropriate percentage of requests is traced

### Observability Endpoints
- [ ] Given actuator endpoints, when I query /actuator/health, then tracing health is reported
- [ ] Given actuator endpoints, when I query /actuator/metrics, then tracing metrics are available
- [ ] Given Prometheus endpoint, when enabled, then metrics are exposed in Prometheus format

## Business Rules

1. All production traffic should be sampled (configurable percentage)
2. Trace IDs must be unique and globally distributed
3. Sensitive data (passwords, tokens) must not be included in traces
4. Trace export must not block application threads
5. Trace backend can be switched via configuration without code changes
6. Trace data retention is managed by the backend system (Jaeger/SigNoz)

## Traced Operations

### HTTP Layer
- All REST API endpoints
- Request method, path, status code, duration
- Request and response headers (excluding sensitive data)

### Data Layer
- R2DBC connection acquisition
- SQL query execution
- Query parameters (sanitized)
- Row counts and timing

### Messaging Layer
- Pulsar message publish
- Pulsar message consume
- Topic names and message metadata

### Resilience Layer
- Circuit breaker state transitions
- Retry attempts and outcomes
- Rate limiter permit/deny decisions

## Out of Scope

- Custom span attributes for business metrics
- Trace-based alerting
- Distributed transaction tracing
- Log aggregation (separate from tracing)
- Metrics aggregation (separate from tracing)
- Real-time trace analysis
- Trace-based testing

## Technical Notes

- Micrometer Tracing Bridge provides abstraction layer
- OpenTelemetry SDK handles trace collection and export
- OTLP HTTP endpoint for trace export (default)
- Baggage propagation enabled for custom context
- Log pattern includes trace/span IDs: [appName,traceId,spanId]
- Backend selection via management.otlp.tracing.endpoint property
