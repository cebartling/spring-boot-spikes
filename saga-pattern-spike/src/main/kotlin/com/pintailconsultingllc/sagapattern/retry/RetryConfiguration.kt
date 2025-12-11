package com.pintailconsultingllc.sagapattern.retry

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration properties for retry functionality.
 */
@ConfigurationProperties(prefix = "saga.retry")
data class RetryConfiguration(
    /**
     * Maximum number of retry attempts allowed per order.
     */
    val maxAttempts: Int = 3,

    /**
     * Time window in hours during which retries are allowed.
     */
    val windowHours: Int = 24,

    /**
     * Minimum time in minutes between retry attempts.
     */
    val cooldownMinutes: Int = 5,

    /**
     * TTL for inventory reservation validity.
     */
    val inventoryReservationTtl: String = "PT1H",

    /**
     * TTL for payment authorization validity.
     */
    val paymentAuthorizationTtl: String = "PT24H",

    /**
     * TTL for shipping quote validity.
     */
    val shippingQuoteTtl: String = "PT4H"
)
