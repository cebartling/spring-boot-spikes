package com.pintailconsultingllc.resiliencyspike.controller

import com.pintailconsultingllc.resiliencyspike.domain.Product
import com.pintailconsultingllc.resiliencyspike.dto.*
import com.pintailconsultingllc.resiliencyspike.service.ProductService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
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
@Tag(name = "Product Catalog", description = "Product catalog management APIs")
class ProductController(
    private val productService: ProductService
) {

    /**
     * Create a new product
     */
    @Operation(summary = "Create a new product", description = "Creates a new product in the catalog")
    @ApiResponses(value = [
        ApiResponse(responseCode = "201", description = "Product created successfully", content = [Content(schema = Schema(implementation = ProductResponse::class))]),
        ApiResponse(responseCode = "400", description = "Invalid input")
    ])
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
    @Operation(summary = "Get product by ID", description = "Retrieves a product by its unique identifier")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Product found", content = [Content(schema = Schema(implementation = ProductResponse::class))]),
        ApiResponse(responseCode = "404", description = "Product not found")
    ])
    @GetMapping("/{productId}")
    fun getProductById(
        @Parameter(description = "Product unique identifier") @PathVariable productId: UUID
    ): Mono<ProductResponse> {
        return productService.findProductById(productId)
            .map { it.toResponse() }
    }

    /**
     * Get product by SKU
     */
    @Operation(summary = "Get product by SKU", description = "Retrieves a product by its SKU (Stock Keeping Unit)")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Product found", content = [Content(schema = Schema(implementation = ProductResponse::class))]),
        ApiResponse(responseCode = "404", description = "Product not found")
    ])
    @GetMapping("/sku/{sku}")
    fun getProductBySku(
        @Parameter(description = "Product SKU") @PathVariable sku: String
    ): Mono<ProductResponse> {
        return productService.findProductBySku(sku)
            .map { it.toResponse() }
    }

    /**
     * Get all products
     */
    @Operation(summary = "Get all products", description = "Retrieves all products, optionally filtered by active status")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Products retrieved successfully")
    ])
    @GetMapping
    fun getAllProducts(
        @Parameter(description = "Filter to show only active products") @RequestParam(required = false, defaultValue = "false") activeOnly: Boolean
    ): Flux<ProductResponse> {
        return if (activeOnly) {
            productService.findActiveProducts()
        } else {
            productService.findAllProducts()
        }.map { it.toResponse() }
    }

    /**
     * Get products by category
     */
    @Operation(summary = "Get products by category", description = "Retrieves products by category, optionally filtered by active status")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Products retrieved successfully")
    ])
    @GetMapping("/category/{categoryId}")
    fun getProductsByCategory(
        @Parameter(description = "Category unique identifier") @PathVariable categoryId: UUID,
        @Parameter(description = "Filter to show only active products") @RequestParam(required = false, defaultValue = "false") activeOnly: Boolean
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
    @Operation(summary = "Search products by name", description = "Searches products by name using a search term")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Search completed successfully")
    ])
    @GetMapping("/search")
    fun searchProducts(
        @Parameter(description = "Search term to match product names") @RequestParam searchTerm: String
    ): Flux<ProductResponse> {
        return productService.searchProductsByName(searchTerm)
            .map { it.toResponse() }
    }

    /**
     * Get products by price range
     */
    @Operation(summary = "Get products by price range", description = "Retrieves products within a specified price range")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Products retrieved successfully")
    ])
    @GetMapping("/price-range")
    fun getProductsByPriceRange(
        @Parameter(description = "Minimum price") @RequestParam minPrice: BigDecimal,
        @Parameter(description = "Maximum price") @RequestParam maxPrice: BigDecimal
    ): Flux<ProductResponse> {
        return productService.findProductsByPriceRange(minPrice, maxPrice)
            .map { it.toResponse() }
    }

    /**
     * Get products by category and price range
     */
    @Operation(summary = "Get products by category and price range", description = "Retrieves products by category within a specified price range")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Products retrieved successfully")
    ])
    @GetMapping("/category/{categoryId}/price-range")
    fun getProductsByCategoryAndPriceRange(
        @Parameter(description = "Category unique identifier") @PathVariable categoryId: UUID,
        @Parameter(description = "Minimum price") @RequestParam minPrice: BigDecimal,
        @Parameter(description = "Maximum price") @RequestParam maxPrice: BigDecimal
    ): Flux<ProductResponse> {
        return productService.findProductsByCategoryAndPriceRange(categoryId, minPrice, maxPrice)
            .map { it.toResponse() }
    }

    /**
     * Get low stock products
     */
    @Operation(summary = "Get low stock products", description = "Retrieves products with stock quantity below the specified threshold")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Low stock products retrieved successfully")
    ])
    @GetMapping("/low-stock")
    fun getLowStockProducts(
        @Parameter(description = "Stock quantity threshold (default: 10)") @RequestParam(required = false, defaultValue = "10") threshold: Int
    ): Flux<ProductResponse> {
        return productService.findLowStockProducts(threshold)
            .map { it.toResponse() }
    }

    /**
     * Update a product
     */
    @Operation(summary = "Update a product", description = "Updates an existing product with partial or complete data")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Product updated successfully", content = [Content(schema = Schema(implementation = ProductResponse::class))]),
        ApiResponse(responseCode = "404", description = "Product not found")
    ])
    @PutMapping("/{productId}")
    fun updateProduct(
        @Parameter(description = "Product unique identifier") @PathVariable productId: UUID,
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
    @Operation(summary = "Update product stock", description = "Updates the stock quantity for a product")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Stock updated successfully", content = [Content(schema = Schema(implementation = ProductResponse::class))]),
        ApiResponse(responseCode = "404", description = "Product not found")
    ])
    @PutMapping("/{productId}/stock")
    fun updateProductStock(
        @Parameter(description = "Product unique identifier") @PathVariable productId: UUID,
        @RequestBody request: UpdateProductStockRequest
    ): Mono<ProductResponse> {
        return productService.updateProductStock(productId, request.stockQuantity)
            .map { it.toResponse() }
    }

    /**
     * Activate a product
     */
    @Operation(summary = "Activate a product", description = "Activates a product, making it available for sale")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Product activated successfully", content = [Content(schema = Schema(implementation = ProductResponse::class))]),
        ApiResponse(responseCode = "404", description = "Product not found")
    ])
    @PostMapping("/{productId}/activate")
    fun activateProduct(
        @Parameter(description = "Product unique identifier") @PathVariable productId: UUID
    ): Mono<ProductResponse> {
        return productService.activateProduct(productId)
            .map { it.toResponse() }
    }

    /**
     * Deactivate a product (soft delete)
     */
    @Operation(summary = "Deactivate a product", description = "Deactivates a product, removing it from active sale (soft delete)")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Product deactivated successfully", content = [Content(schema = Schema(implementation = ProductResponse::class))]),
        ApiResponse(responseCode = "404", description = "Product not found")
    ])
    @PostMapping("/{productId}/deactivate")
    fun deactivateProduct(
        @Parameter(description = "Product unique identifier") @PathVariable productId: UUID
    ): Mono<ProductResponse> {
        return productService.deactivateProduct(productId)
            .map { it.toResponse() }
    }

    /**
     * Delete a product permanently
     */
    @Operation(summary = "Delete a product", description = "Permanently deletes a product from the catalog")
    @ApiResponses(value = [
        ApiResponse(responseCode = "204", description = "Product deleted successfully"),
        ApiResponse(responseCode = "404", description = "Product not found")
    ])
    @DeleteMapping("/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteProduct(
        @Parameter(description = "Product unique identifier") @PathVariable productId: UUID
    ): Mono<Void> {
        return productService.deleteProduct(productId)
    }

    /**
     * Count products by category
     */
    @Operation(summary = "Count products by category", description = "Returns the total count of products in a specific category")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Product count retrieved successfully")
    ])
    @GetMapping("/category/{categoryId}/count")
    fun countProductsByCategory(
        @Parameter(description = "Category unique identifier") @PathVariable categoryId: UUID
    ): Mono<Long> {
        return productService.countProductsByCategory(categoryId)
    }

    /**
     * Count active products
     */
    @Operation(summary = "Count active products", description = "Returns the total count of all active products")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Active product count retrieved successfully")
    ])
    @GetMapping("/count/active")
    fun countActiveProducts(): Mono<Long> {
        return productService.countActiveProducts()
    }
}
