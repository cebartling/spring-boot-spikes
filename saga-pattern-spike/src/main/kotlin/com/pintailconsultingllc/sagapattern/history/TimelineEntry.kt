package com.pintailconsultingllc.sagapattern.history

import java.time.Instant

/**
 * Represents a single entry in the order timeline.
 */
data class TimelineEntry(
    /** When the event occurred. */
    val timestamp: Instant,

    /** Human-readable title. */
    val title: String,

    /** Human-readable description. */
    val description: String,

    /** Visual status indicator. */
    val status: TimelineStatus,

    /** Related saga step name (if applicable). */
    val stepName: String? = null,

    /** Additional context details. */
    val details: Map<String, Any>? = null,

    /** Error information if this is a failure entry. */
    val error: ErrorInfo? = null
) {
    companion object {
        /**
         * Create a success timeline entry.
         */
        fun success(
            timestamp: Instant,
            title: String,
            description: String,
            stepName: String? = null,
            details: Map<String, Any>? = null
        ): TimelineEntry = TimelineEntry(
            timestamp = timestamp,
            title = title,
            description = description,
            status = TimelineStatus.SUCCESS,
            stepName = stepName,
            details = details
        )

        /**
         * Create a failure timeline entry.
         */
        fun failed(
            timestamp: Instant,
            title: String,
            description: String,
            stepName: String? = null,
            error: ErrorInfo? = null
        ): TimelineEntry = TimelineEntry(
            timestamp = timestamp,
            title = title,
            description = description,
            status = TimelineStatus.FAILED,
            stepName = stepName,
            error = error
        )

        /**
         * Create a compensated timeline entry.
         */
        fun compensated(
            timestamp: Instant,
            title: String,
            description: String,
            stepName: String? = null
        ): TimelineEntry = TimelineEntry(
            timestamp = timestamp,
            title = title,
            description = description,
            status = TimelineStatus.COMPENSATED,
            stepName = stepName
        )

        /**
         * Create a neutral/informational timeline entry.
         */
        fun neutral(
            timestamp: Instant,
            title: String,
            description: String,
            stepName: String? = null
        ): TimelineEntry = TimelineEntry(
            timestamp = timestamp,
            title = title,
            description = description,
            status = TimelineStatus.NEUTRAL,
            stepName = stepName
        )
    }
}
