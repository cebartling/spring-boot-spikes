package com.pintailconsultingllc.sagapattern.domain

/**
 * Represents the status of an individual saga step.
 */
enum class StepStatus {
    /**
     * Step has not yet been executed.
     */
    PENDING,

    /**
     * Step is currently executing.
     */
    IN_PROGRESS,

    /**
     * Step completed successfully.
     */
    COMPLETED,

    /**
     * Step execution failed.
     */
    FAILED,

    /**
     * Step is being compensated.
     */
    COMPENSATING,

    /**
     * Step compensation completed.
     */
    COMPENSATED
}
