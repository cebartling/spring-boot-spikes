package com.pintailconsultingllc.cqrsspike.infrastructure.error

import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime

/**
 * Error response for service unavailable (HTTP 503).
 *
 * Implements AC10: "Fallback methods provide graceful degradation"
 */
@Schema(description = "Service unavailable response")
data class ServiceUnavailableResponse(
    @Schema(description = "Error timestamp")
    val timestamp: OffsetDateTime = OffsetDateTime.now(),

    @Schema(description = "HTTP status code", example = "503")
    val status: Int = 503,

    @Schema(description = "HTTP status reason phrase")
    val error: String = "Service Unavailable",

    @Schema(description = "Human-readable error message")
    val message: String,

    @Schema(description = "Request path")
    val path: String,

    @Schema(description = "Correlation ID for request tracing")
    val correlationId: String? = null,

    @Schema(description = "Machine-readable error code")
    val code: String = "SERVICE_UNAVAILABLE",

    @Schema(description = "Seconds to wait before retrying")
    val retryAfter: Int = 30,

    @Schema(description = "Current state of the circuit breaker")
    val circuitBreakerState: String? = null,

    @Schema(description = "Guidance for retry behavior")
    val retryGuidance: String = "The service is temporarily unavailable. Please retry after the specified interval."
)
