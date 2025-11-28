package com.pintailconsultingllc.cqrsspike.product.query.projection

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong

/**
 * Metrics for monitoring projection health and performance.
 */
@Component
class ProjectionMetrics(meterRegistry: MeterRegistry) {

    private val eventsProcessedCounter: Counter
    private val processingTimer: Timer
    private val errorCounter: Counter
    private val currentLag: AtomicLong = AtomicLong(0)

    init {
        eventsProcessedCounter = Counter.builder("projection.events.processed")
            .description("Total number of events processed by projections")
            .tag("projection", "ProductReadModel")
            .register(meterRegistry)

        processingTimer = Timer.builder("projection.processing.time")
            .description("Time taken to process event batches")
            .tag("projection", "ProductReadModel")
            .register(meterRegistry)

        errorCounter = Counter.builder("projection.errors")
            .description("Number of projection processing errors")
            .tag("projection", "ProductReadModel")
            .register(meterRegistry)

        Gauge.builder("projection.lag") { currentLag.get().toDouble() }
            .description("Number of events behind the event store")
            .tag("projection", "ProductReadModel")
            .register(meterRegistry)
    }

    /**
     * Record that an event was successfully processed.
     */
    fun recordEventProcessed() {
        eventsProcessedCounter.increment()
    }

    /**
     * Record the time taken to process a batch of events.
     */
    fun recordProcessingTime(duration: Duration) {
        processingTimer.record(duration)
    }

    /**
     * Record a processing error.
     */
    fun recordError() {
        errorCounter.increment()
    }

    /**
     * Update the current lag value.
     */
    fun recordLag(lag: Long) {
        currentLag.set(lag)
    }
}
