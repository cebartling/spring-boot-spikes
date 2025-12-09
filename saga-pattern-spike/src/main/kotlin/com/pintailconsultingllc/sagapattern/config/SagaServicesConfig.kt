package com.pintailconsultingllc.sagapattern.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration properties for external saga services.
 * These URLs can be stored in Vault's KV secrets engine or in application.yaml.
 */
@ConfigurationProperties(prefix = "saga.services")
data class SagaServicesConfig(
    /**
     * Inventory service configuration.
     */
    var inventory: ServiceConfig = ServiceConfig(),

    /**
     * Payment service configuration.
     */
    var payment: ServiceConfig = ServiceConfig(),

    /**
     * Shipping service configuration.
     */
    var shipping: ServiceConfig = ServiceConfig()
)

/**
 * Configuration for an individual service endpoint.
 */
data class ServiceConfig(
    /**
     * Base URL for the service.
     */
    var baseUrl: String = ""
)
