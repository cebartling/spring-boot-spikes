package com.pintailconsultingllc.sagapattern.saga.steps

import com.pintailconsultingllc.sagapattern.domain.Order
import com.pintailconsultingllc.sagapattern.domain.OrderItem
import com.pintailconsultingllc.sagapattern.domain.OrderStatus
import com.pintailconsultingllc.sagapattern.saga.SagaContext
import com.pintailconsultingllc.sagapattern.domain.ShippingAddress
import com.pintailconsultingllc.sagapattern.service.InventoryException
import com.pintailconsultingllc.sagapattern.service.InventoryService
import com.pintailconsultingllc.sagapattern.service.ReservationResponse
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for InventoryReservationStep.
 */
@Tag("unit")
class InventoryReservationStepTest {

    private lateinit var inventoryService: InventoryService
    private lateinit var step: InventoryReservationStep
    private lateinit var context: SagaContext

    @BeforeEach
    fun setUp() {
        inventoryService = mock()
        step = InventoryReservationStep(inventoryService)

        val orderId = UUID.randomUUID()
        val order = Order(
            id = orderId,
            customerId = UUID.randomUUID(),
            totalAmountInCents = 9999L,
            status = OrderStatus.PROCESSING
        ).apply {
            items = listOf(
                OrderItem(
                    orderId = orderId,
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

    @Test
    fun `getStepName returns correct name`() {
        assertEquals("Inventory Reservation", step.getStepName())
    }

    @Test
    fun `getStepOrder returns 1`() {
        assertEquals(1, step.getStepOrder())
    }

    @Test
    fun `execute succeeds and stores reservation ID`() = runTest {
        val reservationId = UUID.randomUUID().toString()
        whenever(inventoryService.reserveInventory(any(), any()))
            .thenReturn(ReservationResponse(reservationId, "RESERVED", "2024-01-15T12:00:00Z"))

        val result = step.execute(context)

        assertTrue(result.success)
        assertEquals(reservationId, result.getData<String>("reservationId"))
        assertEquals(reservationId, context.getData<String>(SagaContext.KEY_RESERVATION_ID))
        assertTrue(context.isStepCompleted("Inventory Reservation"))
    }

    @Test
    fun `execute fails when inventory service throws exception`() = runTest {
        whenever(inventoryService.reserveInventory(any(), any()))
            .thenThrow(InventoryException("Insufficient stock", "INVENTORY_UNAVAILABLE"))

        val result = step.execute(context)

        assertFalse(result.success)
        assertEquals("Insufficient stock", result.errorMessage)
        assertEquals("INVENTORY_UNAVAILABLE", result.errorCode)
    }

    @Test
    fun `execute fails when order has no items`() = runTest {
        val emptyOrder = context.order.withItems(emptyList())
        val emptyContext = SagaContext(
            order = emptyOrder,
            sagaExecutionId = context.sagaExecutionId,
            customerId = context.customerId,
            paymentMethodId = context.paymentMethodId,
            shippingAddress = context.shippingAddress
        )

        val result = step.execute(emptyContext)

        assertFalse(result.success)
        assertEquals("NO_ITEMS", result.errorCode)
    }

    @Test
    fun `compensate releases reservation`() = runTest {
        val reservationId = UUID.randomUUID().toString()
        context.putData(SagaContext.KEY_RESERVATION_ID, reservationId)

        val result = step.compensate(context)

        assertTrue(result.success)
        verify(inventoryService).releaseReservation(reservationId)
    }

    @Test
    fun `compensate succeeds when no reservation exists`() = runTest {
        val result = step.compensate(context)

        assertTrue(result.success)
        assertEquals("No reservation to release", result.message)
    }
}
