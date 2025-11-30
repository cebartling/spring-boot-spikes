package com.pintailconsultingllc.cqrsspike.testutil.builders

import com.pintailconsultingllc.cqrsspike.product.command.model.ActivateProductCommand
import com.pintailconsultingllc.cqrsspike.product.command.model.ChangePriceCommand
import com.pintailconsultingllc.cqrsspike.product.command.model.CreateProductCommand
import com.pintailconsultingllc.cqrsspike.product.command.model.DeleteProductCommand
import com.pintailconsultingllc.cqrsspike.product.command.model.DiscontinueProductCommand
import com.pintailconsultingllc.cqrsspike.product.command.model.UpdateProductCommand
import java.util.UUID

/**
 * Factory methods for creating command instances in tests.
 *
 * Usage:
 * ```
 * val command = ProductCommandBuilders.createProductCommand(
 *     sku = "TEST-001",
 *     name = "Test Product"
 * )
 * ```
 */
object ProductCommandBuilders {

    /**
     * Creates a CreateProductCommand with sensible defaults.
     */
    fun createProductCommand(
        sku: String = "TEST-SKU-001",
        name: String = "Test Product",
        description: String? = "A test description",
        priceCents: Int = 1999,
        idempotencyKey: String? = null
    ) = CreateProductCommand(
        sku = sku,
        name = name,
        description = description,
        priceCents = priceCents,
        idempotencyKey = idempotencyKey
    )

    /**
     * Creates an UpdateProductCommand with sensible defaults.
     */
    fun updateProductCommand(
        productId: UUID = UUID.randomUUID(),
        expectedVersion: Long = 1L,
        name: String = "Updated Product",
        description: String? = "Updated description",
        idempotencyKey: String? = null
    ) = UpdateProductCommand(
        productId = productId,
        expectedVersion = expectedVersion,
        name = name,
        description = description,
        idempotencyKey = idempotencyKey
    )

    /**
     * Creates a ChangePriceCommand with sensible defaults.
     */
    fun changePriceCommand(
        productId: UUID = UUID.randomUUID(),
        expectedVersion: Long = 1L,
        newPriceCents: Int = 2999,
        confirmLargeChange: Boolean = false,
        idempotencyKey: String? = null
    ) = ChangePriceCommand(
        productId = productId,
        expectedVersion = expectedVersion,
        newPriceCents = newPriceCents,
        confirmLargeChange = confirmLargeChange,
        idempotencyKey = idempotencyKey
    )

    /**
     * Creates an ActivateProductCommand with sensible defaults.
     */
    fun activateProductCommand(
        productId: UUID = UUID.randomUUID(),
        expectedVersion: Long = 1L,
        idempotencyKey: String? = null
    ) = ActivateProductCommand(
        productId = productId,
        expectedVersion = expectedVersion,
        idempotencyKey = idempotencyKey
    )

    /**
     * Creates a DiscontinueProductCommand with sensible defaults.
     */
    fun discontinueProductCommand(
        productId: UUID = UUID.randomUUID(),
        expectedVersion: Long = 1L,
        reason: String? = "No longer manufactured",
        idempotencyKey: String? = null
    ) = DiscontinueProductCommand(
        productId = productId,
        expectedVersion = expectedVersion,
        reason = reason,
        idempotencyKey = idempotencyKey
    )

    /**
     * Creates a DeleteProductCommand with sensible defaults.
     */
    fun deleteProductCommand(
        productId: UUID = UUID.randomUUID(),
        expectedVersion: Long = 1L,
        deletedBy: String? = "admin@example.com",
        idempotencyKey: String? = null
    ) = DeleteProductCommand(
        productId = productId,
        expectedVersion = expectedVersion,
        deletedBy = deletedBy,
        idempotencyKey = idempotencyKey
    )
}
