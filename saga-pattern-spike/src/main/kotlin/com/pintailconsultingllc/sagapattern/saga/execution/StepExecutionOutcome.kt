package com.pintailconsultingllc.sagapattern.saga.execution

import com.pintailconsultingllc.sagapattern.saga.SagaStep
import com.pintailconsultingllc.sagapattern.saga.StepResult

/**
 * Represents the outcome of executing a sequence of saga steps.
 *
 * This sealed class provides a type-safe way to represent all possible
 * outcomes of step execution, enabling exhaustive pattern matching.
 */
sealed class StepExecutionOutcome {

    /**
     * All steps executed successfully.
     */
    data object AllSucceeded : StepExecutionOutcome()

    /**
     * A step failed during execution.
     *
     * @property step The step that failed
     * @property stepIndex The zero-based index of the failed step
     * @property result The failure result containing error details
     */
    data class Failed(
        val step: SagaStep,
        val stepIndex: Int,
        val result: StepResult
    ) : StepExecutionOutcome()
}
