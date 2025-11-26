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
import com.pintailconsultingllc.cqrsspike.product.event.ProductPriceChanged
import com.pintailconsultingllc.cqrsspike.product.event.ProductUpdated
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
        @DisplayName("should reject whitespace-only name")
        fun shouldRejectWhitespaceOnlyName() {
            val exception = assertFailsWith<ProductInvariantViolationException> {
                ProductAggregate.create(
                    sku = VALID_SKU,
                    name = "   ",
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

        @Test
        @DisplayName("should reject SKU that is too long")
        fun shouldRejectSkuTooLong() {
            val longSku = "A".repeat(51)
            val exception = assertFailsWith<ProductInvariantViolationException> {
                ProductAggregate.create(
                    sku = longSku,
                    name = VALID_NAME,
                    description = null,
                    priceCents = VALID_PRICE
                )
            }

            assertEquals("SKU_LENGTH", exception.invariant)
        }

        @Test
        @DisplayName("should reject description that is too long")
        fun shouldRejectDescriptionTooLong() {
            val longDescription = "A".repeat(5001)
            val exception = assertFailsWith<ProductInvariantViolationException> {
                ProductAggregate.create(
                    sku = VALID_SKU,
                    name = VALID_NAME,
                    description = longDescription,
                    priceCents = VALID_PRICE
                )
            }

            assertEquals("DESCRIPTION_LENGTH", exception.invariant)
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

        @Test
        @DisplayName("should update description to null")
        fun shouldUpdateDescriptionToNull() {
            val aggregate = createValidAggregate()
            val updated = aggregate.update(
                newName = VALID_NAME,
                newDescription = null,
                expectedVersion = 1L
            )

            assertNull(updated.description)
            assertEquals(2L, updated.version)
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
        @DisplayName("should not generate event when price unchanged")
        fun shouldNotGenerateEventWhenPriceUnchanged() {
            val aggregate = createValidAggregate()
            aggregate.getUncommittedEvents()

            val updated = aggregate.changePrice(
                newPriceCents = VALID_PRICE,
                expectedVersion = 1L
            )

            assertTrue(updated.getUncommittedEvents().isEmpty())
            assertEquals(1L, updated.version)
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

        @Test
        @DisplayName("should allow small price change without confirmation for ACTIVE")
        fun shouldAllowSmallPriceChangeWithoutConfirmationForActive() {
            val aggregate = createValidAggregate()
                .activate(expectedVersion = 1L)

            // 10% increase - below 20% threshold
            val updated = aggregate.changePrice(
                newPriceCents = 2199,
                expectedVersion = 2L,
                confirmLargeChange = false
            )

            assertEquals(2199, updated.priceCents)
        }

        @Test
        @DisplayName("should reject non-positive price")
        fun shouldRejectNonPositivePrice() {
            val aggregate = createValidAggregate()

            val exception = assertFailsWith<ProductInvariantViolationException> {
                aggregate.changePrice(
                    newPriceCents = 0,
                    expectedVersion = 1L
                )
            }

            assertEquals("PRICE_POSITIVE", exception.invariant)
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
        @DisplayName("should generate ProductActivated event")
        fun shouldGenerateProductActivatedEvent() {
            val aggregate = createValidAggregate()
            aggregate.getUncommittedEvents()

            val activated = aggregate.activate(expectedVersion = 1L)
            val events = activated.getUncommittedEvents()

            assertEquals(1, events.size)
            val event = events.first()
            assertIs<ProductActivated>(event)
            assertEquals(ProductStatus.DRAFT, event.previousStatus)
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
        @DisplayName("should include reason in discontinue event")
        fun shouldIncludeReasonInDiscontinueEvent() {
            val aggregate = createValidAggregate()
            aggregate.getUncommittedEvents()

            val discontinued = aggregate.discontinue(
                expectedVersion = 1L,
                reason = "End of life"
            )

            val events = discontinued.getUncommittedEvents()
            val event = events.first()
            assertIs<ProductDiscontinued>(event)
            assertEquals("End of life", event.reason)
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

        @Test
        @DisplayName("should not allow activating already ACTIVE product")
        fun shouldNotAllowActivatingAlreadyActive() {
            val aggregate = createValidAggregate()
                .activate(expectedVersion = 1L)

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

        @Test
        @DisplayName("should prevent operations on deleted product")
        fun shouldPreventOperationsOnDeletedProduct() {
            val deleted = createValidAggregate().delete(expectedVersion = 1L)

            assertFailsWith<ProductDeletedException> {
                deleted.activate(expectedVersion = 2L)
            }

            assertFailsWith<ProductDeletedException> {
                deleted.changePrice(newPriceCents = 999, expectedVersion = 2L)
            }

            assertFailsWith<ProductDeletedException> {
                deleted.discontinue(expectedVersion = 2L)
            }
        }
    }

    @Nested
    @DisplayName("Event Reconstitution")
    inner class EventReconstitution {

        @Test
        @DisplayName("should reconstitute from events")
        fun shouldReconstituteFromEvents() {
            // Create original aggregate and collect events
            val original = createValidAggregate()
            val originalId = original.id

            // Manually construct events as they would be stored
            val createdEvent = ProductCreated(
                productId = originalId,
                version = 1,
                sku = VALID_SKU.uppercase(),
                name = VALID_NAME,
                description = VALID_DESCRIPTION,
                priceCents = VALID_PRICE
            )
            val updatedEvent = ProductUpdated(
                productId = originalId,
                version = 2,
                name = "Updated Name",
                description = "Updated description",
                previousName = VALID_NAME,
                previousDescription = VALID_DESCRIPTION
            )
            val priceChangedEvent = ProductPriceChanged(
                productId = originalId,
                version = 3,
                newPriceCents = 2999,
                previousPriceCents = VALID_PRICE,
                changePercentage = 50.025
            )
            val activatedEvent = ProductActivated(
                productId = originalId,
                version = 4,
                previousStatus = ProductStatus.DRAFT
            )

            val events = listOf(createdEvent, updatedEvent, priceChangedEvent, activatedEvent)

            // Reconstitute
            val reconstituted = ProductAggregate.reconstitute(events)

            assertEquals(originalId, reconstituted.id)
            assertEquals("Updated Name", reconstituted.name)
            assertEquals(2999, reconstituted.priceCents)
            assertEquals(ProductStatus.ACTIVE, reconstituted.status)
            assertEquals(4L, reconstituted.version)
        }

        @Test
        @DisplayName("should reconstitute deleted product")
        fun shouldReconstituteDeletedProduct() {
            val productId = UUID.randomUUID()

            val events = listOf(
                ProductCreated(
                    productId = productId,
                    version = 1,
                    sku = VALID_SKU.uppercase(),
                    name = VALID_NAME,
                    description = null,
                    priceCents = VALID_PRICE
                ),
                ProductDeleted(
                    productId = productId,
                    version = 2,
                    deletedBy = "admin"
                )
            )

            val reconstituted = ProductAggregate.reconstitute(events)

            assertTrue(reconstituted.isDeleted)
            assertEquals(2L, reconstituted.version)
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

        @Test
        @DisplayName("should reconstitute through discontinuation")
        fun shouldReconstituteThroughDiscontinuation() {
            val productId = UUID.randomUUID()

            val events = listOf(
                ProductCreated(
                    productId = productId,
                    version = 1,
                    sku = VALID_SKU.uppercase(),
                    name = VALID_NAME,
                    description = null,
                    priceCents = VALID_PRICE
                ),
                ProductActivated(
                    productId = productId,
                    version = 2,
                    previousStatus = ProductStatus.DRAFT
                ),
                ProductDiscontinued(
                    productId = productId,
                    version = 3,
                    previousStatus = ProductStatus.ACTIVE,
                    reason = "End of life"
                )
            )

            val reconstituted = ProductAggregate.reconstitute(events)

            assertEquals(ProductStatus.DISCONTINUED, reconstituted.status)
            assertEquals(3L, reconstituted.version)
        }
    }

    @Nested
    @DisplayName("Concurrent Modification")
    inner class ConcurrentModification {

        @Test
        @DisplayName("should detect version mismatch on update")
        fun shouldDetectVersionMismatchOnUpdate() {
            val aggregate = createValidAggregate()

            val exception = assertFailsWith<ConcurrentModificationException> {
                aggregate.update(
                    newName = "New Name",
                    newDescription = null,
                    expectedVersion = 99L
                )
            }

            assertEquals(aggregate.id, exception.productId)
            assertEquals(99L, exception.expectedVersion)
            assertEquals(1L, exception.actualVersion)
        }

        @Test
        @DisplayName("should detect version mismatch on price change")
        fun shouldDetectVersionMismatchOnPriceChange() {
            val aggregate = createValidAggregate()

            assertFailsWith<ConcurrentModificationException> {
                aggregate.changePrice(
                    newPriceCents = 999,
                    expectedVersion = 5L
                )
            }
        }

        @Test
        @DisplayName("should detect version mismatch on status change")
        fun shouldDetectVersionMismatchOnStatusChange() {
            val aggregate = createValidAggregate()

            assertFailsWith<ConcurrentModificationException> {
                aggregate.activate(expectedVersion = 100L)
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
