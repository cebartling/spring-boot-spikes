package com.pintailconsultingllc.sagapattern.history

import java.time.Instant
import java.util.UUID

/**
 * Possible outcomes for a saga execution.
 */
enum class SagaOutcome {
    SUCCESS,
    FAILED,
    COMPENSATED,
    IN_PROGRESS
}

/**
 * Summary of a single saga execution attempt.
 */
data class SagaExecutionSummary(
    /** Saga execution identifier. */
    val executionId: UUID,

    /** Which attempt this is (1 = original, 2+ = retry). */
    val attemptNumber: Int,

    /** When the execution started. */
    val startedAt: Instant,

    /** When the execution completed (null if in progress). */
    val completedAt: Instant? = null,

    /** Execution outcome. */
    val outcome: SagaOutcome,

    /** Name of the step that failed (if applicable). */
    val failedStep: String? = null,

    /** Number of steps completed successfully. */
    val stepsCompleted: Int = 0,

    /** Whether this was a retry attempt. */
    val isRetry: Boolean = false
) {
    companion object {
        /**
         * Create a summary for a successful execution.
         */
        fun success(
            executionId: UUID,
            attemptNumber: Int,
            startedAt: Instant,
            completedAt: Instant,
            stepsCompleted: Int,
            isRetry: Boolean = false
        ): SagaExecutionSummary = SagaExecutionSummary(
            executionId = executionId,
            attemptNumber = attemptNumber,
            startedAt = startedAt,
            completedAt = completedAt,
            outcome = SagaOutcome.SUCCESS,
            stepsCompleted = stepsCompleted,
            isRetry = isRetry
        )

        /**
         * Create a summary for a failed execution.
         */
        fun failed(
            executionId: UUID,
            attemptNumber: Int,
            startedAt: Instant,
            completedAt: Instant,
            failedStep: String,
            stepsCompleted: Int,
            isRetry: Boolean = false
        ): SagaExecutionSummary = SagaExecutionSummary(
            executionId = executionId,
            attemptNumber = attemptNumber,
            startedAt = startedAt,
            completedAt = completedAt,
            outcome = SagaOutcome.FAILED,
            failedStep = failedStep,
            stepsCompleted = stepsCompleted,
            isRetry = isRetry
        )

        /**
         * Create a summary for a compensated execution.
         */
        fun compensated(
            executionId: UUID,
            attemptNumber: Int,
            startedAt: Instant,
            completedAt: Instant,
            failedStep: String,
            stepsCompleted: Int,
            isRetry: Boolean = false
        ): SagaExecutionSummary = SagaExecutionSummary(
            executionId = executionId,
            attemptNumber = attemptNumber,
            startedAt = startedAt,
            completedAt = completedAt,
            outcome = SagaOutcome.COMPENSATED,
            failedStep = failedStep,
            stepsCompleted = stepsCompleted,
            isRetry = isRetry
        )

        /**
         * Create a summary for an in-progress execution.
         */
        fun inProgress(
            executionId: UUID,
            attemptNumber: Int,
            startedAt: Instant,
            stepsCompleted: Int,
            isRetry: Boolean = false
        ): SagaExecutionSummary = SagaExecutionSummary(
            executionId = executionId,
            attemptNumber = attemptNumber,
            startedAt = startedAt,
            outcome = SagaOutcome.IN_PROGRESS,
            stepsCompleted = stepsCompleted,
            isRetry = isRetry
        )
    }

    /**
     * Duration of the execution in milliseconds.
     */
    val durationMillis: Long?
        get() = completedAt?.let { it.toEpochMilli() - startedAt.toEpochMilli() }
}
