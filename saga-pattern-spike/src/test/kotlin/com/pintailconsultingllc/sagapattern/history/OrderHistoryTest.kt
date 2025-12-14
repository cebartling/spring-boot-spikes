package com.pintailconsultingllc.sagapattern.history

import com.pintailconsultingllc.sagapattern.domain.OrderStatus
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for OrderHistory.
 */
@Tag("unit")
class OrderHistoryTest {

    private val testOrderId = UUID.randomUUID()
    private val testTimestamp = Instant.parse("2024-01-15T10:00:00Z")

    @Test
    fun `generateOrderNumber creates correct format`() {
        val orderNumber = OrderHistory.generateOrderNumber(testOrderId, testTimestamp)

        assertTrue(orderNumber.startsWith("ORD-2024-"))
        assertTrue(orderNumber.length == 17) // ORD-YYYY-XXXXXXXX
    }

    @Test
    fun `create builds order history correctly`() {
        val timeline = OrderTimeline.empty(
            orderId = testOrderId,
            orderNumber = "ORD-2024-12345",
            createdAt = testTimestamp,
            currentStatus = OrderStatus.COMPLETED
        )

        val history = OrderHistory.create(
            orderId = testOrderId,
            createdAt = testTimestamp,
            finalStatus = OrderStatus.COMPLETED,
            completedAt = testTimestamp.plusSeconds(60),
            timeline = timeline,
            executions = emptyList()
        )

        assertEquals(testOrderId, history.orderId)
        assertEquals(testTimestamp, history.createdAt)
        assertEquals(OrderStatus.COMPLETED, history.finalStatus)
        assertEquals(testTimestamp.plusSeconds(60), history.completedAt)
    }

    @Test
    fun `totalAttempts returns execution count`() {
        val executions = listOf(
            SagaExecutionSummary.success(
                executionId = UUID.randomUUID(),
                attemptNumber = 1,
                startedAt = testTimestamp,
                completedAt = testTimestamp.plusSeconds(30),
                stepsCompleted = 3
            ),
            SagaExecutionSummary.success(
                executionId = UUID.randomUUID(),
                attemptNumber = 2,
                startedAt = testTimestamp.plusSeconds(60),
                completedAt = testTimestamp.plusSeconds(90),
                stepsCompleted = 3,
                isRetry = true
            )
        )

        val history = createHistoryWithExecutions(executions)

        assertEquals(2, history.totalAttempts)
    }

    @Test
    fun `retryCount returns number of retry attempts`() {
        val executions = listOf(
            SagaExecutionSummary.compensated(
                executionId = UUID.randomUUID(),
                attemptNumber = 1,
                startedAt = testTimestamp,
                completedAt = testTimestamp.plusSeconds(30),
                failedStep = "Payment",
                stepsCompleted = 1
            ),
            SagaExecutionSummary.success(
                executionId = UUID.randomUUID(),
                attemptNumber = 2,
                startedAt = testTimestamp.plusSeconds(60),
                completedAt = testTimestamp.plusSeconds(90),
                stepsCompleted = 3,
                isRetry = true
            )
        )

        val history = createHistoryWithExecutions(executions)

        assertEquals(1, history.retryCount)
    }

    @Test
    fun `wasSuccessful returns true for COMPLETED status`() {
        val history = createHistoryWithStatus(OrderStatus.COMPLETED)
        assertTrue(history.wasSuccessful)
    }

    @Test
    fun `wasSuccessful returns false for FAILED status`() {
        val history = createHistoryWithStatus(OrderStatus.FAILED)
        assertFalse(history.wasSuccessful)
    }

    @Test
    fun `hadCompensations returns true when executions have COMPENSATED outcome`() {
        val executions = listOf(
            SagaExecutionSummary.compensated(
                executionId = UUID.randomUUID(),
                attemptNumber = 1,
                startedAt = testTimestamp,
                completedAt = testTimestamp.plusSeconds(30),
                failedStep = "Payment",
                stepsCompleted = 1
            )
        )

        val history = createHistoryWithExecutions(executions)

        assertTrue(history.hadCompensations)
    }

    @Test
    fun `hadCompensations returns false when no compensated executions`() {
        val executions = listOf(
            SagaExecutionSummary.success(
                executionId = UUID.randomUUID(),
                attemptNumber = 1,
                startedAt = testTimestamp,
                completedAt = testTimestamp.plusSeconds(30),
                stepsCompleted = 3
            )
        )

        val history = createHistoryWithExecutions(executions)

        assertFalse(history.hadCompensations)
    }

    private fun createHistoryWithStatus(status: OrderStatus): OrderHistory {
        val timeline = OrderTimeline.empty(
            orderId = testOrderId,
            orderNumber = "ORD-2024-12345",
            createdAt = testTimestamp,
            currentStatus = status
        )
        return OrderHistory.create(
            orderId = testOrderId,
            createdAt = testTimestamp,
            finalStatus = status,
            timeline = timeline
        )
    }

    private fun createHistoryWithExecutions(executions: List<SagaExecutionSummary>): OrderHistory {
        val timeline = OrderTimeline.empty(
            orderId = testOrderId,
            orderNumber = "ORD-2024-12345",
            createdAt = testTimestamp,
            currentStatus = OrderStatus.COMPLETED
        )
        return OrderHistory.create(
            orderId = testOrderId,
            createdAt = testTimestamp,
            finalStatus = OrderStatus.COMPLETED,
            timeline = timeline,
            executions = executions
        )
    }
}
