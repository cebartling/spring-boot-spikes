package com.pintailconsultingllc.sagapattern.observability

import io.micrometer.tracing.Tracer
import org.springframework.stereotype.Service

/**
 * Service for accessing distributed tracing context.
 *
 * Provides utilities to retrieve the current trace ID from the
 * OpenTelemetry/Micrometer tracing context for inclusion in
 * API responses and persistence.
 */
@Service
class TraceContextService(
    private val tracer: Tracer
) {
    /**
     * Get the current trace ID from the active span.
     *
     * @return The trace ID as a hex string (32 characters), or null if no active trace
     */
    fun getCurrentTraceId(): String? {
        return tracer.currentSpan()?.context()?.traceId()
    }

    /**
     * Get the current span ID from the active span.
     *
     * @return The span ID as a hex string (16 characters), or null if no active span
     */
    fun getCurrentSpanId(): String? {
        return tracer.currentSpan()?.context()?.spanId()
    }

    /**
     * Check if there is an active trace context.
     *
     * @return true if a trace is currently active
     */
    fun hasActiveTrace(): Boolean {
        return tracer.currentSpan() != null
    }

    /**
     * Get the W3C traceparent header value for the current trace.
     *
     * Format: version-traceId-spanId-flags
     * Example: 00-abc123...-def456...-01
     *
     * @return The traceparent header value, or null if no active trace
     */
    fun getTraceparentHeader(): String? {
        val span = tracer.currentSpan() ?: return null
        val context = span.context()
        val traceId = context.traceId() ?: return null
        val spanId = context.spanId() ?: return null

        // W3C Trace Context format: version-traceId-parentId-flags
        // Version is always "00" for current spec
        // Flags: 01 = sampled, 00 = not sampled
        val sampled = if (context.sampled() == true) "01" else "00"
        return "00-$traceId-$spanId-$sampled"
    }
}
