package com.pintailconsultingllc.sagapattern.history

/**
 * Types of events that can occur during order processing.
 */
enum class OrderEventType {
    /** Order initially created. */
    ORDER_CREATED,

    /** Saga execution began. */
    SAGA_STARTED,

    /** Step execution started. */
    STEP_STARTED,

    /** Step finished successfully. */
    STEP_COMPLETED,

    /** Step execution failed. */
    STEP_FAILED,

    /** Rollback initiated. */
    COMPENSATION_STARTED,

    /** Step successfully reversed. */
    STEP_COMPENSATED,

    /** Rollback step failed. */
    COMPENSATION_FAILED,

    /** Saga finished successfully. */
    SAGA_COMPLETED,

    /** Saga finished with failure. */
    SAGA_FAILED,

    /** Customer initiated retry. */
    RETRY_INITIATED,

    /** Order fully processed. */
    ORDER_COMPLETED,

    /** Order cancelled. */
    ORDER_CANCELLED;

    companion object {
        /**
         * Returns true if this event type represents a failure.
         */
        fun isFailureEvent(type: OrderEventType): Boolean = when (type) {
            STEP_FAILED, COMPENSATION_FAILED, SAGA_FAILED -> true
            else -> false
        }

        /**
         * Returns true if this event type represents a compensation action.
         */
        fun isCompensationEvent(type: OrderEventType): Boolean = when (type) {
            COMPENSATION_STARTED, STEP_COMPENSATED, COMPENSATION_FAILED -> true
            else -> false
        }
    }
}
