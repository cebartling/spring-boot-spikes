package com.pintailconsultingllc.resiliencyspike.service

import com.pintailconsultingllc.resiliencyspike.domain.Product
import com.pintailconsultingllc.resiliencyspike.repository.ProductRepositoryExtensionsImpl
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

/**
 * Service extensions for working with products and their category relationships
 */
@Service
class ProductServiceExtensions(
    private val productRepositoryExtensions: ProductRepositoryExtensionsImpl
) {

    /**
     * Find a product by ID with its category loaded
     */
    fun findProductWithCategory(productId: UUID): Mono<Product> {
        return productRepositoryExtensions.findByIdWithCategory(productId)
    }

    /**
     * Find a product by SKU with its category loaded
     */
    fun findProductBySkuWithCategory(sku: String): Mono<Product> {
        return productRepositoryExtensions.findBySkuWithCategory(sku)
    }

    /**
     * Find all products in a category with category details loaded
     */
    fun findProductsInCategoryWithCategory(categoryId: UUID): Flux<Product> {
        return productRepositoryExtensions.findByCategoryIdWithCategory(categoryId)
    }

    /**
     * Find all active products with their categories loaded
     */
    fun findActiveProductsWithCategory(): Flux<Product> {
        return productRepositoryExtensions.findActiveProductsWithCategory()
    }
}
