package com.pintailconsultingllc.cqrsspike.testutil.builders

import com.pintailconsultingllc.cqrsspike.product.query.model.ProductReadModel
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Fluent builder for creating ProductReadModel instances in tests.
 *
 * Usage:
 * ```
 * val readModel = ProductReadModelBuilder.aProductReadModel()
 *     .withSku("TEST-001")
 *     .withName("Test Product")
 *     .withPrice(1999)
 *     .build()
 * ```
 */
class ProductReadModelBuilder private constructor() {
    private var id: UUID = UUID.randomUUID()
    private var sku: String = "TEST-SKU-001"
    private var name: String = "Test Product"
    private var description: String? = "A test product description"
    private var priceCents: Int = 1999
    private var status: String = "ACTIVE"
    private var createdAt: OffsetDateTime = OffsetDateTime.now().minusDays(1)
    private var updatedAt: OffsetDateTime = OffsetDateTime.now()
    private var aggregateVersion: Long = 1L
    private var isDeleted: Boolean = false
    private var lastEventId: UUID? = null

    companion object {
        /**
         * Creates a new builder with default values.
         */
        fun aProductReadModel() = ProductReadModelBuilder()

        /**
         * Creates a builder for an ACTIVE product read model.
         */
        fun anActiveProductReadModel() = aProductReadModel().withStatus("ACTIVE")

        /**
         * Creates a builder for a DRAFT product read model.
         */
        fun aDraftProductReadModel() = aProductReadModel().withStatus("DRAFT")

        /**
         * Creates a builder for a DISCONTINUED product read model.
         */
        fun aDiscontinuedProductReadModel() = aProductReadModel().withStatus("DISCONTINUED")

        /**
         * Creates a builder for a deleted product read model.
         */
        fun aDeletedProductReadModel() = aProductReadModel().deleted()
    }

    fun withId(id: UUID) = apply { this.id = id }
    fun withSku(sku: String) = apply { this.sku = sku }
    fun withName(name: String) = apply { this.name = name }
    fun withDescription(description: String?) = apply { this.description = description }
    fun withPrice(priceCents: Int) = apply { this.priceCents = priceCents }
    fun withStatus(status: String) = apply { this.status = status }
    fun withVersion(version: Long) = apply { this.aggregateVersion = version }
    fun deleted() = apply { this.isDeleted = true }
    fun createdAt(createdAt: OffsetDateTime) = apply { this.createdAt = createdAt }
    fun updatedAt(updatedAt: OffsetDateTime) = apply { this.updatedAt = updatedAt }
    fun withLastEventId(eventId: UUID) = apply { this.lastEventId = eventId }

    /**
     * Builds the ProductReadModel with the configured values.
     */
    fun build(): ProductReadModel = ProductReadModel(
        id = id,
        sku = sku,
        name = name,
        description = description,
        priceCents = priceCents,
        status = status,
        createdAt = createdAt,
        updatedAt = updatedAt,
        aggregateVersion = aggregateVersion,
        isDeleted = isDeleted,
        priceDisplay = formatPrice(priceCents),
        searchText = buildSearchText(),
        lastEventId = lastEventId
    )

    private fun formatPrice(cents: Int): String {
        val dollars = cents / 100
        val remainingCents = cents % 100
        return "$$dollars.${remainingCents.toString().padStart(2, '0')}"
    }

    private fun buildSearchText(): String =
        listOfNotNull(name, description).joinToString(" ")
}
