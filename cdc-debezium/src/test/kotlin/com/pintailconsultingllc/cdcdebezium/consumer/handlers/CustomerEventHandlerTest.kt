package com.pintailconsultingllc.cdcdebezium.consumer.handlers

import com.pintailconsultingllc.cdcdebezium.TestFixtures.createEvent
import com.pintailconsultingllc.cdcdebezium.document.CdcMetadata
import com.pintailconsultingllc.cdcdebezium.document.CdcOperation
import com.pintailconsultingllc.cdcdebezium.document.CustomerDocument
import com.pintailconsultingllc.cdcdebezium.dto.CustomerCdcEvent
import com.pintailconsultingllc.cdcdebezium.metrics.CdcMetricsService
import com.pintailconsultingllc.cdcdebezium.service.CustomerMongoService
import com.pintailconsultingllc.cdcdebezium.validation.AggregatedValidationResult
import com.pintailconsultingllc.cdcdebezium.validation.ValidationResult
import com.pintailconsultingllc.cdcdebezium.validation.ValidationService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.UUID

class CustomerEventHandlerTest {

    private lateinit var objectMapper: ObjectMapper
    private lateinit var customerService: CustomerMongoService
    private lateinit var validationService: ValidationService
    private lateinit var metricsService: CdcMetricsService
    private lateinit var handler: CustomerEventHandler

    @BeforeEach
    fun setUp() {
        objectMapper = mockk()
        customerService = mockk()
        validationService = mockk(relaxed = true)
        metricsService = mockk(relaxed = true)

        handler = CustomerEventHandler(
            objectMapper,
            customerService,
            validationService,
            metricsService
        )
    }

    private fun stubValidationPasses(event: CustomerCdcEvent) {
        every { validationService.validate(event) } returns AggregatedValidationResult.fromResults(
            results = emptyList(),
            eventId = event.id.toString(),
            entityType = "customer"
        )
    }

    private fun stubValidationFails(event: CustomerCdcEvent) {
        every { validationService.validate(event) } returns AggregatedValidationResult(
            valid = false,
            results = listOf(
                ValidationResult.failure(
                    "TEST_RULE",
                    "Validation failed"
                )
            ),
            eventId = event.id.toString(),
            entityType = "customer"
        )
    }

    private fun stubUpsert(event: CustomerCdcEvent) {
        val document = CustomerDocument(
            id = event.id.toString(),
            email = event.email ?: "",
            status = event.status ?: "",
            updatedAt = event.updatedAt ?: Instant.now(),
            cdcMetadata = CdcMetadata(
                sourceTimestamp = event.sourceTimestamp ?: System.currentTimeMillis(),
                operation = CdcOperation.INSERT,
                kafkaOffset = 0L,
                kafkaPartition = 0
            )
        )
        every { customerService.upsert(event, any(), any()) } returns Mono.just(document)
    }

    private fun stubDelete(id: UUID) {
        every {
            customerService.delete(
                id = id.toString(),
                sourceTimestamp = any(),
                kafkaOffset = any(),
                kafkaPartition = any(),
                softDelete = any()
            )
        } returns Mono.empty()
    }

    private fun createRecord(
        key: String = "test-key",
        value: String = """{"id":"550e8400-e29b-41d4-a716-446655440000"}""",
        topic: String = "cdc.public.customer",
        partition: Int = 0,
        offset: Long = 0
    ): ConsumerRecord<String, String> = ConsumerRecord(topic, partition, offset, key, value)

    private fun stubDeserialization(json: String, event: CustomerCdcEvent) {
        every { objectMapper.readValue(json, CustomerCdcEvent::class.java) } returns event
    }

    @Test
    fun `handler has correct topic`() {
        assert(handler.topic == "cdc.public.customer")
    }

    @Test
    fun `handler has correct entity type`() {
        assert(handler.entityType == "customer")
    }

    @Test
    fun `canHandle returns true for matching topic`() {
        assert(handler.canHandle("cdc.public.customer"))
    }

    @Test
    fun `canHandle returns false for non-matching topic`() {
        assert(!handler.canHandle("cdc.public.address"))
    }

    @Nested
    inner class UpsertEvents {

        @Test
        fun `processes insert event`() {
            val event = createEvent(operation = "c")
            val json = """{"id":"${event.id}","__op":"c"}"""

            stubDeserialization(json, event)
            stubValidationPasses(event)
            stubUpsert(event)

            handler.handle(createRecord(value = json)).block()

            verify(exactly = 1) { objectMapper.readValue(json, CustomerCdcEvent::class.java) }
            verify(exactly = 1) { validationService.validate(event) }
            verify(exactly = 1) { customerService.upsert(event, any(), any()) }
        }

        @Test
        fun `processes update event`() {
            val event = createEvent(operation = "u")
            val json = """{"id":"${event.id}","__op":"u"}"""

            stubDeserialization(json, event)
            stubValidationPasses(event)
            stubUpsert(event)

            handler.handle(createRecord(value = json)).block()

            verify(exactly = 1) { customerService.upsert(event, any(), any()) }
        }

        @Test
        fun `processes snapshot event`() {
            val event = createEvent(operation = "r")
            val json = """{"id":"${event.id}","__op":"r"}"""

            stubDeserialization(json, event)
            stubValidationPasses(event)
            stubUpsert(event)

            handler.handle(createRecord(value = json)).block()

            verify(exactly = 1) { customerService.upsert(event, any(), any()) }
        }
    }

    @Nested
    inner class DeleteEvents {

        @Test
        fun `processes delete event with __deleted flag`() {
            val event = createEvent(deleted = "true")
            val json = """{"id":"${event.id}","__deleted":"true"}"""

            stubDeserialization(json, event)
            stubValidationPasses(event)
            stubDelete(event.id)

            handler.handle(createRecord(value = json)).block()

            verify(exactly = 1) {
                customerService.delete(
                    id = event.id.toString(),
                    sourceTimestamp = any(),
                    kafkaOffset = any(),
                    kafkaPartition = any(),
                    softDelete = any()
                )
            }
        }

        @Test
        fun `processes delete event with __op d`() {
            val event = createEvent(operation = "d")
            val json = """{"id":"${event.id}","__op":"d"}"""

            stubDeserialization(json, event)
            stubValidationPasses(event)
            stubDelete(event.id)

            handler.handle(createRecord(value = json)).block()

            verify(exactly = 1) {
                customerService.delete(
                    id = event.id.toString(),
                    sourceTimestamp = any(),
                    kafkaOffset = any(),
                    kafkaPartition = any(),
                    softDelete = any()
                )
            }
        }
    }

    @Nested
    inner class ValidationFailures {

        @Test
        fun `skips processing when validation fails`() {
            val event = createEvent()
            val json = """{"id":"${event.id}"}"""

            stubDeserialization(json, event)
            stubValidationFails(event)

            handler.handle(createRecord(value = json)).block()

            verify(exactly = 1) { validationService.validate(event) }
            verify(exactly = 0) { customerService.upsert(any(), any(), any()) }
            verify(exactly = 0) { customerService.delete(any(), any(), any(), any(), any()) }
        }
    }

    @Nested
    inner class RecordMetadata {

        @Test
        fun `processes record with specific partition and offset`() {
            val event = createEvent()
            val json = """{"id":"${event.id}"}"""

            stubDeserialization(json, event)
            stubValidationPasses(event)
            stubUpsert(event)

            handler.handle(createRecord(value = json, partition = 2, offset = 12345)).block()

            verify(exactly = 1) { customerService.upsert(event, 12345, 2) }
        }
    }
}
