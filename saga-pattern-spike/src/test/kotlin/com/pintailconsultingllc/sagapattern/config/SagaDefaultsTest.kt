package com.pintailconsultingllc.sagapattern.config

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Unit tests for SagaDefaults configuration.
 */
@Tag("unit")
class SagaDefaultsTest {

    @Test
    fun `default values are set correctly`() {
        val defaults = SagaDefaults()

        assertEquals(5, defaults.estimatedDeliveryDays)
        assertEquals(3, defaults.maxRetryAttempts)
        assertNull(defaults.defaultPaymentMethodId)
        assertEquals("An unexpected error occurred", defaults.unknownErrorMessage)
    }

    @Test
    fun `custom values can be set`() {
        val defaults = SagaDefaults(
            estimatedDeliveryDays = 7,
            maxRetryAttempts = 5,
            defaultPaymentMethodId = "pm_default",
            unknownErrorMessage = "Custom error"
        )

        assertEquals(7, defaults.estimatedDeliveryDays)
        assertEquals(5, defaults.maxRetryAttempts)
        assertEquals("pm_default", defaults.defaultPaymentMethodId)
        assertEquals("Custom error", defaults.unknownErrorMessage)
    }

    @Test
    fun `default payment method can be null`() {
        val defaults = SagaDefaults(defaultPaymentMethodId = null)
        assertNull(defaults.defaultPaymentMethodId)
    }
}
