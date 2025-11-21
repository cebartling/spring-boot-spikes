package com.pintailconsultingllc.resiliencyspike.dto

import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.*

/**
 * Response DTO for product
 */
data class ProductResponse(
    val id: UUID,
    val sku: String,
    val name: String,
    val description: String?,
    val categoryId: UUID,
    val price: BigDecimal,
    val stockQuantity: Int,
    val isActive: Boolean,
    val metadata: String?,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime
)

/**
 * Request DTO for creating a product
 */
data class CreateProductRequest(
    val sku: String,
    val name: String,
    val description: String? = null,
    val categoryId: UUID,
    val price: BigDecimal,
    val stockQuantity: Int = 0,
    val metadata: String? = null
)

/**
 * Request DTO for updating a product
 */
data class UpdateProductRequest(
    val name: String? = null,
    val description: String? = null,
    val categoryId: UUID? = null,
    val price: BigDecimal? = null,
    val stockQuantity: Int? = null,
    val metadata: String? = null
)

/**
 * Request DTO for updating product stock
 */
data class UpdateProductStockRequest(
    val stockQuantity: Int
)

/**
 * Response DTO for product search results
 */
data class ProductSearchResponse(
    val products: List<ProductResponse>,
    val totalCount: Int
)
