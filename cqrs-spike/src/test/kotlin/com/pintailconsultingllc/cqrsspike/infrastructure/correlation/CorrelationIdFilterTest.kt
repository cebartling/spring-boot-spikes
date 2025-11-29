package com.pintailconsultingllc.cqrsspike.infrastructure.correlation

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpHeaders
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.http.server.reactive.ServerHttpResponse
import org.springframework.web.server.ServerWebExchange
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

    private lateinit var filter: CorrelationIdFilter
    private lateinit var exchange: ServerWebExchange
    private lateinit var chain: WebFilterChain
    private lateinit var request: ServerHttpRequest
    private lateinit var response: ServerHttpResponse
    private lateinit var requestHeaders: HttpHeaders
    private lateinit var responseHeaders: HttpHeaders

    @BeforeEach
    fun setUp() {
        filter = CorrelationIdFilter()
        exchange = mock()
        chain = mock()
        request = mock()
        response = mock()
        requestHeaders = HttpHeaders()
        responseHeaders = HttpHeaders()

        whenever(exchange.request).thenReturn(request)
        whenever(exchange.response).thenReturn(response)
        whenever(request.headers).thenReturn(requestHeaders)
        whenever(response.headers).thenReturn(responseHeaders)
        whenever(chain.filter(any())).thenReturn(Mono.empty())
    }

    @Nested
    @DisplayName("Correlation ID extraction")
    inner class CorrelationIdExtraction {

        @Test
        @DisplayName("should use existing correlation ID from request header")
        fun shouldUseExistingCorrelationId() {
            val existingCorrelationId = "existing-correlation-id-12345"
            requestHeaders.add(CorrelationIdHolder.CORRELATION_ID_HEADER, existingCorrelationId)

            StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete()

            verify(response.headers).add(
                argThat { header -> header == CorrelationIdHolder.CORRELATION_ID_HEADER },
                argThat { value -> value == existingCorrelationId }
            )
        }

        @Test
        @DisplayName("should generate new correlation ID when not provided")
        fun shouldGenerateNewCorrelationId() {
            // No correlation ID in request headers

            StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete()

            verify(response.headers).add(
                argThat { header -> header == CorrelationIdHolder.CORRELATION_ID_HEADER },
                argThat { value ->
                    // Verify it's a valid UUID format
                    try {
                        UUID.fromString(value)
                        true
                    } catch (e: IllegalArgumentException) {
                        false
                    }
                }
            )
        }
    }

    @Nested
    @DisplayName("Response header handling")
    inner class ResponseHeaderHandling {

        @Test
        @DisplayName("should add correlation ID to response headers")
        fun shouldAddCorrelationIdToResponseHeaders() {
            val correlationId = "test-correlation-id-67890"
            requestHeaders.add(CorrelationIdHolder.CORRELATION_ID_HEADER, correlationId)

            StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete()

            verify(response.headers).add(
                CorrelationIdHolder.CORRELATION_ID_HEADER,
                correlationId
            )
        }
    }

    @Nested
    @DisplayName("Filter chain continuation")
    inner class FilterChainContinuation {

        @Test
        @DisplayName("should continue filter chain")
        fun shouldContinueFilterChain() {
            StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete()

            verify(chain).filter(exchange)
        }
    }
}
