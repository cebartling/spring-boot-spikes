package com.pintailconsultingllc.resiliencyspike.repository

import com.pintailconsultingllc.resiliencyspike.domain.Product
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

/**
 * Implementation of ProductRepositoryExtensions
 * Provides methods to load products with their related category information
 */
@Repository
class ProductRepositoryExtensionsImpl(
    private val productRepository: ProductRepository,
    private val categoryRepository: CategoryRepository
) : ProductRepositoryExtensions {

    override fun findByIdWithCategory(productId: UUID): Mono<Product> {
        return productRepository.findById(productId)
            .flatMap { product -> loadCategory(product) }
    }

    override fun findBySkuWithCategory(sku: String): Mono<Product> {
        return productRepository.findBySku(sku)
            .flatMap { product -> loadCategory(product) }
    }

    override fun findByCategoryIdWithCategory(categoryId: UUID): Flux<Product> {
        return productRepository.findByCategoryId(categoryId)
            .flatMap { product -> loadCategory(product) }
    }

    override fun findActiveProductsWithCategory(): Flux<Product> {
        return productRepository.findByIsActive(true)
            .flatMap { product -> loadCategory(product) }
    }

    private fun loadCategory(product: Product): Mono<Product> {
        return categoryRepository.findById(product.categoryId)
            .map { category ->
                product.copy().also { it.category = category }
            }
            .defaultIfEmpty(product) // Return product without category if category not found
    }
}
