package com.pintailconsultingllc.sagapattern.history

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Unit tests for TimelineEntry factory methods.
 */
@Tag("unit")
class TimelineEntryTest {

    private val testTimestamp = Instant.now()

    @Test
    fun `success creates entry with SUCCESS status`() {
        val entry = TimelineEntry.success(
            timestamp = testTimestamp,
            title = "Payment Processed",
            description = "Payment was successful",
            stepName = "Payment Processing",
            details = mapOf("amount" to 5000)
        )

        assertEquals(testTimestamp, entry.timestamp)
        assertEquals("Payment Processed", entry.title)
        assertEquals("Payment was successful", entry.description)
        assertEquals(TimelineStatus.SUCCESS, entry.status)
        assertEquals("Payment Processing", entry.stepName)
        assertEquals(mapOf("amount" to 5000), entry.details)
        assertNull(entry.error)
    }

    @Test
    fun `failed creates entry with FAILED status`() {
        val errorInfo = ErrorInfo.paymentDeclined()
        val entry = TimelineEntry.failed(
            timestamp = testTimestamp,
            title = "Payment Failed",
            description = "Your payment could not be processed",
            stepName = "Payment Processing",
            error = errorInfo
        )

        assertEquals(testTimestamp, entry.timestamp)
        assertEquals("Payment Failed", entry.title)
        assertEquals("Your payment could not be processed", entry.description)
        assertEquals(TimelineStatus.FAILED, entry.status)
        assertEquals("Payment Processing", entry.stepName)
        assertEquals(errorInfo, entry.error)
    }

    @Test
    fun `compensated creates entry with COMPENSATED status`() {
        val entry = TimelineEntry.compensated(
            timestamp = testTimestamp,
            title = "Inventory Released",
            description = "Items returned to stock",
            stepName = "Inventory Reservation"
        )

        assertEquals(testTimestamp, entry.timestamp)
        assertEquals("Inventory Released", entry.title)
        assertEquals("Items returned to stock", entry.description)
        assertEquals(TimelineStatus.COMPENSATED, entry.status)
        assertEquals("Inventory Reservation", entry.stepName)
    }

    @Test
    fun `neutral creates entry with NEUTRAL status`() {
        val entry = TimelineEntry.neutral(
            timestamp = testTimestamp,
            title = "Order Placed",
            description = "Your order was received"
        )

        assertEquals(testTimestamp, entry.timestamp)
        assertEquals("Order Placed", entry.title)
        assertEquals("Your order was received", entry.description)
        assertEquals(TimelineStatus.NEUTRAL, entry.status)
        assertNull(entry.stepName)
    }
}
