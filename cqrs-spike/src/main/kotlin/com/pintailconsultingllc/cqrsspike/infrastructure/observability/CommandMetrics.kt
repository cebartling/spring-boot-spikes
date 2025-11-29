package com.pintailconsultingllc.cqrsspike.infrastructure.observability

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * Metrics for monitoring command execution.
 *
 * Implements AC11: "Custom metrics track command success/failure rates"
 */
@Component
class CommandMetrics(private val meterRegistry: MeterRegistry) {

    private val commandTimers = ConcurrentHashMap<String, Timer>()
    private val successCounters = ConcurrentHashMap<String, Counter>()
    private val failureCounters = ConcurrentHashMap<String, Counter>()

    /**
     * Record successful command execution.
     */
    fun recordSuccess(commandType: String, duration: Duration) {
        getTimer(commandType).record(duration)
        getSuccessCounter(commandType).increment()
    }

    /**
     * Record failed command execution.
     */
    fun recordFailure(commandType: String, errorType: String, duration: Duration) {
        getTimer(commandType).record(duration)
        getFailureCounter(commandType, errorType).increment()
    }

    private fun getTimer(commandType: String): Timer {
        return commandTimers.computeIfAbsent(commandType) {
            Timer.builder("product.command.duration")
                .description("Command execution time")
                .tag("command_type", commandType)
                .publishPercentiles(0.5, 0.75, 0.95, 0.99)
                .publishPercentileHistogram()
                .register(meterRegistry)
        }
    }

    private fun getSuccessCounter(commandType: String): Counter {
        return successCounters.computeIfAbsent(commandType) {
            Counter.builder("product.command.success")
                .description("Successful command executions")
                .tag("command_type", commandType)
                .register(meterRegistry)
        }
    }

    private fun getFailureCounter(commandType: String, errorType: String): Counter {
        val key = "$commandType:$errorType"
        return failureCounters.computeIfAbsent(key) {
            Counter.builder("product.command.failure")
                .description("Failed command executions")
                .tag("command_type", commandType)
                .tag("error_type", errorType)
                .register(meterRegistry)
        }
    }
}
