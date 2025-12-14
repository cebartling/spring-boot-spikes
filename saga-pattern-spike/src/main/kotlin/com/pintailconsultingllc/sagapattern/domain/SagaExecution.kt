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
 * Represents a saga execution tracking record.
 * Tracks the overall progress and status of a saga.
 *
 * Implements [Persistable] to correctly handle INSERT vs UPDATE with pre-generated UUIDs.
 */
@Table("saga_executions")
data class SagaExecution @PersistenceCreator constructor(
    @Id
    @get:JvmName("id")
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

    @Column("trace_id")
    val traceId: String? = null,

    @Column("started_at")
    val startedAt: Instant = Instant.now(),

    @Column("completed_at")
    val completedAt: Instant? = null,

    @Column("compensation_started_at")
    val compensationStartedAt: Instant? = null,

    @Column("compensation_completed_at")
    val compensationCompletedAt: Instant? = null
) : Persistable<UUID> {

    @Transient
    private var isNewEntity: Boolean = false

    override fun getId(): UUID = id

    override fun isNew(): Boolean = isNewEntity

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

    /**
     * Mark this entity as persisted.
     */
    fun asPersisted(): SagaExecution = this.also { isNewEntity = false }

    companion object {
        /**
         * Create a new SagaExecution instance.
         */
        fun create(orderId: UUID, traceId: String? = null): SagaExecution = SagaExecution(
            id = UUID.randomUUID(),
            orderId = orderId,
            traceId = traceId
        ).apply { isNewEntity = true }

        /**
         * Create a new SagaExecution instance with specific ID and status.
         */
        fun create(
            id: UUID,
            orderId: UUID,
            status: SagaStatus,
            startedAt: Instant,
            traceId: String? = null
        ): SagaExecution = SagaExecution(
            id = id,
            orderId = orderId,
            status = status,
            startedAt = startedAt,
            traceId = traceId
        ).apply { isNewEntity = true }

        /**
         * Create a new SagaExecution instance with all fields.
         * Used primarily for testing and data migration.
         */
        fun createWithDetails(
            id: UUID = UUID.randomUUID(),
            orderId: UUID,
            status: SagaStatus = SagaStatus.PENDING,
            currentStep: Int = 0,
            failedStep: Int? = null,
            failureReason: String? = null,
            traceId: String? = null,
            startedAt: Instant = Instant.now(),
            completedAt: Instant? = null,
            compensationStartedAt: Instant? = null,
            compensationCompletedAt: Instant? = null
        ): SagaExecution = SagaExecution(
            id = id,
            orderId = orderId,
            status = status,
            currentStep = currentStep,
            failedStep = failedStep,
            failureReason = failureReason,
            traceId = traceId,
            startedAt = startedAt,
            completedAt = completedAt,
            compensationStartedAt = compensationStartedAt,
            compensationCompletedAt = compensationCompletedAt
        ).apply { isNewEntity = true }
    }
}
