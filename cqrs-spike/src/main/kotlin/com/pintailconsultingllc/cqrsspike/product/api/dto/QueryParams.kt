package com.pintailconsultingllc.cqrsspike.product.api.dto

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

/**
 * Query parameters for paginated product listing.
 */
data class ProductListParams(
    @field:Min(0, message = "Page must be >= 0")
    val page: Int = 0,

    @field:Min(1, message = "Size must be >= 1")
    @field:Max(100, message = "Size must be <= 100")
    val size: Int = 20,

    @field:Pattern(regexp = "^(DRAFT|ACTIVE|DISCONTINUED)$", message = "Invalid status")
    val status: String? = null,

    @field:Min(0, message = "minPrice must be >= 0")
    val minPrice: Int? = null,

    @field:Min(0, message = "maxPrice must be >= 0")
    val maxPrice: Int? = null,

    @field:Pattern(regexp = "^(name|price|createdAt)$", message = "Invalid sort field")
    val sort: String? = null,

    @field:Pattern(regexp = "^(asc|desc)$", message = "Invalid direction")
    val direction: String? = null
)

/**
 * Query parameters for cursor-based pagination.
 */
data class ProductCursorParams(
    val cursor: String? = null,

    @field:Min(1, message = "Size must be >= 1")
    @field:Max(100, message = "Size must be <= 100")
    val size: Int = 20,

    @field:Pattern(regexp = "^(name|price|createdAt)$", message = "Invalid sort field")
    val sort: String? = null
)

/**
 * Query parameters for product search.
 */
data class ProductSearchParams(
    @field:Size(min = 1, max = 500, message = "Query must be 1-500 characters")
    val q: String,

    @field:Min(1, message = "Limit must be >= 1")
    @field:Max(100, message = "Limit must be <= 100")
    val limit: Int = 50,

    @field:Pattern(regexp = "^(DRAFT|ACTIVE|DISCONTINUED)$", message = "Invalid status")
    val status: String? = null
)

/**
 * Query parameters for autocomplete.
 */
data class AutocompleteParams(
    @field:Size(min = 1, max = 100, message = "Prefix must be 1-100 characters")
    val prefix: String,

    @field:Min(1, message = "Limit must be >= 1")
    @field:Max(20, message = "Limit must be <= 20")
    val limit: Int = 10
)
