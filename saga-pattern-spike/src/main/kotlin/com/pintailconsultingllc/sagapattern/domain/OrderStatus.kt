package com.pintailconsultingllc.sagapattern.domain

/**
 * Represents the possible states of an order throughout its lifecycle.
 *
 * State transitions:
 * ```
 * PENDING → PROCESSING → COMPLETED
 *                     ↘ FAILED
 *                     ↘ COMPENSATING → COMPENSATED
 * ```
 */
enum class OrderStatus {
    /**
     * Order created, saga not yet started.
     */
    PENDING,

    /**
     * Saga execution in progress.
     */
    PROCESSING,

    /**
     * All saga steps completed successfully.
     */
    COMPLETED,

    /**
     * One or more saga steps failed.
     */
    FAILED,

    /**
     * Compensation (rollback) in progress.
     */
    COMPENSATING,

    /**
     * Compensation complete, order rolled back.
     */
    COMPENSATED
}
