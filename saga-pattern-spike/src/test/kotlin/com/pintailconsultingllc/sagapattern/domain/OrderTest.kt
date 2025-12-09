package com.pintailconsultingllc.sagapattern.domain

import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for Order entity.
 */
class OrderTest {

    @Test
    fun `order is created with correct defaults`() {
        val order = Order(
            customerId = UUID.randomUUID(),
            totalAmount = BigDecimal("99.99")
        )

        assertNotNull(order.id)
        assertEquals(OrderStatus.PENDING, order.status)
        assertNotNull(order.createdAt)
        assertNotNull(order.updatedAt)
        assertTrue(order.items.isEmpty())
    }

    @Test
    fun `withStatus creates new order with updated status`() {
        val order = Order(
            customerId = UUID.randomUUID(),
            totalAmount = BigDecimal("99.99"),
            status = OrderStatus.PENDING
        )

        val updatedOrder = order.withStatus(OrderStatus.PROCESSING)

        assertEquals(OrderStatus.PROCESSING, updatedOrder.status)
        assertEquals(order.id, updatedOrder.id)
        assertEquals(order.customerId, updatedOrder.customerId)
        assertTrue(updatedOrder.updatedAt >= order.updatedAt)
    }

    @Test
    fun `withItems creates new order with items`() {
        val order = Order(
            customerId = UUID.randomUUID(),
            totalAmount = BigDecimal("99.99")
        )

        val items = listOf(
            OrderItem(
                orderId = order.id,
                productId = UUID.randomUUID(),
                productName = "Product 1",
                quantity = 2,
                unitPrice = BigDecimal("49.99")
            )
        )

        val orderWithItems = order.withItems(items)

        assertEquals(1, orderWithItems.items.size)
        assertEquals("Product 1", orderWithItems.items[0].productName)
    }
}

/**
 * Unit tests for OrderItem entity.
 */
class OrderItemTest {

    @Test
    fun `lineTotal calculates correctly`() {
        val item = OrderItem(
            orderId = UUID.randomUUID(),
            productId = UUID.randomUUID(),
            productName = "Test Product",
            quantity = 3,
            unitPrice = BigDecimal("10.00")
        )

        assertEquals(BigDecimal("30.00"), item.lineTotal())
    }

    @Test
    fun `lineTotal with single quantity`() {
        val item = OrderItem(
            orderId = UUID.randomUUID(),
            productId = UUID.randomUUID(),
            productName = "Test Product",
            quantity = 1,
            unitPrice = BigDecimal("25.50")
        )

        assertEquals(BigDecimal("25.50"), item.lineTotal())
    }
}

/**
 * Unit tests for SagaExecution entity.
 */
class SagaExecutionTest {

    @Test
    fun `sagaExecution is created with correct defaults`() {
        val orderId = UUID.randomUUID()
        val execution = SagaExecution(orderId = orderId)

        assertNotNull(execution.id)
        assertEquals(orderId, execution.orderId)
        assertEquals(0, execution.currentStep)
        assertEquals(SagaStatus.PENDING, execution.status)
        assertNotNull(execution.startedAt)
    }

    @Test
    fun `start updates status`() {
        val execution = SagaExecution(orderId = UUID.randomUUID())
        val started = execution.start()

        assertEquals(SagaStatus.IN_PROGRESS, started.status)
    }

    @Test
    fun `advanceToStep updates current step`() {
        val execution = SagaExecution(orderId = UUID.randomUUID())
        val advanced = execution.advanceToStep(2)

        assertEquals(2, advanced.currentStep)
    }

    @Test
    fun `complete updates status and timestamp`() {
        val execution = SagaExecution(orderId = UUID.randomUUID())
        val completed = execution.complete()

        assertEquals(SagaStatus.COMPLETED, completed.status)
        assertNotNull(completed.completedAt)
    }

    @Test
    fun `fail updates status and records failure details`() {
        val execution = SagaExecution(orderId = UUID.randomUUID())
        val failed = execution.fail(2, "Step 2 failed")

        assertEquals(SagaStatus.FAILED, failed.status)
        assertEquals(2, failed.failedStep)
        assertEquals("Step 2 failed", failed.failureReason)
        assertNotNull(failed.completedAt)
    }

    @Test
    fun `startCompensation updates status`() {
        val execution = SagaExecution(orderId = UUID.randomUUID())
        val compensating = execution.startCompensation()

        assertEquals(SagaStatus.COMPENSATING, compensating.status)
        assertNotNull(compensating.compensationStartedAt)
    }

    @Test
    fun `completeCompensation updates status`() {
        val execution = SagaExecution(orderId = UUID.randomUUID())
        val compensated = execution.completeCompensation()

        assertEquals(SagaStatus.COMPENSATED, compensated.status)
        assertNotNull(compensated.compensationCompletedAt)
    }
}
