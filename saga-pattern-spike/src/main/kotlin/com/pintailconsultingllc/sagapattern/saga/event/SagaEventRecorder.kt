package com.pintailconsultingllc.sagapattern.saga.event

import com.pintailconsultingllc.sagapattern.history.ErrorInfo
import com.pintailconsultingllc.sagapattern.history.OrderEventService
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Centralized event recording for saga execution lifecycle.
 *
 * Extracted from OrderSagaOrchestrator to follow Single Responsibility Principle.
 * This class provides a clean API for recording saga events to the order history.
 *
 * Responsibilities:
 * - Recording saga lifecycle events (started, completed, failed)
 * - Recording step execution events (started, completed, failed)
 * - Recording compensation events (started, completed, failed)
 */
@Component
class SagaEventRecorder(
    private val orderEventService: OrderEventService
) {
    // ==================== Saga Lifecycle Events ====================

    /**
     * Record that a saga execution has started.
     */
    suspend fun recordSagaStarted(orderId: UUID, sagaExecutionId: UUID) {
        orderEventService.recordSagaStarted(orderId, sagaExecutionId)
    }

    /**
     * Record that a saga has completed successfully.
     */
    suspend fun recordSagaCompleted(
        orderId: UUID,
        sagaExecutionId: UUID,
        resultData: Map<String, Any>
    ) {
        orderEventService.recordSagaCompleted(orderId, sagaExecutionId, resultData)
    }

    /**
     * Record that a saga has failed.
     */
    suspend fun recordSagaFailed(
        orderId: UUID,
        sagaExecutionId: UUID,
        failedStep: String,
        errorInfo: ErrorInfo
    ) {
        orderEventService.recordSagaFailed(orderId, sagaExecutionId, failedStep, errorInfo)
    }

    // ==================== Step Execution Events ====================

    /**
     * Record that a step has started execution.
     */
    suspend fun recordStepStarted(orderId: UUID, sagaExecutionId: UUID, stepName: String) {
        orderEventService.recordStepStarted(orderId, sagaExecutionId, stepName)
    }

    /**
     * Record that a step has completed successfully.
     */
    suspend fun recordStepCompleted(
        orderId: UUID,
        sagaExecutionId: UUID,
        stepName: String,
        resultData: Map<String, Any>?
    ) {
        orderEventService.recordStepCompleted(orderId, sagaExecutionId, stepName, resultData)
    }

    /**
     * Record that a step has failed.
     */
    suspend fun recordStepFailed(
        orderId: UUID,
        sagaExecutionId: UUID,
        stepName: String,
        errorInfo: ErrorInfo
    ) {
        orderEventService.recordStepFailed(orderId, sagaExecutionId, stepName, errorInfo)
    }

    // ==================== Order Lifecycle Events ====================

    /**
     * Record that an order has been completed.
     */
    suspend fun recordOrderCompleted(orderId: UUID, sagaExecutionId: UUID) {
        orderEventService.recordOrderCompleted(orderId, sagaExecutionId)
    }

    // ==================== Compensation Events ====================

    /**
     * Record that compensation has started for a saga.
     */
    suspend fun recordCompensationStarted(
        orderId: UUID,
        sagaExecutionId: UUID,
        failedStep: String
    ) {
        orderEventService.recordCompensationStarted(orderId, sagaExecutionId, failedStep)
    }

    /**
     * Record that a step has been compensated.
     */
    suspend fun recordStepCompensated(orderId: UUID, sagaExecutionId: UUID, stepName: String) {
        orderEventService.recordStepCompensated(orderId, sagaExecutionId, stepName)
    }

    /**
     * Record that compensation has failed for a step.
     */
    suspend fun recordCompensationFailed(
        orderId: UUID,
        sagaExecutionId: UUID,
        stepName: String,
        errorInfo: ErrorInfo
    ) {
        orderEventService.recordCompensationFailed(orderId, sagaExecutionId, stepName, errorInfo)
    }
}
