package com.pintailconsultingllc.cqrsspike.product.query.exception

/**
 * Exception thrown when query rate limit is exceeded.
 *
 * Implements AC10: "Rate limiting prevents abuse of query endpoints"
 */
class QueryRateLimitException(message: String) : RuntimeException(message)

/**
 * Exception thrown when query service is unavailable (circuit breaker open).
 *
 * Implements AC10: "Circuit breaker pattern protects database operations"
 */
class QueryServiceUnavailableException(message: String) : RuntimeException(message)
