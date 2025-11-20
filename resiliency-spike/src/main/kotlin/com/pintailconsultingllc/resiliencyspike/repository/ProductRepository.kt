package com.pintailconsultingllc.resiliencyspike.repository

import com.pintailconsultingllc.resiliencyspike.domain.Product
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.util.*

/**
 * Reactive repository for Product entities
 */
@Repository
interface ProductRepository : ReactiveCrudRepository<Product, UUID> {

    /**
     * Find a product by its SKU
     */
    fun findBySku(sku: String): Mono<Product>

    /**
     * Find all products by category ID
     */
    fun findByCategoryId(categoryId: UUID): Flux<Product>

    /**
     * Find all active products
     */
    fun findByIsActive(isActive: Boolean): Flux<Product>

    /**
     * Find all active products by category ID
     */
    fun findByCategoryIdAndIsActive(categoryId: UUID, isActive: Boolean): Flux<Product>

    /**
     * Search products by name (case-insensitive partial match)
     */
    @Query("SELECT * FROM products WHERE LOWER(name) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    fun searchByName(searchTerm: String): Flux<Product>

    /**
     * Find products within a price range
     */
    @Query("SELECT * FROM products WHERE price >= :minPrice AND price <= :maxPrice ORDER BY price ASC")
    fun findByPriceRange(minPrice: BigDecimal, maxPrice: BigDecimal): Flux<Product>

    /**
     * Find products with low stock (below specified threshold)
     */
    @Query("SELECT * FROM products WHERE stock_quantity < :threshold AND is_active = true ORDER BY stock_quantity ASC")
    fun findLowStockProducts(threshold: Int): Flux<Product>

    /**
     * Find products by category and price range
     */
    @Query("SELECT * FROM products WHERE category_id = :categoryId AND price >= :minPrice AND price <= :maxPrice ORDER BY price ASC")
    fun findByCategoryAndPriceRange(categoryId: UUID, minPrice: BigDecimal, maxPrice: BigDecimal): Flux<Product>

    /**
     * Count products by category
     */
    fun countByCategoryId(categoryId: UUID): Mono<Long>

    /**
     * Count active products
     */
    fun countByIsActive(isActive: Boolean): Mono<Long>
}
