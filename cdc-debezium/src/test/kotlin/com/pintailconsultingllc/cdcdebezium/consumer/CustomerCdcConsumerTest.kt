package com.pintailconsultingllc.cdcdebezium.consumer

import com.pintailconsultingllc.cdcdebezium.TestFixtures.createEvent
import com.pintailconsultingllc.cdcdebezium.dto.CustomerCdcEvent
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.kafka.support.Acknowledgment
import tools.jackson.databind.ObjectMapper

class CustomerCdcConsumerTest {

    private lateinit var objectMapper: ObjectMapper
    private lateinit var acknowledgment: Acknowledgment
    private lateinit var consumer: CustomerCdcConsumer

    @BeforeEach
    fun setUp() {
        objectMapper = mockk()
        acknowledgment = mockk(relaxed = true)
        consumer = CustomerCdcConsumer(objectMapper)
    }

    private fun createRecord(
        key: String = "test-key",
        value: String? = """{"id":"550e8400-e29b-41d4-a716-446655440000"}""",
        topic: String = "cdc.public.customer",
        partition: Int = 0,
        offset: Long = 0
    ) = ConsumerRecord(topic, partition, offset, key, value)

    private fun stubDeserialization(json: String, event: CustomerCdcEvent) {
        every { objectMapper.readValue(json, CustomerCdcEvent::class.java) } returns event
    }

    private fun stubDeserializationThrows(json: String, exception: Exception) {
        every { objectMapper.readValue(json, CustomerCdcEvent::class.java) } throws exception
    }

    private fun consumeAndVerifyAcknowledged(record: ConsumerRecord<String, String?>) {
        consumer.consume(record, acknowledgment)
        verify(exactly = 1) { acknowledgment.acknowledge() }
    }

    @Nested
    inner class UpsertEvents {

        @Test
        fun `processes insert event and acknowledges`() {
            val event = createEvent(operation = "c")
            val json = """{"id":"${event.id}","__op":"c"}"""

            stubDeserialization(json, event)
            consumeAndVerifyAcknowledged(createRecord(value = json))

            verify(exactly = 1) { objectMapper.readValue(json, CustomerCdcEvent::class.java) }
        }

        @Test
        fun `processes update event and acknowledges`() {
            val event = createEvent(operation = "u")
            val json = """{"id":"${event.id}","__op":"u"}"""

            stubDeserialization(json, event)
            consumeAndVerifyAcknowledged(createRecord(value = json))
        }

        @Test
        fun `processes snapshot event and acknowledges`() {
            val event = createEvent(operation = "r")
            val json = """{"id":"${event.id}","__op":"r"}"""

            stubDeserialization(json, event)
            consumeAndVerifyAcknowledged(createRecord(value = json))
        }
    }

    @Nested
    inner class DeleteEvents {

        @Test
        fun `processes delete event with __deleted flag`() {
            val event = createEvent(deleted = "true")
            val json = """{"id":"${event.id}","__deleted":"true"}"""

            stubDeserialization(json, event)
            consumeAndVerifyAcknowledged(createRecord(value = json))
        }

        @Test
        fun `processes delete event with __op d`() {
            val event = createEvent(operation = "d")
            val json = """{"id":"${event.id}","__op":"d"}"""

            stubDeserialization(json, event)
            consumeAndVerifyAcknowledged(createRecord(value = json))
        }
    }

    @Nested
    inner class Tombstones {

        @Test
        fun `handles null value tombstone and acknowledges`() {
            consumeAndVerifyAcknowledged(createRecord(value = null))
            verify(exactly = 0) { objectMapper.readValue(any<String>(), CustomerCdcEvent::class.java) }
        }
    }

    @Nested
    inner class ErrorHandling {

        @Test
        fun `acknowledges message when JSON parsing fails`() {
            val malformedJson = "{ invalid json }"
            stubDeserializationThrows(malformedJson, RuntimeException("Failed to parse JSON"))

            consumeAndVerifyAcknowledged(createRecord(value = malformedJson))
        }

        @Test
        fun `acknowledges message when objectMapper throws exception`() {
            val json = """{"id":"not-a-uuid"}"""
            stubDeserializationThrows(json, IllegalArgumentException("Invalid UUID"))

            consumeAndVerifyAcknowledged(createRecord(value = json))
        }
    }

    @Nested
    inner class RecordMetadata {

        @Test
        fun `processes record with specific partition and offset`() {
            val event = createEvent()
            val json = """{"id":"${event.id}"}"""

            stubDeserialization(json, event)
            consumeAndVerifyAcknowledged(createRecord(value = json, partition = 2, offset = 12345))
        }

        @Test
        fun `processes record with different topic name`() {
            val event = createEvent()
            val json = """{"id":"${event.id}"}"""

            stubDeserialization(json, event)
            consumeAndVerifyAcknowledged(createRecord(value = json, topic = "different.topic.name"))
        }
    }
}
