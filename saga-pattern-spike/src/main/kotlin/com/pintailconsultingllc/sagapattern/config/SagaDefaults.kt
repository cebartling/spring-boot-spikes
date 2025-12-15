package com.pintailconsultingllc.sagapattern.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration properties for saga default values.
 *
 * These values can be overridden via application.yaml or environment variables.
 * Setting defaults to sensible production values ensures the application
 * works out-of-the-box while allowing customization.
 */
@ConfigurationProperties(prefix = "saga.defaults")
data class SagaDefaults(
    /**
     * Default number of days to add for estimated delivery
     * when not provided by the shipping service.
     */
    var estimatedDeliveryDays: Int = 5,

    /**
     * Maximum number of retry attempts allowed for a failed saga.
     */
    var maxRetryAttempts: Int = 3,

    /**
     * Default payment method ID to use when not specified.
     * Setting to null forces explicit specification.
     */
    var defaultPaymentMethodId: String? = null,

    /**
     * Default error message for unknown errors.
     */
    var unknownErrorMessage: String = "An unexpected error occurred"
)
