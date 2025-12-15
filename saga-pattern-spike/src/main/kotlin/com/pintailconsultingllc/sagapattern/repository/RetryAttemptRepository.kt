package com.pintailconsultingllc.sagapattern.repository

import com.pintailconsultingllc.sagapattern.domain.RetryAttempt
import com.pintailconsultingllc.sagapattern.domain.RetryOutcome
import org.springframework.data.r2dbc.repository.Modifying
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

/**
 * Repository for retry attempt persistence.
 *
 * Manages retry attempt records for failed saga executions. Tracks retry history,
 * execution details, and outcomes for each retry operation.
 *
 * ## Naming Conventions
 *
 * This repository follows consistent naming patterns for method types:
 *
 * - **`markXxx` methods**: Update status transitions for retry lifecycle events.
 *   Records completion status and outcomes (e.g., [markCompleted]).
 *
 * - **`updateXxx` methods**: Update specific fields without changing overall status.
 *   Used for recording execution details (e.g., [updateExecutionDetails]).
 *
 * - **`findXxx` methods**: Query for existing records by various criteria.
 *   Returns nullable results for single-record queries or lists for multi-record queries.
 *
 * - **`existsXxx` methods**: Check for record existence without loading full entity.
 *   Returns boolean for efficient existence checks.
 *
 * - **`countXxx` methods**: Count records matching specific criteria.
 *   Used for retry limit checking.
 *
 * - **`save`** (inherited): Handles insert/update based on entity state.
 *   New entities (null ID) are inserted; existing entities are updated.
 *
 * ## Retry Outcome Lifecycle
 *
 * Retry attempts follow this outcome progression:
 * ```
 * (created) → [in-progress] → SUCCESS
 *                           → FAILED
 *                           → PARTIAL_SUCCESS
 * ```
 *
 * @see RetryAttempt
 * @see RetryOutcome
 * @see SagaExecutionRepository
 */
@Repository
interface RetryAttemptRepository : CoroutineCrudRepository<RetryAttempt, UUID> {

    /**
     * Find all retry attempts for an order.
     */
    suspend fun findByOrderIdOrderByAttemptNumberAsc(orderId: UUID): List<RetryAttempt>

    /**
     * Count retry attempts for an order.
     */
    suspend fun countByOrderId(orderId: UUID): Long

    /**
     * Find the latest retry attempt for an order.
     */
    @Query("SELECT * FROM retry_attempts WHERE order_id = :orderId ORDER BY attempt_number DESC LIMIT 1")
    suspend fun findLatestByOrderId(orderId: UUID): RetryAttempt?

    /**
     * Check if there is an active (in-progress) retry for an order.
     */
    @Query("SELECT COUNT(*) > 0 FROM retry_attempts WHERE order_id = :orderId AND outcome IS NULL")
    suspend fun existsActiveRetryByOrderId(orderId: UUID): Boolean

    /**
     * Find active retry attempt for an order (if any).
     */
    @Query("SELECT * FROM retry_attempts WHERE order_id = :orderId AND outcome IS NULL LIMIT 1")
    suspend fun findActiveByOrderId(orderId: UUID): RetryAttempt?

    /**
     * Update retry attempt with execution details.
     */
    @Modifying
    @Query("""
        UPDATE retry_attempts
        SET retry_execution_id = :executionId,
            resumed_from_step = :resumedFromStep,
            skipped_steps = :skippedSteps
        WHERE id = :retryAttemptId
    """)
    suspend fun updateExecutionDetails(
        retryAttemptId: UUID,
        executionId: UUID,
        resumedFromStep: String,
        skippedSteps: Array<String>
    ): Int

    /**
     * Mark retry attempt as completed with outcome.
     */
    @Modifying
    @Query("""
        UPDATE retry_attempts
        SET outcome = :outcome,
            failure_reason = :failureReason,
            completed_at = :completedAt
        WHERE id = :retryAttemptId
    """)
    suspend fun markCompleted(
        retryAttemptId: UUID,
        outcome: RetryOutcome,
        failureReason: String?,
        completedAt: Instant
    ): Int

    /**
     * Find retry attempts by original execution ID.
     */
    suspend fun findByOriginalExecutionId(originalExecutionId: UUID): List<RetryAttempt>
}
