package com.pintailconsultingllc.sagapattern.saga.execution

import com.pintailconsultingllc.sagapattern.domain.SagaStepResult
import com.pintailconsultingllc.sagapattern.history.ErrorInfo
import com.pintailconsultingllc.sagapattern.metrics.SagaMetrics
import com.pintailconsultingllc.sagapattern.repository.SagaExecutionRepository
import com.pintailconsultingllc.sagapattern.repository.SagaStepResultRepository
import com.pintailconsultingllc.sagapattern.saga.SagaContext
import com.pintailconsultingllc.sagapattern.saga.SagaStep
import com.pintailconsultingllc.sagapattern.saga.event.SagaEventRecorder
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.Instant
import java.util.UUID

/**
 * Centralized step execution infrastructure for saga orchestrators.
 *
 * This class extracts common step execution logic shared between
 * [OrderSagaOrchestrator][com.pintailconsultingllc.sagapattern.saga.OrderSagaOrchestrator]
 * and [RetryOrchestrator][com.pintailconsultingllc.sagapattern.retry.RetryOrchestrator].
 *
 * Responsibilities:
 * - Creating and managing step result records
 * - Tracking step progress in the saga execution
 * - Recording step metrics and events
 * - Handling step success and failure outcomes
 *
 * Transaction boundaries: This class does NOT manage transactions as step execution
 * typically involves external HTTP calls that should not be wrapped in database transactions.
 */
@Component
class StepExecutor(
    private val sagaStepResultRepository: SagaStepResultRepository,
    private val sagaExecutionRepository: SagaExecutionRepository,
    private val sagaMetrics: SagaMetrics,
    private val sagaEventRecorder: SagaEventRecorder
) {
    private val logger = LoggerFactory.getLogger(StepExecutor::class.java)
    private val objectMapper = jacksonObjectMapper()

    /**
     * Execute a sequence of saga steps with common infrastructure.
     *
     * This method handles:
     * - Creating step result records
     * - Marking steps as in progress
     * - Updating saga execution progress
     * - Recording events (when enabled)
     * - Handling success/failure outcomes
     * - Skipping steps based on predicate (for retry scenarios)
     *
     * @param steps The ordered list of steps to execute
     * @param context The saga context with order and execution metadata
     * @param sagaExecutionId The ID of the saga execution record
     * @param skipPredicate Optional predicate to determine if a step should be skipped
     * @param recordEvents Whether to record events to the event recorder (default: true)
     * @return The outcome of step execution
     */
    suspend fun executeSteps(
        steps: List<SagaStep>,
        context: SagaContext,
        sagaExecutionId: UUID,
        skipPredicate: (SagaStep) -> Boolean = { false },
        recordEvents: Boolean = true
    ): StepExecutionOutcome {
        for ((index, step) in steps.withIndex()) {
            val stepName = step.getStepName()

            if (skipPredicate(step)) {
                recordSkippedStep(sagaExecutionId, step, index)
                continue
            }

            logger.info("Executing step '{}' ({}/{})", stepName, index + 1, steps.size)

            // Create step result record
            val stepResultRecord = createStepResultRecord(sagaExecutionId, step, index)

            // Mark step as in progress and update saga execution
            sagaStepResultRepository.markInProgress(stepResultRecord.id, Instant.now())
            sagaExecutionRepository.updateCurrentStep(sagaExecutionId, index + 1)

            if (recordEvents) {
                sagaEventRecorder.recordStepStarted(context.order.id, sagaExecutionId, stepName)
            }

            // Execute the step with timing
            val result = sagaMetrics.timeStepSuspend(stepName) {
                step.execute(context)
            }

            if (result.success) {
                handleStepSuccess(stepResultRecord.id, stepName, result.data, context, sagaExecutionId, recordEvents)
            } else {
                handleStepFailure(stepResultRecord.id, stepName, result, context, sagaExecutionId, recordEvents)
                return StepExecutionOutcome.Failed(
                    step = step,
                    stepIndex = index,
                    result = result
                )
            }
        }

        return StepExecutionOutcome.AllSucceeded
    }

    /**
     * Create a step result record in pending state.
     */
    private suspend fun createStepResultRecord(
        sagaExecutionId: UUID,
        step: SagaStep,
        index: Int
    ): SagaStepResult {
        val stepResult = SagaStepResult.pending(
            sagaExecutionId = sagaExecutionId,
            stepName = step.getStepName(),
            stepOrder = index + 1
        )
        return sagaStepResultRepository.save(stepResult)
    }

    /**
     * Record a skipped step (used during retry when previous result is still valid).
     */
    private suspend fun recordSkippedStep(
        sagaExecutionId: UUID,
        step: SagaStep,
        index: Int
    ) {
        val stepName = step.getStepName()
        logger.info("Skipping step '{}' - result still valid from previous execution", stepName)

        val skippedStepResult = SagaStepResult.skipped(
            sagaExecutionId = sagaExecutionId,
            stepName = stepName,
            stepOrder = index + 1
        )
        sagaStepResultRepository.save(skippedStepResult)
        sagaMetrics.stepCompleted(stepName)
    }

    /**
     * Handle successful step completion.
     */
    private suspend fun handleStepSuccess(
        stepResultId: UUID,
        stepName: String,
        data: Map<String, Any>,
        context: SagaContext,
        sagaExecutionId: UUID,
        recordEvents: Boolean
    ) {
        val dataJson = if (data.isNotEmpty()) objectMapper.writeValueAsString(data) else null
        sagaStepResultRepository.markCompleted(stepResultId, dataJson, Instant.now())
        sagaMetrics.stepCompleted(stepName)

        if (recordEvents) {
            sagaEventRecorder.recordStepCompleted(
                context.order.id,
                sagaExecutionId,
                stepName,
                data.ifEmpty { null }
            )
        }

        logger.info("Step '{}' completed successfully", stepName)
    }

    /**
     * Handle step failure.
     */
    private suspend fun handleStepFailure(
        stepResultId: UUID,
        stepName: String,
        result: com.pintailconsultingllc.sagapattern.saga.StepResult,
        context: SagaContext,
        sagaExecutionId: UUID,
        recordEvents: Boolean
    ) {
        sagaStepResultRepository.markFailed(
            stepResultId,
            result.errorMessage ?: "Unknown error",
            Instant.now()
        )

        if (recordEvents) {
            sagaEventRecorder.recordStepFailed(
                context.order.id,
                sagaExecutionId,
                stepName,
                ErrorInfo.fromStepFailure(result.errorCode, result.errorMessage)
            )
        }

        logger.error("Step '{}' failed: {}", stepName, result.errorMessage)
    }
}
