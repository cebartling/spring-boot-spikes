package com.pintailconsultingllc.sagapattern.retry

import com.pintailconsultingllc.sagapattern.config.SagaDefaults
import com.pintailconsultingllc.sagapattern.domain.Order
import com.pintailconsultingllc.sagapattern.domain.OrderStatus
import com.pintailconsultingllc.sagapattern.domain.SagaStepResult
import com.pintailconsultingllc.sagapattern.domain.StepStatus
import com.pintailconsultingllc.sagapattern.repository.SagaStepResultRepository
import com.pintailconsultingllc.sagapattern.saga.SagaContext
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Unit tests for RetryContextBuilder.
 */
@Tag("unit")
class RetryContextBuilderTest {

    private lateinit var sagaStepResultRepository: SagaStepResultRepository
    private lateinit var sagaDefaults: SagaDefaults
    private lateinit var retryContextBuilder: RetryContextBuilder

    private val orderId = UUID.randomUUID()
    private val customerId = UUID.randomUUID()
    private val sagaExecutionId = UUID.randomUUID()
    private val originalExecutionId = UUID.randomUUID()

    private lateinit var order: Order

    @BeforeEach
    fun setUp() {
        sagaStepResultRepository = mock()
        sagaDefaults = SagaDefaults()

        retryContextBuilder = RetryContextBuilder(
            sagaStepResultRepository = sagaStepResultRepository,
            sagaDefaults = sagaDefaults
        )

        order = Order(
            id = orderId,
            customerId = customerId,
            totalAmountInCents = 9999L,
            status = OrderStatus.FAILED
        )

        // Default repository behavior - return empty list
        sagaStepResultRepository.stub {
            onBlocking { findBySagaExecutionIdOrderByStepOrder(any()) } doReturn emptyList()
        }
    }

    @Test
    fun `buildContext throws when shipping address is not provided`() = runTest {
        val request = RetryRequest(
            orderId = orderId,
            updatedPaymentMethodId = "payment-123",
            updatedShippingAddress = null
        )

        val exception = assertThrows<RetryContextValidationException> {
            retryContextBuilder.buildContext(
                order = order,
                sagaExecutionId = sagaExecutionId,
                originalExecutionId = originalExecutionId,
                request = request
            )
        }

        assertEquals("shippingAddress", exception.field)
        assert(exception.reason.contains("required"))
    }

    @Test
    fun `buildContext throws when shipping address has empty fields`() = runTest {
        val request = RetryRequest(
            orderId = orderId,
            updatedPaymentMethodId = "payment-123",
            updatedShippingAddress = ShippingAddress(
                street = "",
                city = "Anytown",
                state = "CA",
                postalCode = "90210",
                country = "US"
            )
        )

        val exception = assertThrows<RetryContextValidationException> {
            retryContextBuilder.buildContext(
                order = order,
                sagaExecutionId = sagaExecutionId,
                originalExecutionId = originalExecutionId,
                request = request
            )
        }

        assertEquals("shippingAddress", exception.field)
        assert(exception.reason.contains("street"))
    }

    @Test
    fun `buildContext throws when payment method is not provided and no default`() = runTest {
        val request = RetryRequest(
            orderId = orderId,
            updatedPaymentMethodId = null,
            updatedShippingAddress = validShippingAddress()
        )

        // Ensure no default payment method is configured
        sagaDefaults.defaultPaymentMethodId = null

        val exception = assertThrows<RetryContextValidationException> {
            retryContextBuilder.buildContext(
                order = order,
                sagaExecutionId = sagaExecutionId,
                originalExecutionId = originalExecutionId,
                request = request
            )
        }

        assertEquals("paymentMethodId", exception.field)
        assert(exception.reason.contains("required"))
    }

    @Test
    fun `buildContext uses default payment method when not provided in request`() = runTest {
        sagaDefaults.defaultPaymentMethodId = "default-payment"

        val request = RetryRequest(
            orderId = orderId,
            updatedPaymentMethodId = null,
            updatedShippingAddress = validShippingAddress()
        )

        val context = retryContextBuilder.buildContext(
            order = order,
            sagaExecutionId = sagaExecutionId,
            originalExecutionId = originalExecutionId,
            request = request
        )

        assertEquals("default-payment", context.paymentMethodId)
    }

    @Test
    fun `buildContext creates valid context with all required data`() = runTest {
        val request = RetryRequest(
            orderId = orderId,
            updatedPaymentMethodId = "payment-123",
            updatedShippingAddress = validShippingAddress()
        )

        val context = retryContextBuilder.buildContext(
            order = order,
            sagaExecutionId = sagaExecutionId,
            originalExecutionId = originalExecutionId,
            request = request
        )

        assertEquals(orderId, context.order.id)
        assertEquals(sagaExecutionId, context.sagaExecutionId)
        assertEquals(customerId, context.customerId)
        assertEquals("payment-123", context.paymentMethodId)
        assertEquals("123 Main St", context.shippingAddress.street)
        assertEquals("Anytown", context.shippingAddress.city)
        assertEquals("CA", context.shippingAddress.state)
        assertEquals("90210", context.shippingAddress.postalCode)
        assertEquals("US", context.shippingAddress.country)
    }

    @Test
    fun `buildContext reconstructs context data from previous step results`() = runTest {
        val stepResults = listOf(
            createCompletedStepResult(
                "InventoryReservationStep",
                1,
                """{"reservationId": "res-123"}"""
            ),
            createCompletedStepResult(
                "PaymentProcessingStep",
                2,
                """{"authorizationId": "auth-456"}"""
            )
        )

        sagaStepResultRepository.stub {
            onBlocking { findBySagaExecutionIdOrderByStepOrder(originalExecutionId) } doReturn stepResults
        }

        val request = RetryRequest(
            orderId = orderId,
            updatedPaymentMethodId = "payment-123",
            updatedShippingAddress = validShippingAddress()
        )

        val context = retryContextBuilder.buildContext(
            order = order,
            sagaExecutionId = sagaExecutionId,
            originalExecutionId = originalExecutionId,
            request = request
        )

        assertEquals("res-123", context.getData(SagaContext.RESERVATION_ID))
        assertEquals("auth-456", context.getData(SagaContext.AUTHORIZATION_ID))
    }

    @Test
    fun `buildContext reconstructs all known context keys`() = runTest {
        val stepResults = listOf(
            createCompletedStepResult(
                "InventoryReservationStep",
                1,
                """{"reservationId": "res-123"}"""
            ),
            createCompletedStepResult(
                "PaymentProcessingStep",
                2,
                """{"authorizationId": "auth-456"}"""
            ),
            createCompletedStepResult(
                "ShippingArrangementStep",
                3,
                """{"shipmentId": "ship-789", "trackingNumber": "TRK-001", "estimatedDelivery": "2025-12-20"}"""
            )
        )

        sagaStepResultRepository.stub {
            onBlocking { findBySagaExecutionIdOrderByStepOrder(originalExecutionId) } doReturn stepResults
        }

        val request = RetryRequest(
            orderId = orderId,
            updatedPaymentMethodId = "payment-123",
            updatedShippingAddress = validShippingAddress()
        )

        val context = retryContextBuilder.buildContext(
            order = order,
            sagaExecutionId = sagaExecutionId,
            originalExecutionId = originalExecutionId,
            request = request
        )

        assertEquals("res-123", context.getData(SagaContext.RESERVATION_ID))
        assertEquals("auth-456", context.getData(SagaContext.AUTHORIZATION_ID))
        assertEquals("ship-789", context.getData(SagaContext.SHIPMENT_ID))
        assertEquals("TRK-001", context.getData(SagaContext.TRACKING_NUMBER))
        assertEquals("2025-12-20", context.getData(SagaContext.ESTIMATED_DELIVERY))
    }

    @Test
    fun `buildContext ignores non-completed step results`() = runTest {
        val stepResults = listOf(
            createCompletedStepResult(
                "InventoryReservationStep",
                1,
                """{"reservationId": "res-123"}"""
            ),
            createStepResult(
                "PaymentProcessingStep",
                2,
                StepStatus.FAILED,
                null
            )
        )

        sagaStepResultRepository.stub {
            onBlocking { findBySagaExecutionIdOrderByStepOrder(originalExecutionId) } doReturn stepResults
        }

        val request = RetryRequest(
            orderId = orderId,
            updatedPaymentMethodId = "payment-123",
            updatedShippingAddress = validShippingAddress()
        )

        val context = retryContextBuilder.buildContext(
            order = order,
            sagaExecutionId = sagaExecutionId,
            originalExecutionId = originalExecutionId,
            request = request
        )

        assertEquals("res-123", context.getData(SagaContext.RESERVATION_ID))
        assertNull(context.getData(SagaContext.AUTHORIZATION_ID))
    }

    @Test
    fun `buildContext handles step results with null step data`() = runTest {
        val stepResults = listOf(
            createCompletedStepResult(
                "InventoryReservationStep",
                1,
                null
            )
        )

        sagaStepResultRepository.stub {
            onBlocking { findBySagaExecutionIdOrderByStepOrder(originalExecutionId) } doReturn stepResults
        }

        val request = RetryRequest(
            orderId = orderId,
            updatedPaymentMethodId = "payment-123",
            updatedShippingAddress = validShippingAddress()
        )

        val context = retryContextBuilder.buildContext(
            order = order,
            sagaExecutionId = sagaExecutionId,
            originalExecutionId = originalExecutionId,
            request = request
        )

        // Should not throw, just not have any reconstructed data
        assertNull(context.getData(SagaContext.RESERVATION_ID))
    }

    @Test
    fun `buildContext handles malformed JSON in step data gracefully`() = runTest {
        val stepResults = listOf(
            createCompletedStepResult(
                "InventoryReservationStep",
                1,
                "not valid json"
            )
        )

        sagaStepResultRepository.stub {
            onBlocking { findBySagaExecutionIdOrderByStepOrder(originalExecutionId) } doReturn stepResults
        }

        val request = RetryRequest(
            orderId = orderId,
            updatedPaymentMethodId = "payment-123",
            updatedShippingAddress = validShippingAddress()
        )

        // Should not throw - gracefully handles malformed JSON
        val context = retryContextBuilder.buildContext(
            order = order,
            sagaExecutionId = sagaExecutionId,
            originalExecutionId = originalExecutionId,
            request = request
        )

        assertNotNull(context)
        assertNull(context.getData(SagaContext.RESERVATION_ID))
    }

    @Test
    fun `validateContextForResume returns Valid when no data required`() {
        val context = createBasicContext()

        val result = retryContextBuilder.validateContextForResume(
            context = context,
            resumeStepName = "InventoryReservationStep"
        )

        assertIs<ContextValidationResult.Valid>(result)
    }

    @Test
    fun `validateContextForResume returns Invalid for PaymentProcessingStep without reservationId`() {
        val context = createBasicContext()

        val result = retryContextBuilder.validateContextForResume(
            context = context,
            resumeStepName = "PaymentProcessingStep"
        )

        assertIs<ContextValidationResult.Invalid>(result)
        assert(result.missingData.any { it.contains("reservationId") })
    }

    @Test
    fun `validateContextForResume returns Valid for PaymentProcessingStep with reservationId`() {
        val context = createBasicContext()
        context.putData(SagaContext.RESERVATION_ID, "res-123")

        val result = retryContextBuilder.validateContextForResume(
            context = context,
            resumeStepName = "PaymentProcessingStep"
        )

        assertIs<ContextValidationResult.Valid>(result)
    }

    @Test
    fun `validateContextForResume returns Invalid for ShippingArrangementStep without all required data`() {
        val context = createBasicContext()
        context.putData(SagaContext.RESERVATION_ID, "res-123")
        // Missing authorizationId

        val result = retryContextBuilder.validateContextForResume(
            context = context,
            resumeStepName = "ShippingArrangementStep"
        )

        assertIs<ContextValidationResult.Invalid>(result)
        assert(result.missingData.any { it.contains("authorizationId") })
    }

    @Test
    fun `validateContextForResume returns Valid for ShippingArrangementStep with all required data`() {
        val context = createBasicContext()
        context.putData(SagaContext.RESERVATION_ID, "res-123")
        context.putData(SagaContext.AUTHORIZATION_ID, "auth-456")

        val result = retryContextBuilder.validateContextForResume(
            context = context,
            resumeStepName = "ShippingArrangementStep"
        )

        assertIs<ContextValidationResult.Valid>(result)
    }

    // Helper methods

    private fun validShippingAddress() = ShippingAddress(
        street = "123 Main St",
        city = "Anytown",
        state = "CA",
        postalCode = "90210",
        country = "US"
    )

    private fun createCompletedStepResult(
        stepName: String,
        stepOrder: Int,
        stepData: String?
    ) = createStepResult(stepName, stepOrder, StepStatus.COMPLETED, stepData)

    private fun createStepResult(
        stepName: String,
        stepOrder: Int,
        status: StepStatus,
        stepData: String?
    ) = SagaStepResult(
        id = UUID.randomUUID(),
        sagaExecutionId = originalExecutionId,
        stepName = stepName,
        stepOrder = stepOrder,
        status = status,
        stepData = stepData,
        startedAt = Instant.now(),
        completedAt = if (status == StepStatus.COMPLETED) Instant.now() else null
    )

    private fun createBasicContext() = SagaContext(
        order = order,
        sagaExecutionId = sagaExecutionId,
        customerId = customerId,
        paymentMethodId = "payment-123",
        shippingAddress = com.pintailconsultingllc.sagapattern.domain.ShippingAddress(
            street = "123 Main St",
            city = "Anytown",
            state = "CA",
            postalCode = "90210",
            country = "US"
        )
    )
}
