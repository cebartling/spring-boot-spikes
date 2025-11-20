package com.pintailconsultingllc.resiliencyspike.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.OffsetDateTime
import java.util.*

/**
 * Entity representing an inventory location (warehouse, store, distribution center, etc.)
 *
 * Inventory locations are physical or logical places where inventory is stored and tracked.
 */
@Table("inventory_locations")
data class InventoryLocation(
    @Id
    val id: UUID? = null,

    @Column("code")
    val code: String,

    @Column("name")
    val name: String,

    @Column("type")
    val type: String, // WAREHOUSE, STORE, DISTRIBUTION_CENTER, SUPPLIER

    @Column("address")
    val address: String? = null,

    @Column("city")
    val city: String? = null,

    @Column("state")
    val state: String? = null,

    @Column("postal_code")
    val postalCode: String? = null,

    @Column("country")
    val country: String? = null,

    @Column("is_active")
    val isActive: Boolean = true,

    @Column("metadata")
    val metadata: String? = null, // JSON stored as String

    @Column("created_at")
    val createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column("updated_at")
    val updatedAt: OffsetDateTime = OffsetDateTime.now()
)
