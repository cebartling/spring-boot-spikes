package com.pintailconsultingllc.sagapattern.repository

import com.pintailconsultingllc.sagapattern.domain.SagaStepResult
import com.pintailconsultingllc.sagapattern.domain.StepStatus
import org.springframework.data.r2dbc.repository.Modifying
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

/**
 * Repository for SagaStepResult entities using R2DBC.
 */
@Repository
interface SagaStepResultRepository : CoroutineCrudRepository<SagaStepResult, UUID> {

    /**
     * Find all step results for a saga execution, ordered by step order.
     */
    suspend fun findBySagaExecutionIdOrderByStepOrder(sagaExecutionId: UUID): List<SagaStepResult>

    /**
     * Find step result by saga execution and step name.
     */
    suspend fun findBySagaExecutionIdAndStepName(sagaExecutionId: UUID, stepName: String): SagaStepResult?

    /**
     * Delete all step results for a saga execution.
     */
    suspend fun deleteBySagaExecutionId(sagaExecutionId: UUID): Long

    /**
     * Update step status to IN_PROGRESS.
     */
    @Modifying
    @Query("UPDATE saga_step_results SET status = 'IN_PROGRESS', started_at = :startedAt WHERE id = :stepResultId")
    suspend fun markInProgress(stepResultId: UUID, startedAt: Instant): Int

    /**
     * Update step status to COMPLETED with data.
     */
    @Modifying
    @Query("UPDATE saga_step_results SET status = 'COMPLETED', step_data = :stepData, completed_at = :completedAt WHERE id = :stepResultId")
    suspend fun markCompleted(stepResultId: UUID, stepData: String?, completedAt: Instant): Int

    /**
     * Update step status to FAILED with error.
     */
    @Modifying
    @Query("UPDATE saga_step_results SET status = 'FAILED', error_message = :errorMessage, completed_at = :completedAt WHERE id = :stepResultId")
    suspend fun markFailed(stepResultId: UUID, errorMessage: String, completedAt: Instant): Int

    /**
     * Update step status to COMPENSATED.
     */
    @Modifying
    @Query("UPDATE saga_step_results SET status = 'COMPENSATED', completed_at = :completedAt WHERE id = :stepResultId")
    suspend fun markCompensated(stepResultId: UUID, completedAt: Instant): Int
}
