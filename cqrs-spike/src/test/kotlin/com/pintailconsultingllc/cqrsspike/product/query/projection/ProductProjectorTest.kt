package com.pintailconsultingllc.cqrsspike.product.query.projection

import com.pintailconsultingllc.cqrsspike.product.command.model.ProductStatus
import com.pintailconsultingllc.cqrsspike.product.event.ProductActivated
import com.pintailconsultingllc.cqrsspike.product.event.ProductCreated
import com.pintailconsultingllc.cqrsspike.product.event.ProductDeleted
import com.pintailconsultingllc.cqrsspike.product.event.ProductDiscontinued
import com.pintailconsultingllc.cqrsspike.product.event.ProductPriceChanged
import com.pintailconsultingllc.cqrsspike.product.event.ProductUpdated
import com.pintailconsultingllc.cqrsspike.product.query.model.ProductReadModel
import com.pintailconsultingllc.cqrsspike.product.query.repository.ProductReadModelRepository
import com.pintailconsultingllc.cqrsspike.product.query.repository.ProjectionPosition
import com.pintailconsultingllc.cqrsspike.product.query.repository.ProjectionPositionRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.capture
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@ExtendWith(MockitoExtension::class)
@DisplayName("ProductProjector")
class ProductProjectorTest {

    @Mock
    private lateinit var readModelRepository: ProductReadModelRepository

    @Mock
    private lateinit var positionRepository: ProjectionPositionRepository

    @Captor
    private lateinit var productCaptor: ArgumentCaptor<ProductReadModel>

    private lateinit var projector: ProductProjector

    private val productId = UUID.randomUUID()
    private val eventId = UUID.randomUUID()
    private val eventSequence = 1L

    @BeforeEach
    fun setUp() {
        projector = ProductProjector(readModelRepository, positionRepository)

        // Default position repository behavior
        whenever(positionRepository.upsertPosition(any(), any(), any(), any()))
            .thenReturn(
                Mono.just(
                    ProjectionPosition(
                        projectionName = "ProductReadModel",
                        lastEventId = eventId,
                        lastEventSequence = eventSequence
                    )
                )
            )
    }

    @Nested
    @DisplayName("ProductCreated Event")
    inner class ProductCreatedEvent {

        @Test
        @DisplayName("should create read model from ProductCreated event")
        fun shouldCreateReadModelFromProductCreatedEvent() {
            val event = ProductCreated(
                productId = productId,
                version = 1,
                sku = "TEST-001",
                name = "Test Product",
                description = "A description",
                priceCents = 1999,
                status = ProductStatus.DRAFT
            )

            whenever(readModelRepository.save(any<ProductReadModel>()))
                .thenAnswer { invocation -> Mono.just(invocation.getArgument<ProductReadModel>(0)) }

            StepVerifier.create(projector.processEvent(event, eventId, eventSequence))
                .verifyComplete()

            verify(readModelRepository).save(capture(productCaptor))
            val captured = productCaptor.value
            assertEquals(productId, captured.id)
            assertEquals("TEST-001", captured.sku)
            assertEquals("Test Product", captured.name)
            assertEquals(1999, captured.priceCents)
            assertEquals("DRAFT", captured.status)
            assertEquals("$19.99", captured.priceDisplay)
            assertFalse(captured.isDeleted)
        }

        @Test
        @DisplayName("should build search text correctly")
        fun shouldBuildSearchTextCorrectly() {
            val event = ProductCreated(
                productId = productId,
                version = 1,
                sku = "TEST-001",
                name = "Widget Pro",
                description = "A professional widget",
                priceCents = 2999,
                status = ProductStatus.DRAFT
            )

            whenever(readModelRepository.save(any<ProductReadModel>()))
                .thenAnswer { invocation -> Mono.just(invocation.getArgument<ProductReadModel>(0)) }

            StepVerifier.create(projector.processEvent(event, eventId, eventSequence))
                .verifyComplete()

            verify(readModelRepository).save(capture(productCaptor))
            assertEquals("Widget Pro A professional widget", productCaptor.value.searchText)
        }
    }

    @Nested
    @DisplayName("ProductUpdated Event")
    inner class ProductUpdatedEvent {

        @Test
        @DisplayName("should update read model from ProductUpdated event")
        fun shouldUpdateReadModelFromProductUpdatedEvent() {
            val existingModel = createExistingReadModel(version = 1)

            val event = ProductUpdated(
                productId = productId,
                version = 2,
                name = "Updated Name",
                description = "Updated description",
                previousName = "Test Product",
                previousDescription = "A description"
            )

            whenever(readModelRepository.findById(productId))
                .thenReturn(Mono.just(existingModel))
            whenever(readModelRepository.save(any<ProductReadModel>()))
                .thenAnswer { invocation -> Mono.just(invocation.getArgument<ProductReadModel>(0)) }

            StepVerifier.create(projector.processEvent(event, eventId, eventSequence))
                .verifyComplete()

            verify(readModelRepository).save(capture(productCaptor))
            val captured = productCaptor.value
            assertEquals("Updated Name", captured.name)
            assertEquals("Updated description", captured.description)
            assertEquals(2L, captured.version)
        }

        @Test
        @DisplayName("should skip event if already processed (idempotency)")
        fun shouldSkipEventIfAlreadyProcessed() {
            val existingModel = createExistingReadModel(version = 2)

            val event = ProductUpdated(
                productId = productId,
                version = 2, // Same version as existing
                name = "Updated Name",
                description = "Updated description",
                previousName = "Test Product",
                previousDescription = "A description"
            )

            whenever(readModelRepository.findById(productId))
                .thenReturn(Mono.just(existingModel))

            StepVerifier.create(projector.processEvent(event, eventId, eventSequence))
                .verifyComplete()

            // save should not be called with a new version because event was already processed
            verify(readModelRepository).findById(productId)
        }

        @Test
        @DisplayName("should handle missing product gracefully")
        fun shouldHandleMissingProductGracefully() {
            val event = ProductUpdated(
                productId = productId,
                version = 2,
                name = "Updated Name",
                description = "Updated description",
                previousName = "Test Product",
                previousDescription = "A description"
            )

            whenever(readModelRepository.findById(productId))
                .thenReturn(Mono.empty())

            StepVerifier.create(projector.processEvent(event, eventId, eventSequence))
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("ProductPriceChanged Event")
    inner class ProductPriceChangedEvent {

        @Test
        @DisplayName("should update price from ProductPriceChanged event")
        fun shouldUpdatePriceFromProductPriceChangedEvent() {
            val existingModel = createExistingReadModel(version = 1)

            val event = ProductPriceChanged(
                productId = productId,
                version = 2,
                newPriceCents = 2999,
                previousPriceCents = 1999,
                changePercentage = 50.0
            )

            whenever(readModelRepository.findById(productId))
                .thenReturn(Mono.just(existingModel))
            whenever(readModelRepository.save(any<ProductReadModel>()))
                .thenAnswer { invocation -> Mono.just(invocation.getArgument<ProductReadModel>(0)) }

            StepVerifier.create(projector.processEvent(event, eventId, eventSequence))
                .verifyComplete()

            verify(readModelRepository).save(capture(productCaptor))
            val captured = productCaptor.value
            assertEquals(2999, captured.priceCents)
            assertEquals("$29.99", captured.priceDisplay)
        }

        @Test
        @DisplayName("should format price correctly for various amounts")
        fun shouldFormatPriceCorrectlyForVariousAmounts() {
            val existingModel = createExistingReadModel(version = 1)

            val event = ProductPriceChanged(
                productId = productId,
                version = 2,
                newPriceCents = 5, // $0.05
                previousPriceCents = 1999,
                changePercentage = -99.75
            )

            whenever(readModelRepository.findById(productId))
                .thenReturn(Mono.just(existingModel))
            whenever(readModelRepository.save(any<ProductReadModel>()))
                .thenAnswer { invocation -> Mono.just(invocation.getArgument<ProductReadModel>(0)) }

            StepVerifier.create(projector.processEvent(event, eventId, eventSequence))
                .verifyComplete()

            verify(readModelRepository).save(capture(productCaptor))
            assertEquals("$0.05", productCaptor.value.priceDisplay)
        }
    }

    @Nested
    @DisplayName("ProductActivated Event")
    inner class ProductActivatedEvent {

        @Test
        @DisplayName("should update status to ACTIVE")
        fun shouldUpdateStatusToActive() {
            val existingModel = createExistingReadModel(version = 1, status = "DRAFT")

            val event = ProductActivated(
                productId = productId,
                version = 2,
                previousStatus = ProductStatus.DRAFT
            )

            whenever(readModelRepository.findById(productId))
                .thenReturn(Mono.just(existingModel))
            whenever(readModelRepository.save(any<ProductReadModel>()))
                .thenAnswer { invocation -> Mono.just(invocation.getArgument<ProductReadModel>(0)) }

            StepVerifier.create(projector.processEvent(event, eventId, eventSequence))
                .verifyComplete()

            verify(readModelRepository).save(capture(productCaptor))
            assertEquals("ACTIVE", productCaptor.value.status)
        }
    }

    @Nested
    @DisplayName("ProductDiscontinued Event")
    inner class ProductDiscontinuedEvent {

        @Test
        @DisplayName("should update status to DISCONTINUED")
        fun shouldUpdateStatusToDiscontinued() {
            val existingModel = createExistingReadModel(version = 1, status = "ACTIVE")

            val event = ProductDiscontinued(
                productId = productId,
                version = 2,
                previousStatus = ProductStatus.ACTIVE,
                reason = "End of life"
            )

            whenever(readModelRepository.findById(productId))
                .thenReturn(Mono.just(existingModel))
            whenever(readModelRepository.save(any<ProductReadModel>()))
                .thenAnswer { invocation -> Mono.just(invocation.getArgument<ProductReadModel>(0)) }

            StepVerifier.create(projector.processEvent(event, eventId, eventSequence))
                .verifyComplete()

            verify(readModelRepository).save(capture(productCaptor))
            assertEquals("DISCONTINUED", productCaptor.value.status)
        }
    }

    @Nested
    @DisplayName("ProductDeleted Event")
    inner class ProductDeletedEvent {

        @Test
        @DisplayName("should mark product as deleted")
        fun shouldMarkProductAsDeleted() {
            val existingModel = createExistingReadModel(version = 1)

            val event = ProductDeleted(
                productId = productId,
                version = 2,
                deletedBy = "admin@example.com"
            )

            whenever(readModelRepository.findById(productId))
                .thenReturn(Mono.just(existingModel))
            whenever(readModelRepository.save(any<ProductReadModel>()))
                .thenAnswer { invocation -> Mono.just(invocation.getArgument<ProductReadModel>(0)) }

            StepVerifier.create(projector.processEvent(event, eventId, eventSequence))
                .verifyComplete()

            verify(readModelRepository).save(capture(productCaptor))
            assertTrue(productCaptor.value.isDeleted)
        }
    }

    @Nested
    @DisplayName("getProjectionPosition")
    inner class GetProjectionPosition {

        @Test
        @DisplayName("should return existing position")
        fun shouldReturnExistingPosition() {
            val existingPosition = ProjectionPosition(
                projectionName = "ProductReadModel",
                lastEventId = eventId,
                lastEventSequence = 100L,
                eventsProcessed = 50
            )

            whenever(positionRepository.findByProjectionName("ProductReadModel"))
                .thenReturn(Mono.just(existingPosition))

            StepVerifier.create(projector.getProjectionPosition())
                .expectNextMatches { position ->
                    position.lastEventId == eventId &&
                        position.lastEventSequence == 100L &&
                        position.eventsProcessed == 50L
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("should return default position when none exists")
        fun shouldReturnDefaultPositionWhenNoneExists() {
            whenever(positionRepository.findByProjectionName("ProductReadModel"))
                .thenReturn(Mono.empty())

            StepVerifier.create(projector.getProjectionPosition())
                .expectNextMatches { position ->
                    position.projectionName == "ProductReadModel" &&
                        position.lastEventId == null &&
                        position.lastEventSequence == null &&
                        position.eventsProcessed == 0L
                }
                .verifyComplete()
        }
    }

    // Helper method
    private fun createExistingReadModel(
        version: Long = 1,
        status: String = "ACTIVE"
    ): ProductReadModel {
        return ProductReadModel(
            id = productId,
            sku = "TEST-001",
            name = "Test Product",
            description = "A description",
            priceCents = 1999,
            status = status,
            createdAt = OffsetDateTime.now().minusDays(1),
            updatedAt = OffsetDateTime.now().minusDays(1),
            version = version,
            isDeleted = false,
            priceDisplay = "$19.99",
            searchText = "Test Product A description"
        )
    }
}
