package com.pintailconsultingllc.sagapattern.progress

import com.pintailconsultingllc.sagapattern.domain.SagaExecution
import com.pintailconsultingllc.sagapattern.domain.SagaStatus
import com.pintailconsultingllc.sagapattern.domain.SagaStepResult
import com.pintailconsultingllc.sagapattern.domain.StepStatus
import java.time.Instant
import java.util.UUID

/**
 * Represents the overall progress of an order's saga execution.
 *
 * This is a view model that aggregates saga execution and step result data
 * for presentation to clients.
 */
data class OrderProgress(
    /**
     * The order ID being tracked.
     */
    val orderId: UUID,

    /**
     * Summary status of the saga execution.
     */
    val overallStatus: ProgressStatus,

    /**
     * Name of the step currently executing (null if not in progress).
     */
    val currentStep: String?,

    /**
     * Individual step progress records.
     */
    val steps: List<StepProgress>,

    /**
     * When the status was last updated.
     */
    val lastUpdated: Instant,

    /**
     * Predicted completion time (optional).
     */
    val estimatedCompletion: Instant? = null
) {
    companion object {
        /**
         * Build OrderProgress from saga execution and step results.
         *
         * @param execution The saga execution record
         * @param stepResults The step result records
         * @return Aggregated order progress view
         */
        fun fromSagaExecution(
            execution: SagaExecution,
            stepResults: List<SagaStepResult>
        ): OrderProgress {
            val steps = stepResults
                .sortedBy { it.stepOrder }
                .map { StepProgress.fromStepResult(it) }

            val currentStep = steps.find { it.isInProgress }?.stepName

            val overallStatus = mapSagaStatusToProgressStatus(execution.status)

            val lastUpdated = determineLastUpdated(execution, stepResults)

            return OrderProgress(
                orderId = execution.orderId,
                overallStatus = overallStatus,
                currentStep = currentStep,
                steps = steps,
                lastUpdated = lastUpdated
            )
        }

        /**
         * Create an initial progress for a newly created order (before saga starts).
         */
        fun initial(orderId: UUID, stepNames: List<String>): OrderProgress {
            val steps = stepNames.mapIndexed { index, name ->
                StepProgress.pending(name, index + 1)
            }

            return OrderProgress(
                orderId = orderId,
                overallStatus = ProgressStatus.QUEUED,
                currentStep = null,
                steps = steps,
                lastUpdated = Instant.now()
            )
        }

        /**
         * Map SagaStatus to ProgressStatus for presentation.
         */
        private fun mapSagaStatusToProgressStatus(sagaStatus: SagaStatus): ProgressStatus =
            when (sagaStatus) {
                SagaStatus.PENDING -> ProgressStatus.QUEUED
                SagaStatus.IN_PROGRESS -> ProgressStatus.IN_PROGRESS
                SagaStatus.COMPLETED -> ProgressStatus.COMPLETED
                SagaStatus.FAILED -> ProgressStatus.FAILED
                SagaStatus.COMPENSATING -> ProgressStatus.ROLLING_BACK
                SagaStatus.COMPENSATED -> ProgressStatus.ROLLED_BACK
            }

        /**
         * Determine the most recent timestamp from execution or steps.
         */
        private fun determineLastUpdated(
            execution: SagaExecution,
            stepResults: List<SagaStepResult>
        ): Instant {
            val stepTimestamps = stepResults.mapNotNull { it.completedAt ?: it.startedAt }
            val executionTimestamps = listOfNotNull(
                execution.startedAt,
                execution.completedAt,
                execution.compensationStartedAt,
                execution.compensationCompletedAt
            )

            return (stepTimestamps + executionTimestamps).maxOrNull() ?: execution.startedAt
        }
    }

    /**
     * Number of steps that have completed successfully.
     */
    val completedStepCount: Int
        get() = steps.count { it.status == StepStatus.COMPLETED }

    /**
     * Total number of steps in the saga.
     */
    val totalStepCount: Int
        get() = steps.size

    /**
     * Progress percentage (0-100).
     */
    val progressPercentage: Int
        get() = if (totalStepCount == 0) 0 else (completedStepCount * 100) / totalStepCount

    /**
     * Whether the saga has finished (success, failure, or rolled back).
     */
    val isTerminal: Boolean
        get() = overallStatus == ProgressStatus.COMPLETED ||
                overallStatus == ProgressStatus.FAILED ||
                overallStatus == ProgressStatus.ROLLED_BACK
}
