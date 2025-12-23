package com.pintailconsultingllc.cdcdebezium.acceptance

import com.pintailconsultingllc.cdcdebezium.repository.CustomerMongoRepository
import com.pintailconsultingllc.cdcdebezium.schema.SchemaChangeDetector
import com.pintailconsultingllc.cdcdebezium.schema.SchemaVersionTracker
import org.awaitility.kotlin.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.kafka.core.KafkaTemplate
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@DisplayName("Schema Change Handling")
class SchemaChangeHandlingAcceptanceTest : AbstractAcceptanceTest() {

    @Autowired
    private lateinit var kafkaTemplate: KafkaTemplate<String, String>

    @Autowired
    private lateinit var customerRepository: CustomerMongoRepository

    @Autowired
    private lateinit var schemaDetector: SchemaChangeDetector

    @Autowired
    private lateinit var schemaTracker: SchemaVersionTracker

    @Autowired
    private lateinit var mongoTemplate: ReactiveMongoTemplate

    @BeforeEach
    fun setUp() {
        customerRepository.deleteAll().block()
        mongoTemplate.dropCollection("schema_history").block()
        schemaDetector.resetTracking()
    }

    @Nested
    @DisplayName("Unknown Field Handling")
    inner class UnknownFieldHandling {

        @Test
        @DisplayName("should process event with unknown field without error")
        fun shouldProcessEventWithUnknownField() {
            val customerId = UUID.randomUUID()
            val email = "schema-test-${customerId}@example.com"

            val cdcEventWithNewField = """
                {
                    "id": "$customerId",
                    "email": "$email",
                    "status": "active",
                    "updated_at": "${Instant.now()}",
                    "phone_number": "+1-555-1234",
                    "__op": "c",
                    "__source_ts_ms": ${System.currentTimeMillis()}
                }
            """.trimIndent()

            kafkaTemplate.send(CUSTOMER_CDC_TOPIC, customerId.toString(), cdcEventWithNewField).get()

            await.atMost(Duration.ofSeconds(10)).untilAsserted {
                val customer = customerRepository.findById(customerId.toString()).block()
                assertNotNull(customer)
                assertEquals(email, customer.email)
                assertEquals("active", customer.status)
            }
        }

        @Test
        @DisplayName("should process multiple events with unknown fields")
        fun shouldProcessMultipleEventsWithUnknownFields() {
            val customerId1 = UUID.randomUUID()
            val customerId2 = UUID.randomUUID()
            // Use unique emails per test run to avoid duplicate key error from MongoDB email index
            val email1 = "multi-schema-1-$customerId1@example.com"
            val email2 = "multi-schema-2-$customerId2@example.com"

            val cdcEvent1 = """
                {
                    "id": "$customerId1",
                    "email": "$email1",
                    "status": "active",
                    "updated_at": "${Instant.now()}",
                    "unknown_field_1": "value1",
                    "__op": "c",
                    "__source_ts_ms": ${System.currentTimeMillis()}
                }
            """.trimIndent()

            val cdcEvent2 = """
                {
                    "id": "$customerId2",
                    "email": "$email2",
                    "status": "pending",
                    "updated_at": "${Instant.now()}",
                    "unknown_field_2": "value2",
                    "unknown_field_3": 42,
                    "__op": "c",
                    "__source_ts_ms": ${System.currentTimeMillis()}
                }
            """.trimIndent()

            kafkaTemplate.send(CUSTOMER_CDC_TOPIC, customerId1.toString(), cdcEvent1).get()
            kafkaTemplate.send(CUSTOMER_CDC_TOPIC, customerId2.toString(), cdcEvent2).get()

            await.atMost(Duration.ofSeconds(30)).untilAsserted {
                val customer1 = customerRepository.findById(customerId1.toString()).block()
                val customer2 = customerRepository.findById(customerId2.toString()).block()

                assertNotNull(customer1)
                assertNotNull(customer2)
                assertEquals(email1, customer1.email)
                assertEquals(email2, customer2.email)
            }
        }
    }

    @Nested
    @DisplayName("Schema Change Detection")
    inner class SchemaChangeDetection {

        @Test
        @DisplayName("should detect new field in CDC event")
        fun shouldDetectNewField() {
            val customerId = UUID.randomUUID()
            val uniqueEmail = "detect-new-field-$customerId@example.com"

            val cdcEventWithNewField = """
                {
                    "id": "$customerId",
                    "email": "$uniqueEmail",
                    "status": "active",
                    "updated_at": "${Instant.now()}",
                    "loyalty_tier": "gold",
                    "__op": "c",
                    "__source_ts_ms": ${System.currentTimeMillis()}
                }
            """.trimIndent()

            kafkaTemplate.send(CUSTOMER_CDC_TOPIC, customerId.toString(), cdcEventWithNewField).get()

            await.atMost(Duration.ofSeconds(30)).untilAsserted {
                val customer = customerRepository.findById(customerId.toString()).block()
                assertNotNull(customer)

                val knownFields = schemaDetector.getKnownFields("customer")
                assertTrue(knownFields.contains("loyalty_tier"))
            }
        }

        @Test
        @DisplayName("should record schema change to history collection")
        fun shouldRecordSchemaChangeToHistory() {
            val customerId = UUID.randomUUID()
            val uniqueEmail = "history-test-$customerId@example.com"

            val cdcEventWithNewField = """
                {
                    "id": "$customerId",
                    "email": "$uniqueEmail",
                    "status": "active",
                    "updated_at": "${Instant.now()}",
                    "unique_tracking_field": "tracked",
                    "__op": "c",
                    "__source_ts_ms": ${System.currentTimeMillis()}
                }
            """.trimIndent()

            kafkaTemplate.send(CUSTOMER_CDC_TOPIC, customerId.toString(), cdcEventWithNewField).get()

            await.atMost(Duration.ofSeconds(30)).untilAsserted {
                val customer = customerRepository.findById(customerId.toString()).block()
                assertNotNull(customer)
            }

            Thread.sleep(1000)

            val history = schemaTracker.getSchemaHistory("customer").block()
            assertNotNull(history)
            assertTrue(history.isNotEmpty())
            assertTrue(history.any { it.fieldName == "unique_tracking_field" })
        }
    }

    @Nested
    @DisplayName("Missing Optional Fields")
    inner class MissingOptionalFields {

        @Test
        @DisplayName("should handle missing optional status field")
        fun shouldHandleMissingOptionalField() {
            val customerId = UUID.randomUUID()
            // Use unique email per test run to avoid duplicate key error from MongoDB email index
            val uniqueEmail = "no-status-$customerId@example.com"

            val cdcEventWithoutStatus = """
                {
                    "id": "$customerId",
                    "email": "$uniqueEmail",
                    "updated_at": "${Instant.now()}",
                    "__op": "c",
                    "__source_ts_ms": ${System.currentTimeMillis()}
                }
            """.trimIndent()

            kafkaTemplate.send(CUSTOMER_CDC_TOPIC, customerId.toString(), cdcEventWithoutStatus).get()

            // Longer timeout to allow consumer to process backlog of events from previous tests
            await.atMost(Duration.ofSeconds(30)).untilAsserted {
                val customer = customerRepository.findById(customerId.toString()).block()
                assertNotNull(customer)
                assertEquals(uniqueEmail, customer.email)
            }
        }
    }

    @Nested
    @DisplayName("Schema Version Tracking")
    inner class SchemaVersionTracking {

        @Test
        @DisplayName("should provide current schema version for entity type")
        fun shouldProvideCurrentSchemaVersion() {
            val version = schemaDetector.getCurrentSchema("customer")

            assertEquals("customer", version.entityType)
            assertTrue(version.fields.isNotEmpty())
            assertTrue(version.fields.contains("email"))
            assertTrue(version.fields.contains("id"))
        }
    }

    companion object {
        private const val CUSTOMER_CDC_TOPIC = "cdc.public.customer"
    }
}
