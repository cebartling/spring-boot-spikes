package com.pintailconsultingllc.resiliencyspike.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.OffsetDateTime
import java.util.*

/**
 * Entity representing an inventory transaction (movement, adjustment, etc.)
 *
 * Records all inventory changes for audit trail and tracking purposes.
 */
@Table("inventory_transactions")
data class InventoryTransaction(
    @Id
    val id: UUID? = null,

    @Column("product_id")
    val productId: UUID,

    @Column("location_id")
    val locationId: UUID,

    @Column("transaction_type")
    val transactionType: String, // RECEIPT, SHIPMENT, ADJUSTMENT, TRANSFER_IN, TRANSFER_OUT, RETURN, DAMAGE, RECOUNT

    @Column("quantity")
    val quantity: Int,

    @Column("reference_number")
    val referenceNumber: String? = null,

    @Column("reason")
    val reason: String? = null,

    @Column("notes")
    val notes: String? = null,

    @Column("performed_by")
    val performedBy: String? = null,

    @Column("transaction_date")
    val transactionDate: OffsetDateTime = OffsetDateTime.now(),

    @Column("metadata")
    val metadata: String? = null, // JSON stored as String

    @Column("created_at")
    val createdAt: OffsetDateTime = OffsetDateTime.now()
)
