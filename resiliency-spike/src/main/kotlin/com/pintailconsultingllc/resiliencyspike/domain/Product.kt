package com.pintailconsultingllc.resiliencyspike.domain

import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Transient
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.*

/**
 * Entity representing a product in the catalog.
 *
 * Products belong to a category and have pricing, inventory tracking, and metadata.
 *
 * Note: The `category` field is transient and not persisted. It can be loaded separately
 * via custom repository methods when needed.
 */
@Table("products")
data class Product(
    @Id
    val id: UUID? = null,

    @Column("sku")
    val sku: String,

    @Column("name")
    val name: String,

    @Column("description")
    val description: String? = null,

    @Column("category_id")
    val categoryId: UUID,

    @Column("price")
    val price: BigDecimal,

    @Column("stock_quantity")
    val stockQuantity: Int = 0,

    @Column("is_active")
    val isActive: Boolean = true,

    @Column("metadata")
    val metadata: String? = null, // JSON stored as String, can be parsed with ObjectMapper

    @Column("created_at")
    val createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column("updated_at")
    val updatedAt: OffsetDateTime = OffsetDateTime.now(),

    // Transient relationship - not stored in database, loaded separately
    @Transient
    val category: Category? = null
)
