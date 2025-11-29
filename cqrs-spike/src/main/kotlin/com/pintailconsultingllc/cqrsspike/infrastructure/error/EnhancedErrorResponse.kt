package com.pintailconsultingllc.cqrsspike.infrastructure.error

import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime

/**
 * Standardized error response with correlation ID and retry guidance.
 *
 * Implements AC10: "All errors are logged with correlation IDs"
 */
@Schema(description = "Standard error response")
data class EnhancedErrorResponse(
    @Schema(description = "Error timestamp")
    val timestamp: OffsetDateTime = OffsetDateTime.now(),

    @Schema(description = "HTTP status code", example = "400")
    val status: Int,

    @Schema(description = "HTTP status reason phrase", example = "Bad Request")
    val error: String,

    @Schema(description = "Human-readable error message")
    val message: String,

    @Schema(description = "Request path", example = "/api/products")
    val path: String,

    @Schema(description = "Correlation ID for request tracing")
    val correlationId: String? = null,

    @Schema(description = "Machine-readable error code", example = "VALIDATION_FAILED")
    val code: String? = null,

    @Schema(description = "Additional error details")
    val details: Any? = null,

    @Schema(description = "Seconds to wait before retrying")
    val retryAfter: Int? = null,

    @Schema(description = "Guidance for retry behavior")
    val retryGuidance: String? = null
)
