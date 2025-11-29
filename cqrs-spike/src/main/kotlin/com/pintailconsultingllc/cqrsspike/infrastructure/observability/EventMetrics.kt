package com.pintailconsultingllc.cqrsspike.infrastructure.observability

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Metrics for monitoring event publication and consumption.
 *
 * Implements AC11: "Custom metrics track event processing lag"
 */
@Component
class EventMetrics(private val meterRegistry: MeterRegistry) {

    private val publishCounters = ConcurrentHashMap<String, Counter>()
    private val consumeCounters = ConcurrentHashMap<String, Counter>()
    private val publishTimers = ConcurrentHashMap<String, Timer>()
    private val consumeTimers = ConcurrentHashMap<String, Timer>()
    private val lagGauges = ConcurrentHashMap<String, AtomicLong>()
    private val lagHistograms = ConcurrentHashMap<String, DistributionSummary>()

    init {
        // Initialize default lag gauge for product projection
        initializeLagGauge("ProductReadModel")
    }

    /**
     * Record event publication.
     */
    fun recordEventPublished(eventType: String, duration: Duration) {
        getPublishCounter(eventType).increment()
        getPublishTimer(eventType).record(duration)
    }

    /**
     * Record event consumption.
     */
    fun recordEventConsumed(eventType: String, duration: Duration, lagMs: Long) {
        getConsumeCounter(eventType).increment()
        getConsumeTimer(eventType).record(duration)
        recordLag("ProductReadModel", lagMs)
    }

    /**
     * Record current processing lag.
     */
    fun recordLag(projectionName: String, lagMs: Long) {
        getLagGauge(projectionName).set(lagMs)
        getLagHistogram(projectionName).record(lagMs.toDouble())
    }

    /**
     * Update current event lag value in milliseconds.
     * @param projectionName the name of the projection
     * @param lagMs the lag value in milliseconds
     */
    fun updateEventLagMs(projectionName: String, lagMs: Long) {
        getLagGauge(projectionName).set(lagMs)
    }

    private fun getPublishCounter(eventType: String): Counter {
        return publishCounters.computeIfAbsent(eventType) {
            Counter.builder("product.event.published")
                .description("Events published to event store")
                .tag("event_type", eventType)
                .register(meterRegistry)
        }
    }

    private fun getConsumeCounter(eventType: String): Counter {
        return consumeCounters.computeIfAbsent(eventType) {
            Counter.builder("product.event.consumed")
                .description("Events consumed by projections")
                .tag("event_type", eventType)
                .register(meterRegistry)
        }
    }

    private fun getPublishTimer(eventType: String): Timer {
        return publishTimers.computeIfAbsent(eventType) {
            Timer.builder("product.event.publish.duration")
                .description("Event publish time")
                .tag("event_type", eventType)
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry)
        }
    }

    private fun getConsumeTimer(eventType: String): Timer {
        return consumeTimers.computeIfAbsent(eventType) {
            Timer.builder("product.event.consume.duration")
                .description("Event consume time")
                .tag("event_type", eventType)
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry)
        }
    }

    private fun initializeLagGauge(projectionName: String) {
        val lagValue = AtomicLong(0)
        lagGauges[projectionName] = lagValue
        Gauge.builder("product.event.lag") { lagValue.get().toDouble() }
            .description("Current event processing lag (ms)")
            .tag("projection", projectionName)
            .register(meterRegistry)
    }

    private fun getLagGauge(projectionName: String): AtomicLong {
        return lagGauges.computeIfAbsent(projectionName) {
            val lagValue = AtomicLong(0)
            Gauge.builder("product.event.lag") { lagValue.get().toDouble() }
                .description("Current event processing lag (ms)")
                .tag("projection", projectionName)
                .register(meterRegistry)
            lagValue
        }
    }

    private fun getLagHistogram(projectionName: String): DistributionSummary {
        return lagHistograms.computeIfAbsent(projectionName) {
            DistributionSummary.builder("product.event.lag.histogram")
                .description("Event processing lag distribution (ms)")
                .tag("projection", projectionName)
                .publishPercentiles(0.5, 0.75, 0.95, 0.99)
                .register(meterRegistry)
        }
    }
}
