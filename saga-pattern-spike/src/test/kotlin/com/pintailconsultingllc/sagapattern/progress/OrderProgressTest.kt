package com.pintailconsultingllc.sagapattern.progress

import com.pintailconsultingllc.sagapattern.domain.SagaExecution
import com.pintailconsultingllc.sagapattern.domain.SagaStatus
import com.pintailconsultingllc.sagapattern.domain.SagaStepResult
import com.pintailconsultingllc.sagapattern.domain.StepStatus
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for OrderProgress.
 */
@Tag("unit")
class OrderProgressTest {

    private val testOrderId = UUID.randomUUID()
    private val testExecutionId = UUID.randomUUID()

    @Test
    fun `initial creates progress with all steps pending`() {
        val stepNames = listOf("Inventory", "Payment", "Shipping")

        val progress = OrderProgress.initial(testOrderId, stepNames)

        assertEquals(testOrderId, progress.orderId)
        assertEquals(ProgressStatus.QUEUED, progress.overallStatus)
        assertNull(progress.currentStep)
        assertEquals(3, progress.steps.size)
        assertTrue(progress.steps.all { it.status == StepStatus.PENDING })
        assertEquals(0, progress.completedStepCount)
        assertEquals(0, progress.progressPercentage)
        assertFalse(progress.isTerminal)
    }

    @Test
    fun `initial assigns correct step orders`() {
        val stepNames = listOf("Step A", "Step B", "Step C")

        val progress = OrderProgress.initial(testOrderId, stepNames)

        assertEquals(1, progress.steps[0].stepOrder)
        assertEquals(2, progress.steps[1].stepOrder)
        assertEquals(3, progress.steps[2].stepOrder)
    }

    @Test
    fun `fromSagaExecution maps IN_PROGRESS status correctly`() {
        val execution = SagaExecution(
            id = testExecutionId,
            orderId = testOrderId,
            status = SagaStatus.IN_PROGRESS,
            currentStep = 1
        )
        val stepResults = listOf(
            createStepResult("Inventory", 1, StepStatus.COMPLETED),
            createStepResult("Payment", 2, StepStatus.IN_PROGRESS)
        )

        val progress = OrderProgress.fromSagaExecution(execution, stepResults)

        assertEquals(ProgressStatus.IN_PROGRESS, progress.overallStatus)
        assertEquals("Payment", progress.currentStep)
        assertEquals(1, progress.completedStepCount)
        assertEquals(50, progress.progressPercentage)
        assertFalse(progress.isTerminal)
    }

    @Test
    fun `fromSagaExecution maps COMPLETED status correctly`() {
        val execution = SagaExecution(
            id = testExecutionId,
            orderId = testOrderId,
            status = SagaStatus.COMPLETED,
            completedAt = Instant.now()
        )
        val stepResults = listOf(
            createStepResult("Inventory", 1, StepStatus.COMPLETED),
            createStepResult("Payment", 2, StepStatus.COMPLETED),
            createStepResult("Shipping", 3, StepStatus.COMPLETED)
        )

        val progress = OrderProgress.fromSagaExecution(execution, stepResults)

        assertEquals(ProgressStatus.COMPLETED, progress.overallStatus)
        assertNull(progress.currentStep)
        assertEquals(3, progress.completedStepCount)
        assertEquals(100, progress.progressPercentage)
        assertTrue(progress.isTerminal)
    }

    @Test
    fun `fromSagaExecution maps FAILED status correctly`() {
        val execution = SagaExecution(
            id = testExecutionId,
            orderId = testOrderId,
            status = SagaStatus.FAILED,
            failedStep = 1,
            failureReason = "Card declined"
        )
        val stepResults = listOf(
            createStepResult("Inventory", 1, StepStatus.COMPLETED),
            createStepResult("Payment", 2, StepStatus.FAILED, "Card declined")
        )

        val progress = OrderProgress.fromSagaExecution(execution, stepResults)

        assertEquals(ProgressStatus.FAILED, progress.overallStatus)
        assertTrue(progress.isTerminal)
    }

    @Test
    fun `fromSagaExecution maps COMPENSATING status correctly`() {
        val execution = SagaExecution(
            id = testExecutionId,
            orderId = testOrderId,
            status = SagaStatus.COMPENSATING,
            compensationStartedAt = Instant.now()
        )
        val stepResults = listOf(
            createStepResult("Inventory", 1, StepStatus.COMPENSATING),
            createStepResult("Payment", 2, StepStatus.FAILED)
        )

        val progress = OrderProgress.fromSagaExecution(execution, stepResults)

        assertEquals(ProgressStatus.ROLLING_BACK, progress.overallStatus)
        assertEquals("Inventory", progress.currentStep)
        assertFalse(progress.isTerminal)
    }

    @Test
    fun `fromSagaExecution maps COMPENSATED status correctly`() {
        val execution = SagaExecution(
            id = testExecutionId,
            orderId = testOrderId,
            status = SagaStatus.COMPENSATED,
            compensationCompletedAt = Instant.now()
        )
        val stepResults = listOf(
            createStepResult("Inventory", 1, StepStatus.COMPENSATED),
            createStepResult("Payment", 2, StepStatus.FAILED)
        )

        val progress = OrderProgress.fromSagaExecution(execution, stepResults)

        assertEquals(ProgressStatus.ROLLED_BACK, progress.overallStatus)
        assertTrue(progress.isTerminal)
    }

    @Test
    fun `fromSagaExecution sorts steps by order`() {
        val execution = SagaExecution(
            id = testExecutionId,
            orderId = testOrderId,
            status = SagaStatus.IN_PROGRESS
        )
        // Deliberately out of order
        val stepResults = listOf(
            createStepResult("Shipping", 3, StepStatus.PENDING),
            createStepResult("Inventory", 1, StepStatus.COMPLETED),
            createStepResult("Payment", 2, StepStatus.IN_PROGRESS)
        )

        val progress = OrderProgress.fromSagaExecution(execution, stepResults)

        assertEquals("Inventory", progress.steps[0].stepName)
        assertEquals("Payment", progress.steps[1].stepName)
        assertEquals("Shipping", progress.steps[2].stepName)
    }

    @Test
    fun `lastUpdated uses most recent timestamp`() {
        val oldTime = Instant.parse("2024-01-01T10:00:00Z")
        val newTime = Instant.parse("2024-01-01T11:00:00Z")

        val execution = SagaExecution(
            id = testExecutionId,
            orderId = testOrderId,
            status = SagaStatus.IN_PROGRESS,
            startedAt = oldTime
        )
        val stepResults = listOf(
            SagaStepResult(
                sagaExecutionId = testExecutionId,
                stepName = "Inventory",
                stepOrder = 1,
                status = StepStatus.COMPLETED,
                startedAt = oldTime,
                completedAt = newTime
            )
        )

        val progress = OrderProgress.fromSagaExecution(execution, stepResults)

        assertEquals(newTime, progress.lastUpdated)
    }

    @Test
    fun `progressPercentage handles empty steps`() {
        val progress = OrderProgress.initial(testOrderId, emptyList())

        assertEquals(0, progress.progressPercentage)
    }

    private fun createStepResult(
        name: String,
        order: Int,
        status: StepStatus,
        errorMessage: String? = null
    ): SagaStepResult = SagaStepResult(
        sagaExecutionId = testExecutionId,
        stepName = name,
        stepOrder = order,
        status = status,
        errorMessage = errorMessage,
        startedAt = if (status != StepStatus.PENDING) Instant.now() else null,
        completedAt = if (status in listOf(StepStatus.COMPLETED, StepStatus.FAILED, StepStatus.COMPENSATED)) Instant.now() else null
    )
}
