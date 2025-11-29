package com.pintailconsultingllc.cqrsspike.product.api.dto

import java.time.OffsetDateTime
import java.util.UUID

/**
 * Standard response for successful commands.
 */
data class CommandResponse(
    val productId: UUID,
    val version: Long,
    val status: String,
    val timestamp: OffsetDateTime = OffsetDateTime.now()
)

/**
 * Extended response for product creation.
 */
data class CreateProductResponse(
    val productId: UUID,
    val sku: String,
    val version: Long,
    val status: String,
    val timestamp: OffsetDateTime = OffsetDateTime.now(),
    val links: ProductLinks? = null
)

/**
 * HATEOAS links for product resources.
 */
data class ProductLinks(
    val self: String,
    val update: String,
    val activate: String?,
    val discontinue: String?,
    val delete: String
)

/**
 * Error response for command failures.
 */
data class CommandErrorResponse(
    val status: Int,
    val error: String,
    val message: String,
    val path: String,
    val timestamp: OffsetDateTime = OffsetDateTime.now(),
    val code: String? = null,
    val details: Any? = null
)

/**
 * Error response for concurrent modification conflicts.
 */
data class ConflictErrorResponse(
    val status: Int = 409,
    val error: String = "Conflict",
    val message: String,
    val path: String,
    val timestamp: OffsetDateTime = OffsetDateTime.now(),
    val currentVersion: Long? = null,
    val expectedVersion: Long? = null,
    val code: String = "CONCURRENT_MODIFICATION"
)

/**
 * Error response for price change threshold exceeded.
 */
data class PriceChangeErrorResponse(
    val status: Int = 422,
    val error: String = "Unprocessable Entity",
    val message: String,
    val path: String,
    val timestamp: OffsetDateTime = OffsetDateTime.now(),
    val currentPrice: Int,
    val requestedPrice: Int,
    val changePercentage: Double,
    val threshold: Double,
    val confirmationRequired: Boolean = true,
    val code: String = "PRICE_CHANGE_THRESHOLD_EXCEEDED"
)
