package com.pintailconsultingllc.cqrsspike.infrastructure.correlation

import reactor.core.publisher.Mono
import reactor.util.context.Context

/**
 * Holder for correlation ID in reactive context.
 *
 * Uses Reactor Context for propagation in reactive streams.
 * Implements AC10: "All errors are logged with correlation IDs"
 */
object CorrelationIdHolder {
    const val CORRELATION_ID_KEY = "correlationId"
    const val CORRELATION_ID_HEADER = "X-Correlation-ID"

    /**
     * Gets the correlation ID from the current Reactor context.
     */
    fun getCorrelationId(): Mono<String> {
        return Mono.deferContextual { ctx ->
            Mono.justOrEmpty(ctx.getOrEmpty<String>(CORRELATION_ID_KEY))
        }
    }

    /**
     * Creates a context with the correlation ID.
     */
    fun withCorrelationId(correlationId: String): Context {
        return Context.of(CORRELATION_ID_KEY, correlationId)
    }

    /**
     * Adds correlation ID to an existing context.
     */
    fun addToContext(context: Context, correlationId: String): Context {
        return context.put(CORRELATION_ID_KEY, correlationId)
    }
}
