package com.pintailconsultingllc.cqrsspike.product.query.projection

import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import reactor.core.Disposable
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.Duration
import java.time.OffsetDateTime
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Runs event projections continuously.
 *
 * Polls for new events at configured intervals and processes them
 * to keep read models up to date.
 */
@Component
class ProjectionRunner(
    private val orchestrator: ProjectionOrchestrator,
    private val config: ProjectionConfig,
    private val metrics: ProjectionMetrics
) {
    private val logger = LoggerFactory.getLogger(ProjectionRunner::class.java)

    private val running = AtomicBoolean(false)
    private val state = AtomicReference(ProjectionState.STOPPED)
    private val lastError = AtomicReference<Throwable?>(null)
    private val lastErrorAt = AtomicReference<OffsetDateTime?>(null)
    private var subscription: Disposable? = null

    /**
     * Start the projection runner.
     */
    fun start(): Mono<Void> {
        if (!running.compareAndSet(false, true)) {
            logger.warn("Projection runner is already running")
            return Mono.empty()
        }

        logger.info("Starting projection runner")
        state.set(ProjectionState.STARTING)

        return orchestrator.processToCurrent()
            .doOnSuccess { count ->
                logger.info("Initial catch-up processed {} events", count)
                state.set(ProjectionState.RUNNING)
                startPolling()
            }
            .doOnError { error ->
                logger.error("Failed to start projection runner", error)
                state.set(ProjectionState.ERROR)
                lastError.set(error)
                lastErrorAt.set(OffsetDateTime.now())
                running.set(false)
            }
            .then()
    }

    /**
     * Stop the projection runner.
     */
    fun stop(): Mono<Void> {
        if (!running.compareAndSet(true, false)) {
            logger.warn("Projection runner is not running")
            return Mono.empty()
        }

        logger.info("Stopping projection runner")
        subscription?.dispose()
        subscription = null
        state.set(ProjectionState.STOPPED)

        return Mono.empty()
    }

    /**
     * Check if the runner is currently running.
     */
    fun isRunning(): Boolean = running.get()

    /**
     * Get the current state of the runner.
     */
    fun getState(): ProjectionState = state.get()

    /**
     * Get the current status of the projection.
     */
    fun getStatus(): Mono<ProjectionStatus> {
        return orchestrator.getProjectionStatus(state.get())
            .map { status ->
                status.copy(
                    lastError = lastError.get()?.message,
                    lastErrorAt = lastErrorAt.get()
                )
            }
    }

    /**
     * Trigger a rebuild of the projection.
     */
    fun rebuild(): Mono<RebuildResult> {
        val wasRunning = running.get()

        return stop()
            .then(Mono.defer {
                state.set(ProjectionState.REBUILDING)
                orchestrator.rebuildProjection()
            })
            .flatMap { result ->
                if (wasRunning && result.success) {
                    start().thenReturn(result)
                } else {
                    Mono.just(result)
                }
            }
    }

    @PostConstruct
    fun init() {
        if (config.autoStart) {
            start()
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                    { },
                    { error -> logger.error("Failed to auto-start projection runner", error) }
                )
        }
    }

    @PreDestroy
    fun destroy() {
        stop().block()
    }

    // Private helper methods

    private fun startPolling() {
        subscription = Flux.interval(config.pollInterval)
            .flatMap({ _ -> pollAndProcess() }, 1) // concurrency = 1 to ensure sequential processing
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe(
                { count ->
                    if (count > 0) {
                        logger.debug("Processed {} events in poll cycle", count)
                    }
                },
                { error ->
                    logger.error("Error in projection polling", error)
                    handlePollingError(error)
                }
            )
    }

    private fun pollAndProcess(): Mono<Long> {
        if (!running.get()) {
            return Mono.just(0L)
        }

        val startTime = System.currentTimeMillis()

        return orchestrator.processEventBatch()
            .doOnNext { _ ->
                metrics.recordEventProcessed()
            }
            .count()
            .doOnSuccess { count ->
                val duration = System.currentTimeMillis() - startTime
                if (count > 0) {
                    metrics.recordProcessingTime(Duration.ofMillis(duration))
                }
                updateLagMetrics()
            }
            .onErrorResume { error ->
                metrics.recordError()
                lastError.set(error)
                lastErrorAt.set(OffsetDateTime.now())
                logger.error("Error processing event batch", error)
                Mono.just(0L)
            }
    }

    private fun handlePollingError(error: Throwable) {
        state.set(ProjectionState.ERROR)
        lastError.set(error)
        lastErrorAt.set(OffsetDateTime.now())
        metrics.recordError()

        // Continue polling after error
        if (running.get()) {
            logger.info("Resuming polling after error")
        }
    }

    private fun updateLagMetrics() {
        orchestrator.getProjectionHealth()
            .subscribe { health ->
                metrics.recordLag(health.eventLag)
            }
    }
}
