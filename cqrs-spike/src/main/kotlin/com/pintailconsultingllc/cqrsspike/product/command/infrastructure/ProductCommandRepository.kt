package com.pintailconsultingllc.cqrsspike.product.command.infrastructure

import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * Reactive repository for Product command model.
 */
@Repository
interface ProductCommandRepository : ReactiveCrudRepository<ProductEntity, UUID> {

    /**
     * Find product by ID, excluding soft-deleted products.
     */
    @Query("SELECT * FROM command_model.product WHERE id = :id AND deleted_at IS NULL")
    fun findByIdNotDeleted(id: UUID): Mono<ProductEntity>

    /**
     * Find product by SKU, excluding soft-deleted products.
     */
    @Query("SELECT * FROM command_model.product WHERE sku = :sku AND deleted_at IS NULL")
    fun findBySku(sku: String): Mono<ProductEntity>

    /**
     * Check if SKU exists (excluding soft-deleted products).
     */
    @Query("SELECT EXISTS(SELECT 1 FROM command_model.product WHERE sku = :sku AND deleted_at IS NULL)")
    fun existsBySku(sku: String): Mono<Boolean>

    /**
     * Find product by ID with specific version (for optimistic locking verification).
     */
    @Query("SELECT * FROM command_model.product WHERE id = :id AND version = :version AND deleted_at IS NULL")
    fun findByIdAndVersion(id: UUID, version: Long): Mono<ProductEntity>
}
