package com.pintailconsultingllc.cqrsspike.product.query.projection

import com.pintailconsultingllc.cqrsspike.product.command.model.ProductStatus
import com.pintailconsultingllc.cqrsspike.product.event.ProductCreated
import com.pintailconsultingllc.cqrsspike.product.query.repository.ProjectionPosition
import com.pintailconsultingllc.cqrsspike.product.query.repository.ProjectionPositionRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Duration
import java.time.OffsetDateTime
import java.util.UUID

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ProjectionOrchestrator")
class ProjectionOrchestratorTest {

    @Mock
    private lateinit var eventQueryService: EventQueryService

    @Mock
    private lateinit var productProjector: ProductProjector

    @Mock
    private lateinit var positionRepository: ProjectionPositionRepository

    private lateinit var config: ProjectionConfig
    private lateinit var orchestrator: ProjectionOrchestrator

    @BeforeEach
    fun setUp() {
        config = ProjectionConfig().apply {
            batchSize = 10
            maxRetries = 2
            retryDelay = Duration.ofMillis(10)
            maxRetryDelay = Duration.ofMillis(100)
            lagWarningThreshold = 50
            lagErrorThreshold = 200
        }
        orchestrator = ProjectionOrchestrator(
            eventQueryService,
            productProjector,
            positionRepository,
            config
        )
    }

    @Nested
    @DisplayName("processEventBatch")
    inner class ProcessEventBatch {

        @Test
        @DisplayName("should process events starting from last position")
        fun shouldProcessEventsFromLastPosition() {
            val lastEventId = UUID.randomUUID()
            val position = ProjectionPosition(
                projectionName = "ProductReadModel",
                lastEventId = lastEventId,
                lastEventSequence = 5L,
                eventsProcessed = 5
            )
            val storedEvent = createStoredEvent()

            whenever(productProjector.getProjectionPosition())
                .thenReturn(Mono.just(position))
            whenever(eventQueryService.findEventsAfterEventId(eq(lastEventId), eq(10)))
                .thenReturn(Flux.just(storedEvent))
            whenever(productProjector.processEvent(any(), any(), any()))
                .thenReturn(Mono.empty())

            StepVerifier.create(orchestrator.processEventBatch())
                .expectNext(storedEvent.eventId)
                .verifyComplete()
        }

        @Test
        @DisplayName("should return empty flux when no events available")
        fun shouldReturnEmptyFluxWhenNoEvents() {
            val position = ProjectionPosition(
                projectionName = "ProductReadModel",
                lastEventId = UUID.randomUUID(),
                lastEventSequence = 10L,
                eventsProcessed = 10
            )

            whenever(productProjector.getProjectionPosition())
                .thenReturn(Mono.just(position))
            whenever(eventQueryService.findEventsAfterEventId(any(), any()))
                .thenReturn(Flux.empty())

            StepVerifier.create(orchestrator.processEventBatch())
                .verifyComplete()
        }

        @Test
        @DisplayName("should process multiple events in order")
        fun shouldProcessMultipleEventsInOrder() {
            val position = ProjectionPosition(
                projectionName = "ProductReadModel",
                lastEventId = null,
                lastEventSequence = null,
                eventsProcessed = 0
            )
            val event1 = createStoredEvent()
            val event2 = createStoredEvent()
            val event3 = createStoredEvent()

            whenever(productProjector.getProjectionPosition())
                .thenReturn(Mono.just(position))
            whenever(eventQueryService.findEventsAfterEventId(eq(null), eq(10)))
                .thenReturn(Flux.just(event1, event2, event3))
            whenever(productProjector.processEvent(any(), any(), any()))
                .thenReturn(Mono.empty())

            StepVerifier.create(orchestrator.processEventBatch())
                .expectNext(event1.eventId)
                .expectNext(event2.eventId)
                .expectNext(event3.eventId)
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("processToCurrent")
    inner class ProcessToCurrent {

        @Test
        @DisplayName("should process all available events")
        fun shouldProcessAllAvailableEvents() {
            val position = ProjectionPosition(
                projectionName = "ProductReadModel",
                lastEventId = null,
                lastEventSequence = null,
                eventsProcessed = 0
            )
            val event = createStoredEvent()

            whenever(productProjector.getProjectionPosition())
                .thenReturn(Mono.just(position))
            // Return single event (less than batch size of 10), so no recursion
            whenever(eventQueryService.findEventsAfterEventId(eq(null), eq(10)))
                .thenReturn(Flux.just(event))
            whenever(productProjector.processEvent(any(), any(), any()))
                .thenReturn(Mono.empty())

            StepVerifier.create(orchestrator.processToCurrent())
                .expectNext(1L)
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("getProjectionHealth")
    inner class GetProjectionHealth {

        @Test
        @DisplayName("should report healthy when lag is below threshold")
        fun shouldReportHealthyWhenLagBelowThreshold() {
            val position = ProjectionPosition(
                projectionName = "ProductReadModel",
                lastEventId = UUID.randomUUID(),
                lastEventSequence = 100L,
                eventsProcessed = 100
            )

            whenever(productProjector.getProjectionPosition())
                .thenReturn(Mono.just(position))
            whenever(eventQueryService.countEventsAfterEventId(any()))
                .thenReturn(Mono.just(5L))

            StepVerifier.create(orchestrator.getProjectionHealth())
                .expectNextMatches { health ->
                    health.healthy && health.eventLag == 5L
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("should report unhealthy when lag exceeds error threshold")
        fun shouldReportUnhealthyWhenLagExceedsErrorThreshold() {
            val position = ProjectionPosition(
                projectionName = "ProductReadModel",
                lastEventId = UUID.randomUUID(),
                lastEventSequence = 100L,
                eventsProcessed = 100
            )

            whenever(productProjector.getProjectionPosition())
                .thenReturn(Mono.just(position))
            whenever(eventQueryService.countEventsAfterEventId(any()))
                .thenReturn(Mono.just(500L)) // Exceeds lagErrorThreshold of 200

            StepVerifier.create(orchestrator.getProjectionHealth())
                .expectNextMatches { health ->
                    !health.healthy && health.eventLag == 500L
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("should report current when no lag")
        fun shouldReportCurrentWhenNoLag() {
            val position = ProjectionPosition(
                projectionName = "ProductReadModel",
                lastEventId = UUID.randomUUID(),
                lastEventSequence = 100L,
                eventsProcessed = 100
            )

            whenever(productProjector.getProjectionPosition())
                .thenReturn(Mono.just(position))
            whenever(eventQueryService.countEventsAfterEventId(any()))
                .thenReturn(Mono.just(0L))

            StepVerifier.create(orchestrator.getProjectionHealth())
                .expectNextMatches { health ->
                    health.healthy &&
                        health.eventLag == 0L &&
                        health.message == "Projection is current"
                }
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("getProjectionStatus")
    inner class GetProjectionStatus {

        @Test
        @DisplayName("should return complete projection status")
        fun shouldReturnCompleteProjectionStatus() {
            val lastEventId = UUID.randomUUID()
            val position = ProjectionPosition(
                projectionName = "ProductReadModel",
                lastEventId = lastEventId,
                lastEventSequence = 50L,
                eventsProcessed = 50
            )

            whenever(productProjector.getProjectionPosition())
                .thenReturn(Mono.just(position))
            whenever(eventQueryService.countEventsAfterEventId(any()))
                .thenReturn(Mono.just(10L))

            StepVerifier.create(orchestrator.getProjectionStatus(ProjectionState.RUNNING))
                .expectNextMatches { status ->
                    status.projectionName == "ProductReadModel" &&
                        status.state == ProjectionState.RUNNING &&
                        status.lastEventId == lastEventId &&
                        status.eventsProcessed == 50L &&
                        status.eventLag == 10L
                }
                .verifyComplete()
        }
    }

    // Note: rebuildProjection tests omitted - complex mocking with nested reactive calls
    // Rebuild functionality is verified through integration tests

    // Helper methods

    private fun createStoredEvent(): StoredEvent {
        return StoredEvent(
            eventId = UUID.randomUUID(),
            streamId = UUID.randomUUID(),
            eventType = "ProductCreated",
            aggregateVersion = 1,
            globalSequence = 1,
            occurredAt = OffsetDateTime.now(),
            event = ProductCreated(
                eventId = UUID.randomUUID(),
                productId = UUID.randomUUID(),
                sku = "TEST-001",
                name = "Test Product",
                description = null,
                priceCents = 1999,
                status = ProductStatus.DRAFT,
                occurredAt = OffsetDateTime.now(),
                version = 1
            )
        )
    }
}
