package com.pintailconsultingllc.sagapattern.saga

import com.pintailconsultingllc.sagapattern.domain.Order
import com.pintailconsultingllc.sagapattern.domain.OrderStatus
import com.pintailconsultingllc.sagapattern.domain.ShippingAddress
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.string
import io.kotest.property.arbitrary.uuid
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Property-based tests for SagaContext type-safe context keys.
 *
 * These tests verify that the ContextKey<T> mechanism provides
 * correct type-safe storage and retrieval across a wide range of inputs.
 */
@Tag("unit")
class SagaContextPropertyTest {

    private lateinit var order: Order

    @BeforeEach
    fun setUp() {
        order = Order(
            id = UUID.randomUUID(),
            customerId = UUID.randomUUID(),
            totalAmountInCents = 10000L,
            status = OrderStatus.PENDING
        )
    }

    private fun createTestContext(): SagaContext {
        return SagaContext(
            order = order,
            sagaExecutionId = UUID.randomUUID(),
            customerId = order.customerId,
            paymentMethodId = "test-payment",
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
    inner class StringValuePropertyTests {

        @Test
        fun `stored string values are retrievable with same type`() = runTest {
            checkAll(Arb.string()) { value ->
                val context = createTestContext()
                val key = ContextKey<String>("testStringKey")

                context.putData(key, value)

                assertEquals(value, context.getData(key))
            }
        }

        @Test
        fun `stored values are present in hasData check`() = runTest {
            checkAll(Arb.string()) { value ->
                val context = createTestContext()
                val key = ContextKey<String>("testKey")

                assertFalse(context.hasData(key))
                context.putData(key, value)
                assertTrue(context.hasData(key))
            }
        }

        @Test
        fun `RESERVATION_ID key stores and retrieves arbitrary strings`() = runTest {
            checkAll(Arb.string()) { value ->
                val context = createTestContext()

                context.putData(SagaContext.RESERVATION_ID, value)
                val retrieved = context.getData(SagaContext.RESERVATION_ID)

                assertEquals(value, retrieved)
            }
        }

        @Test
        fun `AUTHORIZATION_ID key stores and retrieves arbitrary strings`() = runTest {
            checkAll(Arb.string()) { value ->
                val context = createTestContext()

                context.putData(SagaContext.AUTHORIZATION_ID, value)
                val retrieved = context.getData(SagaContext.AUTHORIZATION_ID)

                assertEquals(value, retrieved)
            }
        }

        @Test
        fun `TRACKING_NUMBER key stores and retrieves arbitrary strings`() = runTest {
            checkAll(Arb.string()) { value ->
                val context = createTestContext()

                context.putData(SagaContext.TRACKING_NUMBER, value)
                val retrieved = context.getData(SagaContext.TRACKING_NUMBER)

                assertEquals(value, retrieved)
            }
        }

        @Test
        fun `ESTIMATED_DELIVERY key stores and retrieves arbitrary strings`() = runTest {
            checkAll(Arb.string()) { value ->
                val context = createTestContext()

                context.putData(SagaContext.ESTIMATED_DELIVERY, value)
                val retrieved = context.getData(SagaContext.ESTIMATED_DELIVERY)

                assertEquals(value, retrieved)
            }
        }

        @Test
        fun `SHIPMENT_ID key stores and retrieves arbitrary strings`() = runTest {
            checkAll(Arb.string()) { value ->
                val context = createTestContext()

                context.putData(SagaContext.SHIPMENT_ID, value)
                val retrieved = context.getData(SagaContext.SHIPMENT_ID)

                assertEquals(value, retrieved)
            }
        }
    }

    @Nested
    inner class IntValuePropertyTests {

        @Test
        fun `stored int values are retrievable with same type`() = runTest {
            checkAll(Arb.int()) { value ->
                val context = createTestContext()
                val key = ContextKey<Int>("testIntKey")

                context.putData(key, value)

                assertEquals(value, context.getData(key))
            }
        }
    }

    @Nested
    inner class LongValuePropertyTests {

        @Test
        fun `stored long values are retrievable with same type`() = runTest {
            checkAll(Arb.long()) { value ->
                val context = createTestContext()
                val key = ContextKey<Long>("testLongKey")

                context.putData(key, value)

                assertEquals(value, context.getData(key))
            }
        }
    }

    @Nested
    inner class KeyIsolationPropertyTests {

        @Test
        fun `different keys with same name suffix do not interfere`() = runTest {
            checkAll(Arb.string(), Arb.string()) { value1, value2 ->
                val context = createTestContext()
                val key1 = ContextKey<String>("key1")
                val key2 = ContextKey<String>("key2")

                context.putData(key1, value1)
                context.putData(key2, value2)

                assertEquals(value1, context.getData(key1))
                assertEquals(value2, context.getData(key2))
            }
        }

        @Test
        fun `predefined keys do not interfere with each other`() = runTest {
            checkAll(
                Arb.string(),
                Arb.string(),
                Arb.string(),
                Arb.string(),
                Arb.string()
            ) { reservationId, authId, shipmentId, trackingNumber, estimatedDelivery ->
                val context = createTestContext()

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

        @Test
        fun `overwriting a key updates the value correctly`() = runTest {
            checkAll(Arb.string(), Arb.string()) { originalValue, newValue ->
                val context = createTestContext()
                val key = ContextKey<String>("testKey")

                context.putData(key, originalValue)
                assertEquals(originalValue, context.getData(key))

                context.putData(key, newValue)
                assertEquals(newValue, context.getData(key))
            }
        }
    }

    @Nested
    inner class GetAllDataPropertyTests {

        @Test
        fun `getAllData contains all stored values`() = runTest {
            checkAll(Arb.string(), Arb.string(), Arb.string()) { val1, val2, val3 ->
                val context = createTestContext()
                val key1 = ContextKey<String>("key1")
                val key2 = ContextKey<String>("key2")
                val key3 = ContextKey<String>("key3")

                context.putData(key1, val1)
                context.putData(key2, val2)
                context.putData(key3, val3)

                val allData = context.getAllData()

                assertEquals(3, allData.size)
                assertEquals(val1, allData["key1"])
                assertEquals(val2, allData["key2"])
                assertEquals(val3, allData["key3"])
            }
        }
    }

    @Nested
    inner class StepCompletionPropertyTests {

        @Test
        fun `markStepCompleted preserves order of completion`() = runTest {
            checkAll(Arb.list(Arb.string(1..20), 1..10)) { stepNames ->
                val context = createTestContext()
                val uniqueSteps = stepNames.distinct()

                uniqueSteps.forEach { context.markStepCompleted(it) }

                val completedSteps = context.getCompletedSteps()
                assertEquals(uniqueSteps.size, completedSteps.size)
                assertEquals(uniqueSteps, completedSteps)
            }
        }

        @Test
        fun `isStepCompleted returns true only for completed steps`() = runTest {
            checkAll(Arb.string(1..20), Arb.string(1..20)) { completedStep, otherStep ->
                // Ensure we have two different step names for a meaningful test
                if (completedStep != otherStep) {
                    val context = createTestContext()

                    context.markStepCompleted(completedStep)

                    assertTrue(context.isStepCompleted(completedStep))
                    assertFalse(context.isStepCompleted(otherStep))
                }
            }
        }

        @Test
        fun `duplicate step completions are idempotent`() = runTest {
            checkAll(Arb.string(1..20), Arb.int(1..10)) { stepName, repeatCount ->
                val context = createTestContext()

                repeat(repeatCount) {
                    context.markStepCompleted(stepName)
                }

                assertEquals(1, context.getCompletedSteps().size)
                assertTrue(context.isStepCompleted(stepName))
            }
        }
    }

    @Nested
    inner class ContextKeyEqualityPropertyTests {

        @Test
        fun `ContextKey equality is based on name`() = runTest {
            checkAll(Arb.string()) { keyName ->
                val key1 = ContextKey<String>(keyName)
                val key2 = ContextKey<String>(keyName)

                assertEquals(key1, key2)
                assertEquals(key1.hashCode(), key2.hashCode())
            }
        }

        @Test
        fun `ContextKey inequality for different names`() = runTest {
            checkAll(Arb.string(), Arb.string()) { name1, name2 ->
                if (name1 != name2) {
                    val key1 = ContextKey<String>(name1)
                    val key2 = ContextKey<String>(name2)

                    assertFalse(key1 == key2)
                }
            }
        }
    }

    @Nested
    inner class MissingKeyPropertyTests {

        @Test
        fun `getData returns null for arbitrary missing keys`() = runTest {
            checkAll(Arb.string()) { keyName ->
                val context = createTestContext()
                val key = ContextKey<String>(keyName)

                assertNull(context.getData(key))
            }
        }

        @Test
        fun `hasData returns false for arbitrary missing keys`() = runTest {
            checkAll(Arb.string()) { keyName ->
                val context = createTestContext()
                val key = ContextKey<String>(keyName)

                assertFalse(context.hasData(key))
            }
        }
    }

    @Nested
    inner class UuidValuePropertyTests {

        @Test
        fun `stored UUID values are retrievable with same type`() = runTest {
            checkAll(Arb.uuid()) { value ->
                val context = createTestContext()
                val key = ContextKey<UUID>("testUuidKey")

                context.putData(key, value)

                assertEquals(value, context.getData(key))
            }
        }

        @Test
        fun `UUID values stored as strings round-trip correctly`() = runTest {
            checkAll(Arb.uuid()) { value ->
                val context = createTestContext()

                context.putData(SagaContext.RESERVATION_ID, value.toString())
                val retrieved = context.getData(SagaContext.RESERVATION_ID)

                assertNotNull(retrieved)
                assertEquals(value, UUID.fromString(retrieved))
            }
        }
    }
}
