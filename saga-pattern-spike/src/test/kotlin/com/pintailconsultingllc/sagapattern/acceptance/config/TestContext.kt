package com.pintailconsultingllc.sagapattern.acceptance.config

import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Shared test context for passing data between Cucumber steps.
 *
 * This component is scoped to each scenario execution and holds
 * state that needs to be shared across step definitions.
 */
@Component
class TestContext {

    // Current test customer
    var customerId: UUID? = null

    // Current order being tested
    var orderId: UUID? = null
    var orderResponse: Map<String, Any>? = null

    // Status responses
    var statusResponse: Map<String, Any>? = null

    // History responses
    var historyResponse: Map<String, Any>? = null

    // Retry responses
    var retryEligibilityResponse: Map<String, Any>? = null
    var retryResponse: Map<String, Any>? = null
    var retryHistoryResponse: Map<String, Any>? = null

    // Error/failure tracking
    var lastError: String? = null
    var failureNotification: Map<String, Any>? = null

    // Cart/order items
    var cartItems: MutableList<Map<String, Any>> = mutableListOf()
    var paymentMethodId: String? = null
    var shippingAddress: Map<String, Any>? = null

    // Observability tracking
    var responseHeaders: HttpHeaders? = null
    var traceId: String? = null
    var originalTraceId: String? = null
    var traceIds: MutableList<String> = mutableListOf()

    /**
     * Reset all context state. Called at the start of each scenario.
     */
    fun reset() {
        customerId = null
        orderId = null
        orderResponse = null
        statusResponse = null
        historyResponse = null
        retryEligibilityResponse = null
        retryResponse = null
        retryHistoryResponse = null
        lastError = null
        failureNotification = null
        cartItems.clear()
        paymentMethodId = null
        shippingAddress = null
        responseHeaders = null
        traceId = null
        originalTraceId = null
        traceIds.clear()
    }

    /**
     * Extract trace ID from traceparent header (W3C trace context format).
     * Format: version-traceId-parentId-flags (e.g., 00-abc123...-def456...-01)
     */
    fun extractTraceIdFromTraceparent(traceparent: String?): String? {
        if (traceparent.isNullOrBlank()) return null
        val parts = traceparent.split("-")
        return if (parts.size >= 2) parts[1] else null
    }

    /**
     * Check if a trace ID is in valid W3C trace context format (32 hex characters).
     */
    fun isValidW3CTraceId(traceId: String?): Boolean {
        if (traceId.isNullOrBlank()) return false
        return traceId.matches(Regex("^[0-9a-f]{32}$"))
    }
}
