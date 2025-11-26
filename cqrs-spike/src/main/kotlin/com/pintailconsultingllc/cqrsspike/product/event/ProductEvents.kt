package com.pintailconsultingllc.cqrsspike.product.event

import com.pintailconsultingllc.cqrsspike.product.command.model.ProductStatus
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Event emitted when a new product is created.
 */
data class ProductCreated(
    override val eventId: UUID = UUID.randomUUID(),
    override val productId: UUID,
    override val occurredAt: OffsetDateTime = OffsetDateTime.now(),
    override val version: Long,
    val sku: String,
    val name: String,
    val description: String?,
    val priceCents: Int,
    val status: ProductStatus = ProductStatus.DRAFT
) : ProductEvent

/**
 * Event emitted when product details are updated (name, description).
 */
data class ProductUpdated(
    override val eventId: UUID = UUID.randomUUID(),
    override val productId: UUID,
    override val occurredAt: OffsetDateTime = OffsetDateTime.now(),
    override val version: Long,
    val name: String,
    val description: String?,
    val previousName: String,
    val previousDescription: String?
) : ProductEvent

/**
 * Event emitted when product price is changed.
 */
data class ProductPriceChanged(
    override val eventId: UUID = UUID.randomUUID(),
    override val productId: UUID,
    override val occurredAt: OffsetDateTime = OffsetDateTime.now(),
    override val version: Long,
    val newPriceCents: Int,
    val previousPriceCents: Int,
    val changePercentage: Double
) : ProductEvent

/**
 * Event emitted when product is activated (DRAFT → ACTIVE).
 */
data class ProductActivated(
    override val eventId: UUID = UUID.randomUUID(),
    override val productId: UUID,
    override val occurredAt: OffsetDateTime = OffsetDateTime.now(),
    override val version: Long,
    val previousStatus: ProductStatus
) : ProductEvent

/**
 * Event emitted when product is discontinued.
 */
data class ProductDiscontinued(
    override val eventId: UUID = UUID.randomUUID(),
    override val productId: UUID,
    override val occurredAt: OffsetDateTime = OffsetDateTime.now(),
    override val version: Long,
    val previousStatus: ProductStatus,
    val reason: String?
) : ProductEvent

/**
 * Event emitted when product is soft-deleted.
 */
data class ProductDeleted(
    override val eventId: UUID = UUID.randomUUID(),
    override val productId: UUID,
    override val occurredAt: OffsetDateTime = OffsetDateTime.now(),
    override val version: Long,
    val deletedBy: String?
) : ProductEvent
