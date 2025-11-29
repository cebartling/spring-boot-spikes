package com.pintailconsultingllc.cqrsspike.infrastructure.observability

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@DisplayName("CommandMetrics - AC11")
class CommandMetricsTest {

    private lateinit var meterRegistry: SimpleMeterRegistry
    private lateinit var commandMetrics: CommandMetrics

    @BeforeEach
    fun setUp() {
        meterRegistry = SimpleMeterRegistry()
        commandMetrics = CommandMetrics(meterRegistry)
    }

    @Nested
    @DisplayName("AC11: Custom metrics track command success/failure rates")
    inner class SuccessFailureTracking {

        @Test
        @DisplayName("should record successful command execution")
        fun shouldRecordSuccessfulCommandExecution() {
            commandMetrics.recordSuccess("CreateProductCommand", Duration.ofMillis(100))

            val counter = meterRegistry.find("product.command.success")
                .tag("command_type", "CreateProductCommand")
                .counter()

            assertNotNull(counter)
            assertEquals(1.0, counter.count())
        }

        @Test
        @DisplayName("should record multiple successful commands")
        fun shouldRecordMultipleSuccessfulCommands() {
            commandMetrics.recordSuccess("CreateProductCommand", Duration.ofMillis(100))
            commandMetrics.recordSuccess("CreateProductCommand", Duration.ofMillis(150))
            commandMetrics.recordSuccess("UpdateProductCommand", Duration.ofMillis(80))

            val createCounter = meterRegistry.find("product.command.success")
                .tag("command_type", "CreateProductCommand")
                .counter()
            val updateCounter = meterRegistry.find("product.command.success")
                .tag("command_type", "UpdateProductCommand")
                .counter()

            assertNotNull(createCounter)
            assertNotNull(updateCounter)
            assertEquals(2.0, createCounter.count())
            assertEquals(1.0, updateCounter.count())
        }

        @Test
        @DisplayName("should record failed command execution")
        fun shouldRecordFailedCommandExecution() {
            commandMetrics.recordFailure(
                "UpdateProductCommand",
                "ValidationException",
                Duration.ofMillis(50)
            )

            val counter = meterRegistry.find("product.command.failure")
                .tag("command_type", "UpdateProductCommand")
                .tag("error_type", "ValidationException")
                .counter()

            assertNotNull(counter)
            assertEquals(1.0, counter.count())
        }

        @Test
        @DisplayName("should record command duration")
        fun shouldRecordCommandDuration() {
            commandMetrics.recordSuccess("CreateProductCommand", Duration.ofMillis(150))

            val timer = meterRegistry.find("product.command.duration")
                .tag("command_type", "CreateProductCommand")
                .timer()

            assertNotNull(timer)
            assertEquals(1L, timer.count())
        }

        @Test
        @DisplayName("should track different error types separately")
        fun shouldTrackDifferentErrorTypesSeparately() {
            commandMetrics.recordFailure("CreateProductCommand", "ValidationException", Duration.ofMillis(50))
            commandMetrics.recordFailure("CreateProductCommand", "DuplicateSkuException", Duration.ofMillis(60))
            commandMetrics.recordFailure("CreateProductCommand", "ValidationException", Duration.ofMillis(45))

            val validationCounter = meterRegistry.find("product.command.failure")
                .tag("command_type", "CreateProductCommand")
                .tag("error_type", "ValidationException")
                .counter()
            val duplicateCounter = meterRegistry.find("product.command.failure")
                .tag("command_type", "CreateProductCommand")
                .tag("error_type", "DuplicateSkuException")
                .counter()

            assertNotNull(validationCounter)
            assertNotNull(duplicateCounter)
            assertEquals(2.0, validationCounter.count())
            assertEquals(1.0, duplicateCounter.count())
        }
    }
}
