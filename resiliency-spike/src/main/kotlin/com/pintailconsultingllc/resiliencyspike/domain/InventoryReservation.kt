package com.pintailconsultingllc.resiliencyspike.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.OffsetDateTime
import java.util.*

/**
 * Entity representing an inventory reservation
 *
 * Reserves inventory for orders, transfers, or other purposes.
 */
@Table("inventory_reservations")
data class InventoryReservation(
    @Id
    val id: UUID? = null,

    @Column("product_id")
    val productId: UUID,

    @Column("location_id")
    val locationId: UUID,

    @Column("quantity")
    val quantity: Int,

    @Column("reservation_type")
    val reservationType: String, // ORDER, TRANSFER, HOLD, ALLOCATION

    @Column("reference_number")
    val referenceNumber: String,

    @Column("status")
    val status: String = "ACTIVE", // ACTIVE, FULFILLED, CANCELLED, EXPIRED

    @Column("reserved_by")
    val reservedBy: String? = null,

    @Column("reserved_at")
    val reservedAt: OffsetDateTime = OffsetDateTime.now(),

    @Column("expires_at")
    val expiresAt: OffsetDateTime? = null,

    @Column("fulfilled_at")
    val fulfilledAt: OffsetDateTime? = null,

    @Column("notes")
    val notes: String? = null,

    @Column("metadata")
    val metadata: String? = null, // JSON stored as String

    @Column("created_at")
    val createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column("updated_at")
    val updatedAt: OffsetDateTime = OffsetDateTime.now()
)
