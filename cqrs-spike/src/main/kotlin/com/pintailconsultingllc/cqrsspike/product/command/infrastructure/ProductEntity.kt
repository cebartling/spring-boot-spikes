package com.pintailconsultingllc.cqrsspike.product.command.infrastructure

import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.OffsetDateTime
import java.util.UUID

/**
 * R2DBC entity for Product command model persistence.
 * Maps to command_model.product table.
 */
@Table(name = "product", schema = "command_model")
data class ProductEntity(
    @Id
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

    @Version
    @Column("version")
    val version: Long,

    @Column("created_at")
    val createdAt: OffsetDateTime,

    @Column("updated_at")
    val updatedAt: OffsetDateTime,

    @Column("deleted_at")
    val deletedAt: OffsetDateTime?
)
