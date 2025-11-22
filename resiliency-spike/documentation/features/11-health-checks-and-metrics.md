# Feature: Health Checks and Metrics

**Epic:** Platform Observability
**Status:** Implemented
**Priority:** High

## User Story

**As a** platform operator
**I want** comprehensive health checks and metrics
**So that** I can monitor application health and performance in production

## Description

Health checks and metrics provide real-time visibility into application status, resource utilization, and operational metrics. Spring Boot Actuator exposes endpoints for health, metrics, circuit breakers, retries, rate limiters, and custom application metrics. These endpoints integrate with monitoring systems for alerting and dashboards.

## Acceptance Criteria

### Health Checks
- [ ] Given the application, when I query /actuator/health, then overall health status is returned
- [ ] Given health components, when details are enabled, then individual component health is shown
- [ ] Given database connectivity, when checked, then R2DBC health is reported
- [ ] Given circuit breakers, when monitored, then their states are included in health
- [ ] Given rate limiters, when monitored, then their health is included

### Metrics Endpoints
- [ ] Given the application, when I query /actuator/metrics, then available metrics are listed
- [ ] Given a specific metric, when queried, then current value and tags are returned
- [ ] Given resilience metrics, when queried, then circuit breaker, retry, and rate limiter metrics are available

### Circuit Breaker Metrics
- [ ] Given circuit breakers, when I query /actuator/circuitbreakers, then all instances and states are shown
- [ ] Given circuit breaker events, when I query /actuator/circuitbreakerevents, then recent events are returned
- [ ] Given circuit breaker metrics, when filtered by name, then instance-specific metrics are shown

### Retry Metrics
- [ ] Given retry instances, when I query /actuator/retries, then all configurations are shown
- [ ] Given retry events, when I query /actuator/retryevents, then recent retry attempts are returned
- [ ] Given retry metrics, when filtered by name, then instance-specific metrics are shown

### Rate Limiter Metrics
- [ ] Given rate limiters, when I query /actuator/ratelimiters, then all instances are shown
- [ ] Given rate limiter events, when I query /actuator/ratelimiterevents, then recent events are returned
- [ ] Given rate limiter metrics, when queried, then available permissions are shown

### Prometheus Integration
- [ ] Given Prometheus endpoint, when enabled, then metrics are exposed in Prometheus format
- [ ] Given Prometheus scraping, when it occurs, then all application metrics are available

## Business Rules

1. Health checks must be fast (< 1 second response time)
2. Metrics must not impact application performance significantly
3. Sensitive information must not be exposed via metrics endpoints
4. Health endpoint should be accessible for load balancer checks
5. Detailed health information may require authentication in production
6. Metrics retention is managed by external monitoring systems

## Exposed Endpoints

### Core Actuator Endpoints
- `/actuator/health` - Overall health status
- `/actuator/info` - Application information
- `/actuator/metrics` - Available metrics list
- `/actuator/metrics/{metric}` - Specific metric details

### Resilience4j Endpoints
- `/actuator/circuitbreakers` - Circuit breaker states
- `/actuator/circuitbreakerevents` - Circuit breaker events
- `/actuator/retries` - Retry configurations
- `/actuator/retryevents` - Retry events
- `/actuator/ratelimiters` - Rate limiter states
- `/actuator/ratelimiterevents` - Rate limiter events

### Optional Endpoints
- `/actuator/prometheus` - Prometheus-formatted metrics

## Key Metrics

### Application Metrics
- JVM memory and garbage collection
- Thread pool usage
- HTTP request counts and timing
- Application uptime

### Database Metrics
- Connection pool utilization
- Query execution times
- Active connections

### Resilience Metrics
- Circuit breaker state (CLOSED, OPEN, HALF_OPEN)
- Retry success/failure counts
- Rate limiter permitted/rejected calls
- Failure rates and response times

## Out of Scope

- Custom business metrics (separate feature)
- Log aggregation
- Alerting rules (handled by monitoring system)
- Metrics dashboards (handled by monitoring system)
- Metrics export to multiple backends
- Distributed tracing metrics (separate feature)

## Technical Notes

- Spring Boot Actuator provides core functionality
- Resilience4j auto-configures its metrics
- Micrometer bridges metrics to various backends
- Health details shown based on management.endpoint.health.show-details
- Metrics filtered and tagged for organization
- CORS may need configuration for browser access
