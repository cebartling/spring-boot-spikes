package com.pintailconsultingllc.cqrsspike.product.query.projection

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
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
@DisplayName("ProjectionRunner")
class ProjectionRunnerTest {

    @Mock
    private lateinit var orchestrator: ProjectionOrchestrator

    private lateinit var config: ProjectionConfig
    private lateinit var metrics: ProjectionMetrics
    private lateinit var runner: ProjectionRunner

    @BeforeEach
    fun setUp() {
        config = ProjectionConfig().apply {
            autoStart = false
            pollInterval = Duration.ofMillis(100)
            batchSize = 10
        }
        metrics = ProjectionMetrics(SimpleMeterRegistry())
        runner = ProjectionRunner(orchestrator, config, metrics)
    }

    @Nested
    @DisplayName("start")
    inner class Start {

        @Test
        @DisplayName("should start runner and process initial events")
        fun shouldStartRunnerAndProcessInitialEvents() {
            whenever(orchestrator.processToCurrent())
                .thenReturn(Mono.just(10L))

            StepVerifier.create(runner.start())
                .verifyComplete()

            assert(runner.isRunning())
            assert(runner.getState() == ProjectionState.RUNNING)
        }

        @Test
        @DisplayName("should handle start failure gracefully")
        fun shouldHandleStartFailureGracefully() {
            whenever(orchestrator.processToCurrent())
                .thenReturn(Mono.error(RuntimeException("Database connection failed")))

            StepVerifier.create(runner.start())
                .verifyComplete() // Completes without error (error is handled internally)

            assert(!runner.isRunning())
            assert(runner.getState() == ProjectionState.ERROR)
        }

        @Test
        @DisplayName("should not start if already running")
        fun shouldNotStartIfAlreadyRunning() {
            whenever(orchestrator.processToCurrent())
                .thenReturn(Mono.just(0L))

            runner.start().block()

            // Try to start again
            StepVerifier.create(runner.start())
                .verifyComplete()

            // Should still be running
            assert(runner.isRunning())
        }
    }

    @Nested
    @DisplayName("stop")
    inner class Stop {

        @Test
        @DisplayName("should stop running runner")
        fun shouldStopRunningRunner() {
            whenever(orchestrator.processToCurrent())
                .thenReturn(Mono.just(0L))

            runner.start().block()

            StepVerifier.create(runner.stop())
                .verifyComplete()

            assert(!runner.isRunning())
            assert(runner.getState() == ProjectionState.STOPPED)
        }

        @Test
        @DisplayName("should handle stop when not running")
        fun shouldHandleStopWhenNotRunning() {
            StepVerifier.create(runner.stop())
                .verifyComplete()

            assert(!runner.isRunning())
        }
    }

    @Nested
    @DisplayName("getStatus")
    inner class GetStatus {

        @Test
        @DisplayName("should return current status")
        fun shouldReturnCurrentStatus() {
            val status = ProjectionStatus(
                projectionName = "ProductReadModel",
                state = ProjectionState.STOPPED,
                lastEventId = UUID.randomUUID(),
                lastEventSequence = 100L,
                eventsProcessed = 100,
                eventLag = 5,
                lastProcessedAt = OffsetDateTime.now(),
                lastError = null,
                lastErrorAt = null
            )

            whenever(orchestrator.getProjectionStatus(any()))
                .thenReturn(Mono.just(status))

            StepVerifier.create(runner.getStatus())
                .expectNextMatches { result ->
                    result.projectionName == "ProductReadModel" &&
                        result.eventsProcessed == 100L
                }
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("rebuild")
    inner class Rebuild {

        @Test
        @DisplayName("should rebuild projection successfully")
        fun shouldRebuildProjectionSuccessfully() {
            val result = RebuildResult(
                projectionName = "ProductReadModel",
                eventsProcessed = 100,
                duration = Duration.ofSeconds(5),
                success = true
            )

            whenever(orchestrator.rebuildProjection())
                .thenReturn(Mono.just(result))

            StepVerifier.create(runner.rebuild())
                .expectNextMatches { it.success && it.eventsProcessed == 100L }
                .verifyComplete()
        }

        @Test
        @DisplayName("should restart runner after rebuild if it was running")
        fun shouldRestartRunnerAfterRebuildIfWasRunning() {
            val result = RebuildResult(
                projectionName = "ProductReadModel",
                eventsProcessed = 50,
                duration = Duration.ofSeconds(2),
                success = true
            )

            whenever(orchestrator.processToCurrent())
                .thenReturn(Mono.just(0L))
            whenever(orchestrator.rebuildProjection())
                .thenReturn(Mono.just(result))

            // Start the runner first
            runner.start().block()
            assert(runner.isRunning())

            // Rebuild
            StepVerifier.create(runner.rebuild())
                .expectNextMatches { it.success }
                .verifyComplete()

            // Runner should be restarted
            assert(runner.isRunning())
        }

        @Test
        @DisplayName("should not restart runner after failed rebuild")
        fun shouldNotRestartRunnerAfterFailedRebuild() {
            val result = RebuildResult(
                projectionName = "ProductReadModel",
                eventsProcessed = 0,
                duration = Duration.ofSeconds(1),
                success = false,
                errorMessage = "Database error"
            )

            whenever(orchestrator.processToCurrent())
                .thenReturn(Mono.just(0L))
            whenever(orchestrator.rebuildProjection())
                .thenReturn(Mono.just(result))

            // Start the runner first
            runner.start().block()

            // Rebuild fails
            StepVerifier.create(runner.rebuild())
                .expectNextMatches { !it.success }
                .verifyComplete()

            // Runner should not be restarted
            assert(!runner.isRunning())
        }
    }

    @Nested
    @DisplayName("state transitions")
    inner class StateTransitions {

        @Test
        @DisplayName("should transition through correct states during start")
        fun shouldTransitionThroughCorrectStatesDuringStart() {
            assert(runner.getState() == ProjectionState.STOPPED)

            whenever(orchestrator.processToCurrent())
                .thenReturn(Mono.just(5L))

            runner.start().block()

            assert(runner.getState() == ProjectionState.RUNNING)
        }

        @Test
        @DisplayName("should set error state on failure")
        fun shouldSetErrorStateOnFailure() {
            whenever(orchestrator.processToCurrent())
                .thenReturn(Mono.error(RuntimeException("Failed")))

            runner.start().block()

            assert(runner.getState() == ProjectionState.ERROR)
        }

        @Test
        @DisplayName("should set rebuilding state during rebuild")
        fun shouldSetRebuildingStateDuringRebuild() {
            val result = RebuildResult(
                projectionName = "ProductReadModel",
                eventsProcessed = 10,
                duration = Duration.ofSeconds(1),
                success = true
            )

            whenever(orchestrator.rebuildProjection())
                .thenAnswer {
                    // Check state during rebuild
                    assert(runner.getState() == ProjectionState.REBUILDING)
                    Mono.just(result)
                }

            runner.rebuild().block()
        }
    }
}
