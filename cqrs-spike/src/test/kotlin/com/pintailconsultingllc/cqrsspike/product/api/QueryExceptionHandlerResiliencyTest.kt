package com.pintailconsultingllc.cqrsspike.product.api

import com.pintailconsultingllc.cqrsspike.infrastructure.correlation.CorrelationIdHolder
import com.pintailconsultingllc.cqrsspike.product.query.exception.QueryRateLimitException
import com.pintailconsultingllc.cqrsspike.product.query.exception.QueryServiceUnavailableException
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
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

    private val handler = QueryExceptionHandler()
    private val testCorrelationId = "test-correlation-id-12345"
    private val testPath = "/api/products"

    private fun createExchange(withCorrelationId: Boolean = true): MockServerWebExchange {
        val requestBuilder = MockServerHttpRequest.get(testPath)
        if (withCorrelationId) {
            requestBuilder.header(CorrelationIdHolder.CORRELATION_ID_HEADER, testCorrelationId)
        }
        return MockServerWebExchange.from(requestBuilder.build())
    }

    @Nested
    @DisplayName("AC10: Rate Limit Exception Handling")
    inner class RateLimitHandling {

        @Test
        @DisplayName("should return 429 with correlation ID for QueryRateLimitException")
        fun shouldReturn429WithCorrelationIdForQueryRateLimitException() {
            val exchange = createExchange()
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
            val exchange = createExchange(withCorrelationId = false)
            val ex = QueryRateLimitException("Too many requests.")

            StepVerifier.create(handler.handleQueryRateLimit(ex, exchange))
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
        @DisplayName("should return 503 with correlation ID for QueryServiceUnavailableException")
        fun shouldReturn503WithCorrelationIdForQueryServiceUnavailable() {
            val exchange = createExchange()
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
            val exchange = createExchange()
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
