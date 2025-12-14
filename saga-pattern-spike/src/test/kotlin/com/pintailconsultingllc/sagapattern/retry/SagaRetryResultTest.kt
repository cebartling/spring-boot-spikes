package com.pintailconsultingllc.sagapattern.retry

import com.pintailconsultingllc.sagapattern.domain.Order
import com.pintailconsultingllc.sagapattern.domain.OrderStatus
import com.pintailconsultingllc.sagapattern.saga.SagaResult
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for SagaRetryResult sealed class.
 */
@Tag("unit")
class SagaRetryResultTest {

    private val testOrderId = UUID.randomUUID()
    private val testExecutionId = UUID.randomUUID()
    private val testRetryAttemptId = UUID.randomUUID()

    @Test
    fun `Success contains result details`() {
        val sagaResult = SagaResult.Success(
            order = Order(
                id = testOrderId,
                customerId = UUID.randomUUID(),
                totalAmountInCents = 5000,
                status = OrderStatus.COMPLETED
            ),
            confirmationNumber = "CONF-12345",
            totalChargedInCents = 5000,
            estimatedDelivery = LocalDate.now().plusDays(5),
            trackingNumber = "TRK123"
        )

        val result = SagaRetryResult.Success(
            orderId = testOrderId,
            executionId = testExecutionId,
            retryAttemptId = testRetryAttemptId,
            skippedSteps = listOf("Inventory Reservation"),
            result = sagaResult
        )

        assertEquals(testOrderId, result.orderId)
        assertEquals(testExecutionId, result.executionId)
        assertEquals(testRetryAttemptId, result.retryAttemptId)
        assertEquals(1, result.skippedSteps.size)
        assertEquals("CONF-12345", result.result.confirmationNumber)
    }

    @Test
    fun `Failed contains failure details`() {
        val result = SagaRetryResult.Failed(
            orderId = testOrderId,
            executionId = testExecutionId,
            retryAttemptId = testRetryAttemptId,
            failedStep = "Payment Processing",
            failureReason = "Card declined",
            skippedSteps = listOf("Inventory Reservation")
        )

        assertEquals(testOrderId, result.orderId)
        assertEquals("Payment Processing", result.failedStep)
        assertEquals("Card declined", result.failureReason)
        assertEquals(1, result.skippedSteps.size)
    }

    @Test
    fun `Compensated contains compensation details`() {
        val result = SagaRetryResult.Compensated(
            orderId = testOrderId,
            executionId = testExecutionId,
            retryAttemptId = testRetryAttemptId,
            failedStep = "Shipping Arrangement",
            failureReason = "Invalid address",
            compensatedSteps = listOf("Payment Processing"),
            skippedSteps = listOf("Inventory Reservation")
        )

        assertEquals(testOrderId, result.orderId)
        assertEquals("Shipping Arrangement", result.failedStep)
        assertEquals("Invalid address", result.failureReason)
        assertEquals(1, result.compensatedSteps.size)
        assertEquals("Payment Processing", result.compensatedSteps[0])
        assertEquals(1, result.skippedSteps.size)
    }

    @Test
    fun `NotEligible contains eligibility blockers`() {
        val blockers = listOf(
            RetryBlocker(
                type = BlockerType.MAX_RETRIES_EXCEEDED,
                message = "Maximum retry attempts exceeded",
                resolvable = false
            )
        )

        val result = SagaRetryResult.NotEligible(
            orderId = testOrderId,
            reason = "Maximum retry attempts exceeded",
            blockers = blockers
        )

        assertEquals(testOrderId, result.orderId)
        assertEquals("Maximum retry attempts exceeded", result.reason)
        assertEquals(1, result.blockers.size)
        assertEquals(BlockerType.MAX_RETRIES_EXCEEDED, result.blockers[0].type)
    }

    @Test
    fun `all result types share orderId property`() {
        val success: SagaRetryResult = SagaRetryResult.Success(
            orderId = testOrderId,
            executionId = testExecutionId,
            retryAttemptId = testRetryAttemptId,
            skippedSteps = emptyList(),
            result = SagaResult.Success(
                order = Order(
                    id = testOrderId,
                    customerId = UUID.randomUUID(),
                    totalAmountInCents = 5000,
                    status = OrderStatus.COMPLETED
                ),
                confirmationNumber = "CONF-12345",
                totalChargedInCents = 5000,
                estimatedDelivery = LocalDate.now().plusDays(5)
            )
        )

        val failed: SagaRetryResult = SagaRetryResult.Failed(
            orderId = testOrderId,
            executionId = testExecutionId,
            retryAttemptId = testRetryAttemptId,
            failedStep = "Test",
            failureReason = "Error",
            skippedSteps = emptyList()
        )

        assertEquals(testOrderId, success.orderId)
        assertEquals(testOrderId, failed.orderId)
    }
}
