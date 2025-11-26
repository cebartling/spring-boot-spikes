package com.pintailconsultingllc.cqrsspike.product.command.infrastructure

import com.pintailconsultingllc.cqrsspike.product.command.aggregate.ProductAggregate
import com.pintailconsultingllc.cqrsspike.product.command.model.ProductStatus
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Maps between ProductAggregate and ProductEntity.
 */
object ProductAggregateMapper {

    /**
     * Converts ProductAggregate to ProductEntity for persistence.
     */
    fun toEntity(aggregate: ProductAggregate): ProductEntity {
        return ProductEntity(
            id = aggregate.id,
            sku = aggregate.sku,
            name = aggregate.name,
            description = aggregate.description,
            priceCents = aggregate.priceCents,
            status = aggregate.status.name,
            version = aggregate.version,
            createdAt = aggregate.createdAt,
            updatedAt = aggregate.updatedAt,
            deletedAt = aggregate.deletedAt
        )
    }

    /**
     * Note: Full aggregate reconstitution should be done from events.
     * This method is for snapshot loading only when event sourcing
     * is combined with snapshot persistence.
     */
    fun toAggregateSnapshot(entity: ProductEntity): ProductSnapshot {
        return ProductSnapshot(
            id = entity.id,
            sku = entity.sku,
            name = entity.name,
            description = entity.description,
            priceCents = entity.priceCents,
            status = ProductStatus.valueOf(entity.status),
            version = entity.version,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            deletedAt = entity.deletedAt
        )
    }
}

/**
 * Snapshot data for aggregate (used for loading current state without full event replay).
 */
data class ProductSnapshot(
    val id: UUID,
    val sku: String,
    val name: String,
    val description: String?,
    val priceCents: Int,
    val status: ProductStatus,
    val version: Long,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
    val deletedAt: OffsetDateTime?
)
