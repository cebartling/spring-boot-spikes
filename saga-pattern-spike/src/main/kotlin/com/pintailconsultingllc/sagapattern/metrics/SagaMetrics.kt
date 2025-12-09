package com.pintailconsultingllc.sagapattern.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * Custom metrics for saga pattern observability.
 *
 * Provides metrics for:
 * - Saga execution lifecycle (started, completed, compensated)
 * - Step-level timing and failure tracking
 * - Compensation flow monitoring
 *
 * These metrics complement the automatic tracing from OpenTelemetry
 * and can be visualized in SigNoz or any Prometheus-compatible dashboard.
 */
@Component
class SagaMetrics(private val meterRegistry: MeterRegistry) {

    // Saga lifecycle counters
    private val sagaStartedCounter: Counter = Counter.builder("saga.started")
        .description("Number of sagas started")
        .register(meterRegistry)

    private val sagaCompletedCounter: Counter = Counter.builder("saga.completed")
        .description("Number of sagas completed successfully")
        .register(meterRegistry)

    private val sagaCompensatedCounter: Counter = Counter.builder("saga.compensated")
        .description("Number of sagas that required compensation")
        .register(meterRegistry)

    // Saga duration timer
    private val sagaDurationTimer: Timer = Timer.builder("saga.duration")
        .description("Time taken to complete a saga (success or compensation)")
        .register(meterRegistry)

    /**
     * Record that a saga has started.
     */
    fun sagaStarted() {
        sagaStartedCounter.increment()
    }

    /**
     * Record that a saga completed successfully.
     */
    fun sagaCompleted() {
        sagaCompletedCounter.increment()
    }

    /**
     * Record that a saga required compensation.
     *
     * @param failedStep The name of the step that failed
     */
    fun sagaCompensated(failedStep: String) {
        sagaCompensatedCounter.increment()
        Counter.builder("saga.step.failed")
            .tag("step", failedStep)
            .description("Number of times a specific saga step failed")
            .register(meterRegistry)
            .increment()
    }

    /**
     * Record the total duration of a saga execution.
     *
     * @param duration The time taken from saga start to completion/compensation
     */
    fun recordSagaDuration(duration: Duration) {
        sagaDurationTimer.record(duration)
    }

    /**
     * Time a saga step execution and record the duration.
     *
     * @param stepName The name of the step being executed
     * @param block The step logic to execute
     * @return The result of the step execution
     */
    fun <T : Any> timeStep(stepName: String, block: () -> T): T {
        val timer = Timer.builder("saga.step.duration")
            .tag("step", stepName)
            .description("Duration of individual saga steps")
            .register(meterRegistry)
        val sample = Timer.start(meterRegistry)
        return try {
            block()
        } finally {
            sample.stop(timer)
        }
    }

    /**
     * Record a successful step completion.
     *
     * @param stepName The name of the completed step
     */
    fun stepCompleted(stepName: String) {
        Counter.builder("saga.step.completed")
            .tag("step", stepName)
            .description("Number of times a specific saga step completed successfully")
            .register(meterRegistry)
            .increment()
    }

    /**
     * Record a compensation step execution.
     *
     * @param stepName The name of the step being compensated
     */
    fun compensationExecuted(stepName: String) {
        Counter.builder("saga.compensation.executed")
            .tag("step", stepName)
            .description("Number of times compensation was executed for a step")
            .register(meterRegistry)
            .increment()
    }
}
