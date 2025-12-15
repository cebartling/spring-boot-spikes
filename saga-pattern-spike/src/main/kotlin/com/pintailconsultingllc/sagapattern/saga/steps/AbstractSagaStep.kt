package com.pintailconsultingllc.sagapattern.saga.steps

import com.pintailconsultingllc.sagapattern.saga.CompensationResult
import com.pintailconsultingllc.sagapattern.saga.SagaContext
import com.pintailconsultingllc.sagapattern.saga.SagaStep
import com.pintailconsultingllc.sagapattern.saga.StepResult
import com.pintailconsultingllc.sagapattern.service.SagaServiceException
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Abstract base class for saga steps using the template method pattern.
 *
 * Provides common implementation for:
 * - Logging at step start, success, and failure
 * - Exception handling with proper error categorization
 * - Compensation flow with null-safety checks
 *
 * Subclasses implement the abstract methods to provide step-specific behavior:
 * - [doExecute]: Performs the actual step execution
 * - [doCompensate]: Performs the compensation action
 * - [hasDataToCompensate]: Checks if compensation is needed
 * - [getNoCompensationMessage]: Message when nothing to compensate
 *
 * Optional hooks:
 * - [validatePreConditions]: Pre-validation before execution
 *
 * @param stepName Human-readable name of the step
 * @param stepOrder Execution order (lower numbers execute first)
 */
abstract class AbstractSagaStep(
    private val stepName: String,
    private val stepOrder: Int
) : SagaStep {

    protected val logger: Logger = LoggerFactory.getLogger(this::class.java)

    // ==================== SagaStep Interface Implementation ====================

    override fun getStepName(): String = stepName

    override fun getStepOrder(): Int = stepOrder

    /**
     * Template method for step execution.
     *
     * Handles logging, pre-validation, error handling, and delegates
     * the actual work to [doExecute].
     */
    override suspend fun execute(context: SagaContext): StepResult {
        logger.info("Executing $stepName for order ${context.order.id}")

        // Optional pre-validation hook
        val validationResult = validatePreConditions(context)
        if (validationResult != null) {
            return validationResult
        }

        return try {
            val result = doExecute(context)
            logger.info("$stepName completed successfully")
            result
        } catch (e: SagaServiceException) {
            logger.error("$stepName failed: ${e.message}")
            StepResult.failure(
                errorMessage = e.message ?: "$stepName failed",
                errorCode = e.errorCode
            )
        } catch (e: Exception) {
            logger.error("Unexpected error during $stepName", e)
            StepResult.failure(
                errorMessage = "Unexpected error: ${e.message}",
                errorCode = "UNEXPECTED_ERROR"
            )
        }
    }

    /**
     * Template method for step compensation.
     *
     * Handles logging, null-safety checks, error handling, and delegates
     * the actual work to [doCompensate].
     */
    override suspend fun compensate(context: SagaContext): CompensationResult {
        if (!hasDataToCompensate(context)) {
            logger.warn("No data found for $stepName compensation")
            return CompensationResult.success(getNoCompensationMessage())
        }

        logger.info("Compensating $stepName")

        return try {
            val result = doCompensate(context)
            logger.info("Successfully compensated $stepName")
            result
        } catch (e: Exception) {
            logger.error("Failed to compensate $stepName", e)
            CompensationResult.failure("Failed to compensate $stepName: ${e.message}")
        }
    }

    // ==================== Abstract Methods (Must Implement) ====================

    /**
     * Perform the actual step execution.
     *
     * Called by [execute] after pre-validation passes.
     * Should call the external service and store relevant data in the context.
     *
     * @param context The saga context
     * @return StepResult indicating success with data or failure
     * @throws SagaServiceException for service-specific errors (handled by template)
     */
    protected abstract suspend fun doExecute(context: SagaContext): StepResult

    /**
     * Perform the actual compensation action.
     *
     * Called by [compensate] after verifying data exists.
     * Should call the external service to undo the step's effects.
     *
     * @param context The saga context with stored execution data
     * @return CompensationResult indicating success or failure
     */
    protected abstract suspend fun doCompensate(context: SagaContext): CompensationResult

    /**
     * Check if this step has data to compensate.
     *
     * Typically checks if the context has the ID stored during execution.
     *
     * @param context The saga context
     * @return true if compensation should proceed, false otherwise
     */
    protected abstract fun hasDataToCompensate(context: SagaContext): Boolean

    /**
     * Get the message to return when there's nothing to compensate.
     *
     * @return Human-readable message explaining nothing to compensate
     */
    protected abstract fun getNoCompensationMessage(): String

    // ==================== Optional Hooks (Can Override) ====================

    /**
     * Validate pre-conditions before execution.
     *
     * Override this method to add validation logic that should run
     * before the step attempts to execute.
     *
     * @param context The saga context
     * @return null if validation passes, or a StepResult.failure if validation fails
     */
    protected open fun validatePreConditions(context: SagaContext): StepResult? = null
}
