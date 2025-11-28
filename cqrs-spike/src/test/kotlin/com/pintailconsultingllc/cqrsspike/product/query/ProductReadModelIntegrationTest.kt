package com.pintailconsultingllc.cqrsspike.product.query

import com.pintailconsultingllc.cqrsspike.product.command.model.ProductStatus
import com.pintailconsultingllc.cqrsspike.product.event.ProductActivated
import com.pintailconsultingllc.cqrsspike.product.event.ProductCreated
import com.pintailconsultingllc.cqrsspike.product.event.ProductDeleted
import com.pintailconsultingllc.cqrsspike.product.event.ProductPriceChanged
import com.pintailconsultingllc.cqrsspike.product.event.ProductUpdated
import com.pintailconsultingllc.cqrsspike.product.query.model.ProductStatusView
import com.pintailconsultingllc.cqrsspike.product.query.projection.ProductProjector
import com.pintailconsultingllc.cqrsspike.product.query.repository.ProductReadModelRepository
import com.pintailconsultingllc.cqrsspike.product.query.service.ProductQueryService
import org.junit.jupiter.api.BeforeEach
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
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("Product Read Model Integration Tests")
class ProductReadModelIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:18-alpine")
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
            registry.add("spring.flyway.enabled") { "false" }
            registry.add("spring.cloud.vault.enabled") { "false" }
        }
    }

    @Autowired
    private lateinit var projector: ProductProjector

    @Autowired
    private lateinit var queryService: ProductQueryService

    @Autowired
    private lateinit var readModelRepository: ProductReadModelRepository

    @BeforeEach
    fun setUp() {
        // Clean up read model before each test
        readModelRepository.deleteAll().block()
    }

    @Nested
    @DisplayName("Projection and Query Integration")
    inner class ProjectionAndQueryIntegration {

        @Test
        @DisplayName("should create and query product via projection")
        fun shouldCreateAndQueryProductViaProjection() {
            val productId = UUID.randomUUID()
            val eventId = UUID.randomUUID()
            val sku = "INT-${productId.toString().take(8).uppercase()}"

            val createEvent = ProductCreated(
                productId = productId,
                version = 1,
                sku = sku,
                name = "Integration Test Product",
                description = "Testing read model",
                priceCents = 2999,
                status = ProductStatus.DRAFT
            )

            // Process event through projector
            StepVerifier.create(projector.processEvent(createEvent, eventId, 1L))
                .verifyComplete()

            // Query the read model
            StepVerifier.create(queryService.findById(productId))
                .expectNextMatches { product ->
                    product.id == productId &&
                        product.sku == sku &&
                        product.name == "Integration Test Product" &&
                        product.priceCents == 2999 &&
                        product.status == "DRAFT" &&
                        product.priceDisplay == "$29.99"
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("should query product by SKU")
        fun shouldQueryProductBySku() {
            val productId = UUID.randomUUID()
            val eventId = UUID.randomUUID()
            val sku = "SKU-${productId.toString().take(8).uppercase()}"

            val createEvent = ProductCreated(
                productId = productId,
                version = 1,
                sku = sku,
                name = "SKU Test Product",
                description = "Testing SKU query",
                priceCents = 1999,
                status = ProductStatus.ACTIVE
            )

            projector.processEvent(createEvent, eventId, 1L).block()

            StepVerifier.create(queryService.findBySku(sku))
                .expectNextMatches { product ->
                    product.id == productId && product.sku == sku
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("should update read model through multiple events")
        fun shouldUpdateReadModelThroughMultipleEvents() {
            val productId = UUID.randomUUID()
            val sku = "MULTI-${productId.toString().take(8).uppercase()}"

            // Create product
            val createEvent = ProductCreated(
                productId = productId,
                version = 1,
                sku = sku,
                name = "Multi Event Product",
                description = "Testing multiple events",
                priceCents = 1999,
                status = ProductStatus.DRAFT
            )

            projector.processEvent(createEvent, UUID.randomUUID(), 1L).block()

            // Update product details
            val updateEvent = ProductUpdated(
                productId = productId,
                version = 2,
                name = "Updated Multi Event Product",
                description = "Updated description",
                previousName = "Multi Event Product",
                previousDescription = "Testing multiple events"
            )

            projector.processEvent(updateEvent, UUID.randomUUID(), 2L).block()

            // Change price
            val priceEvent = ProductPriceChanged(
                productId = productId,
                version = 3,
                newPriceCents = 2999,
                previousPriceCents = 1999,
                changePercentage = 50.0
            )

            projector.processEvent(priceEvent, UUID.randomUUID(), 3L).block()

            // Activate product
            val activateEvent = ProductActivated(
                productId = productId,
                version = 4,
                previousStatus = ProductStatus.DRAFT
            )

            projector.processEvent(activateEvent, UUID.randomUUID(), 4L).block()

            // Verify final state
            StepVerifier.create(queryService.findById(productId))
                .expectNextMatches { product ->
                    product.name == "Updated Multi Event Product" &&
                        product.description == "Updated description" &&
                        product.priceCents == 2999 &&
                        product.priceDisplay == "$29.99" &&
                        product.status == "ACTIVE" &&
                        product.version == 4L
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("should support status filtering")
        fun shouldSupportStatusFiltering() {
            // Create products with different statuses
            val draftProduct = createProductWithStatus(ProductStatus.DRAFT, "DRAFT-PROD")
            val activeProduct = createProductWithStatus(ProductStatus.ACTIVE, "ACTIVE-PROD")

            // Query active products
            StepVerifier.create(queryService.findByStatus(ProductStatusView.ACTIVE).collectList())
                .expectNextMatches { products ->
                    products.any { it.sku.startsWith("ACTIVE") } &&
                        products.none { it.sku.startsWith("DRAFT") }
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("should support pagination queries")
        fun shouldSupportPaginationQueries() {
            // Create multiple products
            (1..5).forEach { i ->
                val productId = UUID.randomUUID()
                val createEvent = ProductCreated(
                    productId = productId,
                    version = 1,
                    sku = "PAGE-$i-${productId.toString().take(4).uppercase()}",
                    name = "Pagination Product $i",
                    description = "For pagination test",
                    priceCents = 1000 * i,
                    status = ProductStatus.ACTIVE
                )
                projector.processEvent(createEvent, UUID.randomUUID(), 1L).block()
            }

            // Query with pagination
            StepVerifier.create(queryService.findAllPaginated(0, 3))
                .expectNextMatches { page ->
                    page.content.size == 3 &&
                        page.page == 0 &&
                        page.size == 3 &&
                        page.totalElements == 5L &&
                        page.totalPages == 2 &&
                        page.hasNext &&
                        !page.hasPrevious
                }
                .verifyComplete()

            // Query second page
            StepVerifier.create(queryService.findAllPaginated(1, 3))
                .expectNextMatches { page ->
                    page.content.size == 2 &&
                        page.page == 1 &&
                        !page.hasNext &&
                        page.hasPrevious
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("should support search queries")
        fun shouldSupportSearchQueries() {
            val productId = UUID.randomUUID()
            val createEvent = ProductCreated(
                productId = productId,
                version = 1,
                sku = "SEARCH-${productId.toString().take(8).uppercase()}",
                name = "Searchable Widget Pro",
                description = "A unique searchable widget for testing full-text search",
                priceCents = 4999,
                status = ProductStatus.ACTIVE
            )

            projector.processEvent(createEvent, UUID.randomUUID(), 1L).block()

            // Search for the product
            StepVerifier.create(queryService.search("widget"))
                .expectNextMatches { response ->
                    response.content.any { it.name.contains("Widget", ignoreCase = true) } &&
                        response.totalMatches >= 1L
                }
                .verifyComplete()

            // Search for term in description
            StepVerifier.create(queryService.search("searchable"))
                .expectNextMatches { response ->
                    response.content.any { it.name.contains("Searchable", ignoreCase = true) }
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("should support price range queries")
        fun shouldSupportPriceRangeQueries() {
            // Create products with different prices
            listOf(1000, 2000, 3000, 4000, 5000).forEachIndexed { index, price ->
                val productId = UUID.randomUUID()
                val createEvent = ProductCreated(
                    productId = productId,
                    version = 1,
                    sku = "PRICE-$index-${productId.toString().take(4).uppercase()}",
                    name = "Price Test Product $index",
                    description = "Testing price range",
                    priceCents = price,
                    status = ProductStatus.ACTIVE
                )
                projector.processEvent(createEvent, UUID.randomUUID(), 1L).block()
            }

            // Query price range 2000-4000
            StepVerifier.create(queryService.findByPriceRange(2000, 4000).collectList())
                .expectNextMatches { products ->
                    products.size == 3 &&
                        products.all { it.priceCents in 2000..4000 }
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("should support count queries")
        fun shouldSupportCountQueries() {
            // Create products
            repeat(3) { i ->
                createProductWithStatus(ProductStatus.ACTIVE, "COUNT-ACTIVE-$i")
            }
            repeat(2) { i ->
                createProductWithStatus(ProductStatus.DRAFT, "COUNT-DRAFT-$i")
            }

            // Count all
            StepVerifier.create(queryService.count())
                .expectNext(5L)
                .verifyComplete()

            // Count by status
            StepVerifier.create(queryService.countByStatus(ProductStatusView.ACTIVE))
                .expectNext(3L)
                .verifyComplete()

            StepVerifier.create(queryService.countByStatus(ProductStatusView.DRAFT))
                .expectNext(2L)
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("Soft Delete")
    inner class SoftDelete {

        @Test
        @DisplayName("should exclude deleted products from queries")
        fun shouldExcludeDeletedProductsFromQueries() {
            val productId = UUID.randomUUID()
            val sku = "DEL-${productId.toString().take(8).uppercase()}"

            // Create product
            val createEvent = ProductCreated(
                productId = productId,
                version = 1,
                sku = sku,
                name = "Product To Delete",
                description = "Will be deleted",
                priceCents = 1999,
                status = ProductStatus.ACTIVE
            )
            projector.processEvent(createEvent, UUID.randomUUID(), 1L).block()

            // Verify product exists
            StepVerifier.create(queryService.findById(productId))
                .expectNextMatches { it.id == productId }
                .verifyComplete()

            // Delete product
            val deleteEvent = ProductDeleted(
                productId = productId,
                version = 2,
                deletedBy = "test@example.com"
            )
            projector.processEvent(deleteEvent, UUID.randomUUID(), 2L).block()

            // Verify product is not found by ID query (excludes deleted)
            StepVerifier.create(queryService.findById(productId))
                .verifyComplete()

            // Verify product is not found by SKU query
            StepVerifier.create(queryService.findBySku(sku))
                .verifyComplete()

            // Verify product is not counted
            val countBefore = queryService.count().block() ?: 0L
            StepVerifier.create(queryService.exists(productId))
                .expectNext(false)
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("Idempotency")
    inner class Idempotency {

        @Test
        @DisplayName("should handle duplicate events idempotently")
        fun shouldHandleDuplicateEventsIdempotently() {
            val productId = UUID.randomUUID()
            val eventId = UUID.randomUUID()
            val sku = "IDEMP-${productId.toString().take(8).uppercase()}"

            val createEvent = ProductCreated(
                productId = productId,
                version = 1,
                sku = sku,
                name = "Idempotent Product",
                description = "Testing idempotency",
                priceCents = 1999,
                status = ProductStatus.DRAFT
            )

            // Process same event twice
            projector.processEvent(createEvent, eventId, 1L).block()
            projector.processEvent(createEvent, eventId, 1L).block()

            // Should still have correct state
            StepVerifier.create(queryService.findById(productId))
                .expectNextMatches { product ->
                    product.id == productId &&
                        product.version == 1L
                }
                .verifyComplete()

            // Count should be 1
            StepVerifier.create(queryService.count())
                .expectNext(1L)
                .verifyComplete()
        }

        @Test
        @DisplayName("should skip older version events")
        fun shouldSkipOlderVersionEvents() {
            val productId = UUID.randomUUID()
            val sku = "VER-${productId.toString().take(8).uppercase()}"

            // Create product
            val createEvent = ProductCreated(
                productId = productId,
                version = 1,
                sku = sku,
                name = "Version Test",
                description = "Testing version",
                priceCents = 1999,
                status = ProductStatus.DRAFT
            )
            projector.processEvent(createEvent, UUID.randomUUID(), 1L).block()

            // Update to version 3
            val updateEvent = ProductUpdated(
                productId = productId,
                version = 3,
                name = "Updated to V3",
                description = "Version 3",
                previousName = "Version Test",
                previousDescription = "Testing version"
            )
            projector.processEvent(updateEvent, UUID.randomUUID(), 3L).block()

            // Try to apply version 2 (should be skipped)
            val oldUpdateEvent = ProductUpdated(
                productId = productId,
                version = 2,
                name = "Outdated Update",
                description = "Should be skipped",
                previousName = "Version Test",
                previousDescription = "Testing version"
            )
            projector.processEvent(oldUpdateEvent, UUID.randomUUID(), 2L).block()

            // Should still have version 3 state
            StepVerifier.create(queryService.findById(productId))
                .expectNextMatches { product ->
                    product.name == "Updated to V3" &&
                        product.version == 3L
                }
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("Projection Position")
    inner class ProjectionPositionTests {

        @Test
        @DisplayName("should track projection position")
        fun shouldTrackProjectionPosition() {
            val productId = UUID.randomUUID()
            val eventId = UUID.randomUUID()

            val createEvent = ProductCreated(
                productId = productId,
                version = 1,
                sku = "POS-${productId.toString().take(8).uppercase()}",
                name = "Position Test",
                description = "Testing position",
                priceCents = 999,
                status = ProductStatus.DRAFT
            )

            projector.processEvent(createEvent, eventId, 42L).block()

            StepVerifier.create(projector.getProjectionPosition())
                .expectNextMatches { position ->
                    position.projectionName == "ProductReadModel" &&
                        position.lastEventId == eventId &&
                        position.lastEventSequence == 42L &&
                        position.eventsProcessed >= 1L
                }
                .verifyComplete()
        }
    }

    // Helper method
    private fun createProductWithStatus(status: ProductStatus, skuPrefix: String): UUID {
        val productId = UUID.randomUUID()
        val createEvent = ProductCreated(
            productId = productId,
            version = 1,
            sku = "$skuPrefix-${productId.toString().take(4).uppercase()}",
            name = "Product ${status.name}",
            description = "Product with status ${status.name}",
            priceCents = 1999,
            status = ProductStatus.DRAFT
        )
        projector.processEvent(createEvent, UUID.randomUUID(), 1L).block()

        // Transition to requested status if needed
        when (status) {
            ProductStatus.ACTIVE -> {
                val activateEvent = ProductActivated(
                    productId = productId,
                    version = 2,
                    previousStatus = ProductStatus.DRAFT
                )
                projector.processEvent(activateEvent, UUID.randomUUID(), 2L).block()
            }
            // Add other status transitions here if needed, e.g. DISCONTINUED
            // ProductStatus.DISCONTINUED -> { ... }
            // else -> do nothing (already DRAFT)
        }

        return productId
    }
}
