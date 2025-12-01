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
     *
     * Note: For new entities (domain version = 1), we set the database version to 0
     * so that Spring Data R2DBC performs an INSERT operation. For existing entities,
     * we subtract 1 from the domain version to match the database's optimistic
     * locking expectations (database version starts at 0, domain version at 1).
     */
    fun toEntity(aggregate: ProductAggregate): ProductEntity {
        // Domain version starts at 1 for new entities, database version starts at 0
        // This mapping ensures Spring Data R2DBC correctly identifies new vs existing entities
        val dbVersion = aggregate.version - 1

        return ProductEntity(
            id = aggregate.id,
            sku = aggregate.sku,
            name = aggregate.name,
            description = aggregate.description,
            priceCents = aggregate.priceCents,
            status = aggregate.status.name,
            version = dbVersion,
            createdAt = aggregate.createdAt,
            updatedAt = aggregate.updatedAt,
            deletedAt = aggregate.deletedAt
        )
    }

    /**
     * Note: Full aggregate reconstitution should be done from events.
     * This method is for snapshot loading only when event sourcing
     * is combined with snapshot persistence.
     *
     * The domain version is database version + 1 to maintain consistency
     * with the version mapping in toEntity().
     */
    fun toAggregateSnapshot(entity: ProductEntity): ProductSnapshot {
        // Database version starts at 0, domain version starts at 1
        val domainVersion = entity.version + 1

        return ProductSnapshot(
            id = entity.id,
            sku = entity.sku,
            name = entity.name,
            description = entity.description,
            priceCents = entity.priceCents,
            status = ProductStatus.valueOf(entity.status),
            version = domainVersion,
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
