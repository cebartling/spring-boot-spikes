package com.pintailconsultingllc.sagapattern.history

/**
 * Possible outcomes for order events.
 */
enum class EventOutcome {
    /** Event completed successfully. */
    SUCCESS,

    /** Event failed. */
    FAILED,

    /** Event was compensated/reversed. */
    COMPENSATED,

    /** Neutral event (informational only). */
    NEUTRAL
}
