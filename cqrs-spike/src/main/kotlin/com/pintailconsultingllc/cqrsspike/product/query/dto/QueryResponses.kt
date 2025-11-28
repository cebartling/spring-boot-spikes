package com.pintailconsultingllc.cqrsspike.product.query.dto

import com.pintailconsultingllc.cqrsspike.product.query.model.ProductReadModel
import java.time.OffsetDateTime
import java.util.Base64
import java.util.UUID

/**
 * DTO for single product response.
 */
data class ProductResponse(
    val id: UUID,
    val sku: String,
    val name: String,
    val description: String?,
    val priceCents: Int,
    val priceDisplay: String,
    val status: String,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
    val version: Long
) {
    companion object {
        fun from(model: ProductReadModel): ProductResponse {
            return ProductResponse(
                id = model.id,
                sku = model.sku,
                name = model.name,
                description = model.description,
                priceCents = model.priceCents,
                priceDisplay = model.priceDisplay ?: formatPrice(model.priceCents),
                status = model.status,
                createdAt = model.createdAt,
                updatedAt = model.updatedAt,
                version = model.version
            )
        }

        private fun formatPrice(cents: Int): String {
            val dollars = cents / 100
            val remainingCents = cents % 100
            return "$${dollars}.${remainingCents.toString().padStart(2, '0')}"
        }
    }
}

/**
 * DTO for paginated product list response (offset-based).
 */
data class ProductPageResponse(
    val content: List<ProductResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val first: Boolean,
    val last: Boolean,
    val hasNext: Boolean,
    val hasPrevious: Boolean
) {
    companion object {
        fun of(
            products: List<ProductReadModel>,
            page: Int,
            size: Int,
            totalElements: Long
        ): ProductPageResponse {
            val totalPages = if (totalElements == 0L) 1 else ((totalElements + size - 1) / size).toInt()
            return ProductPageResponse(
                content = products.map { ProductResponse.from(it) },
                page = page,
                size = size,
                totalElements = totalElements,
                totalPages = totalPages,
                first = page == 0,
                last = page >= totalPages - 1,
                hasNext = page < totalPages - 1,
                hasPrevious = page > 0
            )
        }
    }
}

/**
 * Cursor for cursor-based pagination.
 */
data class ProductCursor(
    val value: String,
    val id: UUID
) {
    fun encode(): String {
        return Base64.getEncoder()
            .encodeToString("$value|$id".toByteArray())
    }

    companion object {
        fun decode(encoded: String): ProductCursor? {
            return try {
                val decoded = String(Base64.getDecoder().decode(encoded))
                val parts = decoded.split("|")
                if (parts.size == 2) {
                    ProductCursor(parts[0], UUID.fromString(parts[1]))
                } else null
            } catch (e: Exception) {
                null
            }
        }

        fun fromProduct(product: ProductReadModel, sortField: SortField): ProductCursor {
            val value = when (sortField) {
                SortField.CREATED_AT -> product.createdAt.toString()
                SortField.NAME -> product.name
                SortField.PRICE -> product.priceCents.toString()
            }
            return ProductCursor(value, product.id)
        }
    }
}

/**
 * Supported sort fields for pagination.
 */
enum class SortField {
    CREATED_AT,
    NAME,
    PRICE
}

/**
 * Sort direction.
 */
enum class SortDirection {
    ASC,
    DESC
}

/**
 * DTO for cursor-based paginated product list response.
 */
data class ProductCursorPageResponse(
    val content: List<ProductResponse>,
    val size: Int,
    val hasNext: Boolean,
    val nextCursor: String?,
    val totalElements: Long? = null
) {
    companion object {
        fun of(
            products: List<ProductReadModel>,
            requestedSize: Int,
            sortField: SortField,
            totalElements: Long? = null
        ): ProductCursorPageResponse {
            val hasNext = products.size > requestedSize
            val actualProducts = if (hasNext) products.dropLast(1) else products
            val nextCursor = if (hasNext && actualProducts.isNotEmpty()) {
                ProductCursor.fromProduct(actualProducts.last(), sortField).encode()
            } else null

            return ProductCursorPageResponse(
                content = actualProducts.map { ProductResponse.from(it) },
                size = actualProducts.size,
                hasNext = hasNext,
                nextCursor = nextCursor,
                totalElements = totalElements
            )
        }
    }
}

/**
 * DTO for search results response.
 */
data class ProductSearchResponse(
    val content: List<ProductResponse>,
    val query: String,
    val totalMatches: Long,
    val hasMore: Boolean
)
