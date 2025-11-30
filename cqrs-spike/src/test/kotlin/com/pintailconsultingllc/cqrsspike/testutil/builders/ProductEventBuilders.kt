package com.pintailconsultingllc.cqrsspike.testutil.builders

import com.pintailconsultingllc.cqrsspike.product.command.model.ProductStatus
import com.pintailconsultingllc.cqrsspike.product.event.ProductActivated
import com.pintailconsultingllc.cqrsspike.product.event.ProductCreated
import com.pintailconsultingllc.cqrsspike.product.event.ProductDeleted
import com.pintailconsultingllc.cqrsspike.product.event.ProductDiscontinued
import com.pintailconsultingllc.cqrsspike.product.event.ProductPriceChanged
import com.pintailconsultingllc.cqrsspike.product.event.ProductUpdated
import java.util.UUID

/**
 * Factory methods for creating domain event instances in tests.
 *
 * Usage:
 * ```
 * val event = ProductEventBuilders.productCreated(
 *     productId = UUID.randomUUID(),
 *     sku = "TEST-001",
 *     name = "Test Product"
 * )
 * ```
 */
object ProductEventBuilders {

    /**
     * Creates a ProductCreated event with sensible defaults.
     */
    fun productCreated(
        productId: UUID = UUID.randomUUID(),
        version: Long = 1L,
        sku: String = "TEST-SKU-001",
        name: String = "Test Product",
        description: String? = "A test description",
        priceCents: Int = 1999,
        status: ProductStatus = ProductStatus.DRAFT
    ) = ProductCreated(
        productId = productId,
        version = version,
        sku = sku,
        name = name,
        description = description,
        priceCents = priceCents,
        status = status
    )

    /**
     * Creates a ProductUpdated event with sensible defaults.
     */
    fun productUpdated(
        productId: UUID = UUID.randomUUID(),
        version: Long = 2L,
        name: String = "Updated Product",
        description: String? = "Updated description",
        previousName: String = "Test Product",
        previousDescription: String? = "A test description"
    ) = ProductUpdated(
        productId = productId,
        version = version,
        name = name,
        description = description,
        previousName = previousName,
        previousDescription = previousDescription
    )

    /**
     * Creates a ProductPriceChanged event with sensible defaults.
     */
    fun productPriceChanged(
        productId: UUID = UUID.randomUUID(),
        version: Long = 2L,
        newPriceCents: Int = 2999,
        previousPriceCents: Int = 1999,
        changePercentage: Double = 50.0
    ) = ProductPriceChanged(
        productId = productId,
        version = version,
        newPriceCents = newPriceCents,
        previousPriceCents = previousPriceCents,
        changePercentage = changePercentage
    )

    /**
     * Creates a ProductActivated event with sensible defaults.
     */
    fun productActivated(
        productId: UUID = UUID.randomUUID(),
        version: Long = 2L,
        previousStatus: ProductStatus = ProductStatus.DRAFT
    ) = ProductActivated(
        productId = productId,
        version = version,
        previousStatus = previousStatus
    )

    /**
     * Creates a ProductDiscontinued event with sensible defaults.
     */
    fun productDiscontinued(
        productId: UUID = UUID.randomUUID(),
        version: Long = 2L,
        previousStatus: ProductStatus = ProductStatus.ACTIVE,
        reason: String? = "End of life"
    ) = ProductDiscontinued(
        productId = productId,
        version = version,
        previousStatus = previousStatus,
        reason = reason
    )

    /**
     * Creates a ProductDeleted event with sensible defaults.
     */
    fun productDeleted(
        productId: UUID = UUID.randomUUID(),
        version: Long = 2L,
        deletedBy: String? = "admin@example.com"
    ) = ProductDeleted(
        productId = productId,
        version = version,
        deletedBy = deletedBy
    )
}
