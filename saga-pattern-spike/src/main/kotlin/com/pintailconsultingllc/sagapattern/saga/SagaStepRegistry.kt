package com.pintailconsultingllc.sagapattern.saga

import org.springframework.stereotype.Component

/**
 * Registry that provides access to saga step configuration.
 *
 * This component holds the ordered list of saga steps and provides
 * methods to query step information without hardcoding step names
 * throughout the application.
 */
@Component
class SagaStepRegistry(
    private val steps: List<SagaStep>
) {
    // Sort steps by their order on initialization
    private val orderedSteps: List<SagaStep> = steps.sortedBy { it.getStepOrder() }
    
    // Cache step lookup by name for efficient O(1) access
    private val stepsByName: Map<String, SagaStep> = orderedSteps.associateBy { it.getStepName() }

    /**
     * Get the ordered list of step names.
     *
     * @return List of step names in execution order
     */
    fun getStepNames(): List<String> {
        return orderedSteps.map { it.getStepName() }
    }

    /**
     * Get the number of steps in the saga.
     *
     * @return Total number of steps
     */
    fun getStepCount(): Int {
        return orderedSteps.size
    }

    /**
     * Get a step by its name.
     *
     * @param stepName The name of the step
     * @return The step if found, null otherwise
     */
    fun getStepByName(stepName: String): SagaStep? {
        return stepsByName[stepName]
    }

    /**
     * Get all steps in execution order.
     *
     * @return List of steps ordered by execution order
     */
    fun getOrderedSteps(): List<SagaStep> {
        return orderedSteps
    }
}
