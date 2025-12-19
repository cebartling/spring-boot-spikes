package com.pintailconsultingllc.cdcdebezium.steps

import com.pintailconsultingllc.cdcdebezium.dto.CustomerCdcEvent
import com.pintailconsultingllc.cdcdebezium.tracing.CdcTracingService
import io.cucumber.java.en.And
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.data.SpanData
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.header.internals.RecordHeaders
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OpenTelemetryTracingSteps {

    @Autowired
    private lateinit var spanExporter: InMemorySpanExporter

    @Autowired
    private lateinit var tracingService: CdcTracingService

    private var lastSpan: SpanData? = null
    private var lastException: Exception? = null

    @Given("the tracing infrastructure is initialized")
    fun theTracingInfrastructureIsInitialized() {
        assertNotNull(spanExporter)
        assertNotNull(tracingService)
    }

    @And("the span exporter is cleared")
    fun theSpanExporterIsCleared() {
        spanExporter.reset()
        lastSpan = null
        lastException = null
    }

    @When("a CDC insert event is processed for tracing")
    fun aCdcInsertEventIsProcessedForTracing() {
        processEvent(createEvent(operation = "c"))
    }

    @When("a CDC update event is processed for tracing")
    fun aCdcUpdateEventIsProcessedForTracing() {
        processEvent(createEvent(operation = "u"))
    }

    @When("a CDC delete event is processed for tracing")
    fun aCdcDeleteEventIsProcessedForTracing() {
        processEvent(createEvent(operation = "d"), isDelete = true)
    }

    @When("a CDC event is processed with partition {int} and offset {long}")
    fun aCdcEventIsProcessedWithPartitionAndOffset(partition: Int, offset: Long) {
        val record = createRecord(partition = partition, offset = offset)
        processRecordWithSpan(record, createEvent())
    }

    @When("a CDC event with customer ID {string} is processed")
    fun aCdcEventWithCustomerIdIsProcessed(customerId: String) {
        val record = createRecord(key = customerId)
        processRecordWithSpan(record, createEvent(id = UUID.fromString(customerId)))
    }

    @When("a tombstone event is processed for tracing")
    fun aTombstoneEventIsProcessedForTracing() {
        val record = createRecord(value = null)
        val span = tracingService.startSpan(record, "cdc-consumer-group")
        try {
            span.makeCurrent().use {
                tracingService.setDbOperation(span, CdcTracingService.DbOperation.IGNORE)
                tracingService.endSpanSuccess(span)
            }
        } catch (e: Exception) {
            tracingService.endSpanError(span, e)
        }
        captureLastSpan()
    }

    @When("a CDC event processing fails with an error")
    fun aCdcEventProcessingFailsWithAnError() {
        val record = createRecord()
        val span = tracingService.startSpan(record, "cdc-consumer-group")
        lastException = RuntimeException("Simulated processing error")
        try {
            span.makeCurrent().use {
                throw lastException!!
            }
        } catch (e: Exception) {
            tracingService.endSpanError(span, e)
        }
        captureLastSpan()
    }

    @Then("a span should be created with name containing {string}")
    fun aSpanShouldBeCreatedWithNameContaining(expectedNamePart: String) {
        assertNotNull(lastSpan, "Expected a span to be created")
        assertTrue(
            lastSpan!!.name.contains(expectedNamePart),
            "Span name '${lastSpan!!.name}' should contain '$expectedNamePart'"
        )
    }

    @And("the span should have kind {string}")
    fun theSpanShouldHaveKind(expectedKind: String) {
        assertNotNull(lastSpan)
        val kind = SpanKind.valueOf(expectedKind)
        assertEquals(kind, lastSpan!!.kind, "Span kind should be $expectedKind")
    }

    @And("the span should have attribute {string} with value {string}")
    fun theSpanShouldHaveAttributeWithValue(attributeName: String, expectedValue: String) {
        assertNotNull(lastSpan)
        val attributes = lastSpan!!.attributes

        val actualValue = when {
            attributeName.contains("partition") || attributeName.contains("offset") -> {
                attributes.get(AttributeKey.longKey(attributeName))?.toString()
            }
            else -> {
                attributes.get(AttributeKey.stringKey(attributeName))
            }
        }

        assertEquals(
            expectedValue,
            actualValue,
            "Attribute '$attributeName' should be '$expectedValue' but was '$actualValue'"
        )
    }

    @And("the span should have status {string}")
    fun theSpanShouldHaveStatus(expectedStatus: String) {
        assertNotNull(lastSpan)
        val status = StatusCode.valueOf(expectedStatus)
        assertEquals(status, lastSpan!!.status.statusCode, "Span status should be $expectedStatus")
    }

    @And("the span should have recorded an exception")
    fun theSpanShouldHaveRecordedAnException() {
        assertNotNull(lastSpan)
        val events = lastSpan!!.events
        assertTrue(
            events.any { it.name == "exception" },
            "Span should have recorded an exception event"
        )
    }

    private fun createEvent(
        id: UUID = UUID.randomUUID(),
        email: String = "test@example.com",
        status: String = "active",
        operation: String? = "c",
        deleted: String? = null
    ) = CustomerCdcEvent(
        id = id,
        email = email,
        status = status,
        updatedAt = Instant.now(),
        operation = operation,
        deleted = deleted
    )

    private fun createRecord(
        topic: String = "cdc.public.customer",
        partition: Int = 0,
        offset: Long = 0,
        key: String = UUID.randomUUID().toString(),
        value: String? = """{"id":"${UUID.randomUUID()}"}"""
    ): ConsumerRecord<String, String?> {
        return ConsumerRecord(topic, partition, offset, key, value)
    }

    private fun processEvent(event: CustomerCdcEvent, isDelete: Boolean = false) {
        val record = createRecord(key = event.id.toString())
        processRecordWithSpan(record, event, isDelete)
    }

    private fun processRecordWithSpan(
        record: ConsumerRecord<String, String?>,
        event: CustomerCdcEvent,
        isDelete: Boolean = false
    ) {
        val span = tracingService.startSpan(record, "cdc-consumer-group")
        try {
            span.makeCurrent().use {
                val operation = if (isDelete || event.isDelete()) {
                    CdcTracingService.DbOperation.DELETE
                } else {
                    CdcTracingService.DbOperation.UPSERT
                }
                tracingService.setDbOperation(span, operation)
                tracingService.endSpanSuccess(span)
            }
        } catch (e: Exception) {
            tracingService.endSpanError(span, e)
        }
        captureLastSpan()
    }

    private fun captureLastSpan() {
        val spans = spanExporter.finishedSpanItems
        lastSpan = spans.lastOrNull()
    }
}
