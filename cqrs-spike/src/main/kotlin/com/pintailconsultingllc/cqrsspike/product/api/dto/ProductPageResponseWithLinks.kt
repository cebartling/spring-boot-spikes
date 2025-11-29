package com.pintailconsultingllc.cqrsspike.product.api.dto

import com.pintailconsultingllc.cqrsspike.product.query.dto.ProductPageResponse
import com.pintailconsultingllc.cqrsspike.product.query.dto.ProductResponse
import io.swagger.v3.oas.annotations.media.Schema

/**
 * Extended page response with HATEOAS-style navigation links.
 */
@Schema(description = "Paginated product list with navigation links")
data class ProductPageResponseWithLinks(
    @Schema(description = "List of products on current page")
    val content: List<ProductResponse>,

    @Schema(description = "Current page number (0-indexed)", example = "0")
    val page: Int,

    @Schema(description = "Page size", example = "20")
    val size: Int,

    @Schema(description = "Total number of products", example = "150")
    val totalElements: Long,

    @Schema(description = "Total number of pages", example = "8")
    val totalPages: Int,

    @Schema(description = "Whether this is the first page")
    val first: Boolean,

    @Schema(description = "Whether this is the last page")
    val last: Boolean,

    @Schema(description = "Whether there is a next page")
    val hasNext: Boolean,

    @Schema(description = "Whether there is a previous page")
    val hasPrevious: Boolean,

    @Schema(description = "Navigation links")
    val links: PageLinks?
) {
    companion object {
        fun from(
            response: ProductPageResponse,
            basePath: String,
            additionalParams: Map<String, String> = emptyMap()
        ): ProductPageResponseWithLinks {
            return ProductPageResponseWithLinks(
                content = response.content,
                page = response.page,
                size = response.size,
                totalElements = response.totalElements,
                totalPages = response.totalPages,
                first = response.first,
                last = response.last,
                hasNext = response.hasNext,
                hasPrevious = response.hasPrevious,
                links = PageLinks.build(
                    basePath = basePath,
                    page = response.page,
                    size = response.size,
                    totalPages = response.totalPages,
                    additionalParams = additionalParams
                )
            )
        }
    }
}
