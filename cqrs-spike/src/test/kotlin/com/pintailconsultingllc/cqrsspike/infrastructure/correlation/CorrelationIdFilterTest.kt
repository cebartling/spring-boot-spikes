package com.pintailconsultingllc.cqrsspike.infrastructure.correlation

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.util.UUID

/**
 * Unit tests for CorrelationIdFilter.
 *
 * Tests AC10: "All errors are logged with correlation IDs"
 */
@DisplayName("CorrelationIdFilter - AC10")
class CorrelationIdFilterTest {

    private val filter = CorrelationIdFilter()

    @Nested
    @DisplayName("Correlation ID extraction")
    inner class CorrelationIdExtraction {

        @Test
        @DisplayName("should use existing correlation ID from request header")
        fun shouldUseExistingCorrelationId() {
            val existingCorrelationId = "existing-correlation-id-12345"
            val request = MockServerHttpRequest.get("/api/test")
                .header(CorrelationIdHolder.CORRELATION_ID_HEADER, existingCorrelationId)
                .build()
            val exchange = MockServerWebExchange.from(request)
            val chain = WebFilterChain { Mono.empty() }

            StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete()

            val responseCorrelationId = exchange.response.headers
                .getFirst(CorrelationIdHolder.CORRELATION_ID_HEADER)
            assert(responseCorrelationId == existingCorrelationId) {
                "Expected correlation ID '$existingCorrelationId' but got '$responseCorrelationId'"
            }
        }

        @Test
        @DisplayName("should generate new correlation ID when not provided")
        fun shouldGenerateNewCorrelationId() {
            val request = MockServerHttpRequest.get("/api/test").build()
            val exchange = MockServerWebExchange.from(request)
            val chain = WebFilterChain { Mono.empty() }

            StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete()

            val responseCorrelationId = exchange.response.headers
                .getFirst(CorrelationIdHolder.CORRELATION_ID_HEADER)

            // Verify it's a valid UUID format
            assert(responseCorrelationId != null) { "Correlation ID should be generated" }
            try {
                UUID.fromString(responseCorrelationId)
            } catch (e: IllegalArgumentException) {
                throw AssertionError("Generated correlation ID is not a valid UUID: $responseCorrelationId")
            }
        }
    }

    @Nested
    @DisplayName("Response header handling")
    inner class ResponseHeaderHandling {

        @Test
        @DisplayName("should add correlation ID to response headers")
        fun shouldAddCorrelationIdToResponseHeaders() {
            val correlationId = "test-correlation-id-67890"
            val request = MockServerHttpRequest.get("/api/test")
                .header(CorrelationIdHolder.CORRELATION_ID_HEADER, correlationId)
                .build()
            val exchange = MockServerWebExchange.from(request)
            val chain = WebFilterChain { Mono.empty() }

            StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete()

            assert(exchange.response.headers.getFirst(CorrelationIdHolder.CORRELATION_ID_HEADER) != null) {
                "Response should contain X-Correlation-ID header"
            }
        }
    }

    @Nested
    @DisplayName("Filter chain continuation")
    inner class FilterChainContinuation {

        @Test
        @DisplayName("should continue filter chain")
        fun shouldContinueFilterChain() {
            val request = MockServerHttpRequest.get("/api/test").build()
            val exchange = MockServerWebExchange.from(request)
            var chainCalled = false
            val chain = WebFilterChain {
                chainCalled = true
                Mono.empty()
            }

            StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete()

            assert(chainCalled) { "Filter chain should have been called" }
        }
    }
}
