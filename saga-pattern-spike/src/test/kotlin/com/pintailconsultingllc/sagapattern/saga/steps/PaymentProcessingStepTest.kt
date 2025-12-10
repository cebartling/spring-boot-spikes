package com.pintailconsultingllc.sagapattern.saga.steps

import com.pintailconsultingllc.sagapattern.domain.Order
import com.pintailconsultingllc.sagapattern.domain.OrderItem
import com.pintailconsultingllc.sagapattern.domain.OrderStatus
import com.pintailconsultingllc.sagapattern.saga.SagaContext
import com.pintailconsultingllc.sagapattern.saga.ShippingAddress
import com.pintailconsultingllc.sagapattern.service.AuthorizationResponse
import com.pintailconsultingllc.sagapattern.service.PaymentException
import com.pintailconsultingllc.sagapattern.service.PaymentService
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
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
 * Unit tests for PaymentProcessingStep.
 */
class PaymentProcessingStepTest {

    private lateinit var paymentService: PaymentService
    private lateinit var step: PaymentProcessingStep
    private lateinit var context: SagaContext

    @BeforeEach
    fun setUp() {
        paymentService = mock()
        step = PaymentProcessingStep(paymentService)

        val orderId = UUID.randomUUID()
        val order = Order(
            id = orderId,
            customerId = UUID.randomUUID(),
            totalAmountInCents = 9999L,
            status = OrderStatus.PROCESSING,
            items = listOf(
                OrderItem(
                    orderId = orderId,
                    productId = UUID.randomUUID(),
                    productName = "Test Product",
                    quantity = 2,
                    unitPriceInCents = 4999L
                )
            )
        )

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
        assertEquals("Payment Processing", step.getStepName())
    }

    @Test
    fun `getStepOrder returns 2`() {
        assertEquals(2, step.getStepOrder())
    }

    @Test
    fun `execute succeeds and stores authorization ID`() = runTest {
        val authorizationId = UUID.randomUUID().toString()
        whenever(paymentService.authorizePayment(any(), any(), any(), any()))
            .thenReturn(AuthorizationResponse(authorizationId, "AUTHORIZED", "99.99", "USD"))

        val result = step.execute(context)

        assertTrue(result.success)
        assertEquals(authorizationId, result.getData<String>("authorizationId"))
        assertEquals(authorizationId, context.getData<String>(SagaContext.KEY_AUTHORIZATION_ID))
        assertTrue(context.isStepCompleted("Payment Processing"))
    }

    @Test
    fun `execute fails when payment service throws exception`() = runTest {
        whenever(paymentService.authorizePayment(any(), any(), any(), any()))
            .thenThrow(PaymentException("Card declined", "PAYMENT_DECLINED", true))

        val result = step.execute(context)

        assertFalse(result.success)
        assertEquals("Card declined", result.errorMessage)
        assertEquals("PAYMENT_DECLINED", result.errorCode)
    }

    @Test
    fun `compensate voids authorization`() = runTest {
        val authorizationId = UUID.randomUUID().toString()
        context.putData(SagaContext.KEY_AUTHORIZATION_ID, authorizationId)

        val result = step.compensate(context)

        assertTrue(result.success)
        verify(paymentService).voidAuthorization(authorizationId)
    }

    @Test
    fun `compensate succeeds when no authorization exists`() = runTest {
        val result = step.compensate(context)

        assertTrue(result.success)
        assertEquals("No authorization to void", result.message)
    }
}
