package com.pintailconsultingllc.sagapattern.history

/**
 * Status for timeline entries, used for visual representation.
 */
enum class TimelineStatus {
    /** Successful completion. */
    SUCCESS,

    /** Failed step or action. */
    FAILED,

    /** Compensated/reversed action. */
    COMPENSATED,

    /** Informational/neutral event. */
    NEUTRAL
}
