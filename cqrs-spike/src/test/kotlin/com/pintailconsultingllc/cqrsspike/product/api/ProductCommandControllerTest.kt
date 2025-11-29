package com.pintailconsultingllc.cqrsspike.product.api

import com.pintailconsultingllc.cqrsspike.product.command.exception.ConcurrentModificationException
import com.pintailconsultingllc.cqrsspike.product.command.exception.DuplicateSkuException
import com.pintailconsultingllc.cqrsspike.product.command.exception.InvalidStateTransitionException
import com.pintailconsultingllc.cqrsspike.product.command.exception.PriceChangeThresholdExceededException
import com.pintailconsultingllc.cqrsspike.product.command.exception.ProductDeletedException
import com.pintailconsultingllc.cqrsspike.product.command.exception.ProductNotFoundException
import com.pintailconsultingllc.cqrsspike.product.command.handler.ProductCommandHandler
import com.pintailconsultingllc.cqrsspike.product.command.model.ActivateProductCommand
import com.pintailconsultingllc.cqrsspike.product.command.model.ChangePriceCommand
import com.pintailconsultingllc.cqrsspike.product.command.model.CommandAlreadyProcessed
import com.pintailconsultingllc.cqrsspike.product.command.model.CommandSuccess
import com.pintailconsultingllc.cqrsspike.product.command.model.CreateProductCommand
import com.pintailconsultingllc.cqrsspike.product.command.model.DeleteProductCommand
import com.pintailconsultingllc.cqrsspike.product.command.model.DiscontinueProductCommand
import com.pintailconsultingllc.cqrsspike.product.command.model.ProductStatus
import com.pintailconsultingllc.cqrsspike.product.command.model.UpdateProductCommand
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Mono
import java.time.OffsetDateTime
import java.util.UUID

@WebFluxTest(ProductCommandController::class)
@Import(CommandExceptionHandler::class)
@DisplayName("ProductCommandController")
class ProductCommandControllerTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @MockitoBean
    private lateinit var commandHandler: ProductCommandHandler

    @Nested
    @DisplayName("POST /api/products")
    inner class CreateProduct {

        @Test
        @DisplayName("should create product and return 201")
        fun shouldCreateProduct() {
            val productId = UUID.randomUUID()
            val result = CommandSuccess(productId, 1L, "DRAFT", OffsetDateTime.now())

            whenever(commandHandler.handle(any<CreateProductCommand>()))
                .thenReturn(Mono.just(result))

            webTestClient.post()
                .uri("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                    {
                        "sku": "TEST-001",
                        "name": "Test Product",
                        "description": "A test product",
                        "priceCents": 1999
                    }
                """.trimIndent())
                .exchange()
                .expectStatus().isCreated
                .expectHeader().exists("Location")
                .expectBody()
                .jsonPath("$.productId").isEqualTo(productId.toString())
                .jsonPath("$.version").isEqualTo(1)
                .jsonPath("$.status").isEqualTo("DRAFT")
                .jsonPath("$.sku").isEqualTo("TEST-001")
        }

        @Test
        @DisplayName("should return 400 for missing required fields")
        fun shouldReturn400ForMissingFields() {
            webTestClient.post()
                .uri("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                    {
                        "description": "Missing sku, name, and price"
                    }
                """.trimIndent())
                .exchange()
                .expectStatus().isBadRequest
        }

        @Test
        @DisplayName("should return 400 for invalid SKU format")
        fun shouldReturn400ForInvalidSkuFormat() {
            webTestClient.post()
                .uri("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                    {
                        "sku": "TEST@001",
                        "name": "Test Product",
                        "priceCents": 1999
                    }
                """.trimIndent())
                .exchange()
                .expectStatus().isBadRequest
        }

        @Test
        @DisplayName("should return 400 for negative price")
        fun shouldReturn400ForNegativePrice() {
            webTestClient.post()
                .uri("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                    {
                        "sku": "TEST-001",
                        "name": "Test Product",
                        "priceCents": -100
                    }
                """.trimIndent())
                .exchange()
                .expectStatus().isBadRequest
        }

        @Test
        @DisplayName("should return 409 for duplicate SKU")
        fun shouldReturn409ForDuplicateSku() {
            whenever(commandHandler.handle(any<CreateProductCommand>()))
                .thenReturn(Mono.error(DuplicateSkuException("TEST-001")))

            webTestClient.post()
                .uri("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                    {
                        "sku": "TEST-001",
                        "name": "Test Product",
                        "priceCents": 1999
                    }
                """.trimIndent())
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.code").isEqualTo("DUPLICATE_SKU")
        }

        @Test
        @DisplayName("should handle idempotent request")
        fun shouldHandleIdempotentRequest() {
            val productId = UUID.randomUUID()
            val result = CommandAlreadyProcessed(productId, 1L, "DRAFT", "test-key-123", OffsetDateTime.now())

            whenever(commandHandler.handle(any<CreateProductCommand>()))
                .thenReturn(Mono.just(result))

            webTestClient.post()
                .uri("/api/products")
                .header("Idempotency-Key", "test-key-123")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                    {
                        "sku": "TEST-001",
                        "name": "Test Product",
                        "priceCents": 1999
                    }
                """.trimIndent())
                .exchange()
                .expectStatus().isCreated
                .expectHeader().valueEquals("X-Idempotent-Replayed", "true")
        }
    }

    @Nested
    @DisplayName("PUT /api/products/{id}")
    inner class UpdateProduct {

        @Test
        @DisplayName("should update product and return 200")
        fun shouldUpdateProduct() {
            val productId = UUID.randomUUID()
            val result = CommandSuccess(productId, 2L, "DRAFT", OffsetDateTime.now())

            whenever(commandHandler.handle(any<UpdateProductCommand>()))
                .thenReturn(Mono.just(result))

            webTestClient.put()
                .uri("/api/products/$productId")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                    {
                        "name": "Updated Product",
                        "description": "Updated description",
                        "expectedVersion": 1
                    }
                """.trimIndent())
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.productId").isEqualTo(productId.toString())
                .jsonPath("$.version").isEqualTo(2)
        }

        @Test
        @DisplayName("should return 404 for non-existent product")
        fun shouldReturn404ForNonExistent() {
            val productId = UUID.randomUUID()

            whenever(commandHandler.handle(any<UpdateProductCommand>()))
                .thenReturn(Mono.error(ProductNotFoundException(productId)))

            webTestClient.put()
                .uri("/api/products/$productId")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                    {
                        "name": "Updated Product",
                        "expectedVersion": 1
                    }
                """.trimIndent())
                .exchange()
                .expectStatus().isNotFound
                .expectBody()
                .jsonPath("$.code").isEqualTo("PRODUCT_NOT_FOUND")
        }

        @Test
        @DisplayName("should return 409 for concurrent modification")
        fun shouldReturn409ForConcurrentModification() {
            val productId = UUID.randomUUID()

            whenever(commandHandler.handle(any<UpdateProductCommand>()))
                .thenReturn(Mono.error(ConcurrentModificationException(productId, 1L, 3L)))

            webTestClient.put()
                .uri("/api/products/$productId")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                    {
                        "name": "Updated Product",
                        "expectedVersion": 1
                    }
                """.trimIndent())
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.expectedVersion").isEqualTo(1)
                .jsonPath("$.currentVersion").isEqualTo(3)
        }

        @Test
        @DisplayName("should return 400 for missing expected version")
        fun shouldReturn400ForMissingExpectedVersion() {
            val productId = UUID.randomUUID()

            webTestClient.put()
                .uri("/api/products/$productId")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                    {
                        "name": "Updated Product"
                    }
                """.trimIndent())
                .exchange()
                .expectStatus().isBadRequest
        }
    }

    @Nested
    @DisplayName("PATCH /api/products/{id}/price")
    inner class ChangePrice {

        @Test
        @DisplayName("should change price and return 200")
        fun shouldChangePrice() {
            val productId = UUID.randomUUID()
            val result = CommandSuccess(productId, 2L, "ACTIVE", OffsetDateTime.now())

            whenever(commandHandler.handle(any<ChangePriceCommand>()))
                .thenReturn(Mono.just(result))

            webTestClient.patch()
                .uri("/api/products/$productId/price")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                    {
                        "newPriceCents": 2499,
                        "expectedVersion": 1
                    }
                """.trimIndent())
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.version").isEqualTo(2)
        }

        @Test
        @DisplayName("should return 422 for price threshold exceeded")
        fun shouldReturn422ForPriceThresholdExceeded() {
            val productId = UUID.randomUUID()

            whenever(commandHandler.handle(any<ChangePriceCommand>()))
                .thenReturn(Mono.error(
                    PriceChangeThresholdExceededException(productId, 1000, 2000, 100.0, 20.0)
                ))

            webTestClient.patch()
                .uri("/api/products/$productId/price")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                    {
                        "newPriceCents": 2000,
                        "expectedVersion": 1
                    }
                """.trimIndent())
                .exchange()
                .expectStatus().isEqualTo(422)
                .expectBody()
                .jsonPath("$.code").isEqualTo("PRICE_CHANGE_THRESHOLD_EXCEEDED")
                .jsonPath("$.confirmationRequired").isEqualTo(true)
                .jsonPath("$.currentPrice").isEqualTo(1000)
                .jsonPath("$.requestedPrice").isEqualTo(2000)
        }

        @Test
        @DisplayName("should allow large change with confirmation")
        fun shouldAllowLargeChangeWithConfirmation() {
            val productId = UUID.randomUUID()
            val result = CommandSuccess(productId, 2L, "ACTIVE", OffsetDateTime.now())

            whenever(commandHandler.handle(any<ChangePriceCommand>()))
                .thenReturn(Mono.just(result))

            webTestClient.patch()
                .uri("/api/products/$productId/price")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                    {
                        "newPriceCents": 2000,
                        "confirmLargeChange": true,
                        "expectedVersion": 1
                    }
                """.trimIndent())
                .exchange()
                .expectStatus().isOk
        }
    }

    @Nested
    @DisplayName("POST /api/products/{id}/activate")
    inner class ActivateProduct {

        @Test
        @DisplayName("should activate product and return 200")
        fun shouldActivateProduct() {
            val productId = UUID.randomUUID()
            val result = CommandSuccess(productId, 2L, "ACTIVE", OffsetDateTime.now())

            whenever(commandHandler.handle(any<ActivateProductCommand>()))
                .thenReturn(Mono.just(result))

            webTestClient.post()
                .uri("/api/products/$productId/activate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""{"expectedVersion": 1}""")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.status").isEqualTo("ACTIVE")
                .jsonPath("$.version").isEqualTo(2)
        }

        @Test
        @DisplayName("should return 422 for invalid state transition")
        fun shouldReturn422ForInvalidTransition() {
            val productId = UUID.randomUUID()

            whenever(commandHandler.handle(any<ActivateProductCommand>()))
                .thenReturn(Mono.error(
                    InvalidStateTransitionException(productId, ProductStatus.DISCONTINUED, ProductStatus.ACTIVE)
                ))

            webTestClient.post()
                .uri("/api/products/$productId/activate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""{"expectedVersion": 1}""")
                .exchange()
                .expectStatus().isEqualTo(422)
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_STATE_TRANSITION")
                .jsonPath("$.details.currentStatus").isEqualTo("DISCONTINUED")
                .jsonPath("$.details.targetStatus").isEqualTo("ACTIVE")
        }
    }

    @Nested
    @DisplayName("POST /api/products/{id}/discontinue")
    inner class DiscontinueProduct {

        @Test
        @DisplayName("should discontinue product and return 200")
        fun shouldDiscontinueProduct() {
            val productId = UUID.randomUUID()
            val result = CommandSuccess(productId, 2L, "DISCONTINUED", OffsetDateTime.now())

            whenever(commandHandler.handle(any<DiscontinueProductCommand>()))
                .thenReturn(Mono.just(result))

            webTestClient.post()
                .uri("/api/products/$productId/discontinue")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                    {
                        "reason": "Product end of life",
                        "expectedVersion": 1
                    }
                """.trimIndent())
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.status").isEqualTo("DISCONTINUED")
                .jsonPath("$.version").isEqualTo(2)
        }

        @Test
        @DisplayName("should discontinue product without reason")
        fun shouldDiscontinueProductWithoutReason() {
            val productId = UUID.randomUUID()
            val result = CommandSuccess(productId, 2L, "DISCONTINUED", OffsetDateTime.now())

            whenever(commandHandler.handle(any<DiscontinueProductCommand>()))
                .thenReturn(Mono.just(result))

            webTestClient.post()
                .uri("/api/products/$productId/discontinue")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""{"expectedVersion": 1}""")
                .exchange()
                .expectStatus().isOk
        }
    }

    @Nested
    @DisplayName("DELETE /api/products/{id}")
    inner class DeleteProduct {

        @Test
        @DisplayName("should delete product and return 204")
        fun shouldDeleteProduct() {
            val productId = UUID.randomUUID()
            val result = CommandSuccess(productId, 2L, "DELETED", OffsetDateTime.now())

            whenever(commandHandler.handle(any<DeleteProductCommand>()))
                .thenReturn(Mono.just(result))

            webTestClient.delete()
                .uri("/api/products/$productId?expectedVersion=1")
                .exchange()
                .expectStatus().isNoContent
        }

        @Test
        @DisplayName("should delete product with deletedBy parameter")
        fun shouldDeleteProductWithDeletedBy() {
            val productId = UUID.randomUUID()
            val result = CommandSuccess(productId, 2L, "DELETED", OffsetDateTime.now())

            whenever(commandHandler.handle(any<DeleteProductCommand>()))
                .thenReturn(Mono.just(result))

            webTestClient.delete()
                .uri("/api/products/$productId?expectedVersion=1&deletedBy=admin")
                .exchange()
                .expectStatus().isNoContent
        }

        @Test
        @DisplayName("should return 410 for already deleted product")
        fun shouldReturn410ForDeletedProduct() {
            val productId = UUID.randomUUID()

            whenever(commandHandler.handle(any<DeleteProductCommand>()))
                .thenReturn(Mono.error(ProductDeletedException(productId)))

            webTestClient.delete()
                .uri("/api/products/$productId?expectedVersion=1")
                .exchange()
                .expectStatus().isEqualTo(410)
                .expectBody()
                .jsonPath("$.code").isEqualTo("PRODUCT_DELETED")
        }

        @Test
        @DisplayName("should return 409 for concurrent modification")
        fun shouldReturn409ForConcurrentModification() {
            val productId = UUID.randomUUID()

            whenever(commandHandler.handle(any<DeleteProductCommand>()))
                .thenReturn(Mono.error(ConcurrentModificationException(productId, 1L, 3L)))

            webTestClient.delete()
                .uri("/api/products/$productId?expectedVersion=1")
                .exchange()
                .expectStatus().isEqualTo(409)
        }
    }

    @Nested
    @DisplayName("Idempotency")
    inner class IdempotencyTests {

        @Test
        @DisplayName("should return idempotent response for update")
        fun shouldReturnIdempotentResponseForUpdate() {
            val productId = UUID.randomUUID()
            val result = CommandAlreadyProcessed(productId, 2L, "DRAFT", "update-key", OffsetDateTime.now())

            whenever(commandHandler.handle(any<UpdateProductCommand>()))
                .thenReturn(Mono.just(result))

            webTestClient.put()
                .uri("/api/products/$productId")
                .header("Idempotency-Key", "update-key")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                    {
                        "name": "Updated Product",
                        "expectedVersion": 1
                    }
                """.trimIndent())
                .exchange()
                .expectStatus().isOk
                .expectHeader().valueEquals("X-Idempotent-Replayed", "true")
        }

        @Test
        @DisplayName("should return idempotent response for delete")
        fun shouldReturnIdempotentResponseForDelete() {
            val productId = UUID.randomUUID()
            val result = CommandAlreadyProcessed(productId, 2L, "DELETED", "delete-key", OffsetDateTime.now())

            whenever(commandHandler.handle(any<DeleteProductCommand>()))
                .thenReturn(Mono.just(result))

            webTestClient.delete()
                .uri("/api/products/$productId?expectedVersion=1")
                .header("Idempotency-Key", "delete-key")
                .exchange()
                .expectStatus().isNoContent
        }
    }
}
