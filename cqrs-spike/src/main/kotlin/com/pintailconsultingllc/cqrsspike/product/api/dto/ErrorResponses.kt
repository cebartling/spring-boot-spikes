package com.pintailconsultingllc.cqrsspike.product.api.dto

import java.time.OffsetDateTime

/**
 * Standard API error response.
 */
data class ApiErrorResponse(
    val status: Int,
    val error: String,
    val message: String,
    val path: String,
    val timestamp: OffsetDateTime = OffsetDateTime.now(),
    val details: List<ValidationError>? = null
)

/**
 * Validation error detail.
 */
data class ValidationError(
    val field: String,
    val message: String,
    val rejectedValue: Any? = null
)

/**
 * Product not found error response.
 */
data class ProductNotFoundResponse(
    val status: Int = 404,
    val error: String = "Not Found",
    val message: String,
    val productId: String? = null,
    val sku: String? = null,
    val timestamp: OffsetDateTime = OffsetDateTime.now()
)

/**
 * Count response for aggregate queries.
 */
data class ProductCountResponse(
    val count: Long,
    val status: String? = null
)
