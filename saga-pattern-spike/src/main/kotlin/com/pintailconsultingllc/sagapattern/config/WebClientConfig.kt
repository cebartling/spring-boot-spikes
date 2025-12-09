package com.pintailconsultingllc.sagapattern.config

import io.micrometer.observation.ObservationRegistry
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

/**
 * Configuration for WebClient with observation support.
 *
 * Spring Boot 4.0 with OpenTelemetry automatically instruments WebClient
 * when the ObservationRegistry is configured. This enables:
 * - Automatic trace propagation to downstream services
 * - HTTP client metrics (request count, latency, errors)
 * - Context propagation for distributed tracing
 */
@Configuration
class WebClientConfig {

    /**
     * Creates a general-purpose WebClient with observation support.
     * Trace context is automatically propagated to downstream services.
     */
    @Bean
    fun webClient(
        webClientBuilder: WebClient.Builder,
        observationRegistry: ObservationRegistry
    ): WebClient {
        return webClientBuilder
            .observationRegistry(observationRegistry)
            .build()
    }

    /**
     * Inventory service client with pre-configured base URL.
     */
    @Bean
    fun inventoryWebClient(
        webClientBuilder: WebClient.Builder,
        observationRegistry: ObservationRegistry,
        sagaServicesConfig: SagaServicesConfig
    ): WebClient {
        return webClientBuilder
            .baseUrl(sagaServicesConfig.inventory.baseUrl)
            .observationRegistry(observationRegistry)
            .build()
    }

    /**
     * Payment service client with pre-configured base URL.
     */
    @Bean
    fun paymentWebClient(
        webClientBuilder: WebClient.Builder,
        observationRegistry: ObservationRegistry,
        sagaServicesConfig: SagaServicesConfig
    ): WebClient {
        return webClientBuilder
            .baseUrl(sagaServicesConfig.payment.baseUrl)
            .observationRegistry(observationRegistry)
            .build()
    }

    /**
     * Shipping service client with pre-configured base URL.
     */
    @Bean
    fun shippingWebClient(
        webClientBuilder: WebClient.Builder,
        observationRegistry: ObservationRegistry,
        sagaServicesConfig: SagaServicesConfig
    ): WebClient {
        return webClientBuilder
            .baseUrl(sagaServicesConfig.shipping.baseUrl)
            .observationRegistry(observationRegistry)
            .build()
    }
}
