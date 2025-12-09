package com.pintailconsultingllc.sagapattern.infrastructure

import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import reactor.test.StepVerifier
import java.net.Socket
import java.util.UUID

/**
 * Integration tests for WireMock service stubs.
 *
 * These tests verify that the WireMock server is running and responding correctly
 * to the configured stub mappings for inventory, payment, and shipping services.
 *
 * Requires Docker Compose services to be running:
 *   docker compose up -d
 */
@Tag("integration")
@DisplayName("WireMock Infrastructure Integration Tests")
class WireMockIntegrationTest {

    companion object {
        private const val WIREMOCK_HOST = "localhost"
        private const val WIREMOCK_PORT = 8081
        private const val WIREMOCK_BASE_URL = "http://$WIREMOCK_HOST:$WIREMOCK_PORT"

        private lateinit var webClient: WebClient

        @JvmStatic
        @BeforeAll
        fun setup() {
            // Skip tests if WireMock is not running
            Assumptions.assumeTrue(
                isPortOpen(WIREMOCK_HOST, WIREMOCK_PORT),
                "WireMock server is not running at $WIREMOCK_BASE_URL. Start with: docker compose up -d"
            )

            webClient = WebClient.builder()
                .baseUrl(WIREMOCK_BASE_URL)
                .build()
        }

        private fun isPortOpen(host: String, port: Int): Boolean {
            return try {
                Socket(host, port).use { true }
            } catch (e: Exception) {
                false
            }
        }
    }

    @Nested
    @DisplayName("WireMock Admin API")
    inner class AdminApiTests {

        @Test
        @DisplayName("Should return health status from admin API")
        fun shouldReturnHealthStatus() {
            val response = webClient.get()
                .uri("/__admin/health")
                .retrieve()
                .toBodilessEntity()

            StepVerifier.create(response)
                .expectNextMatches { it.statusCode == HttpStatus.OK }
                .verifyComplete()
        }

        @Test
        @DisplayName("Should return loaded mappings from admin API")
        fun shouldReturnLoadedMappings() {
            val response = webClient.get()
                .uri("/__admin/mappings")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(String::class.java)

            StepVerifier.create(response)
                .expectNextMatches { it.contains("mappings") }
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("Inventory Service Stubs")
    inner class InventoryServiceTests {

        @Test
        @DisplayName("Should reserve inventory successfully")
        fun shouldReserveInventorySuccessfully() {
            val orderId = UUID.randomUUID()
            val requestBody = """
                {
                    "orderId": "$orderId",
                    "items": [{"productId": "product-123", "quantity": 2}]
                }
            """.trimIndent()

            val response = webClient.post()
                .uri("/api/inventory/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .toEntity(String::class.java)

            StepVerifier.create(response)
                .expectNextMatches { entity ->
                    entity.statusCode == HttpStatus.CREATED &&
                        entity.body?.contains("reservationId") == true &&
                        entity.body?.contains("RESERVED") == true
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("Should return conflict for out-of-stock product")
        fun shouldReturnConflictForOutOfStockProduct() {
            val orderId = UUID.randomUUID()
            val requestBody = """
                {
                    "orderId": "$orderId",
                    "items": [{"productId": "out-of-stock-product", "quantity": 5}]
                }
            """.trimIndent()

            val response = webClient.post()
                .uri("/api/inventory/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .onStatus({ it == HttpStatus.CONFLICT }, { it.bodyToMono(String::class.java).map { body -> RuntimeException(body) } })
                .toEntity(String::class.java)
                .onErrorResume { error ->
                    // Expected conflict error
                    reactor.core.publisher.Mono.empty()
                }

            StepVerifier.create(response)
                .verifyComplete()
        }

        @Test
        @DisplayName("Should release inventory reservation")
        fun shouldReleaseInventoryReservation() {
            val reservationId = UUID.randomUUID()

            val response = webClient.delete()
                .uri("/api/inventory/reservations/$reservationId")
                .retrieve()
                .toBodilessEntity()

            StepVerifier.create(response)
                .expectNextMatches { it.statusCode == HttpStatus.NO_CONTENT }
                .verifyComplete()
        }

        @Test
        @DisplayName("Should check product availability")
        fun shouldCheckProductAvailability() {
            val productId = UUID.randomUUID()

            val response = webClient.get()
                .uri("/api/inventory/products/$productId/availability")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .toEntity(String::class.java)

            StepVerifier.create(response)
                .expectNextMatches { entity ->
                    entity.statusCode == HttpStatus.OK &&
                        entity.body?.contains("available") == true
                }
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("Payment Service Stubs")
    inner class PaymentServiceTests {

        @Test
        @DisplayName("Should authorize payment successfully")
        fun shouldAuthorizePaymentSuccessfully() {
            val orderId = UUID.randomUUID()
            val requestBody = """
                {
                    "orderId": "$orderId",
                    "amount": 99.99,
                    "paymentMethodId": "valid-card"
                }
            """.trimIndent()

            val response = webClient.post()
                .uri("/api/payments/authorize")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .toEntity(String::class.java)

            StepVerifier.create(response)
                .expectNextMatches { entity ->
                    entity.statusCode == HttpStatus.CREATED &&
                        entity.body?.contains("authorizationId") == true &&
                        entity.body?.contains("AUTHORIZED") == true
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("Should decline payment for declined-card")
        fun shouldDeclinePaymentForDeclinedCard() {
            val orderId = UUID.randomUUID()
            val requestBody = """
                {
                    "orderId": "$orderId",
                    "amount": 99.99,
                    "paymentMethodId": "declined-card"
                }
            """.trimIndent()

            val response = webClient.post()
                .uri("/api/payments/authorize")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .onStatus({ it == HttpStatus.PAYMENT_REQUIRED }, { it.bodyToMono(String::class.java).map { body -> RuntimeException(body) } })
                .toEntity(String::class.java)
                .onErrorResume { error ->
                    // Expected payment declined error
                    reactor.core.publisher.Mono.empty()
                }

            StepVerifier.create(response)
                .verifyComplete()
        }

        @Test
        @DisplayName("Should void authorization")
        fun shouldVoidAuthorization() {
            val authorizationId = UUID.randomUUID()

            val response = webClient.post()
                .uri("/api/payments/authorizations/$authorizationId/void")
                .retrieve()
                .toEntity(String::class.java)

            StepVerifier.create(response)
                .expectNextMatches { entity ->
                    entity.statusCode == HttpStatus.OK &&
                        entity.body?.contains("VOIDED") == true
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("Should capture payment")
        fun shouldCapturePayment() {
            val authorizationId = UUID.randomUUID()

            val response = webClient.post()
                .uri("/api/payments/authorizations/$authorizationId/capture")
                .retrieve()
                .toEntity(String::class.java)

            StepVerifier.create(response)
                .expectNextMatches { entity ->
                    entity.statusCode == HttpStatus.OK &&
                        entity.body?.contains("CAPTURED") == true
                }
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("Shipping Service Stubs")
    inner class ShippingServiceTests {

        @Test
        @DisplayName("Should create shipment successfully")
        fun shouldCreateShipmentSuccessfully() {
            val orderId = UUID.randomUUID()
            val requestBody = """
                {
                    "orderId": "$orderId",
                    "shippingAddress": {
                        "street": "123 Main St",
                        "city": "Anytown",
                        "state": "CA",
                        "postalCode": "90210",
                        "country": "US"
                    }
                }
            """.trimIndent()

            val response = webClient.post()
                .uri("/api/shipments")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .toEntity(String::class.java)

            StepVerifier.create(response)
                .expectNextMatches { entity ->
                    entity.statusCode == HttpStatus.CREATED &&
                        entity.body?.contains("shipmentId") == true &&
                        entity.body?.contains("trackingNumber") == true
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("Should return error for invalid postal code")
        fun shouldReturnErrorForInvalidPostalCode() {
            val orderId = UUID.randomUUID()
            val requestBody = """
                {
                    "orderId": "$orderId",
                    "shippingAddress": {
                        "street": "123 Main St",
                        "city": "Anytown",
                        "state": "CA",
                        "postalCode": "00000",
                        "country": "US"
                    }
                }
            """.trimIndent()

            val response = webClient.post()
                .uri("/api/shipments")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .onStatus({ it == HttpStatus.BAD_REQUEST }, { it.bodyToMono(String::class.java).map { body -> RuntimeException(body) } })
                .toEntity(String::class.java)
                .onErrorResume { error ->
                    // Expected invalid address error
                    reactor.core.publisher.Mono.empty()
                }

            StepVerifier.create(response)
                .verifyComplete()
        }

        @Test
        @DisplayName("Should cancel shipment")
        fun shouldCancelShipment() {
            val shipmentId = UUID.randomUUID()

            val response = webClient.post()
                .uri("/api/shipments/$shipmentId/cancel")
                .retrieve()
                .toEntity(String::class.java)

            StepVerifier.create(response)
                .expectNextMatches { entity ->
                    entity.statusCode == HttpStatus.OK &&
                        entity.body?.contains("CANCELLED") == true
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("Should get shipping rates")
        fun shouldGetShippingRates() {
            val requestBody = """
                {
                    "shippingAddress": {
                        "postalCode": "90210",
                        "country": "US"
                    },
                    "weight": 1.5
                }
            """.trimIndent()

            val response = webClient.post()
                .uri("/api/shipments/rates")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .toEntity(String::class.java)

            StepVerifier.create(response)
                .expectNextMatches { entity ->
                    entity.statusCode == HttpStatus.OK &&
                        entity.body?.contains("rates") == true &&
                        entity.body?.contains("STANDARD") == true
                }
                .verifyComplete()
        }
    }
}
