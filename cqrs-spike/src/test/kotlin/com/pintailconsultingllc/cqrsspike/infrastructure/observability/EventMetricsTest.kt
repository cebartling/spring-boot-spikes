package com.pintailconsultingllc.cqrsspike.infrastructure.observability

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@DisplayName("EventMetrics - AC11")
class EventMetricsTest {

    private lateinit var meterRegistry: SimpleMeterRegistry
    private lateinit var eventMetrics: EventMetrics

    @BeforeEach
    fun setUp() {
        meterRegistry = SimpleMeterRegistry()
        eventMetrics = EventMetrics(meterRegistry)
    }

    @Nested
    @DisplayName("AC11: Custom metrics track event processing lag")
    inner class EventProcessingLag {

        @Test
        @DisplayName("should record event publication")
        fun shouldRecordEventPublication() {
            eventMetrics.recordEventPublished("ProductCreated", Duration.ofMillis(10))

            val counter = meterRegistry.find("product.event.published")
                .tag("event_type", "ProductCreated")
                .counter()

            assertNotNull(counter)
            assertEquals(1.0, counter.count())
        }

        @Test
        @DisplayName("should record event publication timer")
        fun shouldRecordEventPublicationTimer() {
            eventMetrics.recordEventPublished("ProductCreated", Duration.ofMillis(10))

            val timer = meterRegistry.find("product.event.publish.duration")
                .tag("event_type", "ProductCreated")
                .timer()

            assertNotNull(timer)
            assertEquals(1L, timer.count())
        }

        @Test
        @DisplayName("should record event consumption with lag")
        fun shouldRecordEventConsumptionWithLag() {
            eventMetrics.recordEventConsumed("ProductUpdated", Duration.ofMillis(20), 5L)

            val counter = meterRegistry.find("product.event.consumed")
                .tag("event_type", "ProductUpdated")
                .counter()

            assertNotNull(counter)
            assertEquals(1.0, counter.count())
        }

        @Test
        @DisplayName("should track event processing lag gauge")
        fun shouldTrackEventProcessingLag() {
            eventMetrics.recordLag("ProductReadModel", 150L)

            val gauge = meterRegistry.find("product.event.lag")
                .tag("projection", "ProductReadModel")
                .gauge()

            assertNotNull(gauge)
            assertEquals(150.0, gauge.value())
        }

        @Test
        @DisplayName("should update event lag count")
        fun shouldUpdateEventLagCount() {
            eventMetrics.updateEventLagCount("ProductReadModel", 50L)

            val gauge = meterRegistry.find("product.event.lag")
                .tag("projection", "ProductReadModel")
                .gauge()

            assertNotNull(gauge)
            assertEquals(50.0, gauge.value())
        }

        @Test
        @DisplayName("should record lag histogram")
        fun shouldRecordLagHistogram() {
            eventMetrics.recordLag("ProductReadModel", 100L)
            eventMetrics.recordLag("ProductReadModel", 200L)
            eventMetrics.recordLag("ProductReadModel", 150L)

            val summary = meterRegistry.find("product.event.lag.histogram")
                .tag("projection", "ProductReadModel")
                .summary()

            assertNotNull(summary)
            assertEquals(3L, summary.count())
            assertEquals(450.0, summary.totalAmount())
        }

        @Test
        @DisplayName("should track different event types separately")
        fun shouldTrackDifferentEventTypesSeparately() {
            eventMetrics.recordEventPublished("ProductCreated", Duration.ofMillis(10))
            eventMetrics.recordEventPublished("ProductUpdated", Duration.ofMillis(15))
            eventMetrics.recordEventPublished("ProductCreated", Duration.ofMillis(12))

            val createdCounter = meterRegistry.find("product.event.published")
                .tag("event_type", "ProductCreated")
                .counter()
            val updatedCounter = meterRegistry.find("product.event.published")
                .tag("event_type", "ProductUpdated")
                .counter()

            assertNotNull(createdCounter)
            assertNotNull(updatedCounter)
            assertEquals(2.0, createdCounter.count())
            assertEquals(1.0, updatedCounter.count())
        }
    }
}
