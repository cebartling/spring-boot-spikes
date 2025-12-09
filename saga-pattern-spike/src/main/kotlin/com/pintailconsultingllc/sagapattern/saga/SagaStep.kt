package com.pintailconsultingllc.sagapattern.saga

/**
 * Interface for saga steps that can be executed and compensated.
 *
 * Each saga step represents a single unit of work that can be:
 * - Executed as part of the forward saga flow
 * - Compensated (rolled back) if a subsequent step fails
 */
interface SagaStep {
    /**
     * Execute the forward action for this step.
     *
     * @param context The saga context containing order information and shared data
     * @return The result of the step execution
     */
    suspend fun execute(context: SagaContext): StepResult

    /**
     * Compensate (rollback) this step's actions.
     *
     * Called when a subsequent step fails and we need to undo this step's work.
     *
     * @param context The saga context with data from the original execution
     * @return The result of the compensation
     */
    suspend fun compensate(context: SagaContext): CompensationResult

    /**
     * Get the human-readable name of this step.
     */
    fun getStepName(): String

    /**
     * Get the order in which this step should execute.
     * Lower numbers execute first.
     */
    fun getStepOrder(): Int
}
