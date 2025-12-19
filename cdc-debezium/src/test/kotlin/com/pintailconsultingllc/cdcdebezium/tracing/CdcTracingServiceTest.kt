package com.pintailconsultingllc.cdcdebezium.tracing

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.header.internals.RecordHeaders
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class CdcTracingServiceTest {

    private lateinit var tracingService: CdcTracingService

    @BeforeEach
    fun setUp() {
        tracingService = CdcTracingService()
    }

    private fun createRecord(
        topic: String = "cdc.public.customer",
        partition: Int = 0,
        offset: Long = 42,
        key: String = "test-key-123",
        value: String? = """{"id":"test-id"}"""
    ): ConsumerRecord<String, String?> {
        return ConsumerRecord(topic, partition, offset, key, value)
    }

    @Nested
    inner class StartSpan {

        @Test
        fun `creates span with consumer group`() {
            val record = createRecord()

            val span = tracingService.startSpan(record, "cdc-consumer-group")

            span.end()
        }

        @Test
        fun `creates span with custom topic name`() {
            val record = createRecord(topic = "custom.topic.name")

            val span = tracingService.startSpan(record, "cdc-consumer-group")

            span.end()
        }

        @Test
        fun `creates span with specific partition and offset`() {
            val record = createRecord(partition = 5, offset = 12345)

            val span = tracingService.startSpan(record, "cdc-consumer-group")

            span.end()
        }

        @Test
        fun `creates span with message key as customer id`() {
            val record = createRecord(key = "550e8400-e29b-41d4-a716-446655440001")

            val span = tracingService.startSpan(record, "cdc-consumer-group")

            span.end()
        }
    }

    @Nested
    inner class SetDbOperation {

        @Test
        fun `sets upsert operation`() {
            val span = mockk<Span>(relaxed = true)

            tracingService.setDbOperation(span, CdcTracingService.DbOperation.UPSERT)

            verify { span.setAttribute(any<io.opentelemetry.api.common.AttributeKey<String>>(), "upsert") }
        }

        @Test
        fun `sets delete operation`() {
            val span = mockk<Span>(relaxed = true)

            tracingService.setDbOperation(span, CdcTracingService.DbOperation.DELETE)

            verify { span.setAttribute(any<io.opentelemetry.api.common.AttributeKey<String>>(), "delete") }
        }

        @Test
        fun `sets ignore operation`() {
            val span = mockk<Span>(relaxed = true)

            tracingService.setDbOperation(span, CdcTracingService.DbOperation.IGNORE)

            verify { span.setAttribute(any<io.opentelemetry.api.common.AttributeKey<String>>(), "ignore") }
        }
    }

    @Nested
    inner class EndSpanSuccess {

        @Test
        fun `sets OK status and ends span`() {
            val span = mockk<Span>(relaxed = true)

            tracingService.endSpanSuccess(span)

            verify { span.setStatus(StatusCode.OK) }
            verify { span.end() }
        }
    }

    @Nested
    inner class EndSpanError {

        @Test
        fun `sets ERROR status with message and ends span`() {
            val span = mockk<Span>(relaxed = true)
            val exception = RuntimeException("Test error message")

            tracingService.endSpanError(span, exception)

            verify { span.setStatus(StatusCode.ERROR, "Test error message") }
            verify { span.recordException(exception) }
            verify { span.end() }
        }

        @Test
        fun `handles exception with null message`() {
            val span = mockk<Span>(relaxed = true)
            val exception = RuntimeException()

            tracingService.endSpanError(span, exception)

            verify { span.setStatus(StatusCode.ERROR, "Unknown error") }
            verify { span.recordException(exception) }
            verify { span.end() }
        }
    }

    @Nested
    inner class DbOperationEnum {

        @Test
        fun `UPSERT has correct value`() {
            assert(CdcTracingService.DbOperation.UPSERT.value == "upsert")
        }

        @Test
        fun `DELETE has correct value`() {
            assert(CdcTracingService.DbOperation.DELETE.value == "delete")
        }

        @Test
        fun `IGNORE has correct value`() {
            assert(CdcTracingService.DbOperation.IGNORE.value == "ignore")
        }
    }
}
