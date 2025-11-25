package com.pintailconsultingllc.cqrsspike.dto

import java.time.OffsetDateTime

/**
 * Structured error response DTO for REST API error handling.
 *
 * This data class provides a consistent error response format across all API endpoints,
 * including status code, error type, descriptive message, request path, and timestamp.
 *
 * @param status The HTTP status code
 * @param error The HTTP error reason phrase (e.g., "Unsupported Media Type", "Not Found")
 * @param message A descriptive error message
 * @param path The request path that caused the error
 * @param timestamp The time when the error occurred (defaults to current time)
 */
data class ErrorResponse(
    val status: Int,
    val error: String,
    val message: String,
    val path: String,
    val timestamp: OffsetDateTime = OffsetDateTime.now(),
)
