package com.pintailconsultingllc.resiliencyspike.controller

import com.pintailconsultingllc.resiliencyspike.domain.Product
import com.pintailconsultingllc.resiliencyspike.dto.*
import com.pintailconsultingllc.resiliencyspike.service.ProductService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.*

/**
 * REST controller for product catalog operations
 */
@RestController
@RequestMapping("/api/v1/products")
class ProductController(
    private val productService: ProductService
) {

    /**
     * Create a new product
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createProduct(@RequestBody request: CreateProductRequest): Mono<ProductResponse> {
        val product = Product(
            sku = request.sku,
            name = request.name,
            description = request.description,
            categoryId = request.categoryId,
            price = request.price,
            stockQuantity = request.stockQuantity,
            metadata = request.metadata
        )
        return productService.createProduct(product)
            .map { it.toResponse() }
    }

    /**
     * Get product by ID
     */
    @GetMapping("/{productId}")
    fun getProductById(@PathVariable productId: UUID): Mono<ProductResponse> {
        return productService.findProductById(productId)
            .map { it.toResponse() }
    }

    /**
     * Get product by SKU
     */
    @GetMapping("/sku/{sku}")
    fun getProductBySku(@PathVariable sku: String): Mono<ProductResponse> {
        return productService.findProductBySku(sku)
            .map { it.toResponse() }
    }

    /**
     * Get all products
     */
    @GetMapping
    fun getAllProducts(@RequestParam(required = false, defaultValue = "false") activeOnly: Boolean): Flux<ProductResponse> {
        return if (activeOnly) {
            productService.findActiveProducts()
        } else {
            productService.findAllProducts()
        }.map { it.toResponse() }
    }

    /**
     * Get products by category
     */
    @GetMapping("/category/{categoryId}")
    fun getProductsByCategory(
        @PathVariable categoryId: UUID,
        @RequestParam(required = false, defaultValue = "false") activeOnly: Boolean
    ): Flux<ProductResponse> {
        return if (activeOnly) {
            productService.findActiveProductsByCategory(categoryId)
        } else {
            productService.findProductsByCategory(categoryId)
        }.map { it.toResponse() }
    }

    /**
     * Search products by name
     */
    @GetMapping("/search")
    fun searchProducts(@RequestParam searchTerm: String): Flux<ProductResponse> {
        return productService.searchProductsByName(searchTerm)
            .map { it.toResponse() }
    }

    /**
     * Get products by price range
     */
    @GetMapping("/price-range")
    fun getProductsByPriceRange(
        @RequestParam minPrice: BigDecimal,
        @RequestParam maxPrice: BigDecimal
    ): Flux<ProductResponse> {
        return productService.findProductsByPriceRange(minPrice, maxPrice)
            .map { it.toResponse() }
    }

    /**
     * Get products by category and price range
     */
    @GetMapping("/category/{categoryId}/price-range")
    fun getProductsByCategoryAndPriceRange(
        @PathVariable categoryId: UUID,
        @RequestParam minPrice: BigDecimal,
        @RequestParam maxPrice: BigDecimal
    ): Flux<ProductResponse> {
        return productService.findProductsByCategoryAndPriceRange(categoryId, minPrice, maxPrice)
            .map { it.toResponse() }
    }

    /**
     * Get low stock products
     */
    @GetMapping("/low-stock")
    fun getLowStockProducts(@RequestParam(required = false, defaultValue = "10") threshold: Int): Flux<ProductResponse> {
        return productService.findLowStockProducts(threshold)
            .map { it.toResponse() }
    }

    /**
     * Update a product
     */
    @PutMapping("/{productId}")
    fun updateProduct(
        @PathVariable productId: UUID,
        @RequestBody request: UpdateProductRequest
    ): Mono<ProductResponse> {
        return productService.findProductById(productId)
            .flatMap { existingProduct ->
                val updatedProduct = existingProduct.copy(
                    name = request.name ?: existingProduct.name,
                    description = request.description ?: existingProduct.description,
                    categoryId = request.categoryId ?: existingProduct.categoryId,
                    price = request.price ?: existingProduct.price,
                    stockQuantity = request.stockQuantity ?: existingProduct.stockQuantity,
                    metadata = request.metadata ?: existingProduct.metadata,
                    updatedAt = OffsetDateTime.now()
                )
                productService.updateProduct(updatedProduct)
            }
            .map { it.toResponse() }
    }

    /**
     * Update product stock
     */
    @PutMapping("/{productId}/stock")
    fun updateProductStock(
        @PathVariable productId: UUID,
        @RequestBody request: UpdateProductStockRequest
    ): Mono<ProductResponse> {
        return productService.updateProductStock(productId, request.stockQuantity)
            .map { it.toResponse() }
    }

    /**
     * Activate a product
     */
    @PostMapping("/{productId}/activate")
    fun activateProduct(@PathVariable productId: UUID): Mono<ProductResponse> {
        return productService.activateProduct(productId)
            .map { it.toResponse() }
    }

    /**
     * Deactivate a product (soft delete)
     */
    @PostMapping("/{productId}/deactivate")
    fun deactivateProduct(@PathVariable productId: UUID): Mono<ProductResponse> {
        return productService.deactivateProduct(productId)
            .map { it.toResponse() }
    }

    /**
     * Delete a product permanently
     */
    @DeleteMapping("/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteProduct(@PathVariable productId: UUID): Mono<Void> {
        return productService.deleteProduct(productId)
    }

    /**
     * Count products by category
     */
    @GetMapping("/category/{categoryId}/count")
    fun countProductsByCategory(@PathVariable categoryId: UUID): Mono<Long> {
        return productService.countProductsByCategory(categoryId)
    }

    /**
     * Count active products
     */
    @GetMapping("/count/active")
    fun countActiveProducts(): Mono<Long> {
        return productService.countActiveProducts()
    }
}
