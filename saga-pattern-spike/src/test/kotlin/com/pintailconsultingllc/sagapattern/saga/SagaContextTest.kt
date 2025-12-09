package com.pintailconsultingllc.sagapattern.saga

import com.pintailconsultingllc.sagapattern.domain.Order
import com.pintailconsultingllc.sagapattern.domain.OrderItem
import com.pintailconsultingllc.sagapattern.domain.OrderStatus
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for SagaContext.
 */
class SagaContextTest {

    private lateinit var order: Order
    private lateinit var context: SagaContext

    @BeforeEach
    fun setUp() {
        order = Order(
            id = UUID.randomUUID(),
            customerId = UUID.randomUUID(),
            totalAmount = BigDecimal("99.99"),
            status = OrderStatus.PENDING,
            items = listOf(
                OrderItem(
                    orderId = UUID.randomUUID(),
                    productId = UUID.randomUUID(),
                    productName = "Test Product",
                    quantity = 2,
                    unitPrice = BigDecimal("49.99")
                )
            )
        )

        context = SagaContext(
            order = order,
            sagaExecutionId = UUID.randomUUID(),
            customerId = order.customerId,
            paymentMethodId = "test-payment-method",
            shippingAddress = ShippingAddress(
                street = "123 Main St",
                city = "Anytown",
                state = "CA",
                postalCode = "90210",
                country = "US"
            )
        )
    }

    @Test
    fun `putData and getData work correctly`() {
        context.putData("testKey", "testValue")

        val retrieved: String? = context.getData("testKey")
        assertEquals("testValue", retrieved)
    }

    @Test
    fun `getData returns null for missing key`() {
        val retrieved: String? = context.getData("nonexistent")
        assertNull(retrieved)
    }

    @Test
    fun `hasData returns correct values`() {
        assertFalse(context.hasData("testKey"))

        context.putData("testKey", "testValue")

        assertTrue(context.hasData("testKey"))
    }

    @Test
    fun `getAllData returns all stored data`() {
        context.putData("key1", "value1")
        context.putData("key2", 42)

        val allData = context.getAllData()

        assertEquals(2, allData.size)
        assertEquals("value1", allData["key1"])
        assertEquals(42, allData["key2"])
    }

    @Test
    fun `markStepCompleted tracks completed steps`() {
        assertTrue(context.getCompletedSteps().isEmpty())

        context.markStepCompleted("Step1")
        context.markStepCompleted("Step2")

        val completedSteps = context.getCompletedSteps()
        assertEquals(2, completedSteps.size)
        assertEquals("Step1", completedSteps[0])
        assertEquals("Step2", completedSteps[1])
    }

    @Test
    fun `markStepCompleted ignores duplicates`() {
        context.markStepCompleted("Step1")
        context.markStepCompleted("Step1")

        assertEquals(1, context.getCompletedSteps().size)
    }

    @Test
    fun `isStepCompleted returns correct values`() {
        assertFalse(context.isStepCompleted("Step1"))

        context.markStepCompleted("Step1")

        assertTrue(context.isStepCompleted("Step1"))
        assertFalse(context.isStepCompleted("Step2"))
    }

    @Test
    fun `context stores reservation data correctly`() {
        val reservationId = UUID.randomUUID().toString()
        context.putData(SagaContext.KEY_RESERVATION_ID, reservationId)

        assertEquals(reservationId, context.getData<String>(SagaContext.KEY_RESERVATION_ID))
    }

    @Test
    fun `context stores authorization data correctly`() {
        val authId = UUID.randomUUID().toString()
        context.putData(SagaContext.KEY_AUTHORIZATION_ID, authId)

        assertEquals(authId, context.getData<String>(SagaContext.KEY_AUTHORIZATION_ID))
    }

    @Test
    fun `context stores shipment data correctly`() {
        val shipmentId = UUID.randomUUID().toString()
        val trackingNumber = "TRK123456"
        val estimatedDelivery = "2024-01-15"

        context.putData(SagaContext.KEY_SHIPMENT_ID, shipmentId)
        context.putData(SagaContext.KEY_TRACKING_NUMBER, trackingNumber)
        context.putData(SagaContext.KEY_ESTIMATED_DELIVERY, estimatedDelivery)

        assertEquals(shipmentId, context.getData<String>(SagaContext.KEY_SHIPMENT_ID))
        assertEquals(trackingNumber, context.getData<String>(SagaContext.KEY_TRACKING_NUMBER))
        assertEquals(estimatedDelivery, context.getData<String>(SagaContext.KEY_ESTIMATED_DELIVERY))
    }
}
