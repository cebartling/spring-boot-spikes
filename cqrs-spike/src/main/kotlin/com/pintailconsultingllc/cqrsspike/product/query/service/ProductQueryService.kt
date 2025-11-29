package com.pintailconsultingllc.cqrsspike.product.query.service

import com.pintailconsultingllc.cqrsspike.product.query.dto.ProductCursor
import com.pintailconsultingllc.cqrsspike.product.query.dto.ProductCursorPageResponse
import com.pintailconsultingllc.cqrsspike.product.query.dto.ProductPageResponse
import com.pintailconsultingllc.cqrsspike.product.query.dto.ProductResponse
import com.pintailconsultingllc.cqrsspike.product.query.dto.ProductSearchResponse
import com.pintailconsultingllc.cqrsspike.product.query.dto.SortDirection
import com.pintailconsultingllc.cqrsspike.product.query.dto.SortField
import com.pintailconsultingllc.cqrsspike.product.query.model.ProductStatusView
import com.pintailconsultingllc.cqrsspike.product.query.repository.ProductReadModelRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Service for Product read model queries.
 *
 * Provides methods for retrieving products from the read model
 * with support for pagination, filtering, and search.
 *
 * AC9 Requirement: "Deleted products are soft-deleted and excluded from queries by default"
 *
 * All query methods in this service automatically exclude soft-deleted products.
 * This is enforced at the repository level where all queries include
 * a `WHERE NOT is_deleted` clause.
 */
@Service
class ProductQueryService(
    private val repository: ProductReadModelRepository
) {
    private val logger = LoggerFactory.getLogger(ProductQueryService::class.java)

    companion object {
        const val DEFAULT_PAGE_SIZE = 20
        const val MAX_PAGE_SIZE = 100
        const val DEFAULT_SEARCH_LIMIT = 50
    }

    // ============ Single Product Queries ============

    /**
     * Find a product by ID.
     *
     * @param id Product UUID
     * @return Mono<ProductResponse> or empty if not found
     */
    fun findById(id: UUID): Mono<ProductResponse> {
        logger.debug("Finding product by id: {}", id)
        return repository.findByIdNotDeleted(id)
            .doOnNext { logger.debug("Found product: id={}, sku={}", id, it.sku) }
            .map { ProductResponse.from(it) }
            .switchIfEmpty(Mono.defer {
                logger.debug("Product not found: id={}", id)
                Mono.empty()
            })
    }

    /**
     * Find a product by SKU.
     *
     * @param sku Product SKU
     * @return Mono<ProductResponse> or empty if not found
     */
    fun findBySku(sku: String): Mono<ProductResponse> {
        logger.debug("Finding product by sku: {}", sku)
        return repository.findBySku(sku.uppercase())
            .map { ProductResponse.from(it) }
    }

    /**
     * Check if a product exists.
     *
     * @param id Product UUID
     * @return Mono<Boolean>
     */
    fun exists(id: UUID): Mono<Boolean> {
        return repository.existsByIdNotDeleted(id)
    }

    // ============ List Queries with Filtering ============

    /**
     * Find all products by status.
     *
     * @param status Product status filter
     * @return Flux<ProductResponse>
     */
    fun findByStatus(status: ProductStatusView): Flux<ProductResponse> {
        logger.debug("Finding products by status: {}", status)
        return repository.findByStatus(status.name)
            .map { ProductResponse.from(it) }
    }

    /**
     * Find all active products.
     *
     * @return Flux<ProductResponse>
     */
    fun findAllActive(): Flux<ProductResponse> {
        return repository.findAllActive()
            .map { ProductResponse.from(it) }
    }

    /**
     * Find products within a price range.
     *
     * @param minPriceCents Minimum price in cents
     * @param maxPriceCents Maximum price in cents
     * @return Flux<ProductResponse>
     */
    fun findByPriceRange(minPriceCents: Int, maxPriceCents: Int): Flux<ProductResponse> {
        require(minPriceCents >= 0) { "Minimum price must be non-negative" }
        require(maxPriceCents >= minPriceCents) { "Maximum price must be >= minimum price" }

        logger.debug("Finding products by price range: {} - {}", minPriceCents, maxPriceCents)
        return repository.findByPriceRange(minPriceCents, maxPriceCents)
            .map { ProductResponse.from(it) }
    }

    /**
     * Find products by status and price range.
     *
     * @param status Product status filter
     * @param minPriceCents Minimum price in cents
     * @param maxPriceCents Maximum price in cents
     * @return Flux<ProductResponse>
     */
    fun findByStatusAndPriceRange(
        status: ProductStatusView,
        minPriceCents: Int,
        maxPriceCents: Int
    ): Flux<ProductResponse> {
        require(minPriceCents >= 0) { "Minimum price must be non-negative" }
        require(maxPriceCents >= minPriceCents) { "Maximum price must be >= minimum price" }

        return repository.findByStatusAndPriceRange(status.name, minPriceCents, maxPriceCents)
            .map { ProductResponse.from(it) }
    }

    // ============ Offset-based Pagination ============

    /**
     * Find all products with offset-based pagination.
     *
     * @param page Page number (0-indexed)
     * @param size Page size
     * @return Mono<ProductPageResponse>
     */
    fun findAllPaginated(page: Int, size: Int): Mono<ProductPageResponse> {
        val validatedPage = maxOf(0, page)
        val validatedSize = minOf(maxOf(1, size), MAX_PAGE_SIZE)
        val offset = validatedPage * validatedSize

        logger.debug("Finding all products paginated: page={}, size={}", validatedPage, validatedSize)

        return Mono.zip(
            repository.findAllPaginated(validatedSize, offset).collectList(),
            repository.countAllNotDeleted()
        ).map { tuple ->
            ProductPageResponse.of(tuple.t1, validatedPage, validatedSize, tuple.t2)
        }
    }

    /**
     * Find products by status with offset-based pagination.
     *
     * @param status Product status filter
     * @param page Page number (0-indexed)
     * @param size Page size
     * @return Mono<ProductPageResponse>
     */
    fun findByStatusPaginated(
        status: ProductStatusView,
        page: Int,
        size: Int
    ): Mono<ProductPageResponse> {
        val validatedPage = maxOf(0, page)
        val validatedSize = minOf(maxOf(1, size), MAX_PAGE_SIZE)
        val offset = validatedPage * validatedSize

        return Mono.zip(
            repository.findByStatusPaginated(status.name, validatedSize, offset).collectList(),
            repository.countByStatus(status.name)
        ).map { tuple ->
            ProductPageResponse.of(tuple.t1, validatedPage, validatedSize, tuple.t2)
        }
    }

    /**
     * Find all products sorted by a field with offset-based pagination.
     *
     * @param page Page number (0-indexed)
     * @param size Page size
     * @param sortField Field to sort by
     * @param direction Sort direction
     * @return Mono<ProductPageResponse>
     */
    fun findAllSortedPaginated(
        page: Int,
        size: Int,
        sortField: SortField,
        direction: SortDirection = SortDirection.ASC
    ): Mono<ProductPageResponse> {
        val validatedPage = maxOf(0, page)
        val validatedSize = minOf(maxOf(1, size), MAX_PAGE_SIZE)
        val offset = validatedPage * validatedSize

        logger.debug(
            "Finding all products sorted: page={}, size={}, sortField={}, direction={}",
            validatedPage, validatedSize, sortField, direction
        )

        val productsFlux = when (sortField) {
            SortField.NAME -> when (direction) {
                SortDirection.ASC -> repository.findAllSortedByNameAscPaginated(validatedSize, offset)
                SortDirection.DESC -> repository.findAllSortedByNameDescPaginated(validatedSize, offset)
            }
            SortField.PRICE -> when (direction) {
                SortDirection.ASC -> repository.findAllSortedByPriceAscPaginated(validatedSize, offset)
                SortDirection.DESC -> repository.findAllSortedByPriceDescPaginated(validatedSize, offset)
            }
            SortField.CREATED_AT -> when (direction) {
                SortDirection.ASC -> repository.findAllSortedByCreatedAtAscPaginated(validatedSize, offset)
                SortDirection.DESC -> repository.findAllPaginated(validatedSize, offset)
            }
        }

        return Mono.zip(
            productsFlux.collectList(),
            repository.countAllNotDeleted()
        ).map { tuple ->
            ProductPageResponse.of(tuple.t1, validatedPage, validatedSize, tuple.t2)
        }
    }

    // ============ Cursor-based Pagination ============

    /**
     * Find products with cursor-based pagination.
     *
     * @param cursor Encoded cursor string (null for first page)
     * @param size Page size
     * @param sortField Field to sort by
     * @return Mono<ProductCursorPageResponse>
     */
    fun findWithCursor(
        cursor: String?,
        size: Int,
        sortField: SortField = SortField.CREATED_AT
    ): Mono<ProductCursorPageResponse> {
        val validatedSize = minOf(maxOf(1, size), MAX_PAGE_SIZE)
        // Request one extra to determine if there's a next page
        val fetchSize = validatedSize + 1

        logger.debug(
            "Finding products with cursor: cursor={}, size={}, sortField={}",
            cursor, validatedSize, sortField
        )

        val productsFlux = if (cursor == null) {
            // First page
            repository.findFirstPageByCreatedAt(fetchSize)
        } else {
            val decodedCursor = ProductCursor.decode(cursor)
            if (decodedCursor == null) {
                logger.warn("Invalid cursor: {}", cursor)
                return Mono.just(
                    ProductCursorPageResponse(
                        content = emptyList(),
                        size = 0,
                        hasNext = false,
                        nextCursor = null
                    )
                )
            }

            when (sortField) {
                SortField.CREATED_AT -> {
                    val cursorTime = OffsetDateTime.parse(decodedCursor.value)
                    repository.findAfterCursorByCreatedAt(cursorTime, decodedCursor.id, fetchSize)
                }

                SortField.NAME -> {
                    repository.findAfterCursorByName(decodedCursor.value, decodedCursor.id, fetchSize)
                }

                SortField.PRICE -> {
                    // For price, we'd need another cursor query method
                    // Defaulting to created_at for now
                    repository.findFirstPageByCreatedAt(fetchSize)
                }
            }
        }

        return productsFlux.collectList()
            .map { products ->
                ProductCursorPageResponse.of(products, validatedSize, sortField)
            }
    }

    // ============ Search Queries ============

    /**
     * Full-text search on products.
     *
     * @param query Search query string
     * @param limit Maximum results to return
     * @return Mono<ProductSearchResponse>
     */
    fun search(query: String, limit: Int = DEFAULT_SEARCH_LIMIT): Mono<ProductSearchResponse> {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isBlank()) {
            return Mono.just(
                ProductSearchResponse(
                    content = emptyList(),
                    query = query,
                    totalMatches = 0,
                    hasMore = false
                )
            )
        }

        val validatedLimit = minOf(maxOf(1, limit), MAX_PAGE_SIZE)
        val fetchLimit = validatedLimit + 1 // Fetch one extra to check for more

        logger.debug("Searching products: query='{}', limit={}", trimmedQuery, validatedLimit)

        return Mono.zip(
            repository.searchByText(trimmedQuery, fetchLimit).collectList(),
            repository.countBySearchTerm(trimmedQuery)
        ).map { tuple ->
            val products = tuple.t1
            val totalMatches = tuple.t2
            val hasMore = products.size > validatedLimit
            val actualProducts = if (hasMore) products.dropLast(1) else products

            ProductSearchResponse(
                content = actualProducts.map { ProductResponse.from(it) },
                query = trimmedQuery,
                totalMatches = totalMatches,
                hasMore = hasMore
            )
        }
    }

    /**
     * Search products with status filter.
     *
     * @param query Search query string
     * @param status Product status filter
     * @param limit Maximum results to return
     * @return Flux<ProductResponse>
     */
    fun searchByStatus(
        query: String,
        status: ProductStatusView,
        limit: Int = DEFAULT_SEARCH_LIMIT
    ): Flux<ProductResponse> {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isBlank()) {
            return Flux.empty()
        }

        val validatedLimit = minOf(maxOf(1, limit), MAX_PAGE_SIZE)
        return repository.searchByTextAndStatus(trimmedQuery, status.name, validatedLimit)
            .map { ProductResponse.from(it) }
    }

    /**
     * Autocomplete search by name prefix.
     *
     * @param prefix Name prefix to search for
     * @param limit Maximum results to return
     * @return Flux<ProductResponse>
     */
    fun autocomplete(prefix: String, limit: Int = 10): Flux<ProductResponse> {
        val trimmedPrefix = prefix.trim()
        if (trimmedPrefix.isBlank()) {
            return Flux.empty()
        }

        return repository.findByNameStartingWith(trimmedPrefix, limit)
            .map { ProductResponse.from(it) }
    }

    // ============ Aggregate Queries ============

    /**
     * Get total count of products.
     *
     * @return Mono<Long>
     */
    fun count(): Mono<Long> {
        return repository.countAllNotDeleted()
    }

    /**
     * Get count of products by status.
     *
     * @param status Product status filter
     * @return Mono<Long>
     */
    fun countByStatus(status: ProductStatusView): Mono<Long> {
        return repository.countByStatus(status.name)
    }
}
