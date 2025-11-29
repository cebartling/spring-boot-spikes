package com.pintailconsultingllc.cqrsspike.infrastructure.error

import com.pintailconsultingllc.cqrsspike.infrastructure.correlation.CorrelationIdHolder
import org.springframework.web.server.ServerWebExchange

/**
 * Base functionality for exception handlers.
 *
 * Provides common utilities like correlation ID extraction.
 * Implements AC10: "All errors are logged with correlation IDs"
 */
abstract class BaseExceptionHandler {

    /**
     * Extracts correlation ID from the request headers.
     */
    protected fun getCorrelationId(exchange: ServerWebExchange): String? {
        return exchange.request.headers
            .getFirst(CorrelationIdHolder.CORRELATION_ID_HEADER)
    }

    /**
     * Gets the request path.
     */
    protected fun getPath(exchange: ServerWebExchange): String {
        return exchange.request.path.value()
    }
}
