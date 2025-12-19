package com.pintailconsultingllc.cdcdebezium.consumer

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
import java.time.Instant
import java.util.UUID

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

    private fun createConsumerRecord(
        key: String = "test-key",
        value: String? = """{"id":"550e8400-e29b-41d4-a716-446655440000"}""",
        topic: String = "cdc.public.customer",
        partition: Int = 0,
        offset: Long = 0
    ): ConsumerRecord<String, String?> {
        return ConsumerRecord(topic, partition, offset, key, value)
    }

    @Nested
    inner class ConsumeUpsertEvents {

        @Test
        fun `processes insert event and acknowledges`() {
            val event = CustomerCdcEvent(
                id = UUID.randomUUID(),
                email = "new@example.com",
                status = "active",
                updatedAt = Instant.now(),
                operation = "c"
            )
            val json = """{"id":"${event.id}","email":"new@example.com","status":"active","__op":"c"}"""
            val record = createConsumerRecord(value = json)

            every { objectMapper.readValue(json, CustomerCdcEvent::class.java) } returns event

            consumer.consume(record, acknowledgment)

            verify(exactly = 1) { objectMapper.readValue(json, CustomerCdcEvent::class.java) }
            verify(exactly = 1) { acknowledgment.acknowledge() }
        }

        @Test
        fun `processes update event and acknowledges`() {
            val event = CustomerCdcEvent(
                id = UUID.randomUUID(),
                email = "updated@example.com",
                status = "inactive",
                updatedAt = Instant.now(),
                operation = "u"
            )
            val json = """{"id":"${event.id}","email":"updated@example.com","status":"inactive","__op":"u"}"""
            val record = createConsumerRecord(value = json)

            every { objectMapper.readValue(json, CustomerCdcEvent::class.java) } returns event

            consumer.consume(record, acknowledgment)

            verify(exactly = 1) { acknowledgment.acknowledge() }
        }

        @Test
        fun `processes snapshot event and acknowledges`() {
            val event = CustomerCdcEvent(
                id = UUID.randomUUID(),
                email = "existing@example.com",
                status = "active",
                updatedAt = Instant.now(),
                operation = "r"
            )
            val json = """{"id":"${event.id}","email":"existing@example.com","__op":"r"}"""
            val record = createConsumerRecord(value = json)

            every { objectMapper.readValue(json, CustomerCdcEvent::class.java) } returns event

            consumer.consume(record, acknowledgment)

            verify(exactly = 1) { acknowledgment.acknowledge() }
        }
    }

    @Nested
    inner class ConsumeDeleteEvents {

        @Test
        fun `processes delete event with __deleted flag and acknowledges`() {
            val event = CustomerCdcEvent(
                id = UUID.randomUUID(),
                email = "deleted@example.com",
                status = "active",
                updatedAt = Instant.now(),
                deleted = "true"
            )
            val json = """{"id":"${event.id}","__deleted":"true"}"""
            val record = createConsumerRecord(value = json)

            every { objectMapper.readValue(json, CustomerCdcEvent::class.java) } returns event

            consumer.consume(record, acknowledgment)

            verify(exactly = 1) { acknowledgment.acknowledge() }
        }

        @Test
        fun `processes delete event with __op d and acknowledges`() {
            val event = CustomerCdcEvent(
                id = UUID.randomUUID(),
                email = "deleted@example.com",
                status = "active",
                updatedAt = Instant.now(),
                operation = "d"
            )
            val json = """{"id":"${event.id}","__op":"d"}"""
            val record = createConsumerRecord(value = json)

            every { objectMapper.readValue(json, CustomerCdcEvent::class.java) } returns event

            consumer.consume(record, acknowledgment)

            verify(exactly = 1) { acknowledgment.acknowledge() }
        }
    }

    @Nested
    inner class ConsumeTombstones {

        @Test
        fun `handles null value tombstone and acknowledges`() {
            val record = createConsumerRecord(value = null)

            consumer.consume(record, acknowledgment)

            verify(exactly = 0) { objectMapper.readValue(any<String>(), CustomerCdcEvent::class.java) }
            verify(exactly = 1) { acknowledgment.acknowledge() }
        }

        @Test
        fun `does not call objectMapper for tombstone`() {
            val record = createConsumerRecord(key = "deleted-customer-key", value = null)

            consumer.consume(record, acknowledgment)

            verify(exactly = 0) { objectMapper.readValue(any<String>(), any<Class<*>>()) }
        }
    }

    @Nested
    inner class ErrorHandling {

        @Test
        fun `acknowledges message when JSON parsing fails`() {
            val malformedJson = "{ invalid json }"
            val record = createConsumerRecord(value = malformedJson)

            every {
                objectMapper.readValue(malformedJson, CustomerCdcEvent::class.java)
            } throws RuntimeException("Failed to parse JSON")

            consumer.consume(record, acknowledgment)

            verify(exactly = 1) { acknowledgment.acknowledge() }
        }

        @Test
        fun `acknowledges message when objectMapper throws exception`() {
            val json = """{"id":"not-a-uuid"}"""
            val record = createConsumerRecord(value = json)

            every {
                objectMapper.readValue(json, CustomerCdcEvent::class.java)
            } throws IllegalArgumentException("Invalid UUID")

            consumer.consume(record, acknowledgment)

            verify(exactly = 1) { acknowledgment.acknowledge() }
        }
    }

    @Nested
    inner class RecordMetadata {

        @Test
        fun `processes record with specific partition and offset`() {
            val event = CustomerCdcEvent(
                id = UUID.randomUUID(),
                email = "test@example.com",
                status = "active",
                updatedAt = Instant.now()
            )
            val json = """{"id":"${event.id}"}"""
            val record = createConsumerRecord(
                value = json,
                partition = 2,
                offset = 12345
            )

            every { objectMapper.readValue(json, CustomerCdcEvent::class.java) } returns event

            consumer.consume(record, acknowledgment)

            verify(exactly = 1) { acknowledgment.acknowledge() }
        }

        @Test
        fun `processes record with different topic name`() {
            val event = CustomerCdcEvent(
                id = UUID.randomUUID(),
                email = "test@example.com",
                status = "active",
                updatedAt = Instant.now()
            )
            val json = """{"id":"${event.id}"}"""
            val record = createConsumerRecord(
                value = json,
                topic = "different.topic.name"
            )

            every { objectMapper.readValue(json, CustomerCdcEvent::class.java) } returns event

            consumer.consume(record, acknowledgment)

            verify(exactly = 1) { acknowledgment.acknowledge() }
        }
    }
}
