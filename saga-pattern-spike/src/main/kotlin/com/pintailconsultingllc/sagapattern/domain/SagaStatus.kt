package com.pintailconsultingllc.sagapattern.domain

/**
 * Represents the status of a saga execution.
 */
enum class SagaStatus {
    /**
     * Saga has been created but not yet started.
     */
    PENDING,

    /**
     * Saga execution is in progress.
     */
    IN_PROGRESS,

    /**
     * All saga steps completed successfully.
     */
    COMPLETED,

    /**
     * A saga step failed, triggering compensation.
     */
    FAILED,

    /**
     * Compensation is currently in progress.
     */
    COMPENSATING,

    /**
     * Compensation has completed.
     */
    COMPENSATED
}
