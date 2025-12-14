package com.pintailconsultingllc.sagapattern.history

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for DefaultTimelineDescriptionGenerator.
 */
@Tag("unit")
class TimelineDescriptionGeneratorTest {

    private val generator = DefaultTimelineDescriptionGenerator()
    private val testOrderId = UUID.randomUUID()
    private val testSagaExecutionId = UUID.randomUUID()

    @Test
    fun `generateTitle returns correct title for ORDER_CREATED`() {
        val event = OrderEvent.orderCreated(testOrderId)
        val title = generator.generateTitle(event)
        assertEquals("Order Placed", title)
    }

    @Test
    fun `generateTitle returns correct title for SAGA_STARTED`() {
        val event = OrderEvent.sagaStarted(testOrderId, testSagaExecutionId)
        val title = generator.generateTitle(event)
        assertEquals("Processing Started", title)
    }

    @Test
    fun `generateTitle returns correct title for step events`() {
        val inventoryEvent = OrderEvent.stepCompleted(testOrderId, testSagaExecutionId, "Inventory Reservation")
        assertEquals("Inventory Reserved", generator.generateTitle(inventoryEvent))

        val paymentEvent = OrderEvent.stepCompleted(testOrderId, testSagaExecutionId, "Payment Processing")
        assertEquals("Payment Processed", generator.generateTitle(paymentEvent))

        val shippingEvent = OrderEvent.stepCompleted(testOrderId, testSagaExecutionId, "Shipping Arrangement")
        assertEquals("Shipping Arranged", generator.generateTitle(shippingEvent))
    }

    @Test
    fun `generateTitle returns correct title for failed steps`() {
        val event = OrderEvent.stepFailed(testOrderId, testSagaExecutionId, "Payment Processing", null)
        val title = generator.generateTitle(event)
        assertEquals("Payment Failed", title)
    }

    @Test
    fun `generateTitle returns correct title for compensated steps`() {
        val inventoryEvent = OrderEvent.stepCompensated(testOrderId, testSagaExecutionId, "Inventory Reservation")
        assertEquals("Inventory Released", generator.generateTitle(inventoryEvent))

        val paymentEvent = OrderEvent.stepCompensated(testOrderId, testSagaExecutionId, "Payment Processing")
        assertEquals("Payment Reversed", generator.generateTitle(paymentEvent))
    }

    @Test
    fun `generateDescription returns meaningful descriptions`() {
        val orderCreatedEvent = OrderEvent.orderCreated(testOrderId)
        val description = generator.generateDescription(orderCreatedEvent)
        assertTrue(description.contains("order was received"))
    }

    @Test
    fun `generateDescription includes payment amount when available`() {
        val details = """{"totalChargedInCents": 5000}"""
        val event = OrderEvent.stepCompleted(testOrderId, testSagaExecutionId, "Payment Processing", details)
        val description = generator.generateDescription(event)
        assertTrue(description.contains("$50.00"))
    }

    @Test
    fun `toTimelineEntry converts event to entry with correct status`() {
        val successEvent = OrderEvent.stepCompleted(testOrderId, testSagaExecutionId, "Payment Processing")
        val successEntry = generator.toTimelineEntry(successEvent)
        assertEquals(TimelineStatus.SUCCESS, successEntry.status)
        assertEquals("Payment Processing", successEntry.stepName)

        val failedEvent = OrderEvent.stepFailed(testOrderId, testSagaExecutionId, "Payment Processing", null)
        val failedEntry = generator.toTimelineEntry(failedEvent)
        assertEquals(TimelineStatus.FAILED, failedEntry.status)

        val compensatedEvent = OrderEvent.stepCompensated(testOrderId, testSagaExecutionId, "Inventory Reservation")
        val compensatedEntry = generator.toTimelineEntry(compensatedEvent)
        assertEquals(TimelineStatus.COMPENSATED, compensatedEntry.status)
    }

    @Test
    fun `toTimelineEntry parses error info from JSON`() {
        val errorInfoJson = """{"code":"PAYMENT_DECLINED","message":"Card declined","recoverable":true,"suggestedAction":"Update payment method"}"""
        val event = OrderEvent.stepFailed(testOrderId, testSagaExecutionId, "Payment Processing", errorInfoJson)
        val entry = generator.toTimelineEntry(event)

        assertTrue(entry.description.contains("Card declined"))
    }

    @Test
    fun `generateDescription handles shipping with carrier info`() {
        val details = """{"carrier": "FedEx"}"""
        val event = OrderEvent.stepCompleted(testOrderId, testSagaExecutionId, "Shipping Arrangement", details)
        val description = generator.generateDescription(event)
        assertTrue(description.contains("FedEx"))
    }

    @Test
    fun `generateDescription handles saga completed with confirmation number`() {
        val details = """{"confirmationNumber": "CONF-12345", "trackingNumber": "TRK-67890"}"""
        val event = OrderEvent.sagaCompleted(testOrderId, testSagaExecutionId, details)
        val description = generator.generateDescription(event)
        assertTrue(description.contains("CONF-12345"))
    }
}
