package com.pintailconsultingllc.sagapattern.progress

import com.pintailconsultingllc.sagapattern.domain.SagaStepResult
import com.pintailconsultingllc.sagapattern.domain.StepStatus
import java.time.Instant

/**
 * Represents the progress of an individual saga step.
 *
 * This is a view model that aggregates step execution data
 * for presentation to clients.
 */
data class StepProgress(
    /**
     * Human-readable step name (e.g., "Inventory Reservation").
     */
    val stepName: String,

    /**
     * Position in the execution sequence (1-based).
     */
    val stepOrder: Int,

    /**
     * Current status of the step.
     */
    val status: StepStatus,

    /**
     * When the step started executing.
     */
    val startedAt: Instant? = null,

    /**
     * When the step finished (success, failure, or compensation).
     */
    val completedAt: Instant? = null,

    /**
     * Error message if the step failed.
     */
    val errorMessage: String? = null
) {
    companion object {
        /**
         * Create a StepProgress from a SagaStepResult domain entity.
         */
        fun fromStepResult(result: SagaStepResult): StepProgress = StepProgress(
            stepName = result.stepName,
            stepOrder = result.stepOrder,
            status = result.status,
            startedAt = result.startedAt,
            completedAt = result.completedAt,
            errorMessage = result.errorMessage
        )

        /**
         * Create a pending step progress for a step that hasn't started.
         */
        fun pending(stepName: String, stepOrder: Int): StepProgress = StepProgress(
            stepName = stepName,
            stepOrder = stepOrder,
            status = StepStatus.PENDING
        )

        /**
         * Create a skipped step progress for a step that was bypassed.
         */
        fun skipped(stepName: String, stepOrder: Int): StepProgress = StepProgress(
            stepName = stepName,
            stepOrder = stepOrder,
            status = StepStatus.PENDING // We'll use PENDING for skipped as well
        )
    }

    /**
     * Whether this step has completed (successfully or not).
     */
    val isComplete: Boolean
        get() = status == StepStatus.COMPLETED ||
                status == StepStatus.FAILED ||
                status == StepStatus.COMPENSATED

    /**
     * Whether this step is currently executing.
     */
    val isInProgress: Boolean
        get() = status == StepStatus.IN_PROGRESS || status == StepStatus.COMPENSATING

    /**
     * Calculate the duration of this step if it has completed.
     */
    val durationMillis: Long?
        get() = if (startedAt != null && completedAt != null) {
            completedAt.toEpochMilli() - startedAt.toEpochMilli()
        } else {
            null
        }
}
