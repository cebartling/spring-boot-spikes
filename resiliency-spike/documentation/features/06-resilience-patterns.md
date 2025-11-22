# Feature: Resilience Patterns

**Epic:** Platform Reliability
**Status:** Implemented
**Priority:** Critical

## User Story

**As a** platform operator
**I want** critical operations to be protected with resilience patterns
**So that** the system degrades gracefully under failure conditions and protects against cascading failures

## Description

Resilience patterns provide circuit breakers, rate limiters, and retry logic to protect critical service operations. These patterns prevent cascading failures, shed load during high traffic, and automatically retry transient failures. All patterns are observable via health checks and metrics.

## Acceptance Criteria

### Rate Limiting
- [ ] Given a configured rate limit, when requests exceed the limit, then excess requests are rejected immediately
- [ ] Given a rate limit period, when the period refreshes, then the limit resets
- [ ] Given rate limiter metrics, when I query them, then permitted and rejected call counts are available
- [ ] Given multiple rate limiter instances, when I monitor them, then each instance has independent limits

### Retry Logic
- [ ] Given a transient failure, when operation fails, then it is retried up to configured maximum attempts
- [ ] Given retry configuration, when retrying, then exponential backoff is applied between attempts
- [ ] Given non-retryable exceptions, when they occur, then retries are not attempted
- [ ] Given retry exhaustion, when all attempts fail, then fallback is invoked

### Circuit Breaker
- [ ] Given a failure rate threshold, when failures exceed threshold, then circuit opens
- [ ] Given an open circuit, when requests arrive, then they fail fast without calling downstream
- [ ] Given an open circuit wait duration, when duration elapses, then circuit transitions to half-open
- [ ] Given a half-open circuit, when test calls succeed, then circuit closes
- [ ] Given a half-open circuit, when test calls fail, then circuit reopens

### Fallback Behavior
- [ ] Given any resilience pattern failure, when fallback is triggered, then user-friendly error message is returned
- [ ] Given a fallback invocation, when it occurs, then the error is logged with full context
- [ ] Given fallback execution, when it completes, then original exception is wrapped and propagated

### Observability
- [ ] Given resilience patterns, when I check health, then circuit breaker states are reported
- [ ] Given resilience patterns, when I query metrics, then call counts, failure rates, and state are available
- [ ] Given resilience events, when they occur, then they can be recorded in the database
- [ ] Given actuator endpoints, when I query them, then resilience4j metrics are exposed

## Business Rules

1. Rate limiters protect against traffic spikes and denial of service
2. Retries only apply to transient failures (IOException, TimeoutException, TransientDataAccessException)
3. Circuit breakers prevent cascading failures by failing fast
4. Fallbacks must never throw exceptions, only return Mono.error()
5. Resilience instances are named: shoppingCart, cartItem, product, category
6. Annotation order is critical: @RateLimiter → @Retry → @CircuitBreaker
7. All critical write operations must be protected
8. Read operations (findAll, search) may be unprotected for performance

## Protected Operations

### Shopping Cart Service
- Create cart
- Find cart by ID
- Find cart by session
- Update cart status (abandon, convert, expire, restore)

### Cart Item Service
- Add item to cart

### Product Service
- Create product
- Update product
- Find product by ID

### Category Service
- Create category
- Update category
- Find category by ID

## Out of Scope

- Bulkhead pattern
- Time limiter pattern
- Adaptive rate limiting
- Custom retry policies per operation
- Circuit breaker manual control
- Real-time alerting on pattern activation

## Technical Notes

- Resilience4j library provides all patterns
- Patterns configured via application.properties
- Health indicators enabled for monitoring
- Metrics exposed via Micrometer
- State changes are observable and can be persisted
- OpenTelemetry traces include resilience events
