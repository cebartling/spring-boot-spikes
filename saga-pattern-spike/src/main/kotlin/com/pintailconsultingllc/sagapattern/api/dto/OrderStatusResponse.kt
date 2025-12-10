package com.pintailconsultingllc.sagapattern.api.dto

import com.pintailconsultingllc.sagapattern.domain.StepStatus
import com.pintailconsultingllc.sagapattern.progress.OrderProgress
import com.pintailconsultingllc.sagapattern.progress.ProgressStatus
import com.pintailconsultingllc.sagapattern.progress.StepProgress
import java.time.Instant
import java.util.UUID

/**
 * Response DTO for order status endpoint.
 *
 * Provides real-time visibility into the saga execution progress.
 */
data class OrderStatusResponse(
    /**
     * The order ID being tracked.
     */
    val orderId: UUID,

    /**
     * Overall status of the order processing.
     */
    val overallStatus: ProgressStatus,

    /**
     * Name of the step currently in progress (null if not actively processing).
     */
    val currentStep: String?,

    /**
     * When the status was last updated.
     */
    val lastUpdated: Instant,

    /**
     * Estimated completion time (optional).
     */
    val estimatedCompletion: Instant?,

    /**
     * Progress of individual steps.
     */
    val steps: List<StepStatusResponse>
) {
    companion object {
        /**
         * Create response from OrderProgress view model.
         */
        fun fromOrderProgress(progress: OrderProgress): OrderStatusResponse =
            OrderStatusResponse(
                orderId = progress.orderId,
                overallStatus = progress.overallStatus,
                currentStep = progress.currentStep,
                lastUpdated = progress.lastUpdated,
                estimatedCompletion = progress.estimatedCompletion,
                steps = progress.steps.map { StepStatusResponse.fromStepProgress(it) }
            )
    }
}

/**
 * Response DTO for individual step status.
 */
data class StepStatusResponse(
    /**
     * Human-readable step name.
     */
    val name: String,

    /**
     * Position in the execution sequence (1-based).
     */
    val order: Int,

    /**
     * Current status of the step.
     */
    val status: StepStatus,

    /**
     * When the step started executing.
     */
    val startedAt: Instant?,

    /**
     * When the step completed (success, failure, or compensation).
     */
    val completedAt: Instant?,

    /**
     * Error message if the step failed.
     */
    val errorMessage: String?
) {
    companion object {
        /**
         * Create response from StepProgress view model.
         */
        fun fromStepProgress(progress: StepProgress): StepStatusResponse =
            StepStatusResponse(
                name = progress.stepName,
                order = progress.stepOrder,
                status = progress.status,
                startedAt = progress.startedAt,
                completedAt = progress.completedAt,
                errorMessage = progress.errorMessage
            )
    }
}
