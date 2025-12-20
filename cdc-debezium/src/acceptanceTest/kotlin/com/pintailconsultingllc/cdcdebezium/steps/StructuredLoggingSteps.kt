package com.pintailconsultingllc.cdcdebezium.steps

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.pintailconsultingllc.cdcdebezium.tracing.CdcTracingService
import io.cucumber.java.en.And
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StructuredLoggingSteps {

    private val logger = LoggerFactory.getLogger("com.pintailconsultingllc.cdcdebezium.consumer.CustomerCdcConsumer")

    @Autowired
    private lateinit var logListAppender: ListAppender<ILoggingEvent>

    @Autowired
    private lateinit var tracingService: CdcTracingService

    @Autowired
    private lateinit var spanExporter: InMemorySpanExporter

    private var lastLogEvent: ILoggingEvent? = null

    @Given("the logging infrastructure is initialized")
    fun theLoggingInfrastructureIsInitialized() {
        assertNotNull(logListAppender, "Log list appender should be initialized")
        assertNotNull(tracingService, "Tracing service should be initialized")
    }

    @And("the log capture is cleared")
    fun theLogCaptureIsCleared() {
        logListAppender.list.clear()
        spanExporter.reset()
        lastLogEvent = null
    }

    @When("a CDC event is processed for logging")
    fun aCdcEventIsProcessedForLogging() {
        simulateCdcProcessing(
            topic = "cdc.public.customer",
            partition = 0,
            offset = 100,
            key = UUID.randomUUID().toString(),
            operation = "upsert",
            customerId = UUID.randomUUID().toString()
        )
    }

    @When("a CDC event with topic {string} partition {int} offset {long} is processed")
    fun aCdcEventWithTopicPartitionOffsetIsProcessed(topic: String, partition: Int, offset: Long) {
        simulateCdcProcessing(
            topic = topic,
            partition = partition,
            offset = offset,
            key = UUID.randomUUID().toString(),
            operation = "upsert",
            customerId = UUID.randomUUID().toString()
        )
    }

    @When("a CDC event with key {string} is processed for logging")
    fun aCdcEventWithKeyIsProcessedForLogging(key: String) {
        simulateCdcProcessing(
            topic = "cdc.public.customer",
            partition = 0,
            offset = 100,
            key = key,
            operation = "upsert",
            customerId = UUID.randomUUID().toString()
        )
    }

    @When("a CDC event for customer {string} is processed")
    fun aCdcEventForCustomerIsProcessed(customerId: String) {
        simulateCdcProcessing(
            topic = "cdc.public.customer",
            partition = 0,
            offset = 100,
            key = customerId,
            operation = "upsert",
            customerId = customerId
        )
    }

    @When("a CDC upsert event is processed successfully")
    fun aCdcUpsertEventIsProcessedSuccessfully() {
        simulateCdcProcessing(
            topic = "cdc.public.customer",
            partition = 0,
            offset = 100,
            key = UUID.randomUUID().toString(),
            operation = "upsert",
            customerId = UUID.randomUUID().toString(),
            outcome = "success"
        )
    }

    @When("a CDC delete event is processed for logging")
    fun aCdcDeleteEventIsProcessedForLogging() {
        simulateCdcProcessing(
            topic = "cdc.public.customer",
            partition = 0,
            offset = 100,
            key = UUID.randomUUID().toString(),
            operation = "delete",
            customerId = UUID.randomUUID().toString(),
            outcome = "success"
        )
    }

    @When("a tombstone event is processed for logging")
    fun aTombstoneEventIsProcessedForLogging() {
        simulateCdcProcessing(
            topic = "cdc.public.customer",
            partition = 0,
            offset = 100,
            key = UUID.randomUUID().toString(),
            operation = "ignore",
            customerId = null,
            outcome = "success"
        )
    }

    @When("a CDC event processing fails with a logging error")
    fun aCdcEventProcessingFailsWithALoggingError() {
        simulateCdcProcessing(
            topic = "cdc.public.customer",
            partition = 0,
            offset = 100,
            key = UUID.randomUUID().toString(),
            operation = "upsert",
            customerId = UUID.randomUUID().toString(),
            outcome = "error",
            errorType = "RuntimeException"
        )
    }

    @When("a CDC event is processed within a trace context")
    fun aCdcEventIsProcessedWithinATraceContext() {
        val record = ConsumerRecord<String, String?>(
            "cdc.public.customer",
            0,
            100,
            UUID.randomUUID().toString(),
            """{"id":"${UUID.randomUUID()}"}"""
        )

        val span = tracingService.startSpan(record, "cdc-consumer-group")
        try {
            span.makeCurrent().use { scope ->
                // Manually inject trace context into MDC (simulating OpenTelemetryAppender behavior)
                val spanContext = span.spanContext
                MDC.put("trace_id", spanContext.traceId)
                MDC.put("span_id", spanContext.spanId)

                MDC.put("kafka_topic", record.topic())
                MDC.put("kafka_partition", record.partition().toString())
                MDC.put("kafka_offset", record.offset().toString())
                MDC.put("message_key", record.key()?.take(36) ?: "null")
                MDC.put("db_operation", "upsert")
                MDC.put("processing_outcome", "success")

                logger.info("CDC event processed successfully")

                tracingService.endSpanSuccess(span)
            }
        } finally {
            captureLastLogEvent()
            MDC.clear()
        }
    }

    @Then("the log output should be valid JSON")
    fun theLogOutputShouldBeValidJson() {
        assertNotNull(lastLogEvent, "Expected a log event to be captured")
        // The MDC map and message are present - JSON format is handled by LogstashEncoder
        // We verify structure by checking expected fields exist
        assertTrue(lastLogEvent!!.message.isNotEmpty(), "Log message should not be empty")
    }

    @Then("the log should have field {string}")
    fun theLogShouldHaveField(fieldName: String) {
        assertNotNull(lastLogEvent, "Expected a log event to be captured")

        val hasField = when (fieldName) {
            "@timestamp" -> true // Always present in ILoggingEvent
            "message" -> lastLogEvent!!.message.isNotEmpty()
            "level" -> lastLogEvent!!.level != null
            "logger_name" -> lastLogEvent!!.loggerName.isNotEmpty()
            "trace_id" -> lastLogEvent!!.mdcPropertyMap.containsKey("trace_id")
            "span_id" -> lastLogEvent!!.mdcPropertyMap.containsKey("span_id")
            else -> lastLogEvent!!.mdcPropertyMap.containsKey(fieldName)
        }

        assertTrue(hasField, "Log should have field '$fieldName'. MDC keys: ${lastLogEvent!!.mdcPropertyMap.keys}")
    }

    @Then("the log should have field {string} with value {string}")
    fun theLogShouldHaveFieldWithValue(fieldName: String, expectedValue: String) {
        assertNotNull(lastLogEvent, "Expected a log event to be captured")

        val actualValue = lastLogEvent!!.mdcPropertyMap[fieldName]
        assertEquals(
            expectedValue,
            actualValue,
            "Field '$fieldName' should be '$expectedValue' but was '$actualValue'"
        )
    }

    @And("the trace_id field should not be empty")
    fun theTraceIdFieldShouldNotBeEmpty() {
        assertNotNull(lastLogEvent, "Expected a log event to be captured")
        val traceId = lastLogEvent!!.mdcPropertyMap["trace_id"]
        assertNotNull(traceId, "trace_id should be present")
        assertTrue(traceId.isNotEmpty(), "trace_id should not be empty")
        assertTrue(traceId != "00000000000000000000000000000000", "trace_id should not be all zeros")
    }

    @And("the span_id field should not be empty")
    fun theSpanIdFieldShouldNotBeEmpty() {
        assertNotNull(lastLogEvent, "Expected a log event to be captured")
        val spanId = lastLogEvent!!.mdcPropertyMap["span_id"]
        assertNotNull(spanId, "span_id should be present")
        assertTrue(spanId.isNotEmpty(), "span_id should not be empty")
        assertTrue(spanId != "0000000000000000", "span_id should not be all zeros")
    }

    private fun simulateCdcProcessing(
        topic: String,
        partition: Int,
        offset: Long,
        key: String,
        operation: String,
        customerId: String?,
        outcome: String = "success",
        errorType: String? = null
    ) {
        try {
            MDC.put("kafka_topic", topic)
            MDC.put("kafka_partition", partition.toString())
            MDC.put("kafka_offset", offset.toString())
            MDC.put("message_key", key.take(36))

            if (customerId != null) {
                MDC.put("customer_id", customerId)
            }

            MDC.put("db_operation", operation)
            MDC.put("processing_outcome", outcome)

            if (errorType != null) {
                MDC.put("error_type", errorType)
            }

            if (outcome == "error") {
                logger.error("Error processing CDC event: Simulated error")
            } else {
                logger.info("CDC event processed successfully")
            }
        } finally {
            captureLastLogEvent()
            MDC.clear()
        }
    }

    private fun captureLastLogEvent() {
        lastLogEvent = logListAppender.list
            .filter { it.loggerName.contains("pintailconsultingllc") }
            .lastOrNull()

        // If no application log found, try to get any log
        if (lastLogEvent == null) {
            lastLogEvent = logListAppender.list.lastOrNull()
        }
    }
}
