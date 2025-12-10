package com.pintailconsultingllc.sagapattern.progress

/**
 * Represents the overall progress status of an order's saga execution.
 */
enum class ProgressStatus {
    /**
     * Order received, processing not started.
     */
    QUEUED,

    /**
     * Saga actively executing steps.
     */
    IN_PROGRESS,

    /**
     * All steps completed successfully.
     */
    COMPLETED,

    /**
     * A step failed.
     */
    FAILED,

    /**
     * Compensation (rollback) in progress.
     */
    ROLLING_BACK,

    /**
     * Compensation complete.
     */
    ROLLED_BACK
}
