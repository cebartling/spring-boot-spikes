package com.pintailconsultingllc.sagapattern.saga.steps

import com.pintailconsultingllc.sagapattern.domain.Order
import com.pintailconsultingllc.sagapattern.domain.OrderItem
import com.pintailconsultingllc.sagapattern.domain.OrderStatus
import com.pintailconsultingllc.sagapattern.saga.SagaContext
import com.pintailconsultingllc.sagapattern.domain.ShippingAddress
import com.pintailconsultingllc.sagapattern.service.ShipmentResponse
import com.pintailconsultingllc.sagapattern.service.ShippingException
import com.pintailconsultingllc.sagapattern.service.ShippingService
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
 * Unit tests for ShippingArrangementStep.
 */
@Tag("unit")
class ShippingArrangementStepTest {

    private lateinit var shippingService: ShippingService
    private lateinit var step: ShippingArrangementStep
    private lateinit var context: SagaContext

    @BeforeEach
    fun setUp() {
        shippingService = mock()
        step = ShippingArrangementStep(shippingService)

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
        assertEquals("Shipping Arrangement", step.getStepName())
    }

    @Test
    fun `getStepOrder returns 3`() {
        assertEquals(3, step.getStepOrder())
    }

    @Test
    fun `execute succeeds and stores shipment details`() = runTest {
        val shipmentId = UUID.randomUUID().toString()
        whenever(shippingService.createShipment(any(), any()))
            .thenReturn(ShipmentResponse(
                shipmentId = shipmentId,
                status = "PENDING",
                carrier = "STANDARD",
                trackingNumber = "TRK123456",
                estimatedDelivery = "2024-01-20"
            ))

        val result = step.execute(context)

        assertTrue(result.success)
        assertEquals(shipmentId, result.getData<String>("shipmentId"))
        assertEquals(shipmentId, context.getData<String>(SagaContext.KEY_SHIPMENT_ID))
        assertEquals("TRK123456", context.getData<String>(SagaContext.KEY_TRACKING_NUMBER))
        assertEquals("2024-01-20", context.getData<String>(SagaContext.KEY_ESTIMATED_DELIVERY))
        assertTrue(context.isStepCompleted("Shipping Arrangement"))
    }

    @Test
    fun `execute fails when shipping service throws exception`() = runTest {
        whenever(shippingService.createShipment(any(), any()))
            .thenThrow(ShippingException("Invalid address", "INVALID_ADDRESS", true))

        val result = step.execute(context)

        assertFalse(result.success)
        assertEquals("Invalid address", result.errorMessage)
        assertEquals("INVALID_ADDRESS", result.errorCode)
    }

    @Test
    fun `compensate cancels shipment`() = runTest {
        val shipmentId = UUID.randomUUID().toString()
        context.putData(SagaContext.KEY_SHIPMENT_ID, shipmentId)

        val result = step.compensate(context)

        assertTrue(result.success)
        verify(shippingService).cancelShipment(shipmentId)
    }

    @Test
    fun `compensate succeeds when no shipment exists`() = runTest {
        val result = step.compensate(context)

        assertTrue(result.success)
        assertEquals("No shipment to cancel", result.message)
    }
}
