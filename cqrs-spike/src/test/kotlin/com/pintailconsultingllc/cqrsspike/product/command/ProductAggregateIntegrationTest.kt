package com.pintailconsultingllc.cqrsspike.product.command

import com.pintailconsultingllc.cqrsspike.product.command.aggregate.ProductAggregate
import com.pintailconsultingllc.cqrsspike.product.command.exception.DuplicateSkuException
import com.pintailconsultingllc.cqrsspike.product.command.exception.ProductNotFoundException
import com.pintailconsultingllc.cqrsspike.product.command.infrastructure.ProductAggregateRepository
import com.pintailconsultingllc.cqrsspike.product.command.infrastructure.StubEventStoreRepository
import com.pintailconsultingllc.cqrsspike.product.command.model.ProductStatus
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import reactor.test.StepVerifier
import java.util.UUID

/**
 * Integration tests for Product Aggregate with PostgreSQL.
 *
 * IMPORTANT: Before running these tests, ensure Docker Compose
 * infrastructure is running:
 *   make start
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Product Aggregate Integration Tests")
class ProductAggregateIntegrationTest {

    @Autowired
    private lateinit var repository: ProductAggregateRepository

    @Autowired
    private lateinit var stubEventStore: StubEventStoreRepository

    @BeforeEach
    fun setup() {
        // Clear the stub event store between tests
        stubEventStore.clear()
    }

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
                    saved.status == ProductStatus.DRAFT &&
                    saved.version == 1L
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("should persist events to event store")
        fun shouldPersistEventsToEventStore() {
            val aggregate = ProductAggregate.create(
                sku = "EVT-${UUID.randomUUID().toString().take(8)}",
                name = "Event Test Product",
                description = null,
                priceCents = 999
            )

            StepVerifier.create(repository.save(aggregate))
                .expectNextCount(1)
                .verifyComplete()

            // Verify events were saved
            StepVerifier.create(stubEventStore.findEventsByAggregateId(aggregate.id))
                .expectNextCount(1)
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
        @DisplayName("should find product by SKU")
        fun shouldFindProductBySku() {
            val sku = "SKU-${UUID.randomUUID().toString().take(8)}"
            val aggregate = ProductAggregate.create(
                sku = sku,
                name = "SKU Test Product",
                description = null,
                priceCents = 1499
            )

            StepVerifier.create(
                repository.save(aggregate)
                    .flatMap { repository.findBySku(sku) }
            )
                .expectNextMatches { found ->
                    found.id == aggregate.id &&
                    found.sku == sku.uppercase()
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

        @Test
        @DisplayName("should check if product exists")
        fun shouldCheckIfProductExists() {
            val aggregate = ProductAggregate.create(
                sku = "EXIST-${UUID.randomUUID().toString().take(8)}",
                name = "Existence Test",
                description = null,
                priceCents = 999
            )

            StepVerifier.create(
                repository.save(aggregate)
                    .flatMap { repository.exists(it.id) }
            )
                .expectNext(true)
                .verifyComplete()

            StepVerifier.create(repository.exists(UUID.randomUUID()))
                .expectNext(false)
                .verifyComplete()
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

        @Test
        @DisplayName("should update price")
        fun shouldUpdatePrice() {
            val aggregate = ProductAggregate.create(
                sku = "PRC-${UUID.randomUUID().toString().take(8)}",
                name = "Price Test",
                description = null,
                priceCents = 1000
            )

            StepVerifier.create(
                repository.save(aggregate)
                    .flatMap { saved ->
                        val updated = saved.changePrice(
                            newPriceCents = 1500,
                            expectedVersion = saved.version
                        )
                        repository.update(updated)
                    }
            )
                .expectNextMatches { updated ->
                    updated.priceCents == 1500 &&
                    updated.version == 2L
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("should activate product")
        fun shouldActivateProduct() {
            val aggregate = ProductAggregate.create(
                sku = "ACT-${UUID.randomUUID().toString().take(8)}",
                name = "Activation Test",
                description = null,
                priceCents = 999
            )

            StepVerifier.create(
                repository.save(aggregate)
                    .flatMap { saved ->
                        val activated = saved.activate(expectedVersion = saved.version)
                        repository.update(activated)
                    }
            )
                .expectNextMatches { updated ->
                    updated.status == ProductStatus.ACTIVE &&
                    updated.version == 2L
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("should discontinue product")
        fun shouldDiscontinueProduct() {
            val aggregate = ProductAggregate.create(
                sku = "DIS-${UUID.randomUUID().toString().take(8)}",
                name = "Discontinue Test",
                description = null,
                priceCents = 999
            )

            StepVerifier.create(
                repository.save(aggregate)
                    .flatMap { saved ->
                        val discontinued = saved.discontinue(
                            expectedVersion = saved.version,
                            reason = "End of life"
                        )
                        repository.update(discontinued)
                    }
            )
                .expectNextMatches { updated ->
                    updated.status == ProductStatus.DISCONTINUED &&
                    updated.version == 2L
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("should soft delete product")
        fun shouldSoftDeleteProduct() {
            val aggregate = ProductAggregate.create(
                sku = "DEL-${UUID.randomUUID().toString().take(8)}",
                name = "Delete Test",
                description = null,
                priceCents = 999
            )

            StepVerifier.create(
                repository.save(aggregate)
                    .flatMap { saved ->
                        val deleted = saved.delete(
                            expectedVersion = saved.version,
                            deletedBy = "test@example.com"
                        )
                        repository.update(deleted)
                    }
            )
                .expectNextMatches { updated ->
                    updated.isDeleted &&
                    updated.deletedAt != null
                }
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("Event Reconstitution")
    inner class EventReconstitution {

        @Test
        @DisplayName("should reconstitute aggregate from events")
        fun shouldReconstituteAggregateFromEvents() {
            val aggregate = ProductAggregate.create(
                sku = "REC-${UUID.randomUUID().toString().take(8)}",
                name = "Reconstitution Test",
                description = "Initial",
                priceCents = 1000
            )

            StepVerifier.create(
                repository.save(aggregate)
                    .flatMap { saved ->
                        // Update the aggregate
                        val updated = saved.update(
                            newName = "Updated Name",
                            newDescription = "Updated description",
                            expectedVersion = saved.version
                        )
                        repository.update(updated)
                    }
                    .flatMap { updated ->
                        // Find again - should reconstitute from events
                        repository.findById(updated.id)
                    }
            )
                .expectNextMatches { reconstituted ->
                    reconstituted.name == "Updated Name" &&
                    reconstituted.description == "Updated description" &&
                    reconstituted.version == 2L
                }
                .verifyComplete()
        }
    }
}
