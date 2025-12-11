package com.pintailconsultingllc.sagapattern.history

import com.pintailconsultingllc.sagapattern.domain.OrderStatus
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for OrderTimeline.
 */
class OrderTimelineTest {

    private val testOrderId = UUID.randomUUID()
    private val testTimestamp = Instant.now()

    @Test
    fun `empty creates timeline with no entries`() {
        val timeline = OrderTimeline.empty(
            orderId = testOrderId,
            orderNumber = "ORD-2024-12345",
            createdAt = testTimestamp,
            currentStatus = OrderStatus.PENDING
        )

        assertEquals(testOrderId, timeline.orderId)
        assertEquals("ORD-2024-12345", timeline.orderNumber)
        assertEquals(testTimestamp, timeline.createdAt)
        assertEquals(OrderStatus.PENDING, timeline.currentStatus)
        assertTrue(timeline.entries.isEmpty())
    }

    @Test
    fun `hasFailures returns true when timeline has failed entries`() {
        val entries = listOf(
            TimelineEntry.success(testTimestamp, "Step 1", "Success", "Step1"),
            TimelineEntry.failed(testTimestamp, "Step 2", "Failed", "Step2")
        )
        val timeline = OrderTimeline(
            orderId = testOrderId,
            orderNumber = "ORD-2024-12345",
            createdAt = testTimestamp,
            currentStatus = OrderStatus.FAILED,
            entries = entries
        )

        assertTrue(timeline.hasFailures())
    }

    @Test
    fun `hasFailures returns false when no failed entries`() {
        val entries = listOf(
            TimelineEntry.success(testTimestamp, "Step 1", "Success", "Step1"),
            TimelineEntry.success(testTimestamp, "Step 2", "Success", "Step2")
        )
        val timeline = OrderTimeline(
            orderId = testOrderId,
            orderNumber = "ORD-2024-12345",
            createdAt = testTimestamp,
            currentStatus = OrderStatus.COMPLETED,
            entries = entries
        )

        assertFalse(timeline.hasFailures())
    }

    @Test
    fun `hasCompensations returns true when timeline has compensated entries`() {
        val entries = listOf(
            TimelineEntry.compensated(testTimestamp, "Step 1 Reversed", "Compensation", "Step1")
        )
        val timeline = OrderTimeline(
            orderId = testOrderId,
            orderNumber = "ORD-2024-12345",
            createdAt = testTimestamp,
            currentStatus = OrderStatus.COMPENSATED,
            entries = entries
        )

        assertTrue(timeline.hasCompensations())
    }

    @Test
    fun `latestEntry returns the last entry`() {
        val entries = listOf(
            TimelineEntry.neutral(testTimestamp, "First", "First entry"),
            TimelineEntry.success(testTimestamp.plusSeconds(10), "Last", "Last entry")
        )
        val timeline = OrderTimeline(
            orderId = testOrderId,
            orderNumber = "ORD-2024-12345",
            createdAt = testTimestamp,
            currentStatus = OrderStatus.COMPLETED,
            entries = entries
        )

        val latest = timeline.latestEntry()
        assertEquals("Last", latest?.title)
    }

    @Test
    fun `latestEntry returns null for empty timeline`() {
        val timeline = OrderTimeline.empty(
            orderId = testOrderId,
            orderNumber = "ORD-2024-12345",
            createdAt = testTimestamp,
            currentStatus = OrderStatus.PENDING
        )

        assertNull(timeline.latestEntry())
    }

    @Test
    fun `entriesForStep filters by step name`() {
        val entries = listOf(
            TimelineEntry.neutral(testTimestamp, "Started", "Step started", "Payment Processing"),
            TimelineEntry.success(testTimestamp, "Completed", "Step completed", "Payment Processing"),
            TimelineEntry.neutral(testTimestamp, "Started", "Step started", "Shipping")
        )
        val timeline = OrderTimeline(
            orderId = testOrderId,
            orderNumber = "ORD-2024-12345",
            createdAt = testTimestamp,
            currentStatus = OrderStatus.COMPLETED,
            entries = entries
        )

        val paymentEntries = timeline.entriesForStep("Payment Processing")
        assertEquals(2, paymentEntries.size)
    }
}
