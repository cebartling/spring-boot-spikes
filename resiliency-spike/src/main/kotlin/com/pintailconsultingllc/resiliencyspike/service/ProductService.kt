package com.pintailconsultingllc.resiliencyspike.service

import com.pintailconsultingllc.resiliencyspike.domain.Product
import com.pintailconsultingllc.resiliencyspike.repository.ProductRepository
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.util.*

/**
 * Service for managing products in the catalog
 * Demonstrates reactive database operations with R2DBC
 */
@Service
class ProductService(
    private val productRepository: ProductRepository
) {

    /**
     * Create a new product
     */
    fun createProduct(product: Product): Mono<Product> {
        return productRepository.save(product)
    }

    /**
     * Update an existing product
     */
    fun updateProduct(product: Product): Mono<Product> {
        return productRepository.save(product)
    }

    /**
     * Find a product by ID
     */
    fun findProductById(id: UUID): Mono<Product> {
        return productRepository.findById(id)
    }

    /**
     * Find a product by SKU
     */
    fun findProductBySku(sku: String): Mono<Product> {
        return productRepository.findBySku(sku)
    }

    /**
     * Find all products
     */
    fun findAllProducts(): Flux<Product> {
        return productRepository.findAll()
    }

    /**
     * Find all active products
     */
    fun findActiveProducts(): Flux<Product> {
        return productRepository.findByIsActive(true)
    }

    /**
     * Find products by category
     */
    fun findProductsByCategory(categoryId: UUID): Flux<Product> {
        return productRepository.findByCategoryId(categoryId)
    }

    /**
     * Find active products by category
     */
    fun findActiveProductsByCategory(categoryId: UUID): Flux<Product> {
        return productRepository.findByCategoryIdAndIsActive(categoryId, true)
    }

    /**
     * Search products by name
     */
    fun searchProductsByName(searchTerm: String): Flux<Product> {
        return productRepository.searchByName(searchTerm)
    }

    /**
     * Find products within a price range
     */
    fun findProductsByPriceRange(minPrice: BigDecimal, maxPrice: BigDecimal): Flux<Product> {
        return productRepository.findByPriceRange(minPrice, maxPrice)
    }

    /**
     * Find products by category and price range
     */
    fun findProductsByCategoryAndPriceRange(
        categoryId: UUID,
        minPrice: BigDecimal,
        maxPrice: BigDecimal
    ): Flux<Product> {
        return productRepository.findByCategoryAndPriceRange(categoryId, minPrice, maxPrice)
    }

    /**
     * Find products with low stock
     */
    fun findLowStockProducts(threshold: Int = 10): Flux<Product> {
        return productRepository.findLowStockProducts(threshold)
    }

    /**
     * Update product stock quantity
     */
    fun updateProductStock(productId: UUID, quantity: Int): Mono<Product> {
        return productRepository.findById(productId)
            .flatMap { product ->
                productRepository.save(product.copy(stockQuantity = quantity))
            }
    }

    /**
     * Deactivate a product (soft delete)
     */
    fun deactivateProduct(productId: UUID): Mono<Product> {
        return productRepository.findById(productId)
            .flatMap { product ->
                productRepository.save(product.copy(isActive = false))
            }
    }

    /**
     * Activate a product
     */
    fun activateProduct(productId: UUID): Mono<Product> {
        return productRepository.findById(productId)
            .flatMap { product ->
                productRepository.save(product.copy(isActive = true))
            }
    }

    /**
     * Delete a product permanently
     */
    fun deleteProduct(productId: UUID): Mono<Void> {
        return productRepository.deleteById(productId)
    }

    /**
     * Count products by category
     */
    fun countProductsByCategory(categoryId: UUID): Mono<Long> {
        return productRepository.countByCategoryId(categoryId)
    }

    /**
     * Count active products
     */
    fun countActiveProducts(): Mono<Long> {
        return productRepository.countByIsActive(true)
    }
}
