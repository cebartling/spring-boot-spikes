package com.pintailconsultingllc.cqrsspike.testutil.builders

import com.pintailconsultingllc.cqrsspike.product.command.aggregate.ProductAggregate
import com.pintailconsultingllc.cqrsspike.product.command.model.ProductStatus

/**
 * Fluent builder for creating ProductAggregate instances in tests.
 *
 * Usage:
 * ```
 * val product = ProductAggregateBuilder.aProduct()
 *     .withSku("TEST-001")
 *     .withName("Test Product")
 *     .withPrice(1999)
 *     .build()
 * ```
 */
class ProductAggregateBuilder private constructor() {
    private var sku: String = "TEST-SKU-001"
    private var name: String = "Test Product"
    private var description: String? = "A test product description"
    private var priceCents: Int = 1999
    private var status: ProductStatus = ProductStatus.DRAFT

    companion object {
        /**
         * Creates a new builder with default values.
         */
        fun aProduct() = ProductAggregateBuilder()

        /**
         * Creates a builder pre-configured for a valid product.
         */
        fun aValidProduct() = aProduct()
            .withSku("VALID-001")
            .withName("Valid Product")
            .withPrice(2999)

        /**
         * Creates a builder for a product in DRAFT status.
         */
        fun aDraftProduct() = aProduct().withStatus(ProductStatus.DRAFT)

        /**
         * Creates a builder for a product in ACTIVE status.
         */
        fun anActiveProduct() = aProduct().withStatus(ProductStatus.ACTIVE)

        /**
         * Creates a builder for a product in DISCONTINUED status.
         */
        fun aDiscontinuedProduct() = aProduct().withStatus(ProductStatus.DISCONTINUED)
    }

    fun withSku(sku: String) = apply { this.sku = sku }
    fun withName(name: String) = apply { this.name = name }
    fun withDescription(description: String?) = apply { this.description = description }
    fun withPrice(priceCents: Int) = apply { this.priceCents = priceCents }
    fun withStatus(status: ProductStatus) = apply { this.status = status }

    /**
     * Builds the ProductAggregate with the configured values.
     *
     * Note: If status is ACTIVE, the aggregate will first be created in DRAFT
     * and then activated. If status is DISCONTINUED, it will be created,
     * activated, and then discontinued.
     */
    fun build(): ProductAggregate {
        val aggregate = ProductAggregate.create(
            sku = sku,
            name = name,
            description = description,
            priceCents = priceCents
        )

        return when (status) {
            ProductStatus.DRAFT -> aggregate
            ProductStatus.ACTIVE -> aggregate.activate(expectedVersion = 1L)
            ProductStatus.DISCONTINUED -> {
                // Must go through ACTIVE to reach DISCONTINUED
                aggregate
                    .activate(expectedVersion = 1L)
                    .discontinue(expectedVersion = 2L)
            }
        }
    }

    /**
     * Builds and clears uncommitted events (useful for testing state only).
     */
    fun buildWithClearedEvents(): ProductAggregate {
        val aggregate = build()
        aggregate.getUncommittedEvents() // Clear events
        return aggregate
    }
}
