package com.pintailconsultingllc.sagapattern.saga

import com.pintailconsultingllc.sagapattern.domain.Order
import com.pintailconsultingllc.sagapattern.domain.OrderItem
import com.pintailconsultingllc.sagapattern.domain.OrderStatus
import com.pintailconsultingllc.sagapattern.domain.ShippingAddress
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for SagaContext.
 */
@Tag("unit")
class SagaContextTest {

    private lateinit var order: Order
    private lateinit var context: SagaContext

    @BeforeEach
    fun setUp() {
        order = Order(
            id = UUID.randomUUID(),
            customerId = UUID.randomUUID(),
            totalAmountInCents = 9999L,
            status = OrderStatus.PENDING
        ).apply {
            items = listOf(
                OrderItem(
                    orderId = UUID.randomUUID(),
                    productId = UUID.randomUUID(),
                    productName = "Test Product",
                    quantity = 2,
                    unitPriceInCents = 4999L
                )
            )
        }

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

    @Nested
    inner class TypeSafeKeyTests {

        @Test
        fun `putData and getData work correctly with type-safe keys`() {
            val testKey = ContextKey<String>("testKey")
            context.putData(testKey, "testValue")

            val retrieved = context.getData(testKey)
            assertEquals("testValue", retrieved)
        }

        @Test
        fun `getData returns null for missing type-safe key`() {
            val testKey = ContextKey<String>("nonexistent")
            val retrieved = context.getData(testKey)
            assertNull(retrieved)
        }

        @Test
        fun `hasData returns correct values for type-safe keys`() {
            val testKey = ContextKey<String>("testKey")
            assertFalse(context.hasData(testKey))

            context.putData(testKey, "testValue")

            assertTrue(context.hasData(testKey))
        }

        @Test
        fun `type-safe keys preserve type information`() {
            val intKey = ContextKey<Int>("intValue")
            val stringKey = ContextKey<String>("stringValue")

            context.putData(intKey, 42)
            context.putData(stringKey, "hello")

            // Type is inferred correctly - no need for explicit type parameter
            val intValue: Int? = context.getData(intKey)
            val stringValue: String? = context.getData(stringKey)

            assertEquals(42, intValue)
            assertEquals("hello", stringValue)
        }

        @Test
        fun `predefined context keys work correctly`() {
            val reservationId = UUID.randomUUID().toString()
            val authId = UUID.randomUUID().toString()
            val shipmentId = UUID.randomUUID().toString()
            val trackingNumber = "TRK123456"
            val estimatedDelivery = "2024-01-15"

            context.putData(SagaContext.RESERVATION_ID, reservationId)
            context.putData(SagaContext.AUTHORIZATION_ID, authId)
            context.putData(SagaContext.SHIPMENT_ID, shipmentId)
            context.putData(SagaContext.TRACKING_NUMBER, trackingNumber)
            context.putData(SagaContext.ESTIMATED_DELIVERY, estimatedDelivery)

            assertEquals(reservationId, context.getData(SagaContext.RESERVATION_ID))
            assertEquals(authId, context.getData(SagaContext.AUTHORIZATION_ID))
            assertEquals(shipmentId, context.getData(SagaContext.SHIPMENT_ID))
            assertEquals(trackingNumber, context.getData(SagaContext.TRACKING_NUMBER))
            assertEquals(estimatedDelivery, context.getData(SagaContext.ESTIMATED_DELIVERY))
        }
    }

    @Nested
    @Suppress("DEPRECATION")
    inner class LegacyStringKeyTests {

        @Test
        fun `putData and getData work correctly with string keys`() {
            context.putData("testKey", "testValue")

            val retrieved: String? = context.getData("testKey")
            assertEquals("testValue", retrieved)
        }

        @Test
        fun `getData returns null for missing string key`() {
            val retrieved: String? = context.getData("nonexistent")
            assertNull(retrieved)
        }

        @Test
        fun `hasData returns correct values for string keys`() {
            assertFalse(context.hasData("testKey"))

            context.putData("testKey", "testValue")

            assertTrue(context.hasData("testKey"))
        }

        @Test
        fun `legacy key constants still work`() {
            val reservationId = UUID.randomUUID().toString()
            context.putData(SagaContext.KEY_RESERVATION_ID, reservationId)

            assertEquals(reservationId, context.getData<String>(SagaContext.KEY_RESERVATION_ID))
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
    }

    @Nested
    inner class StepCompletionTrackingTests {

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
    }

    @Nested
    inner class ContextKeyTests {

        @Test
        fun `ContextKey equals and hashCode work correctly`() {
            val key1 = ContextKey<String>("testKey")
            val key2 = ContextKey<String>("testKey")
            val key3 = ContextKey<String>("differentKey")

            assertEquals(key1, key2)
            assertEquals(key1.hashCode(), key2.hashCode())
            assertFalse(key1 == key3)
        }

        @Test
        fun `ContextKey toString is descriptive`() {
            val key = ContextKey<String>("testKey")
            assertTrue(key.toString().contains("testKey"))
        }
    }
}
