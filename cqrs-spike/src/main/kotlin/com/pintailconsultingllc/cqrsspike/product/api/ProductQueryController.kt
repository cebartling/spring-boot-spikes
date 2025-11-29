package com.pintailconsultingllc.cqrsspike.product.api

import com.pintailconsultingllc.cqrsspike.product.api.dto.ProductCountResponse
import com.pintailconsultingllc.cqrsspike.product.query.dto.ProductCursorPageResponse
import com.pintailconsultingllc.cqrsspike.product.query.dto.ProductPageResponse
import com.pintailconsultingllc.cqrsspike.product.query.dto.ProductResponse
import com.pintailconsultingllc.cqrsspike.product.query.dto.ProductSearchResponse
import com.pintailconsultingllc.cqrsspike.product.query.dto.SortDirection
import com.pintailconsultingllc.cqrsspike.product.query.dto.SortField
import com.pintailconsultingllc.cqrsspike.product.query.model.ProductStatusView
import com.pintailconsultingllc.cqrsspike.product.query.service.ProductQueryService
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.slf4j.LoggerFactory
import org.springframework.http.CacheControl
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.UUID

/**
 * REST controller for Product query operations.
 *
 * Provides endpoints for retrieving products from the read model
 * with support for pagination, filtering, sorting, and search.
 */
@RestController
@RequestMapping("/api/products")
@Validated
class ProductQueryController(
    private val queryService: ProductQueryService
) {
    private val logger = LoggerFactory.getLogger(ProductQueryController::class.java)

    companion object {
        private val DEFAULT_CACHE_CONTROL = CacheControl.maxAge(Duration.ofSeconds(60))
        private val NO_CACHE = CacheControl.noCache()
    }

    // ============ Single Product Endpoints ============

    /**
     * Get a product by ID.
     */
    @GetMapping("/{id}")
    fun getProductById(
        @PathVariable id: UUID
    ): Mono<ResponseEntity<ProductResponse>> {
        logger.debug("GET /api/products/{}", id)

        return queryService.findById(id)
            .map { product ->
                ResponseEntity.ok()
                    .cacheControl(DEFAULT_CACHE_CONTROL)
                    .body(product)
            }
            .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()))
    }

    /**
     * Get a product by SKU.
     */
    @GetMapping("/sku/{sku}")
    fun getProductBySku(
        @PathVariable
        @Size(min = 3, max = 50)
        sku: String
    ): Mono<ResponseEntity<ProductResponse>> {
        logger.debug("GET /api/products/sku/{}", sku)

        return queryService.findBySku(sku)
            .map { product ->
                ResponseEntity.ok()
                    .cacheControl(DEFAULT_CACHE_CONTROL)
                    .body(product)
            }
            .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()))
    }

    // ============ List Endpoints (Offset Pagination) ============

    /**
     * List products with offset-based pagination and optional filters.
     */
    @GetMapping
    fun listProducts(
        @RequestParam(defaultValue = "0")
        @Min(0) page: Int,

        @RequestParam(defaultValue = "20")
        @Min(1) @Max(100) size: Int,

        @RequestParam(required = false)
        @Pattern(regexp = "^(DRAFT|ACTIVE|DISCONTINUED)?$") status: String?,

        @RequestParam(required = false)
        @Min(0) minPrice: Int?,

        @RequestParam(required = false)
        @Min(0) maxPrice: Int?,

        @RequestParam(defaultValue = "createdAt")
        @Pattern(regexp = "^(name|price|createdAt)$") sort: String,

        @RequestParam(defaultValue = "desc")
        @Pattern(regexp = "^(asc|desc)$") direction: String
    ): Mono<ResponseEntity<ProductPageResponse>> {
        logger.debug(
            "GET /api/products - page={}, size={}, status={}, minPrice={}, maxPrice={}, sort={}, direction={}",
            page, size, status, minPrice, maxPrice, sort, direction
        )

        val sortField = parseSortField(sort)
        val sortDirection = parseSortDirection(direction)

        return when {
            // Filter by status AND price range
            status != null && minPrice != null && maxPrice != null -> {
                val statusEnum = ProductStatusView.valueOf(status)
                queryService.findByStatusAndPriceRange(statusEnum, minPrice, maxPrice)
                    .collectList()
                    .map { products ->
                        val paged = paginateList(products, page, size)
                        createPageResponse(paged, page, size, products.size.toLong())
                    }
            }

            // Filter by status only
            status != null -> {
                val statusEnum = ProductStatusView.valueOf(status)
                queryService.findByStatusPaginated(statusEnum, page, size)
            }

            // Filter by price range only
            minPrice != null && maxPrice != null -> {
                queryService.findByPriceRange(minPrice, maxPrice)
                    .collectList()
                    .map { products ->
                        val paged = paginateList(products, page, size)
                        createPageResponse(paged, page, size, products.size.toLong())
                    }
            }

            // No filters - sorted pagination
            else -> {
                queryService.findAllSortedPaginated(page, size, sortField, sortDirection)
            }
        }.map { response ->
            ResponseEntity.ok()
                .cacheControl(DEFAULT_CACHE_CONTROL)
                .body(response)
        }
    }

    // ============ Cursor Pagination Endpoint ============

    /**
     * List products with cursor-based pagination.
     */
    @GetMapping("/cursor")
    fun listProductsWithCursor(
        @RequestParam(required = false) cursor: String?,

        @RequestParam(defaultValue = "20")
        @Min(1) @Max(100) size: Int,

        @RequestParam(defaultValue = "createdAt")
        @Pattern(regexp = "^(name|price|createdAt)$") sort: String
    ): Mono<ResponseEntity<ProductCursorPageResponse>> {
        logger.debug("GET /api/products/cursor - cursor={}, size={}, sort={}", cursor, size, sort)

        val sortField = parseSortField(sort)

        return queryService.findWithCursor(cursor, size, sortField)
            .map { response ->
                ResponseEntity.ok()
                    .cacheControl(DEFAULT_CACHE_CONTROL)
                    .body(response)
            }
    }

    // ============ Status Filter Endpoint ============

    /**
     * Get products by status with pagination.
     */
    @GetMapping("/by-status/{status}")
    fun getProductsByStatus(
        @PathVariable
        @Pattern(regexp = "^(DRAFT|ACTIVE|DISCONTINUED)$") status: String,

        @RequestParam(defaultValue = "0")
        @Min(0) page: Int,

        @RequestParam(defaultValue = "20")
        @Min(1) @Max(100) size: Int
    ): Mono<ResponseEntity<ProductPageResponse>> {
        logger.debug("GET /api/products/by-status/{} - page={}, size={}", status, page, size)

        val statusEnum = ProductStatusView.valueOf(status.uppercase())

        return queryService.findByStatusPaginated(statusEnum, page, size)
            .map { response ->
                ResponseEntity.ok()
                    .cacheControl(DEFAULT_CACHE_CONTROL)
                    .body(response)
            }
    }

    // ============ Search Endpoints ============

    /**
     * Full-text search on products.
     */
    @GetMapping("/search")
    fun searchProducts(
        @RequestParam
        @Size(min = 1, max = 500) q: String,

        @RequestParam(defaultValue = "50")
        @Min(1) @Max(100) limit: Int,

        @RequestParam(required = false)
        @Pattern(regexp = "^(DRAFT|ACTIVE|DISCONTINUED)?$") status: String?
    ): Mono<ResponseEntity<ProductSearchResponse>> {
        logger.debug("GET /api/products/search - q='{}', limit={}, status={}", q, limit, status)

        return if (status != null) {
            val statusEnum = ProductStatusView.valueOf(status.uppercase())
            queryService.searchByStatus(q, statusEnum, limit)
                .collectList()
                .map { results ->
                    ProductSearchResponse(
                        content = results,
                        query = q,
                        totalMatches = results.size.toLong(),
                        hasMore = false
                    )
                }
        } else {
            queryService.search(q, limit)
        }.map { response ->
            ResponseEntity.ok()
                .cacheControl(NO_CACHE) // Search results should not be cached
                .body(response)
        }
    }

    /**
     * Autocomplete search by name prefix.
     */
    @GetMapping("/autocomplete")
    fun autocomplete(
        @RequestParam
        @Size(min = 1, max = 100) prefix: String,

        @RequestParam(defaultValue = "10")
        @Min(1) @Max(20) limit: Int
    ): Mono<ResponseEntity<List<ProductResponse>>> {
        logger.debug("GET /api/products/autocomplete - prefix='{}', limit={}", prefix, limit)

        return queryService.autocomplete(prefix, limit)
            .collectList()
            .map { suggestions ->
                ResponseEntity.ok()
                    .cacheControl(CacheControl.maxAge(Duration.ofSeconds(30)))
                    .body(suggestions)
            }
    }

    // ============ Aggregate Endpoints ============

    /**
     * Get product count.
     */
    @GetMapping("/count")
    fun countProducts(
        @RequestParam(required = false)
        @Pattern(regexp = "^(DRAFT|ACTIVE|DISCONTINUED)?$") status: String?
    ): Mono<ResponseEntity<ProductCountResponse>> {
        logger.debug("GET /api/products/count - status={}", status)

        return if (status != null) {
            val statusEnum = ProductStatusView.valueOf(status.uppercase())
            queryService.countByStatus(statusEnum)
                .map { count -> ProductCountResponse(count, status) }
        } else {
            queryService.count()
                .map { count -> ProductCountResponse(count) }
        }.map { response ->
            ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofSeconds(10)))
                .body(response)
        }
    }

    // ============ Helper Methods ============

    private fun parseSortField(sort: String): SortField {
        return when (sort.lowercase()) {
            "name" -> SortField.NAME
            "price" -> SortField.PRICE
            "createdat", "created_at" -> SortField.CREATED_AT
            else -> SortField.CREATED_AT
        }
    }

    private fun parseSortDirection(direction: String): SortDirection {
        return when (direction.lowercase()) {
            "asc" -> SortDirection.ASC
            "desc" -> SortDirection.DESC
            else -> SortDirection.DESC
        }
    }

    private fun <T> paginateList(list: List<T>, page: Int, size: Int): List<T> {
        val startIndex = page * size
        if (startIndex >= list.size) {
            return emptyList()
        }
        val endIndex = minOf(startIndex + size, list.size)
        return list.subList(startIndex, endIndex)
    }

    private fun createPageResponse(
        content: List<ProductResponse>,
        page: Int,
        size: Int,
        totalElements: Long
    ): ProductPageResponse {
        val totalPages = if (totalElements == 0L) 1 else ((totalElements + size - 1) / size).toInt()
        return ProductPageResponse(
            content = content,
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
