package com.pintailconsultingllc.sagapattern.repository

import com.pintailconsultingllc.sagapattern.domain.SagaExecution
import com.pintailconsultingllc.sagapattern.domain.SagaStatus
import org.springframework.data.r2dbc.repository.Modifying
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

/**
 * Repository for saga execution persistence.
 *
 * Manages the lifecycle of saga execution records, including status transitions,
 * compensation tracking, and execution metadata.
 *
 * ## Naming Conventions
 *
 * This repository follows consistent naming patterns for method types:
 *
 * - **`markXxx` methods**: Update status transitions for saga lifecycle events.
 *   These methods record state changes (e.g., [markCompleted], [markFailed],
 *   [markCompensationStarted], [markCompensationCompleted]) and capture timing.
 *
 * - **`updateXxx` methods**: Update specific fields without changing overall status.
 *   Used for progress tracking (e.g., [updateCurrentStep], [updateStatus]).
 *
 * - **`findXxx` methods**: Query for existing records by various criteria.
 *   Returns nullable results for single-record queries or lists for multi-record queries.
 *
 * - **`save`** (inherited): Handles insert/update based on entity state.
 *   New entities (null ID) are inserted; existing entities are updated.
 *
 * ## Saga Status Lifecycle
 *
 * Sagas follow this status progression:
 * ```
 * PENDING → IN_PROGRESS → COMPLETED
 *                       ↘ FAILED → COMPENSATING → COMPENSATED
 *                                              ↘ PARTIALLY_COMPENSATED
 * ```
 *
 * @see SagaExecution
 * @see SagaStatus
 * @see SagaStepResultRepository
 */
@Repository
interface SagaExecutionRepository : CoroutineCrudRepository<SagaExecution, UUID> {

    /**
     * Find the saga execution for a specific order.
     */
    suspend fun findByOrderId(orderId: UUID): SagaExecution?

    /**
     * Find all saga executions for a specific order (including retries).
     */
    suspend fun findAllByOrderId(orderId: UUID): List<SagaExecution>

    /**
     * Find saga executions by status.
     */
    suspend fun findByStatus(status: SagaStatus): List<SagaExecution>

    /**
     * Update the current step of a saga execution.
     */
    @Modifying
    @Query("UPDATE saga_executions SET current_step = :step WHERE id = :sagaExecutionId")
    suspend fun updateCurrentStep(sagaExecutionId: UUID, step: Int): Int

    /**
     * Update saga execution status.
     */
    @Modifying
    @Query("UPDATE saga_executions SET status = :status WHERE id = :sagaExecutionId")
    suspend fun updateStatus(sagaExecutionId: UUID, status: SagaStatus): Int

    /**
     * Mark saga as completed.
     */
    @Modifying
    @Query("UPDATE saga_executions SET status = 'COMPLETED', completed_at = :completedAt WHERE id = :sagaExecutionId")
    suspend fun markCompleted(sagaExecutionId: UUID, completedAt: Instant): Int

    /**
     * Mark saga as failed.
     */
    @Modifying
    @Query("""
        UPDATE saga_executions
        SET status = 'FAILED', failed_step = :failedStep, failure_reason = :reason, completed_at = :completedAt
        WHERE id = :sagaExecutionId
    """)
    suspend fun markFailed(sagaExecutionId: UUID, failedStep: Int, reason: String, completedAt: Instant): Int

    /**
     * Mark compensation as started.
     */
    @Modifying
    @Query("UPDATE saga_executions SET status = 'COMPENSATING', compensation_started_at = :startedAt WHERE id = :sagaExecutionId")
    suspend fun markCompensationStarted(sagaExecutionId: UUID, startedAt: Instant): Int

    /**
     * Mark compensation as completed.
     */
    @Modifying
    @Query("UPDATE saga_executions SET status = 'COMPENSATED', compensation_completed_at = :completedAt WHERE id = :sagaExecutionId")
    suspend fun markCompensationCompleted(sagaExecutionId: UUID, completedAt: Instant): Int
}
