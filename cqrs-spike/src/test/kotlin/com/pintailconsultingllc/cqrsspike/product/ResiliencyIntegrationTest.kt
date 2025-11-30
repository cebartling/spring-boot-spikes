package com.pintailconsultingllc.cqrsspike.product

import com.pintailconsultingllc.cqrsspike.infrastructure.correlation.CorrelationIdHolder
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import java.util.UUID

/**
 * Integration tests for AC10: Resiliency and Error Handling.
 *
 * Tests:
 * - All errors are logged with correlation IDs
 * - Concurrent modification conflicts return HTTP 409 with retry guidance
 * - Domain exceptions are translated to appropriate HTTP responses
 * - Correlation ID propagation through request/response
 *
 * IMPORTANT: Before running these tests, ensure Docker Compose
 * infrastructure is running:
 *   make start
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
@DisplayName("AC10: Resiliency and Error Handling Integration Tests")
class ResiliencyIntegrationTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Nested
    @DisplayName("AC10: All errors are logged with correlation IDs")
    inner class CorrelationIdTests {

        @Test
        @DisplayName("should return correlation ID in response header when provided in request")
        fun shouldReturnCorrelationIdWhenProvided() {
            val correlationId = UUID.randomUUID().toString()

            webTestClient.get()
                .uri("/api/products/${UUID.randomUUID()}")
                .header(CorrelationIdHolder.CORRELATION_ID_HEADER, correlationId)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isNotFound
                .expectHeader().valueEquals(CorrelationIdHolder.CORRELATION_ID_HEADER, correlationId)
        }

        @Test
        @DisplayName("should generate correlation ID when not provided in request")
        fun shouldGenerateCorrelationIdWhenNotProvided() {
            webTestClient.get()
                .uri("/api/products/${UUID.randomUUID()}")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isNotFound
                .expectHeader().exists(CorrelationIdHolder.CORRELATION_ID_HEADER)
        }

        @Test
        @DisplayName("should include correlation ID in 400 error response")
        fun shouldIncludeCorrelationIdIn400Response() {
            val correlationId = UUID.randomUUID().toString()

            webTestClient.get()
                .uri("/api/products/not-a-valid-uuid")
                .header(CorrelationIdHolder.CORRELATION_ID_HEADER, correlationId)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isBadRequest
                .expectHeader().valueEquals(CorrelationIdHolder.CORRELATION_ID_HEADER, correlationId)
        }
    }

    @Nested
    @DisplayName("AC10: Domain exceptions are translated to appropriate HTTP responses")
    inner class DomainExceptionTranslationTests {

        @Test
        @DisplayName("should return 404 for product not found with correlation ID")
        fun shouldReturn404ForNotFound() {
            val correlationId = UUID.randomUUID().toString()

            webTestClient.get()
                .uri("/api/products/${UUID.randomUUID()}")
                .header(CorrelationIdHolder.CORRELATION_ID_HEADER, correlationId)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isNotFound
                .expectHeader().valueEquals(CorrelationIdHolder.CORRELATION_ID_HEADER, correlationId)
        }

        @Test
        @DisplayName("should return 400 for validation errors")
        fun shouldReturn400ForValidationErrors() {
            val correlationId = UUID.randomUUID().toString()

            webTestClient.post()
                .uri("/api/products")
                .header(CorrelationIdHolder.CORRELATION_ID_HEADER, correlationId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                    {
                        "sku": "",
                        "name": "",
                        "priceCents": -1
                    }
                """.trimIndent())
                .exchange()
                .expectStatus().isBadRequest
                .expectHeader().valueEquals(CorrelationIdHolder.CORRELATION_ID_HEADER, correlationId)
        }
    }

    @Nested
    @DisplayName("AC10: Concurrent modification conflicts return HTTP 409 with retry guidance")
    inner class ConcurrentModificationTests {

        @Test
        @DisplayName("should return 409 with retry guidance on version conflict")
        fun shouldReturn409WithRetryGuidance() {
            val correlationId = UUID.randomUUID().toString()
            val sku = "CONFLICT-${UUID.randomUUID().toString().take(8)}"

            // Create product
            val createResult = webTestClient.post()
                .uri("/api/products")
                .header(CorrelationIdHolder.CORRELATION_ID_HEADER, correlationId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                    {
                        "sku": "$sku",
                        "name": "Conflict Test Product",
                        "priceCents": 1000
                    }
                """.trimIndent())
                .exchange()
                .expectStatus().isCreated
                .expectBody()
                .returnResult()

            // Extract product ID from response
            val responseBody = String(createResult.responseBody ?: ByteArray(0))
            val productIdMatch = Regex(""""productId":"([^"]+)"""").find(responseBody)
            val productId = productIdMatch?.groupValues?.get(1) ?: throw AssertionError("Could not extract productId")

            // Try to update with wrong version
            webTestClient.put()
                .uri("/api/products/$productId")
                .header(CorrelationIdHolder.CORRELATION_ID_HEADER, correlationId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                    {
                        "name": "Updated Name",
                        "expectedVersion": 999
                    }
                """.trimIndent())
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectHeader().valueEquals(CorrelationIdHolder.CORRELATION_ID_HEADER, correlationId)
                .expectBody()
                .jsonPath("$.code").isEqualTo("CONCURRENT_MODIFICATION")
                .jsonPath("$.retryGuidance").exists()
                .jsonPath("$.recommendedAction").exists()
                .jsonPath("$.currentVersion").exists()
                .jsonPath("$.expectedVersion").isEqualTo(999)
                .jsonPath("$.correlationId").isEqualTo(correlationId)
        }
    }

    @Nested
    @DisplayName("AC10: Error response structure")
    inner class ErrorResponseStructureTests {

        @Test
        @DisplayName("should include standard error fields in 404 response")
        fun shouldIncludeStandardErrorFieldsIn404() {
            val correlationId = UUID.randomUUID().toString()

            webTestClient.get()
                .uri("/api/products/${UUID.randomUUID()}")
                .header(CorrelationIdHolder.CORRELATION_ID_HEADER, correlationId)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isNotFound
                .expectBody()
                .jsonPath("$.status").isEqualTo(404)
                .jsonPath("$.error").isEqualTo("Not Found")
                .jsonPath("$.path").exists()
                .jsonPath("$.timestamp").exists()
        }

        @Test
        @DisplayName("should include validation details in 400 response")
        fun shouldIncludeValidationDetailsIn400() {
            val correlationId = UUID.randomUUID().toString()

            webTestClient.post()
                .uri("/api/products")
                .header(CorrelationIdHolder.CORRELATION_ID_HEADER, correlationId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                    {
                        "sku": "",
                        "name": "",
                        "priceCents": -1
                    }
                """.trimIndent())
                .exchange()
                .expectStatus().isBadRequest
                .expectBody()
                .jsonPath("$.status").isEqualTo(400)
                .jsonPath("$.message").exists()
        }
    }
}
