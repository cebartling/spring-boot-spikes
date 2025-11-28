package com.pintailconsultingllc.cqrsspike.product.query.projection

import com.pintailconsultingllc.cqrsspike.product.command.model.ProductStatus
import com.pintailconsultingllc.cqrsspike.product.event.ProductCreated
import com.pintailconsultingllc.cqrsspike.product.event.ProductUpdated
import com.pintailconsultingllc.cqrsspike.product.query.repository.ProductReadModelRepository
import com.pintailconsultingllc.cqrsspike.product.query.repository.ProjectionPositionRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
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
@DisplayName("Projection Integration Tests")
@Disabled("Integration tests disabled - require Docker and full database setup")
class ProjectionIntegrationTest {

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
            registry.add("projection.auto-start") { "false" }
        }
    }

    @Autowired
    private lateinit var productProjector: ProductProjector

    @Autowired
    private lateinit var runner: ProjectionRunner

    @Autowired
    private lateinit var orchestrator: ProjectionOrchestrator

    @Autowired
    private lateinit var readModelRepository: ProductReadModelRepository

    @Autowired
    private lateinit var positionRepository: ProjectionPositionRepository

    @BeforeEach
    fun setUp() {
        // Ensure projection is stopped and clean up before each test
        runner.stop().block()
        readModelRepository.deleteAll().block()
        positionRepository.deleteAll().block()
    }

    @Nested
    @DisplayName("End-to-End Projection Flow")
    inner class EndToEndProjectionFlow {

        @Test
        @DisplayName("should project ProductCreated event to read model")
        fun shouldProjectProductCreatedEvent() {
            val productId = UUID.randomUUID()
            val eventId = UUID.randomUUID()
            val sku = "PROJ-${productId.toString().take(8).uppercase()}"

            val event = ProductCreated(
                productId = productId,
                version = 1,
                sku = sku,
                name = "Projection Test Product",
                description = "Testing projection flow",
                priceCents = 2499,
                status = ProductStatus.DRAFT
            )

            // Process the event through the projector
            StepVerifier.create(productProjector.processEvent(event, eventId, 1L))
                .verifyComplete()

            // Verify read model was updated
            StepVerifier.create(readModelRepository.findById(productId))
                .expectNextMatches { readModel ->
                    readModel.sku == sku &&
                        readModel.name == "Projection Test Product" &&
                        readModel.priceCents == 2499 &&
                        readModel.aggregateVersion == 1L
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("should process multiple events in sequence")
        fun shouldProcessMultipleEventsInSequence() {
            val productId = UUID.randomUUID()
            val sku = "SEQ-${productId.toString().take(8).uppercase()}"

            val createEvent = ProductCreated(
                productId = productId,
                version = 1,
                sku = sku,
                name = "Original Name",
                description = "Original description",
                priceCents = 1000,
                status = ProductStatus.DRAFT
            )

            val updateEvent = ProductUpdated(
                productId = productId,
                version = 2,
                name = "Updated Name",
                description = "Updated description",
                previousName = "Original Name",
                previousDescription = "Original description"
            )

            // Process create event
            StepVerifier.create(productProjector.processEvent(createEvent, UUID.randomUUID(), 1L))
                .verifyComplete()

            // Process update event
            StepVerifier.create(productProjector.processEvent(updateEvent, UUID.randomUUID(), 2L))
                .verifyComplete()

            // Verify final state
            StepVerifier.create(readModelRepository.findById(productId))
                .expectNextMatches { readModel ->
                    readModel.name == "Updated Name" &&
                        readModel.description == "Updated description" &&
                        readModel.aggregateVersion == 2L
                }
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("Projection Position Tracking")
    inner class ProjectionPositionTracking {

        @Test
        @DisplayName("should track projection position after processing events")
        fun shouldTrackProjectionPositionAfterProcessingEvents() {
            val productId = UUID.randomUUID()
            val eventId = UUID.randomUUID()

            val event = ProductCreated(
                productId = productId,
                version = 1,
                sku = "POS-${productId.toString().take(8).uppercase()}",
                name = "Position Test Product",
                description = null,
                priceCents = 999,
                status = ProductStatus.DRAFT
            )

            // Process event
            StepVerifier.create(productProjector.processEvent(event, eventId, 1L))
                .verifyComplete()

            // Verify position was updated
            StepVerifier.create(productProjector.getProjectionPosition())
                .expectNextMatches { position ->
                    position.lastEventId == eventId &&
                        position.lastEventSequence == 1L &&
                        position.eventsProcessed > 0
                }
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("Projection Health")
    inner class ProjectionHealth {

        @Test
        @DisplayName("should report healthy projection status")
        fun shouldReportHealthyProjectionStatus() {
            StepVerifier.create(orchestrator.getProjectionHealth())
                .expectNextMatches { health ->
                    health.projectionName == "ProductReadModel"
                }
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("Projection Runner Lifecycle")
    inner class ProjectionRunnerLifecycle {

        @Test
        @DisplayName("should start and stop runner")
        fun shouldStartAndStopRunner() {
            // Start
            StepVerifier.create(runner.start())
                .verifyComplete()

            assert(runner.isRunning())
            assert(runner.getState() == ProjectionState.RUNNING)

            // Stop
            StepVerifier.create(runner.stop())
                .verifyComplete()

            assert(!runner.isRunning())
            assert(runner.getState() == ProjectionState.STOPPED)
        }
    }

    @Nested
    @DisplayName("Projection Rebuild")
    inner class ProjectionRebuild {

        @Test
        @DisplayName("should rebuild projection successfully")
        fun shouldRebuildProjectionSuccessfully() {
            // First, create some data
            val productId = UUID.randomUUID()
            val eventId = UUID.randomUUID()

            val event = ProductCreated(
                productId = productId,
                version = 1,
                sku = "REBUILD-${productId.toString().take(8).uppercase()}",
                name = "Rebuild Test Product",
                description = null,
                priceCents = 1500,
                status = ProductStatus.DRAFT
            )

            productProjector.processEvent(event, eventId, 1L).block()

            // Verify data exists
            assert(readModelRepository.findById(productId).block() != null)

            // Rebuild (without external event store, this will be a no-op)
            StepVerifier.create(orchestrator.rebuildProjection())
                .expectNextMatches { result ->
                    result.success && result.projectionName == "ProductReadModel"
                }
                .verifyComplete()
        }
    }
}
