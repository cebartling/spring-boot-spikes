package com.pintailconsultingllc.cqrsspike.product.api

import com.pintailconsultingllc.cqrsspike.infrastructure.correlation.CorrelationIdHolder
import com.pintailconsultingllc.cqrsspike.product.query.exception.QueryRateLimitException
import com.pintailconsultingllc.cqrsspike.product.query.exception.QueryServiceUnavailableException
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.ratelimiter.RateLimiter
import io.github.resilience4j.ratelimiter.RequestNotPermitted
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.server.RequestPath
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.web.server.ServerWebExchange
import reactor.test.StepVerifier

/**
 * Unit tests for QueryExceptionHandler resiliency exception handling.
 *
 * Tests AC10: Resiliency and Error Handling
 * - All errors are logged with correlation IDs
 * - Rate limit responses include Retry-After header
 * - Circuit breaker responses include retry guidance
 */
@DisplayName("QueryExceptionHandler Resiliency - AC10")
class QueryExceptionHandlerResiliencyTest {

    private lateinit var handler: QueryExceptionHandler
    private lateinit var exchange: ServerWebExchange
    private lateinit var request: ServerHttpRequest
    private lateinit var headers: HttpHeaders
    private lateinit var path: RequestPath

    private val testCorrelationId = "test-correlation-id-12345"
    private val testPath = "/api/products"

    @BeforeEach
    fun setUp() {
        handler = QueryExceptionHandler()
        exchange = mock()
        request = mock()
        headers = HttpHeaders()
        path = mock()

        whenever(exchange.request).thenReturn(request)
        whenever(request.headers).thenReturn(headers)
        whenever(request.path).thenReturn(path)
        whenever(path.value()).thenReturn(testPath)
    }

    @Nested
    @DisplayName("AC10: Rate Limit Exception Handling")
    inner class RateLimitHandling {

        @Test
        @DisplayName("should return 429 with correlation ID for RequestNotPermitted")
        fun shouldReturn429WithCorrelationIdForRequestNotPermitted() {
            headers.add(CorrelationIdHolder.CORRELATION_ID_HEADER, testCorrelationId)
            val rateLimiter = mock<RateLimiter>()
            whenever(rateLimiter.name).thenReturn("productQueries")
            val ex = RequestNotPermitted.createRequestNotPermitted(rateLimiter)

            StepVerifier.create(handler.handleRateLimitExceeded(ex, exchange))
                .expectNextMatches { response ->
                    response.statusCode == HttpStatus.TOO_MANY_REQUESTS &&
                        response.body?.correlationId == testCorrelationId &&
                        response.body?.code == "RATE_LIMIT_EXCEEDED" &&
                        response.body?.retryAfter == 2 &&
                        response.body?.retryGuidance != null &&
                        response.headers["Retry-After"]?.get(0) == "2"
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("should return 429 with correlation ID for QueryRateLimitException")
        fun shouldReturn429WithCorrelationIdForQueryRateLimitException() {
            headers.add(CorrelationIdHolder.CORRELATION_ID_HEADER, testCorrelationId)
            val ex = QueryRateLimitException("Too many requests. Please try again later.")

            StepVerifier.create(handler.handleQueryRateLimit(ex, exchange))
                .expectNextMatches { response ->
                    response.statusCode == HttpStatus.TOO_MANY_REQUESTS &&
                        response.body?.correlationId == testCorrelationId &&
                        response.body?.code == "RATE_LIMIT_EXCEEDED" &&
                        response.body?.message == "Too many requests. Please try again later." &&
                        response.headers["Retry-After"]?.get(0) == "2"
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("should handle missing correlation ID")
        fun shouldHandleMissingCorrelationId() {
            val rateLimiter = mock<RateLimiter>()
            whenever(rateLimiter.name).thenReturn("productQueries")
            val ex = RequestNotPermitted.createRequestNotPermitted(rateLimiter)

            StepVerifier.create(handler.handleRateLimitExceeded(ex, exchange))
                .expectNextMatches { response ->
                    response.statusCode == HttpStatus.TOO_MANY_REQUESTS &&
                        response.body?.correlationId == null &&
                        response.body?.code == "RATE_LIMIT_EXCEEDED"
                }
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("AC10: Circuit Breaker Exception Handling")
    inner class CircuitBreakerHandling {

        @Test
        @DisplayName("should return 503 with correlation ID for CallNotPermittedException")
        fun shouldReturn503WithCorrelationIdForCallNotPermitted() {
            headers.add(CorrelationIdHolder.CORRELATION_ID_HEADER, testCorrelationId)
            val circuitBreaker = mock<CircuitBreaker>()
            whenever(circuitBreaker.name).thenReturn("productQueries")
            val ex = CallNotPermittedException.createCallNotPermittedException(circuitBreaker)

            StepVerifier.create(handler.handleCircuitBreakerOpen(ex, exchange))
                .expectNextMatches { response ->
                    response.statusCode == HttpStatus.SERVICE_UNAVAILABLE &&
                        response.body?.correlationId == testCorrelationId &&
                        response.body?.circuitBreakerState == "OPEN" &&
                        response.body?.retryAfter == 15 &&
                        response.headers["Retry-After"]?.get(0) == "15"
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("should return 503 with correlation ID for QueryServiceUnavailableException")
        fun shouldReturn503WithCorrelationIdForQueryServiceUnavailable() {
            headers.add(CorrelationIdHolder.CORRELATION_ID_HEADER, testCorrelationId)
            val ex = QueryServiceUnavailableException("Query service temporarily unavailable. Please try again later.")

            StepVerifier.create(handler.handleQueryServiceUnavailable(ex, exchange))
                .expectNextMatches { response ->
                    response.statusCode == HttpStatus.SERVICE_UNAVAILABLE &&
                        response.body?.correlationId == testCorrelationId &&
                        response.body?.message == "Query service temporarily unavailable. Please try again later." &&
                        response.body?.retryAfter == 15 &&
                        response.headers["Retry-After"]?.get(0) == "15"
                }
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("AC10: Generic Exception Handling")
    inner class GenericExceptionHandling {

        @Test
        @DisplayName("should return 500 with correlation ID for unexpected errors")
        fun shouldReturn500WithCorrelationIdForUnexpectedErrors() {
            headers.add(CorrelationIdHolder.CORRELATION_ID_HEADER, testCorrelationId)
            val ex = RuntimeException("Unexpected error")

            StepVerifier.create(handler.handleGenericError(ex, exchange))
                .expectNextMatches { response ->
                    response.statusCode == HttpStatus.INTERNAL_SERVER_ERROR &&
                        response.body?.correlationId == testCorrelationId &&
                        response.body?.code == "INTERNAL_ERROR" &&
                        response.body?.message == "An unexpected error occurred"
                }
                .verifyComplete()
        }
    }
}
