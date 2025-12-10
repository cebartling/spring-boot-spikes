package com.pintailconsultingllc.sagapattern.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.UUID

/**
 * Represents a saga execution tracking record.
 * Tracks the overall progress and status of a saga.
 */
@Table("saga_executions")
data class SagaExecution(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column("order_id")
    val orderId: UUID,

    @Column("current_step")
    val currentStep: Int = 0,

    val status: SagaStatus = SagaStatus.PENDING,

    @Column("failed_step")
    val failedStep: Int? = null,

    @Column("failure_reason")
    val failureReason: String? = null,

    @Column("started_at")
    val startedAt: Instant = Instant.now(),

    @Column("completed_at")
    val completedAt: Instant? = null,

    @Column("compensation_started_at")
    val compensationStartedAt: Instant? = null,

    @Column("compensation_completed_at")
    val compensationCompletedAt: Instant? = null
) {
    /**
     * Create a copy with status set to IN_PROGRESS.
     */
    fun start(): SagaExecution = copy(
        status = SagaStatus.IN_PROGRESS,
        startedAt = Instant.now()
    )

    /**
     * Create a copy with updated current step.
     */
    fun advanceToStep(step: Int): SagaExecution = copy(currentStep = step)

    /**
     * Create a copy marking the saga as completed.
     */
    fun complete(): SagaExecution = copy(
        status = SagaStatus.COMPLETED,
        completedAt = Instant.now()
    )

    /**
     * Create a copy marking the saga as failed.
     */
    fun fail(failedStepIndex: Int, reason: String): SagaExecution = copy(
        status = SagaStatus.FAILED,
        failedStep = failedStepIndex,
        failureReason = reason,
        completedAt = Instant.now()
    )

    /**
     * Create a copy marking compensation as started.
     */
    fun startCompensation(): SagaExecution = copy(
        status = SagaStatus.COMPENSATING,
        compensationStartedAt = Instant.now()
    )

    /**
     * Create a copy marking compensation as completed.
     */
    fun completeCompensation(): SagaExecution = copy(
        status = SagaStatus.COMPENSATED,
        compensationCompletedAt = Instant.now()
    )
}
