package com.pintailconsultingllc.cdcdebezium.acceptance

import com.pintailconsultingllc.cdcdebezium.repository.CustomerMongoRepository
import org.awaitility.kotlin.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.kafka.core.KafkaTemplate
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@DisplayName("Customer CDC Error Handling")
class CustomerCdcErrorHandlingAcceptanceTest : AbstractAcceptanceTest() {

    @Autowired
    private lateinit var kafkaTemplate: KafkaTemplate<String, String>

    @Autowired
    private lateinit var customerRepository: CustomerMongoRepository

    @Autowired
    private lateinit var consumerReadinessChecker: KafkaConsumerReadinessChecker

    @BeforeEach
    fun setUp() {
        customerRepository.deleteAll().block()
        consumerReadinessChecker.waitForConsumerReady()
    }

    @Nested
    @DisplayName("Malformed JSON Handling")
    inner class MalformedJsonHandling {

        @Test
        @DisplayName("should continue processing after malformed JSON message")
        fun shouldContinueProcessingAfterMalformedJson() {
            val malformedJson = "{ invalid json content"
            kafkaTemplate.send(CUSTOMER_CDC_TOPIC, "bad-key", malformedJson).get()

            val validCustomerId = UUID.randomUUID()
            val validEvent = buildCdcEventJson(
                id = validCustomerId,
                email = "after-error-${validCustomerId}@example.com",
                status = "active",
                updatedAt = Instant.now(),
                operation = "c",
                sourceTimestamp = System.currentTimeMillis()
            )
            kafkaTemplate.send(CUSTOMER_CDC_TOPIC, validCustomerId.toString(), validEvent).get()

            await.atMost(Duration.ofSeconds(15)).untilAsserted {
                val customer = customerRepository.findById(validCustomerId.toString()).block()
                assertNotNull(customer)
                assertEquals("after-error-${validCustomerId}@example.com", customer.email)
            }
        }

        @Test
        @DisplayName("should continue processing after invalid UUID in message")
        fun shouldContinueProcessingAfterInvalidUuid() {
            val invalidUuidJson = """{ "id": "not-a-uuid", "email": "test@example.com", "status": "active" }"""
            kafkaTemplate.send(CUSTOMER_CDC_TOPIC, "invalid-uuid-key", invalidUuidJson).get()

            val validCustomerId = UUID.randomUUID()
            val validEvent = buildCdcEventJson(
                id = validCustomerId,
                email = "after-uuid-error-${validCustomerId}@example.com",
                status = "active",
                updatedAt = Instant.now(),
                operation = "c",
                sourceTimestamp = System.currentTimeMillis()
            )
            kafkaTemplate.send(CUSTOMER_CDC_TOPIC, validCustomerId.toString(), validEvent).get()

            await.atMost(Duration.ofSeconds(15)).untilAsserted {
                val customer = customerRepository.findById(validCustomerId.toString()).block()
                assertNotNull(customer)
                assertEquals("after-uuid-error-${validCustomerId}@example.com", customer.email)
            }
        }
    }

    @Nested
    @DisplayName("Missing Required Fields")
    inner class MissingRequiredFields {

        @Test
        @DisplayName("should handle message with missing email field")
        fun shouldHandleMissingEmail() {
            val customerId = UUID.randomUUID()
            val jsonWithMissingEmail = """{ "id": "$customerId", "status": "active", "__source_ts_ms": ${System.currentTimeMillis()} }"""

            kafkaTemplate.send(CUSTOMER_CDC_TOPIC, customerId.toString(), jsonWithMissingEmail).get()

            val validCustomerId = UUID.randomUUID()
            val validEvent = buildCdcEventJson(
                id = validCustomerId,
                email = "after-missing-email-${validCustomerId}@example.com",
                status = "active",
                updatedAt = Instant.now(),
                operation = "c",
                sourceTimestamp = System.currentTimeMillis()
            )
            kafkaTemplate.send(CUSTOMER_CDC_TOPIC, validCustomerId.toString(), validEvent).get()

            await.atMost(Duration.ofSeconds(15)).untilAsserted {
                val customer = customerRepository.findById(validCustomerId.toString()).block()
                assertNotNull(customer)
            }
        }
    }

    @Nested
    @DisplayName("Schema Evolution")
    inner class SchemaEvolution {

        @Test
        @DisplayName("should ignore unknown fields in CDC event")
        fun shouldIgnoreUnknownFields() {
            val customerId = UUID.randomUUID()
            val jsonWithExtraFields = """{
                "id": "$customerId",
                "email": "schema-evolution-${customerId}@example.com",
                "status": "active",
                "updated_at": "${Instant.now()}",
                "__op": "c",
                "__source_ts_ms": ${System.currentTimeMillis()},
                "phone": "+1-555-0100",
                "address": "123 Main St",
                "unknown_field": "some value"
            }""".trimIndent()

            kafkaTemplate.send(CUSTOMER_CDC_TOPIC, customerId.toString(), jsonWithExtraFields).get()

            await.atMost(Duration.ofSeconds(10)).untilAsserted {
                val customer = customerRepository.findById(customerId.toString()).block()
                assertNotNull(customer)
                assertEquals("schema-evolution-${customerId}@example.com", customer.email)
                assertEquals("active", customer.status)
            }
        }
    }

    companion object {
        private const val CUSTOMER_CDC_TOPIC = "cdc.public.customer"

        fun buildCdcEventJson(
            id: UUID,
            email: String,
            status: String,
            updatedAt: Instant,
            operation: String? = null,
            deleted: String? = null,
            sourceTimestamp: Long
        ): String {
            val fields = mutableListOf<String>()
            fields.add(""""id": "$id"""")
            fields.add(""""email": "$email"""")
            fields.add(""""status": "$status"""")
            fields.add(""""updated_at": "$updatedAt"""")
            operation?.let { fields.add(""""__op": "$it"""") }
            deleted?.let { fields.add(""""__deleted": "$it"""") }
            fields.add(""""__source_ts_ms": $sourceTimestamp""")
            return "{ ${fields.joinToString(", ")} }"
        }
    }
}
