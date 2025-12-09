package com.pintailconsultingllc.sagapattern.acceptance.config

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.web.reactive.function.client.WebClient

/**
 * Test configuration for acceptance tests.
 *
 * Provides test-specific beans and configuration for Cucumber tests.
 */
@TestConfiguration
class AcceptanceTestConfig {

    companion object {
        const val WIREMOCK_BASE_URL = "http://localhost:8081"
        const val APP_BASE_URL = "http://localhost"
    }

    /**
     * WebClient configured to call WireMock services.
     */
    @Bean
    fun wireMockWebClient(): WebClient {
        return WebClient.builder()
            .baseUrl(WIREMOCK_BASE_URL)
            .build()
    }
}
