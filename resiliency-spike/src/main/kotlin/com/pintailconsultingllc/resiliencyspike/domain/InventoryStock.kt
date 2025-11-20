package com.pintailconsultingllc.resiliencyspike.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.OffsetDateTime
import java.util.*

/**
 * Entity representing current inventory stock levels for a product at a specific location
 *
 * Tracks on-hand quantity, reserved quantity, and available quantity (computed).
 */
@Table("inventory_stock")
data class InventoryStock(
    @Id
    val id: UUID? = null,

    @Column("product_id")
    val productId: UUID,

    @Column("location_id")
    val locationId: UUID,

    @Column("quantity_on_hand")
    val quantityOnHand: Int = 0,

    @Column("quantity_reserved")
    val quantityReserved: Int = 0,

    @Column("quantity_available")
    val quantityAvailable: Int = 0, // Computed by database trigger

    @Column("reorder_point")
    val reorderPoint: Int = 0,

    @Column("reorder_quantity")
    val reorderQuantity: Int = 0,

    @Column("last_stock_check")
    val lastStockCheck: OffsetDateTime? = null,

    @Column("created_at")
    val createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column("updated_at")
    val updatedAt: OffsetDateTime = OffsetDateTime.now()
)
