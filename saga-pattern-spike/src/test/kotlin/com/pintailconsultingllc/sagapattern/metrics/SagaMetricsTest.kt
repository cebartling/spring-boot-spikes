package com.pintailconsultingllc.sagapattern.metrics

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for SagaMetrics.
 */
@Tag("unit")
class SagaMetricsTest {

    private lateinit var meterRegistry: SimpleMeterRegistry
    private lateinit var sagaMetrics: SagaMetrics

    @BeforeEach
    fun setUp() {
        meterRegistry = SimpleMeterRegistry()
        sagaMetrics = SagaMetrics(meterRegistry)
    }

    @Test
    fun `sagaStarted increments counter`() {
        sagaMetrics.sagaStarted()
        sagaMetrics.sagaStarted()

        val counter = meterRegistry.find("saga.started").counter()
        assertNotNull(counter)
        assertEquals(2.0, counter.count())
    }

    @Test
    fun `sagaCompleted increments counter`() {
        sagaMetrics.sagaCompleted()

        val counter = meterRegistry.find("saga.completed").counter()
        assertNotNull(counter)
        assertEquals(1.0, counter.count())
    }

    @Test
    fun `sagaCompensated increments counters with step tag`() {
        sagaMetrics.sagaCompensated("process-payment")

        val compensatedCounter = meterRegistry.find("saga.compensated").counter()
        assertNotNull(compensatedCounter)
        assertEquals(1.0, compensatedCounter.count())

        val stepFailedCounter = meterRegistry.find("saga.step.failed")
            .tag("step", "process-payment")
            .counter()
        assertNotNull(stepFailedCounter)
        assertEquals(1.0, stepFailedCounter.count())
    }

    @Test
    fun `recordSagaDuration records duration`() {
        sagaMetrics.recordSagaDuration(Duration.ofMillis(500))

        val timer = meterRegistry.find("saga.duration").timer()
        assertNotNull(timer)
        assertEquals(1, timer.count())
        assertTrue(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS) >= 500)
    }

    @Test
    fun `timeStep records step duration`() {
        val result = sagaMetrics.timeStep("reserve-inventory") {
            Thread.sleep(50)
            "completed"
        }

        assertEquals("completed", result)

        val timer = meterRegistry.find("saga.step.duration")
            .tag("step", "reserve-inventory")
            .timer()
        assertNotNull(timer)
        assertEquals(1, timer.count())
        assertTrue(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS) >= 50)
    }

    @Test
    fun `stepCompleted increments counter with step tag`() {
        sagaMetrics.stepCompleted("arrange-shipping")

        val counter = meterRegistry.find("saga.step.completed")
            .tag("step", "arrange-shipping")
            .counter()
        assertNotNull(counter)
        assertEquals(1.0, counter.count())
    }

    @Test
    fun `compensationExecuted increments counter with step tag`() {
        sagaMetrics.compensationExecuted("release-inventory")

        val counter = meterRegistry.find("saga.compensation.executed")
            .tag("step", "release-inventory")
            .counter()
        assertNotNull(counter)
        assertEquals(1.0, counter.count())
    }
}
