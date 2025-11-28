package com.pintailconsultingllc.cqrsspike.product.query.projection

import com.pintailconsultingllc.cqrsspike.product.query.repository.ProjectionPosition
import com.pintailconsultingllc.cqrsspike.product.query.repository.ProjectionPositionRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import java.time.Duration
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Orchestrates event projection processing.
 *
 * Coordinates between the event query service and individual projectors,
 * handling batch processing, error recovery, and position tracking.
 */
@Component
class ProjectionOrchestrator(
    private val eventQueryService: EventQueryService,
    private val productProjector: ProductProjector,
    private val positionRepository: ProjectionPositionRepository,
    private val config: ProjectionConfig
) {
    private val logger = LoggerFactory.getLogger(ProjectionOrchestrator::class.java)

    companion object {
        const val PROJECTION_NAME = "ProductReadModel"
    }

    /**
     * Process a batch of events starting from the last processed position.
     *
     * @return Flux of processed event IDs
     */
    fun processEventBatch(): Flux<UUID> {
        return getLastProcessedEventId()
            .flatMapMany { lastEventId ->
                eventQueryService.findEventsAfterEventId(lastEventId, config.batchSize)
            }
            .concatMap { storedEvent ->
                processEventWithRetry(storedEvent)
            }
            .doOnComplete {
                logger.debug("Completed processing event batch")
            }
    }

    /**
     * Process events until caught up with the event store.
     *
     * @return Mono<Long> with total events processed
     */
    fun processToCurrent(): Mono<Long> {
        return processEventBatch()
            .count()
            .flatMap { count ->
                if (count >= config.batchSize) {
                    // More events may be available, continue processing
                    processToCurrent().map { it + count }
                } else {
                    Mono.just(count)
                }
            }
    }

    /**
     * Rebuild the projection from scratch by replaying all events.
     *
     * WARNING: This will delete existing read model data and replay all events.
     *
     * @return Mono<RebuildResult> with the result of the rebuild
     */
    @Transactional
    fun rebuildProjection(): Mono<RebuildResult> {
        val startTime = System.currentTimeMillis()

        return resetProjectionPosition()
            .then(processAllEvents())
            .map { eventsProcessed ->
                val duration = Duration.ofMillis(System.currentTimeMillis() - startTime)
                RebuildResult(
                    projectionName = PROJECTION_NAME,
                    eventsProcessed = eventsProcessed,
                    duration = duration,
                    success = true
                )
            }
            .doOnSuccess { result ->
                logger.info(
                    "Projection rebuild completed: events={}, duration={}",
                    result.eventsProcessed, result.duration
                )
            }
            .onErrorResume { error ->
                val duration = Duration.ofMillis(System.currentTimeMillis() - startTime)
                logger.error("Projection rebuild failed", error)
                Mono.just(
                    RebuildResult(
                        projectionName = PROJECTION_NAME,
                        eventsProcessed = 0,
                        duration = duration,
                        success = false,
                        errorMessage = error.message
                    )
                )
            }
    }

    /**
     * Get the current health status of the projection.
     */
    fun getProjectionHealth(): Mono<ProjectionHealth> {
        return productProjector.getProjectionPosition()
            .flatMap { position ->
                calculateLag(position.lastEventId).map { lag ->
                    val healthy = lag < config.lagErrorThreshold
                    val message = when {
                        lag == 0L -> "Projection is current"
                        lag < config.lagWarningThreshold -> "Projection is slightly behind ($lag events)"
                        lag < config.lagErrorThreshold -> "Projection is behind ($lag events) - WARNING"
                        else -> "Projection is significantly behind ($lag events) - ERROR"
                    }

                    ProjectionHealth(
                        projectionName = PROJECTION_NAME,
                        healthy = healthy,
                        eventLag = lag,
                        lastProcessedAt = position.lastProcessedAt,
                        message = message
                    )
                }
            }
    }

    /**
     * Get the current projection status.
     */
    fun getProjectionStatus(state: ProjectionState): Mono<ProjectionStatus> {
        return Mono.zip(
            productProjector.getProjectionPosition(),
            productProjector.getProjectionPosition().flatMap { pos -> calculateLag(pos.lastEventId) }
        ).map { tuple ->
            val position = tuple.t1
            val lag = tuple.t2

            ProjectionStatus(
                projectionName = PROJECTION_NAME,
                state = state,
                lastEventId = position.lastEventId,
                lastEventSequence = position.lastEventSequence,
                eventsProcessed = position.eventsProcessed,
                eventLag = lag,
                lastProcessedAt = position.lastProcessedAt,
                lastError = null,
                lastErrorAt = null
            )
        }
    }

    // Private helper methods

    private fun getLastProcessedEventId(): Mono<UUID?> {
        return productProjector.getProjectionPosition()
            .map { it.lastEventId }
    }

    private fun processEventWithRetry(storedEvent: StoredEvent): Mono<UUID> {
        return productProjector.processEvent(
            event = storedEvent.event,
            eventId = storedEvent.eventId,
            eventSequence = storedEvent.aggregateVersion
        )
            .retryWhen(
                Retry.backoff(config.maxRetries.toLong(), config.retryDelay)
                    .maxBackoff(config.maxRetryDelay)
                    .doBeforeRetry { signal ->
                        logger.warn(
                            "Retrying event processing: eventId={}, attempt={}, error={}",
                            storedEvent.eventId, signal.totalRetries() + 1, signal.failure().message
                        )
                    }
            )
            .thenReturn(storedEvent.eventId)
            .doOnSuccess {
                logger.debug(
                    "Processed event: eventId={}, type={}",
                    storedEvent.eventId, storedEvent.eventType
                )
            }
            .doOnError { error ->
                logger.error(
                    "Failed to process event after retries: eventId={}, type={}",
                    storedEvent.eventId, storedEvent.eventType, error
                )
            }
    }

    private fun processAllEvents(): Mono<Long> {
        return processAllEventsBatched(0, 0)
    }

    private fun processAllEventsBatched(offset: Long, totalProcessed: Long): Mono<Long> {
        return eventQueryService.findAllEventsOrdered(config.batchSize, offset)
            .concatMap { storedEvent ->
                processEventWithRetry(storedEvent)
            }
            .count()
            .flatMap { count ->
                val newTotal = totalProcessed + count
                if (count >= config.batchSize) {
                    // More events available
                    processAllEventsBatched(offset + count, newTotal)
                } else {
                    Mono.just(newTotal)
                }
            }
    }

    private fun resetProjectionPosition(): Mono<Void> {
        return positionRepository.deleteById(PROJECTION_NAME)
            .doOnSuccess {
                logger.info("Reset projection position for: {}", PROJECTION_NAME)
            }
    }

    private fun calculateLag(lastEventId: UUID?): Mono<Long> {
        return eventQueryService.countEventsAfterEventId(lastEventId)
    }
}
