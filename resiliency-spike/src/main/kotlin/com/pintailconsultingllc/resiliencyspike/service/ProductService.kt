package com.pintailconsultingllc.resiliencyspike.service

import com.pintailconsultingllc.resiliencyspike.domain.Product
import com.pintailconsultingllc.resiliencyspike.repository.ProductRepository
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import io.github.resilience4j.ratelimiter.annotation.RateLimiter
import io.github.resilience4j.retry.annotation.Retry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.util.*

/**
 * Service for managing products in the catalog
 * Demonstrates reactive database operations with R2DBC
 * Circuit breaker protection and retry logic applied to database operations
 */
@Service
class ProductService(
    private val productRepository: ProductRepository
) {

    private val logger = LoggerFactory.getLogger(ProductService::class.java)

    /**
     * Create a new product
     */
    @RateLimiter(name = "product", fallbackMethod = "createProductFallback")
    @Retry(name = "product", fallbackMethod = "createProductFallback")
    @CircuitBreaker(name = "product", fallbackMethod = "createProductFallback")
    fun createProduct(product: Product): Mono<Product> {
        return productRepository.save(product)
    }

    private fun createProductFallback(product: Product, ex: Exception): Mono<Product> {
        logger.error("Rate limiter/Retry/Circuit breaker fallback for createProduct - product: ${product.name}, error: ${ex.message}", ex)
        return Mono.error(RuntimeException("Product service is temporarily unavailable. Please try again later.", ex))
    }

    /**
     * Update an existing product
     */
    @RateLimiter(name = "product", fallbackMethod = "updateProductFallback")
    @Retry(name = "product", fallbackMethod = "updateProductFallback")
    @CircuitBreaker(name = "product", fallbackMethod = "updateProductFallback")
    fun updateProduct(product: Product): Mono<Product> {
        return productRepository.save(product)
    }

    private fun updateProductFallback(product: Product, ex: Exception): Mono<Product> {
        logger.error("Rate limiter/Retry/Circuit breaker fallback for updateProduct - product: ${product.name}, error: ${ex.message}", ex)
        return Mono.error(RuntimeException("Unable to update product. Please try again later.", ex))
    }

    /**
     * Find a product by ID
     */
    @RateLimiter(name = "product", fallbackMethod = "findProductByIdFallback")
    @Retry(name = "product", fallbackMethod = "findProductByIdFallback")
    @CircuitBreaker(name = "product", fallbackMethod = "findProductByIdFallback")
    fun findProductById(id: UUID): Mono<Product> {
        return productRepository.findById(id)
    }

    private fun findProductByIdFallback(id: UUID, ex: Exception): Mono<Product> {
        logger.error("Rate limiter/Retry/Circuit breaker fallback for findProductById - id: $id, error: ${ex.message}", ex)
        return Mono.error(RuntimeException("Unable to retrieve product. Please try again later.", ex))
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
