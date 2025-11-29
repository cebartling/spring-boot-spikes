package com.pintailconsultingllc.cqrsspike.product.api.dto

import io.swagger.v3.oas.annotations.media.Schema

/**
 * HATEOAS-style pagination links.
 */
@Schema(description = "Pagination navigation links")
data class PageLinks(
    @Schema(description = "Link to current page", example = "/api/products?page=2&size=20")
    val self: String,

    @Schema(description = "Link to first page", example = "/api/products?page=0&size=20")
    val first: String,

    @Schema(description = "Link to previous page (null if on first page)")
    val prev: String?,

    @Schema(description = "Link to next page (null if on last page)")
    val next: String?,

    @Schema(description = "Link to last page", example = "/api/products?page=5&size=20")
    val last: String
) {
    companion object {
        /**
         * Build pagination links for a given page state.
         */
        fun build(
            basePath: String,
            page: Int,
            size: Int,
            totalPages: Int,
            additionalParams: Map<String, String> = emptyMap()
        ): PageLinks {
            val params = additionalParams.entries
                .filter { it.value.isNotBlank() }
                .joinToString("&") { "${it.key}=${it.value}" }
            val suffix = if (params.isNotBlank()) "&$params" else ""

            return PageLinks(
                self = "$basePath?page=$page&size=$size$suffix",
                first = "$basePath?page=0&size=$size$suffix",
                prev = if (page > 0) "$basePath?page=${page - 1}&size=$size$suffix" else null,
                next = if (page < totalPages - 1) "$basePath?page=${page + 1}&size=$size$suffix" else null,
                last = "$basePath?page=${maxOf(0, totalPages - 1)}&size=$size$suffix"
            )
        }
    }
}
