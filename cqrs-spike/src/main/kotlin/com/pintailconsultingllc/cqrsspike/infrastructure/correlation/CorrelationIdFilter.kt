package com.pintailconsultingllc.cqrsspike.infrastructure.correlation

import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * Web filter that ensures every request has a correlation ID.
 *
 * Implements AC10: "All errors are logged with correlation IDs"
 *
 * - Extracts existing correlation ID from X-Correlation-ID header
 * - Generates new UUID if not present
 * - Adds correlation ID to response headers
 * - Propagates through Reactor context and MDC for logging
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class CorrelationIdFilter : WebFilter {

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val correlationId = exchange.request.headers
            .getFirst(CorrelationIdHolder.CORRELATION_ID_HEADER)
            ?: UUID.randomUUID().toString()

        // Add to response headers
        exchange.response.headers.add(
            CorrelationIdHolder.CORRELATION_ID_HEADER,
            correlationId
        )

        return chain.filter(exchange)
            .contextWrite { ctx ->
                CorrelationIdHolder.addToContext(ctx, correlationId)
            }
            .doOnEach { signal ->
                if (!signal.isOnComplete && !signal.isOnError) {
                    MDC.put(CorrelationIdHolder.CORRELATION_ID_KEY, correlationId)
                }
            }
            .doFinally {
                MDC.remove(CorrelationIdHolder.CORRELATION_ID_KEY)
            }
    }
}
