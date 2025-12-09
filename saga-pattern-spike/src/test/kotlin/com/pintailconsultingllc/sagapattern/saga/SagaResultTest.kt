package com.pintailconsultingllc.sagapattern.saga

import com.pintailconsultingllc.sagapattern.domain.Order
import com.pintailconsultingllc.sagapattern.domain.OrderStatus
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for SagaResult.
 */
class SagaResultTest {

    private val testOrder = Order(
        id = UUID.randomUUID(),
        customerId = UUID.randomUUID(),
        totalAmount = BigDecimal("99.99"),
        status = OrderStatus.COMPLETED
    )

    @Test
    fun `Success result has correct status`() {
        val result = SagaResult.Success(
            order = testOrder,
            confirmationNumber = "ORD-2024-ABC123",
            totalCharged = BigDecimal("99.99"),
            estimatedDelivery = LocalDate.now().plusDays(5),
            trackingNumber = "TRK123"
        )

        assertEquals(OrderStatus.COMPLETED, result.status)
        assertEquals("ORD-2024-ABC123", result.confirmationNumber)
        assertEquals(BigDecimal("99.99"), result.totalCharged)
        assertEquals("TRK123", result.trackingNumber)
    }

    @Test
    fun `Compensated result has correct status`() {
        val result = SagaResult.Compensated(
            order = testOrder.withStatus(OrderStatus.COMPENSATED),
            failedStep = "Payment Processing",
            failureReason = "Card declined",
            compensatedSteps = listOf("Inventory Reservation")
        )

        assertEquals(OrderStatus.COMPENSATED, result.status)
        assertEquals("Payment Processing", result.failedStep)
        assertEquals("Card declined", result.failureReason)
        assertEquals(listOf("Inventory Reservation"), result.compensatedSteps)
    }

    @Test
    fun `Failed result has correct status`() {
        val result = SagaResult.Failed(
            order = testOrder.withStatus(OrderStatus.FAILED),
            failedStep = "Inventory Reservation",
            failureReason = "Out of stock",
            errorCode = "INVENTORY_UNAVAILABLE"
        )

        assertEquals(OrderStatus.FAILED, result.status)
        assertEquals("Inventory Reservation", result.failedStep)
        assertEquals("Out of stock", result.failureReason)
        assertEquals("INVENTORY_UNAVAILABLE", result.errorCode)
    }

    @Test
    fun `generateConfirmationNumber creates valid format`() {
        val confirmationNumber = SagaResult.generateConfirmationNumber()

        assertNotNull(confirmationNumber)
        assertTrue(confirmationNumber.startsWith("ORD-"))
        assertTrue(confirmationNumber.contains(LocalDate.now().year.toString()))
    }

    @Test
    fun `generateConfirmationNumber creates unique values`() {
        val numbers = (1..100).map { SagaResult.generateConfirmationNumber() }
        val uniqueNumbers = numbers.toSet()

        assertEquals(100, uniqueNumbers.size)
    }
}
