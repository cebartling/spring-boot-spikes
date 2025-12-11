package com.pintailconsultingllc.sagapattern.domain

import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Transient
import org.springframework.data.domain.Persistable
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.UUID

/**
 * Represents the result of an individual saga step execution.
 * Persisted to the database for tracking and auditing.
 *
 * Implements [Persistable] to correctly handle INSERT vs UPDATE with pre-generated UUIDs.
 */
@Table("saga_step_results")
data class SagaStepResult(
    @Id
    @get:JvmName("id")
    val id: UUID = UUID.randomUUID(),

    @Column("saga_execution_id")
    val sagaExecutionId: UUID,

    @Column("step_name")
    val stepName: String,

    @Column("step_order")
    val stepOrder: Int,

    val status: StepStatus,

    @Column("step_data")
    val stepData: String? = null,

    @Column("error_message")
    val errorMessage: String? = null,

    @Column("started_at")
    val startedAt: Instant? = null,

    @Column("completed_at")
    val completedAt: Instant? = null,

    @Transient
    private val isNewEntity: Boolean = true
) : Persistable<UUID> {

    override fun getId(): UUID = id

    override fun isNew(): Boolean = isNewEntity

    companion object {
        /**
         * Create a pending step result record.
         */
        fun pending(
            sagaExecutionId: UUID,
            stepName: String,
            stepOrder: Int
        ): SagaStepResult = SagaStepResult(
            sagaExecutionId = sagaExecutionId,
            stepName = stepName,
            stepOrder = stepOrder,
            status = StepStatus.PENDING
        )

        /**
         * Create a skipped step result record.
         * Used during retry when a step's previous result is still valid.
         */
        fun skipped(
            sagaExecutionId: UUID,
            stepName: String,
            stepOrder: Int
        ): SagaStepResult = SagaStepResult(
            sagaExecutionId = sagaExecutionId,
            stepName = stepName,
            stepOrder = stepOrder,
            status = StepStatus.SKIPPED,
            completedAt = Instant.now()
        )
    }

    /**
     * Mark the step as in progress.
     */
    fun start(): SagaStepResult = copy(
        status = StepStatus.IN_PROGRESS,
        startedAt = Instant.now(),
        isNewEntity = false
    )

    /**
     * Mark the step as completed with optional data.
     */
    fun complete(data: String? = null): SagaStepResult = copy(
        status = StepStatus.COMPLETED,
        stepData = data,
        completedAt = Instant.now(),
        isNewEntity = false
    )

    /**
     * Mark the step as failed with an error message.
     */
    fun fail(error: String): SagaStepResult = copy(
        status = StepStatus.FAILED,
        errorMessage = error,
        completedAt = Instant.now(),
        isNewEntity = false
    )

    /**
     * Mark the step as compensated.
     */
    fun compensate(): SagaStepResult = copy(
        status = StepStatus.COMPENSATED,
        completedAt = Instant.now(),
        isNewEntity = false
    )

    /**
     * Mark this entity as persisted.
     */
    fun asPersisted(): SagaStepResult = copy(isNewEntity = false)
}
