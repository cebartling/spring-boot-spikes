package com.pintailconsultingllc.cqrsspike.product.command.model

import java.util.UUID

/**
 * Marker interface for all Product commands.
 * Commands are immutable requests to change the state of the system.
 */
sealed interface ProductCommand {
    /**
     * Optional idempotency key for duplicate request detection.
     * If provided, the same command with the same key will not be processed twice.
     */
    val idempotencyKey: String?
}

/**
 * Commands that target an existing product.
 */
sealed interface ExistingProductCommand : ProductCommand {
    val productId: UUID
    val expectedVersion: Long
}

/**
 * Command to create a new product.
 */
data class CreateProductCommand(
    val sku: String,
    val name: String,
    val description: String?,
    val priceCents: Int,
    override val idempotencyKey: String? = null
) : ProductCommand

/**
 * Command to update product details (name and description).
 */
data class UpdateProductCommand(
    override val productId: UUID,
    override val expectedVersion: Long,
    val name: String,
    val description: String?,
    override val idempotencyKey: String? = null
) : ExistingProductCommand

/**
 * Command to change the product price.
 */
data class ChangePriceCommand(
    override val productId: UUID,
    override val expectedVersion: Long,
    val newPriceCents: Int,
    val confirmLargeChange: Boolean = false,
    override val idempotencyKey: String? = null
) : ExistingProductCommand

/**
 * Command to activate a product (DRAFT -> ACTIVE).
 */
data class ActivateProductCommand(
    override val productId: UUID,
    override val expectedVersion: Long,
    override val idempotencyKey: String? = null
) : ExistingProductCommand

/**
 * Command to discontinue a product.
 */
data class DiscontinueProductCommand(
    override val productId: UUID,
    override val expectedVersion: Long,
    val reason: String? = null,
    override val idempotencyKey: String? = null
) : ExistingProductCommand

/**
 * Command to soft-delete a product.
 */
data class DeleteProductCommand(
    override val productId: UUID,
    override val expectedVersion: Long,
    val deletedBy: String? = null,
    override val idempotencyKey: String? = null
) : ExistingProductCommand
