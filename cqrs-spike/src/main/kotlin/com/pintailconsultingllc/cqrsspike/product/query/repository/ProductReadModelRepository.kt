package com.pintailconsultingllc.cqrsspike.product.query.repository

import com.pintailconsultingllc.cqrsspike.product.query.model.ProductReadModel
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Reactive repository for Product read model queries.
 *
 * Provides optimized query methods for common access patterns.
 * All queries exclude soft-deleted products by default.
 */
@Repository
interface ProductReadModelRepository : ReactiveCrudRepository<ProductReadModel, UUID> {

    // ============ Single Product Queries ============

    /**
     * Find a product by ID (excluding deleted).
     */
    @Query("""
        SELECT * FROM read_model.product
        WHERE id = :id AND NOT is_deleted
    """)
    fun findByIdNotDeleted(id: UUID): Mono<ProductReadModel>

    /**
     * Find a product by SKU (excluding deleted).
     */
    @Query("""
        SELECT * FROM read_model.product
        WHERE sku = :sku AND NOT is_deleted
    """)
    fun findBySku(sku: String): Mono<ProductReadModel>

    /**
     * Check if a product exists by ID (excluding deleted).
     */
    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM read_model.product
            WHERE id = :id AND NOT is_deleted
        )
    """)
    fun existsByIdNotDeleted(id: UUID): Mono<Boolean>

    // ============ List Queries with Filtering ============

    /**
     * Find all products by status (excluding deleted).
     */
    @Query("""
        SELECT * FROM read_model.product
        WHERE status = :status AND NOT is_deleted
        ORDER BY name ASC
    """)
    fun findByStatus(status: String): Flux<ProductReadModel>

    /**
     * Find products by status with pagination (offset-based).
     */
    @Query("""
        SELECT * FROM read_model.product
        WHERE status = :status AND NOT is_deleted
        ORDER BY name ASC
        LIMIT :limit OFFSET :offset
    """)
    fun findByStatusPaginated(status: String, limit: Int, offset: Int): Flux<ProductReadModel>

    /**
     * Find all active products (status = ACTIVE).
     */
    @Query("""
        SELECT * FROM read_model.product
        WHERE status = 'ACTIVE' AND NOT is_deleted
        ORDER BY name ASC
    """)
    fun findAllActive(): Flux<ProductReadModel>

    /**
     * Find products within a price range.
     */
    @Query("""
        SELECT * FROM read_model.product
        WHERE price_cents >= :minPrice
        AND price_cents <= :maxPrice
        AND NOT is_deleted
        ORDER BY price_cents ASC
    """)
    fun findByPriceRange(minPrice: Int, maxPrice: Int): Flux<ProductReadModel>

    /**
     * Find products by status and price range.
     */
    @Query("""
        SELECT * FROM read_model.product
        WHERE status = :status
        AND price_cents >= :minPrice
        AND price_cents <= :maxPrice
        AND NOT is_deleted
        ORDER BY price_cents ASC
    """)
    fun findByStatusAndPriceRange(
        status: String,
        minPrice: Int,
        maxPrice: Int
    ): Flux<ProductReadModel>

    // ============ Pagination Queries (Offset-based) ============

    /**
     * Find all products with pagination (offset-based).
     */
    @Query("""
        SELECT * FROM read_model.product
        WHERE NOT is_deleted
        ORDER BY created_at DESC
        LIMIT :limit OFFSET :offset
    """)
    fun findAllPaginated(limit: Int, offset: Int): Flux<ProductReadModel>

    /**
     * Find all products sorted by name ASC with pagination.
     */
    @Query("""
        SELECT * FROM read_model.product
        WHERE NOT is_deleted
        ORDER BY name ASC
        LIMIT :limit OFFSET :offset
    """)
    fun findAllSortedByNameAscPaginated(limit: Int, offset: Int): Flux<ProductReadModel>

    /**
     * Find all products sorted by name DESC with pagination.
     */
    @Query("""
        SELECT * FROM read_model.product
        WHERE NOT is_deleted
        ORDER BY name DESC
        LIMIT :limit OFFSET :offset
    """)
    fun findAllSortedByNameDescPaginated(limit: Int, offset: Int): Flux<ProductReadModel>

    /**
     * Find all products sorted by price ASC with pagination.
     */
    @Query("""
        SELECT * FROM read_model.product
        WHERE NOT is_deleted
        ORDER BY price_cents ASC
        LIMIT :limit OFFSET :offset
    """)
    fun findAllSortedByPriceAscPaginated(limit: Int, offset: Int): Flux<ProductReadModel>

    /**
     * Find all products sorted by price DESC with pagination.
     */
    @Query("""
        SELECT * FROM read_model.product
        WHERE NOT is_deleted
        ORDER BY price_cents DESC
        LIMIT :limit OFFSET :offset
    """)
    fun findAllSortedByPriceDescPaginated(limit: Int, offset: Int): Flux<ProductReadModel>

    /**
     * Find all products sorted by created_at ASC with pagination.
     */
    @Query("""
        SELECT * FROM read_model.product
        WHERE NOT is_deleted
        ORDER BY created_at ASC
        LIMIT :limit OFFSET :offset
    """)
    fun findAllSortedByCreatedAtAscPaginated(limit: Int, offset: Int): Flux<ProductReadModel>

    // ============ Cursor-based Pagination ============

    /**
     * Find products after a cursor (cursor = created_at + id).
     * For cursor-based pagination, sorted by created_at DESC.
     */
    @Query("""
        SELECT * FROM read_model.product
        WHERE NOT is_deleted
        AND (created_at, id) < (:cursorCreatedAt, :cursorId::uuid)
        ORDER BY created_at DESC, id DESC
        LIMIT :limit
    """)
    fun findAfterCursorByCreatedAt(
        cursorCreatedAt: OffsetDateTime,
        cursorId: UUID,
        limit: Int
    ): Flux<ProductReadModel>

    /**
     * Find products after a cursor (cursor = name + id).
     * For cursor-based pagination, sorted by name ASC.
     */
    @Query("""
        SELECT * FROM read_model.product
        WHERE NOT is_deleted
        AND (name, id) > (:cursorName, :cursorId::uuid)
        ORDER BY name ASC, id ASC
        LIMIT :limit
    """)
    fun findAfterCursorByName(
        cursorName: String,
        cursorId: UUID,
        limit: Int
    ): Flux<ProductReadModel>

    /**
     * Find first page (no cursor) sorted by created_at DESC.
     */
    @Query("""
        SELECT * FROM read_model.product
        WHERE NOT is_deleted
        ORDER BY created_at DESC, id DESC
        LIMIT :limit
    """)
    fun findFirstPageByCreatedAt(limit: Int): Flux<ProductReadModel>

    // ============ Search Queries ============

    /**
     * Full-text search on name and description.
     */
    @Query("""
        SELECT * FROM read_model.product
        WHERE NOT is_deleted
        AND to_tsvector('english', coalesce(name, '') || ' ' || coalesce(description, ''))
            @@ plainto_tsquery('english', :searchTerm)
        ORDER BY ts_rank(
            to_tsvector('english', coalesce(name, '') || ' ' || coalesce(description, '')),
            plainto_tsquery('english', :searchTerm)
        ) DESC
        LIMIT :limit
    """)
    fun searchByText(searchTerm: String, limit: Int): Flux<ProductReadModel>

    /**
     * Search products with status filter.
     */
    @Query("""
        SELECT * FROM read_model.product
        WHERE NOT is_deleted
        AND status = :status
        AND to_tsvector('english', coalesce(name, '') || ' ' || coalesce(description, ''))
            @@ plainto_tsquery('english', :searchTerm)
        ORDER BY ts_rank(
            to_tsvector('english', coalesce(name, '') || ' ' || coalesce(description, '')),
            plainto_tsquery('english', :searchTerm)
        ) DESC
        LIMIT :limit
    """)
    fun searchByTextAndStatus(searchTerm: String, status: String, limit: Int): Flux<ProductReadModel>

    /**
     * Like-based search on name (for autocomplete).
     */
    @Query("""
        SELECT * FROM read_model.product
        WHERE NOT is_deleted
        AND LOWER(name) LIKE LOWER(:prefix || '%')
        ORDER BY name ASC
        LIMIT :limit
    """)
    fun findByNameStartingWith(prefix: String, limit: Int): Flux<ProductReadModel>

    // ============ Count Queries ============

    /**
     * Count all non-deleted products.
     */
    @Query("SELECT COUNT(*) FROM read_model.product WHERE NOT is_deleted")
    fun countAllNotDeleted(): Mono<Long>

    /**
     * Count products by status.
     */
    @Query("SELECT COUNT(*) FROM read_model.product WHERE status = :status AND NOT is_deleted")
    fun countByStatus(status: String): Mono<Long>

    /**
     * Count products matching search term.
     */
    @Query("""
        SELECT COUNT(*) FROM read_model.product
        WHERE NOT is_deleted
        AND to_tsvector('english', coalesce(name, '') || ' ' || coalesce(description, ''))
            @@ plainto_tsquery('english', :searchTerm)
    """)
    fun countBySearchTerm(searchTerm: String): Mono<Long>
}
