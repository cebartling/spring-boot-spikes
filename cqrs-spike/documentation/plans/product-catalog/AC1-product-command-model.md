# Implementation Plan: AC1 - Product Command Model

**Feature:** Product Catalog (CQRS Architecture)
**Acceptance Criteria:** AC1 - Product Command Model
**Status:** Planning
**Estimated Effort:** 3-4 days

---

## Overview

This implementation plan details the creation of the `Product` aggregate root for the command side of the CQRS architecture. The aggregate will enforce business invariants, generate domain events for all state changes, support optimistic concurrency, and enable reconstitution from event streams.

## Prerequisites

Before starting this implementation:

- [ ] PostgreSQL database is running with `command_model` schema created
- [ ] Event store schema exists (see AC2 implementation plan)
- [ ] Spring Boot project structure is in place
- [ ] R2DBC dependencies are configured in `build.gradle.kts`

## Acceptance Criteria Reference

From the feature specification:

> - A `Product` aggregate root exists in the `command_model` schema
> - The aggregate enforces business invariants (e.g., price must be positive, SKU must be unique)
> - The aggregate generates domain events for all state changes
> - Optimistic concurrency is implemented using version numbers
> - The aggregate can be reconstituted from its event stream
> - All command operations return `Mono<T>` for reactive compatibility

---

## Implementation Steps

### Step 1: Create Database Schema

**Objective:** Create the `command_model` schema and `product` table for storing aggregate state.

#### 1.1 Create Schema Migration File

**File:** `docker/init-scripts/03-command-model-schema.sql`

```sql
-- Command Model Schema for Product Aggregate
CREATE SCHEMA IF NOT EXISTS command_model;

-- Product Aggregate Table
CREATE TABLE command_model.product (
    id UUID PRIMARY KEY,
    sku VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    price_cents INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    CONSTRAINT chk_price_positive CHECK (price_cents > 0),
    CONSTRAINT chk_status_valid CHECK (status IN ('DRAFT', 'ACTIVE', 'DISCONTINUED')),
    CONSTRAINT chk_sku_format CHECK (sku ~ '^[A-Za-z0-9\-]{3,50}$')
);

-- Unique constraint on SKU (excluding soft-deleted products)
CREATE UNIQUE INDEX idx_product_sku_unique
    ON command_model.product(sku)
    WHERE deleted_at IS NULL;

-- Index for status queries
CREATE INDEX idx_product_status
    ON command_model.product(status)
    WHERE deleted_at IS NULL;

-- Index for version-based queries (optimistic locking)
CREATE INDEX idx_product_version
    ON command_model.product(id, version);
```

#### 1.2 Verification

- [ ] Schema created successfully
- [ ] Constraints are enforced
- [ ] Indexes are created

---

### Step 2: Define Product Status Enum

**Objective:** Create a type-safe enum for product status with valid state transitions.

#### 2.1 Create Status Enum

**File:** `src/main/kotlin/com/example/cqrsspike/product/command/model/ProductStatus.kt`

```kotlin
package com.example.cqrsspike.product.command.model

/**
 * Represents the lifecycle status of a Product.
 *
 * State transitions:
 * - DRAFT → ACTIVE (via activate)
 * - DRAFT → DISCONTINUED (via discontinue)
 * - ACTIVE → DISCONTINUED (via discontinue)
 * - DISCONTINUED is terminal (no transitions out)
 */
enum class ProductStatus {
    /** Product is being prepared, not yet visible to customers */
    DRAFT,

    /** Product is live and available for purchase */
    ACTIVE,

    /** Product is no longer available (terminal state) */
    DISCONTINUED;

    /**
     * Checks if transition to the target status is valid.
     * @param target The desired new status
     * @return true if the transition is allowed
     */
    fun canTransitionTo(target: ProductStatus): Boolean = when (this) {
        DRAFT -> target in setOf(ACTIVE, DISCONTINUED)
        ACTIVE -> target == DISCONTINUED
        DISCONTINUED -> false
    }

    /**
     * Returns all valid target statuses from current status.
     */
    fun validTransitions(): Set<ProductStatus> = when (this) {
        DRAFT -> setOf(ACTIVE, DISCONTINUED)
        ACTIVE -> setOf(DISCONTINUED)
        DISCONTINUED -> emptySet()
    }
}
```

#### 2.2 Create Unit Tests for Status Transitions

**File:** `src/test/kotlin/com/example/cqrsspike/product/command/model/ProductStatusTest.kt`

```kotlin
package com.example.cqrsspike.product.command.model

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@DisplayName("ProductStatus")
class ProductStatusTest {

    @Nested
    @DisplayName("State Transitions")
    inner class StateTransitions {

        @Test
        @DisplayName("DRAFT can transition to ACTIVE")
        fun draftCanTransitionToActive() {
            assertTrue(ProductStatus.DRAFT.canTransitionTo(ProductStatus.ACTIVE))
        }

        @Test
        @DisplayName("DRAFT can transition to DISCONTINUED")
        fun draftCanTransitionToDiscontinued() {
            assertTrue(ProductStatus.DRAFT.canTransitionTo(ProductStatus.DISCONTINUED))
        }

        @Test
        @DisplayName("DRAFT cannot transition to DRAFT")
        fun draftCannotTransitionToDraft() {
            assertFalse(ProductStatus.DRAFT.canTransitionTo(ProductStatus.DRAFT))
        }

        @Test
        @DisplayName("ACTIVE can transition to DISCONTINUED")
        fun activeCanTransitionToDiscontinued() {
            assertTrue(ProductStatus.ACTIVE.canTransitionTo(ProductStatus.DISCONTINUED))
        }

        @Test
        @DisplayName("ACTIVE cannot transition to DRAFT")
        fun activeCannotTransitionToDraft() {
            assertFalse(ProductStatus.ACTIVE.canTransitionTo(ProductStatus.DRAFT))
        }

        @Test
        @DisplayName("DISCONTINUED cannot transition to any status")
        fun discontinuedIsTerminal() {
            ProductStatus.entries.forEach { target ->
                assertFalse(ProductStatus.DISCONTINUED.canTransitionTo(target))
            }
        }
    }

    @Nested
    @DisplayName("Valid Transitions")
    inner class ValidTransitions {

        @Test
        @DisplayName("DRAFT has two valid transitions")
        fun draftValidTransitions() {
            val expected = setOf(ProductStatus.ACTIVE, ProductStatus.DISCONTINUED)
            assertEquals(expected, ProductStatus.DRAFT.validTransitions())
        }

        @Test
        @DisplayName("ACTIVE has one valid transition")
        fun activeValidTransitions() {
            val expected = setOf(ProductStatus.DISCONTINUED)
            assertEquals(expected, ProductStatus.ACTIVE.validTransitions())
        }

        @Test
        @DisplayName("DISCONTINUED has no valid transitions")
        fun discontinuedValidTransitions() {
            assertTrue(ProductStatus.DISCONTINUED.validTransitions().isEmpty())
        }
    }
}
```

#### 2.3 Verification

- [ ] Enum compiles without errors
- [ ] All unit tests pass
- [ ] State transition logic is correct

---

### Step 3: Create Domain Events

**Objective:** Define immutable domain events that capture all state changes to the Product aggregate.

#### 3.1 Create Base Event Interface

**File:** `src/main/kotlin/com/example/cqrsspike/product/event/ProductEvent.kt`

```kotlin
package com.example.cqrsspike.product.event

import java.time.OffsetDateTime
import java.util.UUID

/**
 * Marker interface for all Product domain events.
 * All events are immutable and capture a specific state change.
 */
sealed interface ProductEvent {
    /** Unique identifier for this event */
    val eventId: UUID

    /** The aggregate (product) this event belongs to */
    val productId: UUID

    /** When this event occurred */
    val occurredAt: OffsetDateTime

    /** Version of the aggregate after this event */
    val version: Long
}
```

#### 3.2 Create Specific Event Classes

**File:** `src/main/kotlin/com/example/cqrsspike/product/event/ProductEvents.kt`

```kotlin
package com.example.cqrsspike.product.event

import com.example.cqrsspike.product.command.model.ProductStatus
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
```

#### 3.3 Verification

- [ ] All event classes compile
- [ ] Events are immutable (data classes with val properties)
- [ ] Each event captures relevant state change information
- [ ] Previous values are captured for auditing

---

### Step 4: Create Domain Exceptions

**Objective:** Define specific exceptions for business rule violations.

#### 4.1 Create Exception Classes

**File:** `src/main/kotlin/com/example/cqrsspike/product/command/exception/ProductExceptions.kt`

```kotlin
package com.example.cqrsspike.product.command.exception

import com.example.cqrsspike.product.command.model.ProductStatus
import java.util.UUID

/**
 * Base exception for product domain errors.
 */
sealed class ProductDomainException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

/**
 * Thrown when a product with the given ID is not found.
 */
class ProductNotFoundException(
    val productId: UUID
) : ProductDomainException("Product not found: $productId")

/**
 * Thrown when attempting to create a product with a duplicate SKU.
 */
class DuplicateSkuException(
    val sku: String
) : ProductDomainException("Product with SKU '$sku' already exists")

/**
 * Thrown when an invalid state transition is attempted.
 */
class InvalidStateTransitionException(
    val productId: UUID,
    val currentStatus: ProductStatus,
    val targetStatus: ProductStatus
) : ProductDomainException(
    "Cannot transition product $productId from $currentStatus to $targetStatus. " +
    "Valid transitions: ${currentStatus.validTransitions()}"
)

/**
 * Thrown when a business invariant is violated.
 */
class ProductInvariantViolationException(
    val productId: UUID?,
    val invariant: String,
    val details: String
) : ProductDomainException("Invariant violation for product ${productId ?: "new"}: $invariant - $details")

/**
 * Thrown when optimistic locking detects a concurrent modification.
 */
class ConcurrentModificationException(
    val productId: UUID,
    val expectedVersion: Long,
    val actualVersion: Long
) : ProductDomainException(
    "Concurrent modification detected for product $productId. " +
    "Expected version: $expectedVersion, actual version: $actualVersion"
)

/**
 * Thrown when attempting to modify a deleted product.
 */
class ProductDeletedException(
    val productId: UUID
) : ProductDomainException("Product $productId has been deleted and cannot be modified")

/**
 * Thrown when price change exceeds allowed threshold without confirmation.
 */
class PriceChangeThresholdExceededException(
    val productId: UUID,
    val currentPriceCents: Int,
    val newPriceCents: Int,
    val changePercentage: Double,
    val thresholdPercentage: Double
) : ProductDomainException(
    "Price change of ${String.format("%.2f", changePercentage)}% exceeds threshold of ${thresholdPercentage}% " +
    "for product $productId. Confirmation required."
)
```

#### 4.2 Verification

- [ ] All exceptions compile
- [ ] Exceptions extend sealed base class
- [ ] Error messages are descriptive and include context

---

### Step 5: Create Product Aggregate

**Objective:** Implement the Product aggregate root with business invariants and event generation.

#### 5.1 Create Product Aggregate Class

**File:** `src/main/kotlin/com/example/cqrsspike/product/command/aggregate/ProductAggregate.kt`

```kotlin
package com.example.cqrsspike.product.command.aggregate

import com.example.cqrsspike.product.command.exception.*
import com.example.cqrsspike.product.command.model.ProductStatus
import com.example.cqrsspike.product.event.*
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Product Aggregate Root.
 *
 * Encapsulates all business logic for product management.
 * Enforces invariants and generates domain events for all state changes.
 *
 * This is the command-side model in CQRS architecture.
 */
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
            kotlin.math.abs(changePercentage) > PRICE_CHANGE_THRESHOLD_PERCENT &&
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
```

#### 5.2 Verification

- [ ] Aggregate class compiles
- [ ] All business invariants are enforced
- [ ] Events are generated for all state changes
- [ ] Optimistic concurrency is implemented
- [ ] Event reconstitution works correctly

---

### Step 6: Create Unit Tests for Product Aggregate

**Objective:** Comprehensive unit tests for all aggregate behavior.

#### 6.1 Create Aggregate Test Class

**File:** `src/test/kotlin/com/example/cqrsspike/product/command/aggregate/ProductAggregateTest.kt`

```kotlin
package com.example.cqrsspike.product.command.aggregate

import com.example.cqrsspike.product.command.exception.*
import com.example.cqrsspike.product.command.model.ProductStatus
import com.example.cqrsspike.product.event.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.UUID
import kotlin.test.*

@DisplayName("ProductAggregate")
class ProductAggregateTest {

    companion object {
        const val VALID_SKU = "PROD-001"
        const val VALID_NAME = "Test Product"
        const val VALID_DESCRIPTION = "A test product description"
        const val VALID_PRICE = 1999 // $19.99
    }

    @Nested
    @DisplayName("Creation")
    inner class Creation {

        @Test
        @DisplayName("should create product with valid data")
        fun shouldCreateProductWithValidData() {
            val aggregate = ProductAggregate.create(
                sku = VALID_SKU,
                name = VALID_NAME,
                description = VALID_DESCRIPTION,
                priceCents = VALID_PRICE
            )

            assertEquals(VALID_SKU.uppercase(), aggregate.sku)
            assertEquals(VALID_NAME, aggregate.name)
            assertEquals(VALID_DESCRIPTION, aggregate.description)
            assertEquals(VALID_PRICE, aggregate.priceCents)
            assertEquals(ProductStatus.DRAFT, aggregate.status)
            assertEquals(1L, aggregate.version)
            assertFalse(aggregate.isDeleted)
        }

        @Test
        @DisplayName("should generate ProductCreated event")
        fun shouldGenerateProductCreatedEvent() {
            val aggregate = ProductAggregate.create(
                sku = VALID_SKU,
                name = VALID_NAME,
                description = VALID_DESCRIPTION,
                priceCents = VALID_PRICE
            )

            val events = aggregate.getUncommittedEvents()
            assertEquals(1, events.size)

            val event = events.first()
            assertIs<ProductCreated>(event)
            assertEquals(aggregate.id, event.productId)
            assertEquals(VALID_SKU.uppercase(), event.sku)
            assertEquals(VALID_NAME, event.name)
            assertEquals(1L, event.version)
        }

        @Test
        @DisplayName("should create product with null description")
        fun shouldCreateProductWithNullDescription() {
            val aggregate = ProductAggregate.create(
                sku = VALID_SKU,
                name = VALID_NAME,
                description = null,
                priceCents = VALID_PRICE
            )

            assertNull(aggregate.description)
        }

        @Test
        @DisplayName("should trim and uppercase SKU")
        fun shouldTrimAndUppercaseSku() {
            val aggregate = ProductAggregate.create(
                sku = "  prod-001  ",
                name = VALID_NAME,
                description = null,
                priceCents = VALID_PRICE
            )

            assertEquals("PROD-001", aggregate.sku)
        }

        @ParameterizedTest
        @ValueSource(ints = [0, -1, -100])
        @DisplayName("should reject non-positive price")
        fun shouldRejectNonPositivePrice(price: Int) {
            val exception = assertFailsWith<ProductInvariantViolationException> {
                ProductAggregate.create(
                    sku = VALID_SKU,
                    name = VALID_NAME,
                    description = null,
                    priceCents = price
                )
            }

            assertEquals("PRICE_POSITIVE", exception.invariant)
        }

        @Test
        @DisplayName("should reject empty name")
        fun shouldRejectEmptyName() {
            val exception = assertFailsWith<ProductInvariantViolationException> {
                ProductAggregate.create(
                    sku = VALID_SKU,
                    name = "",
                    description = null,
                    priceCents = VALID_PRICE
                )
            }

            assertEquals("NAME_LENGTH", exception.invariant)
        }

        @Test
        @DisplayName("should reject SKU with invalid characters")
        fun shouldRejectSkuWithInvalidCharacters() {
            val exception = assertFailsWith<ProductInvariantViolationException> {
                ProductAggregate.create(
                    sku = "PROD@001",
                    name = VALID_NAME,
                    description = null,
                    priceCents = VALID_PRICE
                )
            }

            assertEquals("SKU_FORMAT", exception.invariant)
        }

        @Test
        @DisplayName("should reject SKU that is too short")
        fun shouldRejectSkuTooShort() {
            val exception = assertFailsWith<ProductInvariantViolationException> {
                ProductAggregate.create(
                    sku = "AB",
                    name = VALID_NAME,
                    description = null,
                    priceCents = VALID_PRICE
                )
            }

            assertEquals("SKU_LENGTH", exception.invariant)
        }
    }

    @Nested
    @DisplayName("Update")
    inner class Update {

        @Test
        @DisplayName("should update name and description")
        fun shouldUpdateNameAndDescription() {
            val aggregate = createValidAggregate()
            val updated = aggregate.update(
                newName = "Updated Name",
                newDescription = "Updated description",
                expectedVersion = 1L
            )

            assertEquals("Updated Name", updated.name)
            assertEquals("Updated description", updated.description)
            assertEquals(2L, updated.version)
        }

        @Test
        @DisplayName("should generate ProductUpdated event")
        fun shouldGenerateProductUpdatedEvent() {
            val aggregate = createValidAggregate()
            aggregate.getUncommittedEvents() // Clear creation event

            val updated = aggregate.update(
                newName = "Updated Name",
                newDescription = "Updated description",
                expectedVersion = 1L
            )

            val events = updated.getUncommittedEvents()
            assertEquals(1, events.size)

            val event = events.first()
            assertIs<ProductUpdated>(event)
            assertEquals("Updated Name", event.name)
            assertEquals(VALID_NAME, event.previousName)
        }

        @Test
        @DisplayName("should not generate event when no changes")
        fun shouldNotGenerateEventWhenNoChanges() {
            val aggregate = createValidAggregate()
            aggregate.getUncommittedEvents() // Clear creation event

            val updated = aggregate.update(
                newName = VALID_NAME,
                newDescription = VALID_DESCRIPTION,
                expectedVersion = 1L
            )

            assertTrue(updated.getUncommittedEvents().isEmpty())
            assertEquals(1L, updated.version) // Version unchanged
        }

        @Test
        @DisplayName("should fail with wrong version")
        fun shouldFailWithWrongVersion() {
            val aggregate = createValidAggregate()

            assertFailsWith<ConcurrentModificationException> {
                aggregate.update(
                    newName = "Updated Name",
                    newDescription = null,
                    expectedVersion = 999L
                )
            }
        }

        @Test
        @DisplayName("should fail when deleted")
        fun shouldFailWhenDeleted() {
            val aggregate = createValidAggregate()
            val deleted = aggregate.delete(expectedVersion = 1L)

            assertFailsWith<ProductDeletedException> {
                deleted.update(
                    newName = "Updated Name",
                    newDescription = null,
                    expectedVersion = 2L
                )
            }
        }
    }

    @Nested
    @DisplayName("Price Change")
    inner class PriceChange {

        @Test
        @DisplayName("should change price for DRAFT product")
        fun shouldChangePriceForDraftProduct() {
            val aggregate = createValidAggregate()
            val updated = aggregate.changePrice(
                newPriceCents = 2999,
                expectedVersion = 1L
            )

            assertEquals(2999, updated.priceCents)
            assertEquals(2L, updated.version)
        }

        @Test
        @DisplayName("should generate ProductPriceChanged event")
        fun shouldGeneratePriceChangedEvent() {
            val aggregate = createValidAggregate()
            aggregate.getUncommittedEvents()

            val updated = aggregate.changePrice(
                newPriceCents = 2999,
                expectedVersion = 1L
            )

            val events = updated.getUncommittedEvents()
            assertEquals(1, events.size)

            val event = events.first()
            assertIs<ProductPriceChanged>(event)
            assertEquals(2999, event.newPriceCents)
            assertEquals(VALID_PRICE, event.previousPriceCents)
        }

        @Test
        @DisplayName("should allow large price change for DRAFT")
        fun shouldAllowLargePriceChangeForDraft() {
            val aggregate = createValidAggregate()

            // 50% price increase - no confirmation needed for DRAFT
            val updated = aggregate.changePrice(
                newPriceCents = 2999,
                expectedVersion = 1L,
                confirmLargeChange = false
            )

            assertEquals(2999, updated.priceCents)
        }

        @Test
        @DisplayName("should require confirmation for large price change on ACTIVE product")
        fun shouldRequireConfirmationForLargePriceChange() {
            val aggregate = createValidAggregate()
                .activate(expectedVersion = 1L)

            assertFailsWith<PriceChangeThresholdExceededException> {
                aggregate.changePrice(
                    newPriceCents = 999, // ~50% decrease
                    expectedVersion = 2L,
                    confirmLargeChange = false
                )
            }
        }

        @Test
        @DisplayName("should allow large price change with confirmation")
        fun shouldAllowLargePriceChangeWithConfirmation() {
            val aggregate = createValidAggregate()
                .activate(expectedVersion = 1L)

            val updated = aggregate.changePrice(
                newPriceCents = 999,
                expectedVersion = 2L,
                confirmLargeChange = true
            )

            assertEquals(999, updated.priceCents)
        }
    }

    @Nested
    @DisplayName("Status Transitions")
    inner class StatusTransitions {

        @Test
        @DisplayName("should activate DRAFT product")
        fun shouldActivateDraftProduct() {
            val aggregate = createValidAggregate()
            val activated = aggregate.activate(expectedVersion = 1L)

            assertEquals(ProductStatus.ACTIVE, activated.status)
            assertEquals(2L, activated.version)
        }

        @Test
        @DisplayName("should discontinue DRAFT product")
        fun shouldDiscontinueDraftProduct() {
            val aggregate = createValidAggregate()
            val discontinued = aggregate.discontinue(
                expectedVersion = 1L,
                reason = "No longer manufactured"
            )

            assertEquals(ProductStatus.DISCONTINUED, discontinued.status)
        }

        @Test
        @DisplayName("should discontinue ACTIVE product")
        fun shouldDiscontinueActiveProduct() {
            val aggregate = createValidAggregate()
                .activate(expectedVersion = 1L)

            val discontinued = aggregate.discontinue(expectedVersion = 2L)

            assertEquals(ProductStatus.DISCONTINUED, discontinued.status)
        }

        @Test
        @DisplayName("should not allow ACTIVE -> DRAFT")
        fun shouldNotAllowActiveBackToDraft() {
            val aggregate = createValidAggregate()
                .activate(expectedVersion = 1L)

            // There's no method to go back to DRAFT, but test the status transition
            assertFalse(ProductStatus.ACTIVE.canTransitionTo(ProductStatus.DRAFT))
        }

        @Test
        @DisplayName("should not allow transitions from DISCONTINUED")
        fun shouldNotAllowTransitionsFromDiscontinued() {
            val aggregate = createValidAggregate()
                .discontinue(expectedVersion = 1L)

            assertFailsWith<InvalidStateTransitionException> {
                aggregate.activate(expectedVersion = 2L)
            }
        }
    }

    @Nested
    @DisplayName("Deletion")
    inner class Deletion {

        @Test
        @DisplayName("should soft delete product")
        fun shouldSoftDeleteProduct() {
            val aggregate = createValidAggregate()
            val deleted = aggregate.delete(
                expectedVersion = 1L,
                deletedBy = "admin@example.com"
            )

            assertTrue(deleted.isDeleted)
            assertNotNull(deleted.deletedAt)
            assertEquals(2L, deleted.version)
        }

        @Test
        @DisplayName("should generate ProductDeleted event")
        fun shouldGenerateDeletedEvent() {
            val aggregate = createValidAggregate()
            aggregate.getUncommittedEvents()

            val deleted = aggregate.delete(
                expectedVersion = 1L,
                deletedBy = "admin@example.com"
            )

            val events = deleted.getUncommittedEvents()
            assertEquals(1, events.size)

            val event = events.first()
            assertIs<ProductDeleted>(event)
            assertEquals("admin@example.com", event.deletedBy)
        }

        @Test
        @DisplayName("should not allow deleting already deleted product")
        fun shouldNotAllowDeletingAlreadyDeleted() {
            val aggregate = createValidAggregate()
                .delete(expectedVersion = 1L)

            assertFailsWith<ProductDeletedException> {
                aggregate.delete(expectedVersion = 2L)
            }
        }
    }

    @Nested
    @DisplayName("Event Reconstitution")
    inner class EventReconstitution {

        @Test
        @DisplayName("should reconstitute from events")
        fun shouldReconstituteFromEvents() {
            // Create original aggregate
            val original = createValidAggregate()
                .update("Updated Name", "Updated description", 1L)
                .changePrice(2999, 2L)
                .activate(3L)

            // Collect all events (in real system, these come from event store)
            val events = mutableListOf<ProductEvent>()

            // Manually reconstruct events as they would be stored
            val createdEvent = ProductCreated(
                productId = original.id,
                version = 1,
                sku = VALID_SKU.uppercase(),
                name = VALID_NAME,
                description = VALID_DESCRIPTION,
                priceCents = VALID_PRICE
            )
            val updatedEvent = ProductUpdated(
                productId = original.id,
                version = 2,
                name = "Updated Name",
                description = "Updated description",
                previousName = VALID_NAME,
                previousDescription = VALID_DESCRIPTION
            )
            val priceChangedEvent = ProductPriceChanged(
                productId = original.id,
                version = 3,
                newPriceCents = 2999,
                previousPriceCents = VALID_PRICE,
                changePercentage = 50.025
            )
            val activatedEvent = ProductActivated(
                productId = original.id,
                version = 4,
                previousStatus = ProductStatus.DRAFT
            )

            events.addAll(listOf(createdEvent, updatedEvent, priceChangedEvent, activatedEvent))

            // Reconstitute
            val reconstituted = ProductAggregate.reconstitute(events)

            assertEquals(original.id, reconstituted.id)
            assertEquals("Updated Name", reconstituted.name)
            assertEquals(2999, reconstituted.priceCents)
            assertEquals(ProductStatus.ACTIVE, reconstituted.status)
            assertEquals(4L, reconstituted.version)
        }

        @Test
        @DisplayName("should fail reconstitution with empty events")
        fun shouldFailReconstitutionWithEmptyEvents() {
            assertFailsWith<IllegalArgumentException> {
                ProductAggregate.reconstitute(emptyList())
            }
        }

        @Test
        @DisplayName("should fail reconstitution without ProductCreated first")
        fun shouldFailReconstitutionWithoutCreatedFirst() {
            val event = ProductUpdated(
                productId = UUID.randomUUID(),
                version = 1,
                name = "Name",
                description = null,
                previousName = "Old",
                previousDescription = null
            )

            assertFailsWith<IllegalArgumentException> {
                ProductAggregate.reconstitute(listOf(event))
            }
        }
    }

    // Helper method
    private fun createValidAggregate(): ProductAggregate {
        return ProductAggregate.create(
            sku = VALID_SKU,
            name = VALID_NAME,
            description = VALID_DESCRIPTION,
            priceCents = VALID_PRICE
        )
    }
}
```

#### 6.2 Verification

- [ ] All tests pass
- [ ] Coverage meets 80% threshold for aggregate class
- [ ] Edge cases are tested

---

### Step 7: Create R2DBC Entity and Repository

**Objective:** Create the database entity mapping and reactive repository.

#### 7.1 Create Product Entity (Database Mapping)

**File:** `src/main/kotlin/com/example/cqrsspike/product/command/infrastructure/ProductEntity.kt`

```kotlin
package com.example.cqrsspike.product.command.infrastructure

import com.example.cqrsspike.product.command.model.ProductStatus
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Version
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.OffsetDateTime
import java.util.UUID

/**
 * R2DBC entity for Product command model persistence.
 * Maps to command_model.product table.
 */
@Table("command_model.product")
data class ProductEntity(
    @Id
    val id: UUID,

    @Column("sku")
    val sku: String,

    @Column("name")
    val name: String,

    @Column("description")
    val description: String?,

    @Column("price_cents")
    val priceCents: Int,

    @Column("status")
    val status: String,

    @Version
    @Column("version")
    val version: Long,

    @Column("created_at")
    val createdAt: OffsetDateTime,

    @Column("updated_at")
    val updatedAt: OffsetDateTime,

    @Column("deleted_at")
    val deletedAt: OffsetDateTime?
)
```

#### 7.2 Create Product Repository

**File:** `src/main/kotlin/com/example/cqrsspike/product/command/infrastructure/ProductCommandRepository.kt`

```kotlin
package com.example.cqrsspike.product.command.infrastructure

import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * Reactive repository for Product command model.
 */
@Repository
interface ProductCommandRepository : ReactiveCrudRepository<ProductEntity, UUID> {

    /**
     * Find product by ID, excluding soft-deleted products.
     */
    @Query("SELECT * FROM command_model.product WHERE id = :id AND deleted_at IS NULL")
    fun findByIdNotDeleted(id: UUID): Mono<ProductEntity>

    /**
     * Find product by SKU, excluding soft-deleted products.
     */
    @Query("SELECT * FROM command_model.product WHERE sku = :sku AND deleted_at IS NULL")
    fun findBySku(sku: String): Mono<ProductEntity>

    /**
     * Check if SKU exists (excluding soft-deleted products).
     */
    @Query("SELECT EXISTS(SELECT 1 FROM command_model.product WHERE sku = :sku AND deleted_at IS NULL)")
    fun existsBySku(sku: String): Mono<Boolean>

    /**
     * Find product by ID with specific version (for optimistic locking verification).
     */
    @Query("SELECT * FROM command_model.product WHERE id = :id AND version = :version AND deleted_at IS NULL")
    fun findByIdAndVersion(id: UUID, version: Long): Mono<ProductEntity>
}
```

#### 7.3 Create Aggregate Mapper

**File:** `src/main/kotlin/com/example/cqrsspike/product/command/infrastructure/ProductAggregateMapper.kt`

```kotlin
package com.example.cqrsspike.product.command.infrastructure

import com.example.cqrsspike.product.command.aggregate.ProductAggregate
import com.example.cqrsspike.product.command.model.ProductStatus

/**
 * Maps between ProductAggregate and ProductEntity.
 */
object ProductAggregateMapper {

    /**
     * Converts ProductAggregate to ProductEntity for persistence.
     */
    fun toEntity(aggregate: ProductAggregate): ProductEntity {
        return ProductEntity(
            id = aggregate.id,
            sku = aggregate.sku,
            name = aggregate.name,
            description = aggregate.description,
            priceCents = aggregate.priceCents,
            status = aggregate.status.name,
            version = aggregate.version,
            createdAt = aggregate.createdAt,
            updatedAt = aggregate.updatedAt,
            deletedAt = aggregate.deletedAt
        )
    }

    /**
     * Note: Full aggregate reconstitution should be done from events.
     * This method is for snapshot loading only when event sourcing
     * is combined with snapshot persistence.
     */
    fun toAggregateSnapshot(entity: ProductEntity): ProductSnapshot {
        return ProductSnapshot(
            id = entity.id,
            sku = entity.sku,
            name = entity.name,
            description = entity.description,
            priceCents = entity.priceCents,
            status = ProductStatus.valueOf(entity.status),
            version = entity.version,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            deletedAt = entity.deletedAt
        )
    }
}

/**
 * Snapshot data for aggregate (used for loading current state without full event replay).
 */
data class ProductSnapshot(
    val id: java.util.UUID,
    val sku: String,
    val name: String,
    val description: String?,
    val priceCents: Int,
    val status: ProductStatus,
    val version: Long,
    val createdAt: java.time.OffsetDateTime,
    val updatedAt: java.time.OffsetDateTime,
    val deletedAt: java.time.OffsetDateTime?
)
```

#### 7.4 Verification

- [ ] Entity class compiles and maps correctly
- [ ] Repository methods work with R2DBC
- [ ] Mapper correctly converts between aggregate and entity

---

### Step 8: Create Aggregate Repository Service

**Objective:** Create a service that handles aggregate persistence with event publishing.

#### 8.1 Create Aggregate Repository Service

**File:** `src/main/kotlin/com/example/cqrsspike/product/command/infrastructure/ProductAggregateRepository.kt`

```kotlin
package com.example.cqrsspike.product.command.infrastructure

import com.example.cqrsspike.product.command.aggregate.ProductAggregate
import com.example.cqrsspike.product.command.exception.ConcurrentModificationException
import com.example.cqrsspike.product.command.exception.DuplicateSkuException
import com.example.cqrsspike.product.command.exception.ProductNotFoundException
import com.example.cqrsspike.product.event.ProductEvent
import org.slf4j.LoggerFactory
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * Repository service for Product aggregate persistence.
 *
 * Handles:
 * - Aggregate state persistence
 * - Event publication (to event store)
 * - Optimistic locking
 * - SKU uniqueness validation
 */
@Service
class ProductAggregateRepository(
    private val repository: ProductCommandRepository,
    private val eventStoreRepository: EventStoreRepository // Will be implemented in AC2
) {
    private val logger = LoggerFactory.getLogger(ProductAggregateRepository::class.java)

    /**
     * Saves a new Product aggregate.
     *
     * @param aggregate The aggregate to save
     * @return Mono<ProductAggregate> with events cleared
     * @throws DuplicateSkuException if SKU already exists
     */
    @Transactional
    fun save(aggregate: ProductAggregate): Mono<ProductAggregate> {
        val events = aggregate.getUncommittedEvents()

        return validateSkuUniqueness(aggregate.sku, aggregate.id)
            .then(persistEntity(aggregate))
            .flatMap { savedEntity ->
                publishEvents(events)
                    .thenReturn(aggregate)
            }
            .doOnSuccess {
                logger.info("Saved product aggregate: id=${aggregate.id}, sku=${aggregate.sku}")
            }
            .doOnError { error ->
                logger.error("Failed to save product aggregate: id=${aggregate.id}", error)
            }
    }

    /**
     * Updates an existing Product aggregate.
     *
     * @param aggregate The aggregate to update
     * @return Mono<ProductAggregate> with events cleared
     * @throws ConcurrentModificationException if version mismatch
     */
    @Transactional
    fun update(aggregate: ProductAggregate): Mono<ProductAggregate> {
        val events = aggregate.getUncommittedEvents()

        return persistEntity(aggregate)
            .flatMap { savedEntity ->
                publishEvents(events)
                    .thenReturn(aggregate)
            }
            .onErrorMap(OptimisticLockingFailureException::class.java) { error ->
                ConcurrentModificationException(
                    productId = aggregate.id,
                    expectedVersion = aggregate.version - 1,
                    actualVersion = aggregate.version
                )
            }
            .doOnSuccess {
                logger.info("Updated product aggregate: id=${aggregate.id}, version=${aggregate.version}")
            }
    }

    /**
     * Finds a Product aggregate by ID.
     *
     * Note: In a full event-sourced system, this would reconstitute from events.
     * This implementation uses snapshot + events for efficiency.
     *
     * @param id The product ID
     * @return Mono<ProductAggregate>
     * @throws ProductNotFoundException if not found
     */
    fun findById(id: UUID): Mono<ProductAggregate> {
        return repository.findByIdNotDeleted(id)
            .switchIfEmpty(Mono.error(ProductNotFoundException(id)))
            .flatMap { entity ->
                // In full event sourcing, reconstitute from event store
                // For now, use snapshot approach
                reconstitute(entity)
            }
    }

    /**
     * Finds a Product aggregate by SKU.
     */
    fun findBySku(sku: String): Mono<ProductAggregate> {
        return repository.findBySku(sku.uppercase())
            .switchIfEmpty(Mono.error(
                ProductNotFoundException(UUID.fromString("00000000-0000-0000-0000-000000000000"))
            ))
            .flatMap { entity -> reconstitute(entity) }
    }

    /**
     * Checks if a product exists by ID (not deleted).
     */
    fun exists(id: UUID): Mono<Boolean> {
        return repository.findByIdNotDeleted(id)
            .hasElement()
    }

    // Private helper methods

    private fun validateSkuUniqueness(sku: String, excludeId: UUID): Mono<Void> {
        return repository.findBySku(sku.uppercase())
            .filter { it.id != excludeId }
            .flatMap<Void> {
                Mono.error(DuplicateSkuException(sku))
            }
            .then()
    }

    private fun persistEntity(aggregate: ProductAggregate): Mono<ProductEntity> {
        val entity = ProductAggregateMapper.toEntity(aggregate)
        return repository.save(entity)
    }

    private fun publishEvents(events: List<ProductEvent>): Mono<Void> {
        if (events.isEmpty()) {
            return Mono.empty()
        }

        // Event store persistence will be implemented in AC2
        return eventStoreRepository.saveEvents(events)
            .doOnSuccess {
                logger.debug("Published ${events.size} events")
            }
    }

    private fun reconstitute(entity: ProductEntity): Mono<ProductAggregate> {
        // In full event sourcing, load events and reconstitute
        // For now, load from event store
        return eventStoreRepository.findEventsByAggregateId(entity.id)
            .collectList()
            .map { events ->
                if (events.isNotEmpty()) {
                    ProductAggregate.reconstitute(events)
                } else {
                    // Fallback: Create from snapshot (shouldn't happen in normal flow)
                    throw IllegalStateException("No events found for product ${entity.id}")
                }
            }
    }
}

/**
 * Interface for Event Store Repository (to be implemented in AC2).
 */
interface EventStoreRepository {
    fun saveEvents(events: List<ProductEvent>): Mono<Void>
    fun findEventsByAggregateId(aggregateId: UUID): reactor.core.publisher.Flux<ProductEvent>
}
```

#### 8.2 Verification

- [ ] Repository service compiles
- [ ] Transaction boundaries are correct
- [ ] Error handling is appropriate

---

### Step 9: Integration Testing

**Objective:** Create integration tests that verify the complete flow.

#### 9.1 Create Integration Test

**File:** `src/test/kotlin/com/example/cqrsspike/product/command/ProductAggregateIntegrationTest.kt`

```kotlin
package com.example.cqrsspike.product.command

import com.example.cqrsspike.product.command.aggregate.ProductAggregate
import com.example.cqrsspike.product.command.exception.DuplicateSkuException
import com.example.cqrsspike.product.command.exception.ProductNotFoundException
import com.example.cqrsspike.product.command.infrastructure.ProductAggregateRepository
import com.example.cqrsspike.product.command.model.ProductStatus
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import reactor.test.StepVerifier
import java.util.UUID

@SpringBootTest
@Testcontainers
@DisplayName("Product Aggregate Integration Tests")
class ProductAggregateIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:18-alpine")
            .withDatabaseName("cqrs_test")
            .withUsername("test")
            .withPassword("test")
            .withInitScript("init-test-schema.sql")

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.r2dbc.url") {
                "r2dbc:postgresql://${postgres.host}:${postgres.firstMappedPort}/${postgres.databaseName}"
            }
            registry.add("spring.r2dbc.username", postgres::getUsername)
            registry.add("spring.r2dbc.password", postgres::getPassword)
        }
    }

    @Autowired
    private lateinit var repository: ProductAggregateRepository

    @Nested
    @DisplayName("Save Operations")
    inner class SaveOperations {

        @Test
        @DisplayName("should save new product")
        fun shouldSaveNewProduct() {
            val aggregate = ProductAggregate.create(
                sku = "TEST-${UUID.randomUUID().toString().take(8)}",
                name = "Integration Test Product",
                description = "Test description",
                priceCents = 1999
            )

            StepVerifier.create(repository.save(aggregate))
                .expectNextMatches { saved ->
                    saved.id == aggregate.id &&
                    saved.status == ProductStatus.DRAFT
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("should reject duplicate SKU")
        fun shouldRejectDuplicateSku() {
            val sku = "DUP-${UUID.randomUUID().toString().take(8)}"

            val first = ProductAggregate.create(
                sku = sku,
                name = "First Product",
                description = null,
                priceCents = 999
            )

            val second = ProductAggregate.create(
                sku = sku, // Same SKU
                name = "Second Product",
                description = null,
                priceCents = 1999
            )

            StepVerifier.create(
                repository.save(first)
                    .then(repository.save(second))
            )
                .expectError(DuplicateSkuException::class.java)
                .verify()
        }
    }

    @Nested
    @DisplayName("Find Operations")
    inner class FindOperations {

        @Test
        @DisplayName("should find product by ID")
        fun shouldFindProductById() {
            val aggregate = ProductAggregate.create(
                sku = "FIND-${UUID.randomUUID().toString().take(8)}",
                name = "Findable Product",
                description = "Description",
                priceCents = 2999
            )

            StepVerifier.create(
                repository.save(aggregate)
                    .flatMap { repository.findById(it.id) }
            )
                .expectNextMatches { found ->
                    found.id == aggregate.id &&
                    found.sku == aggregate.sku.uppercase()
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("should return error for non-existent product")
        fun shouldReturnErrorForNonExistent() {
            val nonExistentId = UUID.randomUUID()

            StepVerifier.create(repository.findById(nonExistentId))
                .expectError(ProductNotFoundException::class.java)
                .verify()
        }
    }

    @Nested
    @DisplayName("Update Operations")
    inner class UpdateOperations {

        @Test
        @DisplayName("should update product and increment version")
        fun shouldUpdateProductAndIncrementVersion() {
            val aggregate = ProductAggregate.create(
                sku = "UPD-${UUID.randomUUID().toString().take(8)}",
                name = "Original Name",
                description = "Original description",
                priceCents = 1999
            )

            StepVerifier.create(
                repository.save(aggregate)
                    .flatMap { saved ->
                        val updated = saved.update(
                            newName = "Updated Name",
                            newDescription = "Updated description",
                            expectedVersion = saved.version
                        )
                        repository.update(updated)
                    }
            )
                .expectNextMatches { updated ->
                    updated.name == "Updated Name" &&
                    updated.version == 2L
                }
                .verifyComplete()
        }
    }
}
```

#### 9.2 Create Test Schema Init Script

**File:** `src/test/resources/init-test-schema.sql`

```sql
-- Test schema initialization
CREATE SCHEMA IF NOT EXISTS command_model;
CREATE SCHEMA IF NOT EXISTS event_store;

-- Product table
CREATE TABLE command_model.product (
    id UUID PRIMARY KEY,
    sku VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    price_cents INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    CONSTRAINT chk_price_positive CHECK (price_cents > 0)
);

CREATE UNIQUE INDEX idx_product_sku_unique
    ON command_model.product(sku)
    WHERE deleted_at IS NULL;

-- Event store tables (minimal for testing)
CREATE TABLE event_store.event_stream (
    stream_id UUID PRIMARY KEY,
    aggregate_type VARCHAR(255) NOT NULL,
    aggregate_id UUID NOT NULL,
    version BIGINT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE event_store.domain_event (
    event_id UUID PRIMARY KEY,
    stream_id UUID REFERENCES event_store.event_stream(stream_id),
    event_type VARCHAR(255) NOT NULL,
    event_data JSONB NOT NULL,
    metadata JSONB,
    version BIGINT NOT NULL,
    occurred_at TIMESTAMPTZ DEFAULT NOW()
);
```

#### 9.3 Verification

- [ ] Integration tests pass with Testcontainers
- [ ] Database operations work correctly
- [ ] Event persistence verified

---

## Verification Checklist

### Functional Requirements

- [ ] Product aggregate enforces all business invariants
- [ ] SKU validation (format, uniqueness)
- [ ] Name validation (length, required)
- [ ] Description validation (max length)
- [ ] Price validation (positive integer)
- [ ] Status transitions follow state machine rules
- [ ] Optimistic concurrency prevents concurrent modifications
- [ ] Events are generated for all state changes
- [ ] Aggregate can be reconstituted from events
- [ ] Soft delete works correctly

### Technical Requirements

- [ ] All code follows CONSTITUTION.md guidelines
- [ ] Reactive types used throughout (`Mono<T>`, `Flux<T>`)
- [ ] R2DBC used for database access (not JPA)
- [ ] Data classes used for immutability
- [ ] Proper logging implemented
- [ ] Unit tests achieve 80%+ coverage
- [ ] Integration tests pass

### Code Quality

- [ ] Code compiles without warnings
- [ ] No blocking operations in reactive pipeline
- [ ] Proper error handling with domain exceptions
- [ ] Clear and meaningful variable/method names
- [ ] KDoc documentation on public APIs

---

## Dependencies on Other ACs

- **AC2 (Event Store):** Required for event persistence. Until AC2 is implemented, use a stub implementation of `EventStoreRepository`.
- **AC3 (Command Handlers):** Will use this aggregate through command handlers.

## Estimated Completion

- **Step 1-2:** 2 hours (Schema and Status enum)
- **Step 3-4:** 3 hours (Events and Exceptions)
- **Step 5-6:** 6 hours (Aggregate and Tests)
- **Step 7-8:** 4 hours (Repository and Persistence)
- **Step 9:** 3 hours (Integration Testing)

**Total:** 18-20 hours (~3-4 days)

---

## Notes and Decisions

1. **Event Sourcing Strategy:** Using hybrid approach with snapshot persistence + event store. Full event reconstitution available but snapshots used for efficiency.

2. **SKU Format:** Uppercase, alphanumeric with hyphens, 3-50 characters. Enforced at aggregate level and database constraint.

3. **Price Storage:** Stored as integer cents to avoid floating-point issues. All calculations done in cents.

4. **Soft Delete:** Products are soft-deleted with `deleted_at` timestamp. Excluded from normal queries via partial indexes.

5. **Version Management:** Using Spring Data R2DBC `@Version` annotation for optimistic locking.
