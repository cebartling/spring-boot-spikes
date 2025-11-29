package com.pintailconsultingllc.cqrsspike.product.api

import com.pintailconsultingllc.cqrsspike.infrastructure.correlation.CorrelationIdHolder
import com.pintailconsultingllc.cqrsspike.product.command.exception.ConcurrentModificationException
import com.pintailconsultingllc.cqrsspike.product.command.handler.CommandRateLimitException
import com.pintailconsultingllc.cqrsspike.product.command.handler.CommandServiceUnavailableException
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import reactor.test.StepVerifier
import java.util.UUID

/**
 * Unit tests for CommandExceptionHandler with correlation ID support.
 *
 * Tests AC10: Resiliency and Error Handling
 * - All errors are logged with correlation IDs
 * - Concurrent modification conflicts return HTTP 409 with retry guidance
 */
@DisplayName("CommandExceptionHandler Correlation ID - AC10")
class CommandExceptionHandlerCorrelationTest {

    private val handler = CommandExceptionHandler()
    private val testCorrelationId = "test-correlation-id-67890"
    private val testProductId = UUID.randomUUID()
    private val testPath = "/api/products/$testProductId"

    private fun createExchange(withCorrelationId: Boolean = true): MockServerWebExchange {
        val requestBuilder = MockServerHttpRequest.put(testPath)
        if (withCorrelationId) {
            requestBuilder.header(CorrelationIdHolder.CORRELATION_ID_HEADER, testCorrelationId)
        }
        return MockServerWebExchange.from(requestBuilder.build())
    }

    @Nested
    @DisplayName("AC10: Concurrent Modification Handling")
    inner class ConcurrentModificationHandling {

        @Test
        @DisplayName("should return 409 with correlation ID and retry guidance")
        fun shouldReturn409WithCorrelationIdAndRetryGuidance() {
            val exchange = createExchange()
            val ex = ConcurrentModificationException(testProductId, 1, 2)

            StepVerifier.create(handler.handleConcurrentModification(ex, exchange))
                .expectNextMatches { response ->
                    response.statusCode == HttpStatus.CONFLICT &&
                        response.body?.correlationId == testCorrelationId &&
                        response.body?.code == "CONCURRENT_MODIFICATION" &&
                        response.body?.currentVersion == 2L &&
                        response.body?.expectedVersion == 1L &&
                        response.body?.retryGuidance != null &&
                        response.body?.recommendedAction?.contains(testProductId.toString()) == true
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("should include retry guidance in conflict response")
        fun shouldIncludeRetryGuidanceInConflictResponse() {
            val exchange = createExchange()
            val ex = ConcurrentModificationException(testProductId, 5, 10)

            StepVerifier.create(handler.handleConcurrentModification(ex, exchange))
                .expectNextMatches { response ->
                    response.body?.retryGuidance == "Fetch the latest version of the resource and retry with the updated expectedVersion" &&
                        response.body?.recommendedAction?.contains("expectedVersion=10") == true
                }
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("AC10: Rate Limit Exception Handling")
    inner class RateLimitHandling {

        @Test
        @DisplayName("should return 429 with correlation ID for CommandRateLimitException")
        fun shouldReturn429WithCorrelationIdForCommandRateLimitException() {
            val exchange = createExchange()
            val ex = CommandRateLimitException("Too many requests. Please try again later.")

            StepVerifier.create(handler.handleCommandRateLimit(ex, exchange))
                .expectNextMatches { response ->
                    response.statusCode == HttpStatus.TOO_MANY_REQUESTS &&
                        response.body?.correlationId == testCorrelationId &&
                        response.body?.code == "RATE_LIMIT_EXCEEDED" &&
                        response.body?.message == "Too many requests. Please try again later." &&
                        response.headers["Retry-After"]?.get(0) == "5"
                }
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("AC10: Circuit Breaker Exception Handling")
    inner class CircuitBreakerHandling {

        @Test
        @DisplayName("should return 503 with correlation ID for CommandServiceUnavailableException")
        fun shouldReturn503WithCorrelationIdForCommandServiceUnavailable() {
            val exchange = createExchange()
            val ex = CommandServiceUnavailableException("Service temporarily unavailable. Please try again later.")

            StepVerifier.create(handler.handleServiceUnavailable(ex, exchange))
                .expectNextMatches { response ->
                    response.statusCode == HttpStatus.SERVICE_UNAVAILABLE &&
                        response.body?.correlationId == testCorrelationId &&
                        response.body?.message == "Service temporarily unavailable. Please try again later." &&
                        response.body?.retryAfter == 30 &&
                        response.headers["Retry-After"]?.get(0) == "30"
                }
                .verifyComplete()
        }
    }
}
