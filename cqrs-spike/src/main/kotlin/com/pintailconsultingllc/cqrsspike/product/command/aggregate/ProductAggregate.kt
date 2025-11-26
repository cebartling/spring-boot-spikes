package com.pintailconsultingllc.cqrsspike.product.command.aggregate

import com.pintailconsultingllc.cqrsspike.product.command.exception.ConcurrentModificationException
import com.pintailconsultingllc.cqrsspike.product.command.exception.InvalidStateTransitionException
import com.pintailconsultingllc.cqrsspike.product.command.exception.PriceChangeThresholdExceededException
import com.pintailconsultingllc.cqrsspike.product.command.exception.ProductDeletedException
import com.pintailconsultingllc.cqrsspike.product.command.exception.ProductInvariantViolationException
import com.pintailconsultingllc.cqrsspike.product.command.model.ProductStatus
import com.pintailconsultingllc.cqrsspike.product.event.ProductActivated
import com.pintailconsultingllc.cqrsspike.product.event.ProductCreated
import com.pintailconsultingllc.cqrsspike.product.event.ProductDeleted
import com.pintailconsultingllc.cqrsspike.product.event.ProductDiscontinued
import com.pintailconsultingllc.cqrsspike.product.event.ProductEvent
import com.pintailconsultingllc.cqrsspike.product.event.ProductPriceChanged
import com.pintailconsultingllc.cqrsspike.product.event.ProductUpdated
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.math.abs

/**
 * Product Aggregate Root.
 *
 * Encapsulates all business logic for product management.
 * Enforces invariants and generates domain events for all state changes.
 *
 * This is the command-side model in CQRS architecture.
 */
@ConsistentCopyVisibility
data class ProductAggregate private constructor(
    val id: UUID,
    val sku: String,
    val name: String,
    val description: String?,
    val priceCents: Int,
    val status: ProductStatus,
    val version: Long,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
    val deletedAt: OffsetDateTime?,
    private val uncommittedEvents: MutableList<ProductEvent> = mutableListOf()
) {

    /**
     * Returns list of uncommitted events and clears them.
     */
    fun getUncommittedEvents(): List<ProductEvent> {
        val events = uncommittedEvents.toList()
        uncommittedEvents.clear()
        return events
    }

    /**
     * Returns uncommitted events without clearing (for inspection).
     */
    fun peekUncommittedEvents(): List<ProductEvent> = uncommittedEvents.toList()

    /**
     * Checks if the product has been soft-deleted.
     */
    val isDeleted: Boolean get() = deletedAt != null

    companion object {
        // Business rule constants
        const val MAX_NAME_LENGTH = 255
        const val MIN_NAME_LENGTH = 1
        const val MAX_SKU_LENGTH = 50
        const val MIN_SKU_LENGTH = 3
        const val MAX_DESCRIPTION_LENGTH = 5000
        const val PRICE_CHANGE_THRESHOLD_PERCENT = 20.0
        private val SKU_PATTERN = Regex("^[A-Za-z0-9\\-]{$MIN_SKU_LENGTH,$MAX_SKU_LENGTH}$")

        /**
         * Factory method to create a new Product aggregate.
         *
         * @param sku Stock Keeping Unit (must be unique)
         * @param name Product name
         * @param description Optional product description
         * @param priceCents Price in cents (must be positive)
         * @return New ProductAggregate with ProductCreated event
         * @throws ProductInvariantViolationException if any invariant is violated
         */
        fun create(
            sku: String,
            name: String,
            description: String?,
            priceCents: Int
        ): ProductAggregate {
            val productId = UUID.randomUUID()
            val now = OffsetDateTime.now()

            // Validate invariants
            validateSku(sku, null)
            validateName(name, null)
            validateDescription(description, null)
            validatePrice(priceCents, null)

            val aggregate = ProductAggregate(
                id = productId,
                sku = sku.uppercase().trim(),
                name = name.trim(),
                description = description?.trim(),
                priceCents = priceCents,
                status = ProductStatus.DRAFT,
                version = 1,
                createdAt = now,
                updatedAt = now,
                deletedAt = null
            )

            // Generate creation event
            aggregate.uncommittedEvents.add(
                ProductCreated(
                    productId = productId,
                    version = 1,
                    sku = aggregate.sku,
                    name = aggregate.name,
                    description = aggregate.description,
                    priceCents = aggregate.priceCents,
                    status = aggregate.status
                )
            )

            return aggregate
        }

        /**
         * Reconstitutes aggregate from event stream.
         *
         * @param events List of events in order
         * @return Reconstituted ProductAggregate
         * @throws IllegalArgumentException if event stream is empty or invalid
         */
        fun reconstitute(events: List<ProductEvent>): ProductAggregate {
            require(events.isNotEmpty()) { "Cannot reconstitute aggregate from empty event stream" }

            val firstEvent = events.first()
            require(firstEvent is ProductCreated) {
                "First event must be ProductCreated, got ${firstEvent::class.simpleName}"
            }

            var aggregate = fromCreatedEvent(firstEvent)

            // Apply remaining events
            events.drop(1).forEach { event ->
                aggregate = aggregate.applyEvent(event)
            }

            return aggregate
        }

        private fun fromCreatedEvent(event: ProductCreated): ProductAggregate {
            return ProductAggregate(
                id = event.productId,
                sku = event.sku,
                name = event.name,
                description = event.description,
                priceCents = event.priceCents,
                status = event.status,
                version = event.version,
                createdAt = event.occurredAt,
                updatedAt = event.occurredAt,
                deletedAt = null
            )
        }

        // Validation helper methods
        private fun validateSku(sku: String, productId: UUID?) {
            val trimmed = sku.trim()
            if (trimmed.length < MIN_SKU_LENGTH || trimmed.length > MAX_SKU_LENGTH) {
                throw ProductInvariantViolationException(
                    productId = productId,
                    invariant = "SKU_LENGTH",
                    details = "SKU must be between $MIN_SKU_LENGTH and $MAX_SKU_LENGTH characters, got ${trimmed.length}"
                )
            }
            if (!SKU_PATTERN.matches(trimmed)) {
                throw ProductInvariantViolationException(
                    productId = productId,
                    invariant = "SKU_FORMAT",
                    details = "SKU must contain only alphanumeric characters and hyphens"
                )
            }
        }

        private fun validateName(name: String, productId: UUID?) {
            val trimmed = name.trim()
            if (trimmed.length < MIN_NAME_LENGTH || trimmed.length > MAX_NAME_LENGTH) {
                throw ProductInvariantViolationException(
                    productId = productId,
                    invariant = "NAME_LENGTH",
                    details = "Name must be between $MIN_NAME_LENGTH and $MAX_NAME_LENGTH characters, got ${trimmed.length}"
                )
            }
        }

        private fun validateDescription(description: String?, productId: UUID?) {
            description?.let {
                if (it.length > MAX_DESCRIPTION_LENGTH) {
                    throw ProductInvariantViolationException(
                        productId = productId,
                        invariant = "DESCRIPTION_LENGTH",
                        details = "Description must not exceed $MAX_DESCRIPTION_LENGTH characters, got ${it.length}"
                    )
                }
            }
        }

        private fun validatePrice(priceCents: Int, productId: UUID?) {
            if (priceCents <= 0) {
                throw ProductInvariantViolationException(
                    productId = productId,
                    invariant = "PRICE_POSITIVE",
                    details = "Price must be positive, got $priceCents cents"
                )
            }
        }
    }

    /**
     * Updates product details (name and description).
     * Only allowed for non-deleted products.
     *
     * @param newName New product name
     * @param newDescription New product description (optional)
     * @param expectedVersion Version for optimistic locking
     * @return Updated ProductAggregate with ProductUpdated event
     */
    fun update(
        newName: String,
        newDescription: String?,
        expectedVersion: Long
    ): ProductAggregate {
        ensureNotDeleted()
        ensureVersion(expectedVersion)
        validateName(newName, id)
        validateDescription(newDescription, id)

        val trimmedName = newName.trim()
        val trimmedDescription = newDescription?.trim()

        // No changes needed
        if (trimmedName == name && trimmedDescription == description) {
            return this
        }

        val newVersion = version + 1
        val now = OffsetDateTime.now()

        val updated = copy(
            name = trimmedName,
            description = trimmedDescription,
            version = newVersion,
            updatedAt = now
        )

        updated.uncommittedEvents.add(
            ProductUpdated(
                productId = id,
                version = newVersion,
                name = trimmedName,
                description = trimmedDescription,
                previousName = name,
                previousDescription = description
            )
        )

        return updated
    }

    /**
     * Changes the product price.
     *
     * For ACTIVE products, changes exceeding PRICE_CHANGE_THRESHOLD_PERCENT
     * require explicit confirmation.
     *
     * @param newPriceCents New price in cents
     * @param expectedVersion Version for optimistic locking
     * @param confirmLargeChange Set to true to confirm large price changes
     * @return Updated ProductAggregate with ProductPriceChanged event
     */
    fun changePrice(
        newPriceCents: Int,
        expectedVersion: Long,
        confirmLargeChange: Boolean = false
    ): ProductAggregate {
        ensureNotDeleted()
        ensureVersion(expectedVersion)
        validatePrice(newPriceCents, id)

        // No change needed
        if (newPriceCents == priceCents) {
            return this
        }

        val changePercentage = calculatePriceChangePercentage(newPriceCents)

        // Check threshold for ACTIVE products
        if (status == ProductStatus.ACTIVE &&
            abs(changePercentage) > PRICE_CHANGE_THRESHOLD_PERCENT &&
            !confirmLargeChange
        ) {
            throw PriceChangeThresholdExceededException(
                productId = id,
                currentPriceCents = priceCents,
                newPriceCents = newPriceCents,
                changePercentage = changePercentage,
                thresholdPercentage = PRICE_CHANGE_THRESHOLD_PERCENT
            )
        }

        val newVersion = version + 1
        val now = OffsetDateTime.now()

        val updated = copy(
            priceCents = newPriceCents,
            version = newVersion,
            updatedAt = now
        )

        updated.uncommittedEvents.add(
            ProductPriceChanged(
                productId = id,
                version = newVersion,
                newPriceCents = newPriceCents,
                previousPriceCents = priceCents,
                changePercentage = changePercentage
            )
        )

        return updated
    }

    /**
     * Activates the product (transitions to ACTIVE status).
     * Only valid from DRAFT status.
     *
     * @param expectedVersion Version for optimistic locking
     * @return Updated ProductAggregate with ProductActivated event
     */
    fun activate(expectedVersion: Long): ProductAggregate {
        ensureNotDeleted()
        ensureVersion(expectedVersion)
        ensureCanTransitionTo(ProductStatus.ACTIVE)

        val newVersion = version + 1
        val now = OffsetDateTime.now()

        val updated = copy(
            status = ProductStatus.ACTIVE,
            version = newVersion,
            updatedAt = now
        )

        updated.uncommittedEvents.add(
            ProductActivated(
                productId = id,
                version = newVersion,
                previousStatus = status
            )
        )

        return updated
    }

    /**
     * Discontinues the product (transitions to DISCONTINUED status).
     * Valid from DRAFT or ACTIVE status.
     *
     * @param expectedVersion Version for optimistic locking
     * @param reason Optional reason for discontinuation
     * @return Updated ProductAggregate with ProductDiscontinued event
     */
    fun discontinue(
        expectedVersion: Long,
        reason: String? = null
    ): ProductAggregate {
        ensureNotDeleted()
        ensureVersion(expectedVersion)
        ensureCanTransitionTo(ProductStatus.DISCONTINUED)

        val newVersion = version + 1
        val now = OffsetDateTime.now()

        val updated = copy(
            status = ProductStatus.DISCONTINUED,
            version = newVersion,
            updatedAt = now
        )

        updated.uncommittedEvents.add(
            ProductDiscontinued(
                productId = id,
                version = newVersion,
                previousStatus = status,
                reason = reason
            )
        )

        return updated
    }

    /**
     * Soft-deletes the product.
     * Sets deletedAt timestamp and generates ProductDeleted event.
     *
     * @param expectedVersion Version for optimistic locking
     * @param deletedBy Optional identifier of who deleted the product
     * @return Updated ProductAggregate with ProductDeleted event
     */
    fun delete(
        expectedVersion: Long,
        deletedBy: String? = null
    ): ProductAggregate {
        ensureNotDeleted()
        ensureVersion(expectedVersion)

        val newVersion = version + 1
        val now = OffsetDateTime.now()

        val updated = copy(
            version = newVersion,
            updatedAt = now,
            deletedAt = now
        )

        updated.uncommittedEvents.add(
            ProductDeleted(
                productId = id,
                version = newVersion,
                deletedBy = deletedBy
            )
        )

        return updated
    }

    /**
     * Applies an event to the aggregate (for reconstitution).
     * Does not add to uncommitted events.
     */
    private fun applyEvent(event: ProductEvent): ProductAggregate {
        return when (event) {
            is ProductCreated -> throw IllegalStateException("ProductCreated should only be first event")
            is ProductUpdated -> copy(
                name = event.name,
                description = event.description,
                version = event.version,
                updatedAt = event.occurredAt
            )
            is ProductPriceChanged -> copy(
                priceCents = event.newPriceCents,
                version = event.version,
                updatedAt = event.occurredAt
            )
            is ProductActivated -> copy(
                status = ProductStatus.ACTIVE,
                version = event.version,
                updatedAt = event.occurredAt
            )
            is ProductDiscontinued -> copy(
                status = ProductStatus.DISCONTINUED,
                version = event.version,
                updatedAt = event.occurredAt
            )
            is ProductDeleted -> copy(
                version = event.version,
                updatedAt = event.occurredAt,
                deletedAt = event.occurredAt
            )
        }
    }

    // Helper methods
    private fun ensureNotDeleted() {
        if (isDeleted) {
            throw ProductDeletedException(id)
        }
    }

    private fun ensureVersion(expectedVersion: Long) {
        if (version != expectedVersion) {
            throw ConcurrentModificationException(id, expectedVersion, version)
        }
    }

    private fun ensureCanTransitionTo(targetStatus: ProductStatus) {
        if (!status.canTransitionTo(targetStatus)) {
            throw InvalidStateTransitionException(id, status, targetStatus)
        }
    }

    private fun calculatePriceChangePercentage(newPriceCents: Int): Double {
        return ((newPriceCents - priceCents).toDouble() / priceCents) * 100.0
    }
}
