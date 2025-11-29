package com.pintailconsultingllc.cqrsspike.product.api

import com.pintailconsultingllc.cqrsspike.infrastructure.correlation.CorrelationIdHolder
import com.pintailconsultingllc.cqrsspike.product.command.exception.ConcurrentModificationException
import com.pintailconsultingllc.cqrsspike.product.command.handler.CommandRateLimitException
import com.pintailconsultingllc.cqrsspike.product.command.handler.CommandServiceUnavailableException
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

    private lateinit var handler: CommandExceptionHandler
    private lateinit var exchange: ServerWebExchange
    private lateinit var request: ServerHttpRequest
    private lateinit var headers: HttpHeaders
    private lateinit var path: RequestPath

    private val testCorrelationId = "test-correlation-id-67890"
    private val testProductId = UUID.randomUUID()
    private val testPath = "/api/products/$testProductId"

    @BeforeEach
    fun setUp() {
        handler = CommandExceptionHandler()
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
    @DisplayName("AC10: Concurrent Modification Handling")
    inner class ConcurrentModificationHandling {

        @Test
        @DisplayName("should return 409 with correlation ID and retry guidance")
        fun shouldReturn409WithCorrelationIdAndRetryGuidance() {
            headers.add(CorrelationIdHolder.CORRELATION_ID_HEADER, testCorrelationId)
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
            headers.add(CorrelationIdHolder.CORRELATION_ID_HEADER, testCorrelationId)
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
        @DisplayName("should return 429 with correlation ID for RequestNotPermitted")
        fun shouldReturn429WithCorrelationIdForRequestNotPermitted() {
            headers.add(CorrelationIdHolder.CORRELATION_ID_HEADER, testCorrelationId)
            val rateLimiter = mock<RateLimiter>()
            whenever(rateLimiter.name).thenReturn("productCommands")
            val ex = RequestNotPermitted.createRequestNotPermitted(rateLimiter)

            StepVerifier.create(handler.handleRateLimitExceeded(ex, exchange))
                .expectNextMatches { response ->
                    response.statusCode == HttpStatus.TOO_MANY_REQUESTS &&
                        response.body?.correlationId == testCorrelationId &&
                        response.body?.code == "RATE_LIMIT_EXCEEDED" &&
                        response.body?.retryAfter == 5 &&
                        response.headers["Retry-After"]?.get(0) == "5"
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("should return 429 with correlation ID for CommandRateLimitException")
        fun shouldReturn429WithCorrelationIdForCommandRateLimitException() {
            headers.add(CorrelationIdHolder.CORRELATION_ID_HEADER, testCorrelationId)
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
        @DisplayName("should return 503 with correlation ID for CallNotPermittedException")
        fun shouldReturn503WithCorrelationIdForCallNotPermitted() {
            headers.add(CorrelationIdHolder.CORRELATION_ID_HEADER, testCorrelationId)
            val circuitBreaker = mock<CircuitBreaker>()
            whenever(circuitBreaker.name).thenReturn("productCommands")
            val ex = CallNotPermittedException.createCallNotPermittedException(circuitBreaker)

            StepVerifier.create(handler.handleCircuitBreakerOpen(ex, exchange))
                .expectNextMatches { response ->
                    response.statusCode == HttpStatus.SERVICE_UNAVAILABLE &&
                        response.body?.correlationId == testCorrelationId &&
                        response.body?.circuitBreakerState == "OPEN" &&
                        response.body?.retryAfter == 30 &&
                        response.headers["Retry-After"]?.get(0) == "30"
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("should return 503 with correlation ID for CommandServiceUnavailableException")
        fun shouldReturn503WithCorrelationIdForCommandServiceUnavailable() {
            headers.add(CorrelationIdHolder.CORRELATION_ID_HEADER, testCorrelationId)
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
