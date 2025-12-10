package com.pintailconsultingllc.sagapattern.progress

import com.pintailconsultingllc.sagapattern.domain.SagaStepResult
import com.pintailconsultingllc.sagapattern.domain.StepStatus
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for StepProgress.
 */
class StepProgressTest {

    private val testExecutionId = UUID.randomUUID()

    @Test
    fun `pending creates step with PENDING status`() {
        val progress = StepProgress.pending("Inventory", 1)

        assertEquals("Inventory", progress.stepName)
        assertEquals(1, progress.stepOrder)
        assertEquals(StepStatus.PENDING, progress.status)
        assertNull(progress.startedAt)
        assertNull(progress.completedAt)
        assertNull(progress.errorMessage)
        assertFalse(progress.isComplete)
        assertFalse(progress.isInProgress)
    }

    @Test
    fun `skipped creates step with PENDING status`() {
        val progress = StepProgress.skipped("Shipping", 3)

        assertEquals("Shipping", progress.stepName)
        assertEquals(3, progress.stepOrder)
        assertEquals(StepStatus.PENDING, progress.status)
    }

    @Test
    fun `fromStepResult maps all fields correctly`() {
        val startTime = Instant.now().minusSeconds(5)
        val endTime = Instant.now()

        val result = SagaStepResult(
            sagaExecutionId = testExecutionId,
            stepName = "Payment",
            stepOrder = 2,
            status = StepStatus.COMPLETED,
            stepData = """{"transactionId": "TXN123"}""",
            startedAt = startTime,
            completedAt = endTime
        )

        val progress = StepProgress.fromStepResult(result)

        assertEquals("Payment", progress.stepName)
        assertEquals(2, progress.stepOrder)
        assertEquals(StepStatus.COMPLETED, progress.status)
        assertEquals(startTime, progress.startedAt)
        assertEquals(endTime, progress.completedAt)
        assertNull(progress.errorMessage)
    }

    @Test
    fun `fromStepResult includes error message for failed steps`() {
        val result = SagaStepResult(
            sagaExecutionId = testExecutionId,
            stepName = "Payment",
            stepOrder = 2,
            status = StepStatus.FAILED,
            errorMessage = "Card declined"
        )

        val progress = StepProgress.fromStepResult(result)

        assertEquals(StepStatus.FAILED, progress.status)
        assertEquals("Card declined", progress.errorMessage)
    }

    @Test
    fun `isComplete returns true for COMPLETED status`() {
        val progress = StepProgress(
            stepName = "Test",
            stepOrder = 1,
            status = StepStatus.COMPLETED
        )

        assertTrue(progress.isComplete)
        assertFalse(progress.isInProgress)
    }

    @Test
    fun `isComplete returns true for FAILED status`() {
        val progress = StepProgress(
            stepName = "Test",
            stepOrder = 1,
            status = StepStatus.FAILED
        )

        assertTrue(progress.isComplete)
        assertFalse(progress.isInProgress)
    }

    @Test
    fun `isComplete returns true for COMPENSATED status`() {
        val progress = StepProgress(
            stepName = "Test",
            stepOrder = 1,
            status = StepStatus.COMPENSATED
        )

        assertTrue(progress.isComplete)
        assertFalse(progress.isInProgress)
    }

    @Test
    fun `isInProgress returns true for IN_PROGRESS status`() {
        val progress = StepProgress(
            stepName = "Test",
            stepOrder = 1,
            status = StepStatus.IN_PROGRESS
        )

        assertTrue(progress.isInProgress)
        assertFalse(progress.isComplete)
    }

    @Test
    fun `isInProgress returns true for COMPENSATING status`() {
        val progress = StepProgress(
            stepName = "Test",
            stepOrder = 1,
            status = StepStatus.COMPENSATING
        )

        assertTrue(progress.isInProgress)
        assertFalse(progress.isComplete)
    }

    @Test
    fun `durationMillis returns null when no timestamps`() {
        val progress = StepProgress.pending("Test", 1)

        assertNull(progress.durationMillis)
    }

    @Test
    fun `durationMillis returns null when only startedAt is set`() {
        val progress = StepProgress(
            stepName = "Test",
            stepOrder = 1,
            status = StepStatus.IN_PROGRESS,
            startedAt = Instant.now()
        )

        assertNull(progress.durationMillis)
    }

    @Test
    fun `durationMillis calculates correct duration`() {
        val startTime = Instant.parse("2024-01-01T10:00:00Z")
        val endTime = Instant.parse("2024-01-01T10:00:05Z")

        val progress = StepProgress(
            stepName = "Test",
            stepOrder = 1,
            status = StepStatus.COMPLETED,
            startedAt = startTime,
            completedAt = endTime
        )

        assertEquals(5000, progress.durationMillis)
    }

    @Test
    fun `pending status is not complete and not in progress`() {
        val progress = StepProgress.pending("Test", 1)

        assertFalse(progress.isComplete)
        assertFalse(progress.isInProgress)
    }
}
