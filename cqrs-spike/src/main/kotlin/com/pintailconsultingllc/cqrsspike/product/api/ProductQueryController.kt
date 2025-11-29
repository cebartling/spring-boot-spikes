package com.pintailconsultingllc.cqrsspike.product.api

import com.pintailconsultingllc.cqrsspike.product.api.dto.ApiErrorResponse
import com.pintailconsultingllc.cqrsspike.product.api.dto.ProductCountResponse
import com.pintailconsultingllc.cqrsspike.product.api.dto.ProductPageResponseWithLinks
import com.pintailconsultingllc.cqrsspike.product.query.dto.ProductCursorPageResponse
import com.pintailconsultingllc.cqrsspike.product.query.dto.ProductPageResponse
import com.pintailconsultingllc.cqrsspike.product.query.dto.ProductResponse
import com.pintailconsultingllc.cqrsspike.product.query.dto.ProductSearchResponse
import com.pintailconsultingllc.cqrsspike.product.query.dto.SortDirection
import com.pintailconsultingllc.cqrsspike.product.query.dto.SortField
import com.pintailconsultingllc.cqrsspike.product.query.model.ProductStatusView
import com.pintailconsultingllc.cqrsspike.product.query.service.ProductQueryService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.slf4j.LoggerFactory
import org.springframework.http.CacheControl
import org.springframework.http.MediaType
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
@RequestMapping("/api/products", produces = [MediaType.APPLICATION_JSON_VALUE])
@Validated
@Tag(name = "Product Queries", description = "Endpoints for querying products from the read model")
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
    @Operation(
        summary = "Get product by ID",
        description = "Retrieves a single product by its unique identifier (UUID)"
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Product found",
            content = [Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = Schema(implementation = ProductResponse::class)
            )]
        ),
        ApiResponse(
            responseCode = "400",
            description = "Invalid product ID format",
            content = [Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = Schema(implementation = ApiErrorResponse::class)
            )]
        ),
        ApiResponse(
            responseCode = "404",
            description = "Product not found",
            content = [Content()]
        )
    )
    fun getProductById(
        @Parameter(description = "Product UUID", required = true, example = "550e8400-e29b-41d4-a716-446655440000")
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
    @Operation(
        summary = "Get product by SKU",
        description = "Retrieves a single product by its Stock Keeping Unit (SKU)"
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Product found",
            content = [Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = Schema(implementation = ProductResponse::class)
            )]
        ),
        ApiResponse(
            responseCode = "400",
            description = "Invalid SKU format",
            content = [Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = Schema(implementation = ApiErrorResponse::class)
            )]
        ),
        ApiResponse(
            responseCode = "404",
            description = "Product not found",
            content = [Content()]
        )
    )
    fun getProductBySku(
        @Parameter(description = "Product SKU", required = true, example = "PROD-001")
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
    @Operation(
        summary = "List products",
        description = """
            Retrieves a paginated list of products with optional filtering and sorting.

            **Filtering:**
            - By status: DRAFT, ACTIVE, DISCONTINUED
            - By price range: minPrice and maxPrice (in cents)

            **Sorting:**
            - Fields: name, price, createdAt
            - Directions: asc, desc
        """
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Products retrieved successfully",
            content = [Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = Schema(implementation = ProductPageResponseWithLinks::class)
            )]
        ),
        ApiResponse(
            responseCode = "400",
            description = "Invalid query parameters",
            content = [Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = Schema(implementation = ApiErrorResponse::class)
            )]
        )
    )
    fun listProducts(
        @Parameter(description = "Page number (0-indexed)", example = "0")
        @RequestParam(defaultValue = "0")
        @Min(0) page: Int,

        @Parameter(description = "Page size (1-100)", example = "20")
        @RequestParam(defaultValue = "20")
        @Min(1) @Max(100) size: Int,

        @Parameter(description = "Filter by status", example = "ACTIVE")
        @RequestParam(required = false)
        @Pattern(regexp = "^(DRAFT|ACTIVE|DISCONTINUED)?$") status: String?,

        @Parameter(description = "Minimum price in cents", example = "1000")
        @RequestParam(required = false)
        @Min(0) minPrice: Int?,

        @Parameter(description = "Maximum price in cents", example = "5000")
        @RequestParam(required = false)
        @Min(0) maxPrice: Int?,

        @Parameter(description = "Sort field", example = "createdAt")
        @RequestParam(defaultValue = "createdAt")
        @Pattern(regexp = "^(name|price|createdAt)$") sort: String,

        @Parameter(description = "Sort direction", example = "desc")
        @RequestParam(defaultValue = "desc")
        @Pattern(regexp = "^(asc|desc)$") direction: String
    ): Mono<ResponseEntity<ProductPageResponseWithLinks>> {
        logger.debug(
            "GET /api/products - page={}, size={}, status={}, minPrice={}, maxPrice={}, sort={}, direction={}",
            page, size, status, minPrice, maxPrice, sort, direction
        )

        val sortField = parseSortField(sort)
        val sortDirection = parseSortDirection(direction)

        // Build additional params for pagination links
        val additionalParams = buildMap {
            status?.let { put("status", it) }
            minPrice?.let { put("minPrice", it.toString()) }
            maxPrice?.let { put("maxPrice", it.toString()) }
            put("sort", sort)
            put("direction", direction)
        }

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
            val responseWithLinks = ProductPageResponseWithLinks.from(
                response,
                "/api/products",
                additionalParams
            )
            ResponseEntity.ok()
                .cacheControl(DEFAULT_CACHE_CONTROL)
                .body(responseWithLinks)
        }
    }

    // ============ Cursor Pagination Endpoint ============

    /**
     * List products with cursor-based pagination.
     */
    @GetMapping("/cursor")
    @Operation(
        summary = "List products with cursor pagination",
        description = """
            Retrieves products using cursor-based pagination. More efficient for large datasets
            as it avoids the offset counting overhead of traditional pagination.

            Use the `nextCursor` from the response to fetch the next page.
        """
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Products retrieved successfully",
            content = [Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = Schema(implementation = ProductCursorPageResponse::class)
            )]
        ),
        ApiResponse(
            responseCode = "400",
            description = "Invalid query parameters",
            content = [Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = Schema(implementation = ApiErrorResponse::class)
            )]
        )
    )
    fun listProductsWithCursor(
        @Parameter(description = "Cursor from previous response for pagination", example = "dGVzdC1jdXJzb3I=")
        @RequestParam(required = false) cursor: String?,

        @Parameter(description = "Page size (1-100)", example = "20")
        @RequestParam(defaultValue = "20")
        @Min(1) @Max(100) size: Int,

        @Parameter(description = "Sort field", example = "createdAt")
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
    @Operation(
        summary = "Get products by status",
        description = "Retrieves paginated products filtered by status"
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Products retrieved successfully",
            content = [Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = Schema(implementation = ProductPageResponseWithLinks::class)
            )]
        ),
        ApiResponse(
            responseCode = "400",
            description = "Invalid status value",
            content = [Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = Schema(implementation = ApiErrorResponse::class)
            )]
        )
    )
    fun getProductsByStatus(
        @Parameter(description = "Product status", required = true, example = "ACTIVE")
        @PathVariable
        @Pattern(regexp = "^(DRAFT|ACTIVE|DISCONTINUED)$") status: String,

        @Parameter(description = "Page number (0-indexed)", example = "0")
        @RequestParam(defaultValue = "0")
        @Min(0) page: Int,

        @Parameter(description = "Page size (1-100)", example = "20")
        @RequestParam(defaultValue = "20")
        @Min(1) @Max(100) size: Int
    ): Mono<ResponseEntity<ProductPageResponseWithLinks>> {
        logger.debug("GET /api/products/by-status/{} - page={}, size={}", status, page, size)

        val statusEnum = ProductStatusView.valueOf(status)

        return queryService.findByStatusPaginated(statusEnum, page, size)
            .map { response ->
                val responseWithLinks = ProductPageResponseWithLinks.from(
                    response,
                    "/api/products/by-status/$status",
                    emptyMap()
                )
                ResponseEntity.ok()
                    .cacheControl(DEFAULT_CACHE_CONTROL)
                    .body(responseWithLinks)
            }
    }

    // ============ Search Endpoints ============

    /**
     * Full-text search on products.
     */
    @GetMapping("/search")
    @Operation(
        summary = "Search products",
        description = """
            Full-text search on product name and description.
            Uses PostgreSQL full-text search for efficient matching.
            Results are ranked by relevance.
        """
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Search results",
            content = [Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = Schema(implementation = ProductSearchResponse::class)
            )]
        ),
        ApiResponse(
            responseCode = "400",
            description = "Invalid search parameters",
            content = [Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = Schema(implementation = ApiErrorResponse::class)
            )]
        )
    )
    fun searchProducts(
        @Parameter(description = "Search query", required = true, example = "widget")
        @RequestParam
        @Size(min = 1, max = 500) q: String,

        @Parameter(description = "Maximum results (1-100)", example = "50")
        @RequestParam(defaultValue = "50")
        @Min(1) @Max(100) limit: Int,

        @Parameter(description = "Filter results by status", example = "ACTIVE")
        @RequestParam(required = false)
        @Pattern(regexp = "^(DRAFT|ACTIVE|DISCONTINUED)?$") status: String?
    ): Mono<ResponseEntity<ProductSearchResponse>> {
        logger.debug("GET /api/products/search - q='{}', limit={}, status={}", q, limit, status)

        return if (status != null) {
            val statusEnum = ProductStatusView.valueOf(status)
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
    @Operation(
        summary = "Autocomplete product names",
        description = "Returns product suggestions based on name prefix for autocomplete functionality"
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Autocomplete suggestions",
            content = [Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = Schema(implementation = ProductResponse::class, type = "array")
            )]
        ),
        ApiResponse(
            responseCode = "400",
            description = "Invalid parameters",
            content = [Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = Schema(implementation = ApiErrorResponse::class)
            )]
        )
    )
    fun autocomplete(
        @Parameter(description = "Name prefix to search for", required = true, example = "Wid")
        @RequestParam
        @Size(min = 1, max = 100) prefix: String,

        @Parameter(description = "Maximum suggestions (1-20)", example = "10")
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
    @Operation(
        summary = "Get product count",
        description = "Returns the total number of products, optionally filtered by status"
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Product count",
            content = [Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = Schema(implementation = ProductCountResponse::class)
            )]
        ),
        ApiResponse(
            responseCode = "400",
            description = "Invalid status value",
            content = [Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = Schema(implementation = ApiErrorResponse::class)
            )]
        )
    )
    fun countProducts(
        @Parameter(description = "Filter count by status", example = "ACTIVE")
        @RequestParam(required = false)
        @Pattern(regexp = "^(DRAFT|ACTIVE|DISCONTINUED)$") status: String?
    ): Mono<ResponseEntity<ProductCountResponse>> {
        logger.debug("GET /api/products/count - status={}", status)

        return if (status != null) {
            val statusEnum = ProductStatusView.valueOf(status)
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
        val totalPages = if (totalElements == 0L) 0 else ((totalElements + size - 1) / size).toInt()
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
