package com.pintailconsultingllc.cqrsspike.product.query.model

import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Read model entity for Product queries.
 *
 * This is a denormalized view optimized for read operations.
 * It is updated asynchronously from domain events via projections.
 *
 * Maps to read_model.product table.
 */
@Table("read_model\".\"product")
data class ProductReadModel(
    @Id
    @Column("id")
    val id: UUID,

    @Column("sku")
    val sku: String,

    @Column("name")
    val name: String,

    @Column("description")
    val description: String?,

    @Column("price_cents")
    val priceCents: Int,

    @Column("status")
    val status: String,

    @Column("created_at")
    val createdAt: OffsetDateTime,

    @Column("updated_at")
    val updatedAt: OffsetDateTime,

    @Column("version")
    val aggregateVersion: Long,

    @Column("is_deleted")
    val isDeleted: Boolean = false,

    @Column("price_display")
    val priceDisplay: String? = null,

    @Column("search_text")
    val searchText: String? = null,

    @Column("last_event_id")
    val lastEventId: UUID? = null
)
