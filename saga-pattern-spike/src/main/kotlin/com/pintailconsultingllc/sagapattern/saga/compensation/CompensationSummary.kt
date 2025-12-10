package com.pintailconsultingllc.sagapattern.saga.compensation

import com.pintailconsultingllc.sagapattern.saga.CompensationResult

/**
 * Summary of a compensation (rollback) execution.
 *
 * Aggregates the results of compensating all completed steps after a saga failure.
 *
 * @property failedStep The name of the step that originally failed
 * @property failureReason The reason for the original failure
 * @property compensatedSteps List of steps that were successfully compensated
 * @property failedCompensations List of steps that failed compensation (if any)
 * @property stepResults Individual compensation results for each step
 */
data class CompensationSummary(
    val failedStep: String,
    val failureReason: String,
    val compensatedSteps: List<String>,
    val failedCompensations: List<String>,
    val stepResults: Map<String, CompensationResult>
) {
    /**
     * Whether all compensations completed successfully.
     */
    val allCompensationsSuccessful: Boolean
        get() = failedCompensations.isEmpty()

    /**
     * Whether at least one compensation succeeded.
     */
    val hasCompensatedSteps: Boolean
        get() = compensatedSteps.isNotEmpty()

    /**
     * Total number of steps that required compensation.
     */
    val totalStepsToCompensate: Int
        get() = compensatedSteps.size + failedCompensations.size

    companion object {
        /**
         * Create a summary when no compensation was needed (first step failed).
         */
        fun noCompensationNeeded(failedStep: String, failureReason: String): CompensationSummary =
            CompensationSummary(
                failedStep = failedStep,
                failureReason = failureReason,
                compensatedSteps = emptyList(),
                failedCompensations = emptyList(),
                stepResults = emptyMap()
            )

        /**
         * Build a summary from step results.
         */
        fun fromResults(
            failedStep: String,
            failureReason: String,
            stepResults: Map<String, CompensationResult>
        ): CompensationSummary {
            val compensatedSteps = stepResults.filter { it.value.success }.keys.toList()
            val failedCompensations = stepResults.filter { !it.value.success }.keys.toList()

            return CompensationSummary(
                failedStep = failedStep,
                failureReason = failureReason,
                compensatedSteps = compensatedSteps,
                failedCompensations = failedCompensations,
                stepResults = stepResults
            )
        }
    }
}
