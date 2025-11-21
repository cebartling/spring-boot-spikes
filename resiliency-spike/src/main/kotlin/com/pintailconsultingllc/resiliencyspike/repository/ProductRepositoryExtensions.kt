package com.pintailconsultingllc.resiliencyspike.repository

import com.pintailconsultingllc.resiliencyspike.domain.Product
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

/**
 * Extension methods for ProductRepository to load relationships
 */
interface ProductRepositoryExtensions {

    /**
     * Find a product by ID with its category loaded
     */
    fun findByIdWithCategory(productId: UUID): Mono<Product>

    /**
     * Find a product by SKU with its category loaded
     */
    fun findBySkuWithCategory(sku: String): Mono<Product>

    /**
     * Find all products by category with category details loaded
     */
    fun findByCategoryIdWithCategory(categoryId: UUID): Flux<Product>

    /**
     * Find all active products with category loaded
     */
    fun findActiveProductsWithCategory(): Flux<Product>
}
