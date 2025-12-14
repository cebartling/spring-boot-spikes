package com.pintailconsultingllc.sagapattern.domain

import org.springframework.data.annotation.Id
import org.springframework.data.annotation.PersistenceCreator
import org.springframework.data.annotation.Transient
import org.springframework.data.domain.Persistable
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.UUID

/**
 * Represents a retry attempt for a failed order saga.
 *
 * Tracks all retry attempts including which step execution resumed from,
 * which steps were skipped, and the outcome of the retry.
 *
 * Implements [Persistable] to correctly handle INSERT vs UPDATE with pre-generated UUIDs.
 */
@Table("retry_attempts")
data class RetryAttempt @PersistenceCreator constructor(
    @Id
    @get:JvmName("id")
    val id: UUID = UUID.randomUUID(),

    @Column("order_id")
    val orderId: UUID,

    @Column("original_execution_id")
    val originalExecutionId: UUID,

    @Column("retry_execution_id")
    val retryExecutionId: UUID? = null,

    @Column("attempt_number")
    val attemptNumber: Int,

    @Column("resumed_from_step")
    val resumedFromStep: String? = null,

    @Column("skipped_steps")
    val skippedSteps: Array<String>? = null,

    val outcome: RetryOutcome? = null,

    @Column("failure_reason")
    val failureReason: String? = null,

    @Column("initiated_at")
    val initiatedAt: Instant = Instant.now(),

    @Column("completed_at")
    val completedAt: Instant? = null
) : Persistable<UUID> {

    @Transient
    private var isNewEntity: Boolean = false

    override fun getId(): UUID = id

    override fun isNew(): Boolean = isNewEntity

    /**
     * Create a copy with the retry execution ID set.
     */
    fun withRetryExecution(executionId: UUID): RetryAttempt = copy(
        retryExecutionId = executionId
    )

    /**
     * Create a copy with step information set.
     */
    fun withStepInfo(resumedFrom: String, skipped: List<String>): RetryAttempt = copy(
        resumedFromStep = resumedFrom,
        skippedSteps = skipped.toTypedArray()
    )

    /**
     * Mark the retry as successful.
     */
    fun markSuccessful(): RetryAttempt = copy(
        outcome = RetryOutcome.SUCCESS,
        completedAt = Instant.now()
    )

    /**
     * Mark the retry as failed.
     */
    fun markFailed(reason: String): RetryAttempt = copy(
        outcome = RetryOutcome.FAILED,
        failureReason = reason,
        completedAt = Instant.now()
    )

    /**
     * Mark the retry as cancelled.
     */
    fun markCancelled(reason: String): RetryAttempt = copy(
        outcome = RetryOutcome.CANCELLED,
        failureReason = reason,
        completedAt = Instant.now()
    )

    /**
     * Mark this entity as persisted.
     */
    fun asPersisted(): RetryAttempt = this.also { isNewEntity = false }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RetryAttempt

        if (id != other.id) return false
        if (orderId != other.orderId) return false
        if (originalExecutionId != other.originalExecutionId) return false
        if (retryExecutionId != other.retryExecutionId) return false
        if (attemptNumber != other.attemptNumber) return false
        if (resumedFromStep != other.resumedFromStep) return false
        if (skippedSteps != null) {
            if (other.skippedSteps == null) return false
            if (!skippedSteps.contentEquals(other.skippedSteps)) return false
        } else if (other.skippedSteps != null) return false
        if (outcome != other.outcome) return false
        if (failureReason != other.failureReason) return false
        if (initiatedAt != other.initiatedAt) return false
        if (completedAt != other.completedAt) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + orderId.hashCode()
        result = 31 * result + originalExecutionId.hashCode()
        result = 31 * result + (retryExecutionId?.hashCode() ?: 0)
        result = 31 * result + attemptNumber
        result = 31 * result + (resumedFromStep?.hashCode() ?: 0)
        result = 31 * result + (skippedSteps?.contentHashCode() ?: 0)
        result = 31 * result + (outcome?.hashCode() ?: 0)
        result = 31 * result + (failureReason?.hashCode() ?: 0)
        result = 31 * result + initiatedAt.hashCode()
        result = 31 * result + (completedAt?.hashCode() ?: 0)
        return result
    }

    companion object {
        /**
         * Create a new RetryAttempt instance.
         */
        fun create(
            orderId: UUID,
            originalExecutionId: UUID,
            attemptNumber: Int
        ): RetryAttempt = RetryAttempt(
            id = UUID.randomUUID(),
            orderId = orderId,
            originalExecutionId = originalExecutionId,
            attemptNumber = attemptNumber
        ).apply { isNewEntity = true }

        /**
         * Create a new RetryAttempt instance with all fields.
         * Used primarily for testing historical retry records.
         */
        fun createWithDetails(
            orderId: UUID,
            originalExecutionId: UUID,
            attemptNumber: Int,
            initiatedAt: Instant = Instant.now(),
            completedAt: Instant? = null,
            outcome: RetryOutcome? = null,
            failureReason: String? = null,
            resumedFromStep: String? = null,
            skippedSteps: Array<String>? = null,
            retryExecutionId: UUID? = null
        ): RetryAttempt = RetryAttempt(
            id = UUID.randomUUID(),
            orderId = orderId,
            originalExecutionId = originalExecutionId,
            attemptNumber = attemptNumber,
            initiatedAt = initiatedAt,
            completedAt = completedAt,
            outcome = outcome,
            failureReason = failureReason,
            resumedFromStep = resumedFromStep,
            skippedSteps = skippedSteps,
            retryExecutionId = retryExecutionId
        ).apply { isNewEntity = true }
    }
}

/**
 * Outcome of a retry attempt.
 */
enum class RetryOutcome {
    /**
     * Retry completed successfully, order is now complete.
     */
    SUCCESS,

    /**
     * Retry failed, order remains in failed state.
     */
    FAILED,

    /**
     * Retry was cancelled before completion.
     */
    CANCELLED
}
