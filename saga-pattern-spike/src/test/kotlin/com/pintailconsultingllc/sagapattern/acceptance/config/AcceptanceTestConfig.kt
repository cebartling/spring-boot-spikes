package com.pintailconsultingllc.sagapattern.acceptance.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient

/**
 * Test configuration for acceptance tests.
 *
 * Provides test-specific beans and configuration for Cucumber tests.
 * WebClients are configured for communicating with WireMock services
 * and the application under test.
 */
@TestConfiguration
class AcceptanceTestConfig {

    companion object {
        const val WIREMOCK_BASE_URL = "http://localhost:8081"
        const val APP_BASE_URL = "http://localhost"

        // WireMock service paths
        const val INVENTORY_PATH = "/api/inventory"
        const val PAYMENT_PATH = "/api/payments"
        const val SHIPPING_PATH = "/api/shipments"

        // Trigger values for WireMock failure scenarios
        const val OUT_OF_STOCK_PRODUCT_ID = "out-of-stock-product"
        const val DECLINED_CARD_ID = "declined-card"
        const val FRAUD_CARD_ID = "fraud-card"
        const val INVALID_POSTAL_CODE = "00000"
        const val UNDELIVERABLE_COUNTRY = "XX"
    }

    @Value("\${local.server.port:8080}")
    private var serverPort: Int = 8080

    /**
     * WebClient configured to call WireMock services.
     */
    @Bean
    fun wireMockWebClient(): WebClient {
        return WebClient.builder()
            .baseUrl(WIREMOCK_BASE_URL)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .build()
    }

    /**
     * WebClient configured to call the application under test.
     */
    @Bean
    fun applicationWebClient(): WebClient {
        return WebClient.builder()
            .baseUrl("$APP_BASE_URL:$serverPort")
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .build()
    }

    /**
     * WebClient for WireMock admin API operations (verification, reset, etc).
     */
    @Bean
    fun wireMockAdminClient(): WebClient {
        return WebClient.builder()
            .baseUrl("$WIREMOCK_BASE_URL/__admin")
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build()
    }
}
