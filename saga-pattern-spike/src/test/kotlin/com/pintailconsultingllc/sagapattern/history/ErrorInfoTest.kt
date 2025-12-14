package com.pintailconsultingllc.sagapattern.history

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for ErrorInfo factory methods.
 */
@Tag("unit")
class ErrorInfoTest {

    @Test
    fun `fromStepFailure creates correct error info`() {
        val errorInfo = ErrorInfo.fromStepFailure("PAYMENT_DECLINED", "Card declined", true)

        assertEquals("PAYMENT_DECLINED", errorInfo.code)
        assertEquals("Card declined", errorInfo.message)
        assertTrue(errorInfo.recoverable)
        assertEquals("Please try again or contact support.", errorInfo.suggestedAction)
    }

    @Test
    fun `fromStepFailure handles null error code`() {
        val errorInfo = ErrorInfo.fromStepFailure(null, "Some error", false)

        assertEquals("UNKNOWN_ERROR", errorInfo.code)
        assertEquals("Some error", errorInfo.message)
        assertFalse(errorInfo.recoverable)
        assertNull(errorInfo.suggestedAction)
    }

    @Test
    fun `fromStepFailure handles null error message`() {
        val errorInfo = ErrorInfo.fromStepFailure("ERROR", null, true)

        assertEquals("ERROR", errorInfo.code)
        assertEquals("An unexpected error occurred", errorInfo.message)
    }

    @Test
    fun `paymentDeclined creates correct error info`() {
        val errorInfo = ErrorInfo.paymentDeclined()

        assertEquals("PAYMENT_DECLINED", errorInfo.code)
        assertEquals("Your card was declined by your bank.", errorInfo.message)
        assertTrue(errorInfo.recoverable)
        assertEquals("Please update your payment method and try again.", errorInfo.suggestedAction)
    }

    @Test
    fun `paymentDeclined with custom message`() {
        val customMessage = "Insufficient funds"
        val errorInfo = ErrorInfo.paymentDeclined(customMessage)

        assertEquals("PAYMENT_DECLINED", errorInfo.code)
        assertEquals(customMessage, errorInfo.message)
    }

    @Test
    fun `outOfStock creates correct error info`() {
        val errorInfo = ErrorInfo.outOfStock()

        assertEquals("OUT_OF_STOCK", errorInfo.code)
        assertEquals("One or more items are out of stock.", errorInfo.message)
        assertTrue(errorInfo.recoverable)
        assertEquals("Please remove unavailable items and try again.", errorInfo.suggestedAction)
    }

    @Test
    fun `shippingUnavailable creates correct error info`() {
        val errorInfo = ErrorInfo.shippingUnavailable()

        assertEquals("SHIPPING_UNAVAILABLE", errorInfo.code)
        assertEquals("Unable to arrange shipping to the provided address.", errorInfo.message)
        assertTrue(errorInfo.recoverable)
        assertEquals("Please verify your shipping address and try again.", errorInfo.suggestedAction)
    }
}
