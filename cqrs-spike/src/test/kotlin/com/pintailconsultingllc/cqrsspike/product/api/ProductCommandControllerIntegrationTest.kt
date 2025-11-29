package com.pintailconsultingllc.cqrsspike.product.api

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient(timeout = "30s")
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("ProductCommandController Integration Tests")
class ProductCommandControllerIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:18-alpine")
            .withDatabaseName("cqrs_test")
            .withUsername("test")
            .withPassword("test")
            .withInitScript("init-test-schema.sql")

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.r2dbc.url") {
                "r2dbc:postgresql://${postgres.host}:${postgres.firstMappedPort}/${postgres.databaseName}"
            }
            registry.add("spring.r2dbc.username", postgres::getUsername)
            registry.add("spring.r2dbc.password", postgres::getPassword)
            registry.add("spring.flyway.enabled") { "false" }
            registry.add("spring.cloud.vault.enabled") { "false" }
        }
    }

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Nested
    @DisplayName("Full Product Lifecycle")
    inner class FullProductLifecycle {

        @Test
        @DisplayName("should create, update, activate, and delete product")
        fun fullProductLifecycle() {
            val sku = "INT-TEST-${UUID.randomUUID().toString().take(8)}"

            // 1. Create product
            val createResponse = webTestClient.post()
                .uri("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                    {
                        "sku": "$sku",
                        "name": "Integration Test Product",
                        "description": "Created for integration testing",
                        "priceCents": 1999
                    }
                """.trimIndent())
                .exchange()
                .expectStatus().isCreated
                .expectHeader().exists("Location")
                .expectBody(String::class.java)
                .returnResult()

            // Extract product ID from response
            val responseBody = createResponse.responseBody!!
            val productIdMatch = Regex(""""productId":"([^"]+)"""").find(responseBody)
            val productId = productIdMatch!!.groupValues[1]

            // 2. Update product
            webTestClient.put()
                .uri("/api/products/$productId")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                    {
                        "name": "Updated Integration Test Product",
                        "description": "Updated description",
                        "expectedVersion": 1
                    }
                """.trimIndent())
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.productId").isEqualTo(productId)
                .jsonPath("$.version").isEqualTo(2)

            // 3. Activate product
            webTestClient.post()
                .uri("/api/products/$productId/activate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""{"expectedVersion": 2}""")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.status").isEqualTo("ACTIVE")
                .jsonPath("$.version").isEqualTo(3)

            // 4. Change price
            webTestClient.patch()
                .uri("/api/products/$productId/price")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                    {
                        "newPriceCents": 2199,
                        "expectedVersion": 3
                    }
                """.trimIndent())
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.version").isEqualTo(4)

            // 5. Delete product
            webTestClient.delete()
                .uri("/api/products/$productId?expectedVersion=4")
                .exchange()
                .expectStatus().isNoContent
        }

        @Test
        @DisplayName("should create and discontinue product")
        fun createAndDiscontinueProduct() {
            val sku = "DISC-${UUID.randomUUID().toString().take(8)}"

            // Create product
            val createResponse = webTestClient.post()
                .uri("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                    {
                        "sku": "$sku",
                        "name": "Discontinue Test Product",
                        "priceCents": 999
                    }
                """.trimIndent())
                .exchange()
                .expectStatus().isCreated
                .expectBody(String::class.java)
                .returnResult()

            val responseBody = createResponse.responseBody!!
            val productIdMatch = Regex(""""productId":"([^"]+)"""").find(responseBody)
            val productId = productIdMatch!!.groupValues[1]

            // Discontinue product (directly from DRAFT)
            webTestClient.post()
                .uri("/api/products/$productId/discontinue")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                    {
                        "reason": "No longer needed",
                        "expectedVersion": 1
                    }
                """.trimIndent())
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.status").isEqualTo("DISCONTINUED")
                .jsonPath("$.version").isEqualTo(2)
        }
    }

    @Nested
    @DisplayName("Concurrent Modification Handling")
    inner class ConcurrentModificationHandling {

        @Test
        @DisplayName("should detect concurrent modification on update")
        fun shouldDetectConcurrentModificationOnUpdate() {
            val sku = "CONCURRENT-${UUID.randomUUID().toString().take(8)}"

            // Create product
            val createResponse = webTestClient.post()
                .uri("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                    {
                        "sku": "$sku",
                        "name": "Concurrent Test Product",
                        "priceCents": 1000
                    }
                """.trimIndent())
                .exchange()
                .expectStatus().isCreated
                .expectBody(String::class.java)
                .returnResult()

            val responseBody = createResponse.responseBody!!
            val productIdMatch = Regex(""""productId":"([^"]+)"""").find(responseBody)
            val productId = productIdMatch!!.groupValues[1]

            // First update succeeds
            webTestClient.put()
                .uri("/api/products/$productId")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                    {
                        "name": "First Update",
                        "expectedVersion": 1
                    }
                """.trimIndent())
                .exchange()
                .expectStatus().isOk

            // Second update with stale version fails
            webTestClient.put()
                .uri("/api/products/$productId")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                    {
                        "name": "Stale Update",
                        "expectedVersion": 1
                    }
                """.trimIndent())
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.code").isEqualTo("CONCURRENT_MODIFICATION")
                .jsonPath("$.expectedVersion").isEqualTo(1)
        }
    }

    @Nested
    @DisplayName("Duplicate SKU Handling")
    inner class DuplicateSkuHandling {

        @Test
        @DisplayName("should reject duplicate SKU")
        fun shouldRejectDuplicateSku() {
            val sku = "DUP-${UUID.randomUUID().toString().take(8)}"

            // Create first product
            webTestClient.post()
                .uri("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                    {
                        "sku": "$sku",
                        "name": "First Product",
                        "priceCents": 1000
                    }
                """.trimIndent())
                .exchange()
                .expectStatus().isCreated

            // Try to create second product with same SKU
            webTestClient.post()
                .uri("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                    {
                        "sku": "$sku",
                        "name": "Second Product",
                        "priceCents": 2000
                    }
                """.trimIndent())
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.code").isEqualTo("DUPLICATE_SKU")
        }
    }

    @Nested
    @DisplayName("State Transition Validation")
    inner class StateTransitionValidation {

        @Test
        @DisplayName("should reject activation of discontinued product")
        fun shouldRejectActivationOfDiscontinued() {
            val sku = "STATE-${UUID.randomUUID().toString().take(8)}"

            // Create and discontinue product
            val createResponse = webTestClient.post()
                .uri("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                    {
                        "sku": "$sku",
                        "name": "State Test Product",
                        "priceCents": 1000
                    }
                """.trimIndent())
                .exchange()
                .expectStatus().isCreated
                .expectBody(String::class.java)
                .returnResult()

            val responseBody = createResponse.responseBody!!
            val productIdMatch = Regex(""""productId":"([^"]+)"""").find(responseBody)
            val productId = productIdMatch!!.groupValues[1]

            // Discontinue the product
            webTestClient.post()
                .uri("/api/products/$productId/discontinue")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""{"expectedVersion": 1}""")
                .exchange()
                .expectStatus().isOk

            // Try to activate (should fail - DISCONTINUED is terminal)
            webTestClient.post()
                .uri("/api/products/$productId/activate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""{"expectedVersion": 2}""")
                .exchange()
                .expectStatus().isEqualTo(422)
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_STATE_TRANSITION")
        }
    }

    @Nested
    @DisplayName("Validation")
    inner class Validation {

        @Test
        @DisplayName("should reject invalid SKU format")
        fun shouldRejectInvalidSkuFormat() {
            webTestClient.post()
                .uri("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                    {
                        "sku": "INVALID@SKU!",
                        "name": "Test Product",
                        "priceCents": 1000
                    }
                """.trimIndent())
                .exchange()
                .expectStatus().isBadRequest
        }

        @Test
        @DisplayName("should reject negative price")
        fun shouldRejectNegativePrice() {
            webTestClient.post()
                .uri("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                    {
                        "sku": "VALID-SKU",
                        "name": "Test Product",
                        "priceCents": -100
                    }
                """.trimIndent())
                .exchange()
                .expectStatus().isBadRequest
        }

        @Test
        @DisplayName("should reject empty name")
        fun shouldRejectEmptyName() {
            webTestClient.post()
                .uri("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                    {
                        "sku": "VALID-SKU",
                        "name": "",
                        "priceCents": 1000
                    }
                """.trimIndent())
                .exchange()
                .expectStatus().isBadRequest
        }

        @Test
        @DisplayName("should reject SKU that is too short")
        fun shouldRejectShortSku() {
            webTestClient.post()
                .uri("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                    {
                        "sku": "AB",
                        "name": "Test Product",
                        "priceCents": 1000
                    }
                """.trimIndent())
                .exchange()
                .expectStatus().isBadRequest
        }
    }

    @Nested
    @DisplayName("Idempotency")
    inner class Idempotency {

        @Test
        @DisplayName("should handle idempotent create requests")
        fun shouldHandleIdempotentCreate() {
            val sku = "IDEMP-${UUID.randomUUID().toString().take(8)}"
            val idempotencyKey = UUID.randomUUID().toString()

            // First request
            val firstResponse = webTestClient.post()
                .uri("/api/products")
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                    {
                        "sku": "$sku",
                        "name": "Idempotent Product",
                        "priceCents": 1000
                    }
                """.trimIndent())
                .exchange()
                .expectStatus().isCreated
                .expectBody(String::class.java)
                .returnResult()

            val responseBody = firstResponse.responseBody!!
            val productIdMatch = Regex(""""productId":"([^"]+)"""").find(responseBody)
            val firstProductId = productIdMatch!!.groupValues[1]

            // Second request with same idempotency key
            webTestClient.post()
                .uri("/api/products")
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                    {
                        "sku": "$sku",
                        "name": "Idempotent Product",
                        "priceCents": 1000
                    }
                """.trimIndent())
                .exchange()
                .expectStatus().isCreated
                .expectHeader().valueEquals("X-Idempotent-Replayed", "true")
                .expectBody()
                .jsonPath("$.productId").isEqualTo(firstProductId)
        }
    }

    @Nested
    @DisplayName("Product Not Found")
    inner class ProductNotFound {

        @Test
        @DisplayName("should return 404 for non-existent product on update")
        fun shouldReturn404OnUpdate() {
            val nonExistentId = UUID.randomUUID()

            webTestClient.put()
                .uri("/api/products/$nonExistentId")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                    {
                        "name": "Update Non-Existent",
                        "expectedVersion": 1
                    }
                """.trimIndent())
                .exchange()
                .expectStatus().isNotFound
                .expectBody()
                .jsonPath("$.code").isEqualTo("PRODUCT_NOT_FOUND")
        }

        @Test
        @DisplayName("should return 404 for non-existent product on activate")
        fun shouldReturn404OnActivate() {
            val nonExistentId = UUID.randomUUID()

            webTestClient.post()
                .uri("/api/products/$nonExistentId/activate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""{"expectedVersion": 1}""")
                .exchange()
                .expectStatus().isNotFound
        }

        @Test
        @DisplayName("should return 404 for non-existent product on delete")
        fun shouldReturn404OnDelete() {
            val nonExistentId = UUID.randomUUID()

            webTestClient.delete()
                .uri("/api/products/$nonExistentId?expectedVersion=1")
                .exchange()
                .expectStatus().isNotFound
        }
    }
}
