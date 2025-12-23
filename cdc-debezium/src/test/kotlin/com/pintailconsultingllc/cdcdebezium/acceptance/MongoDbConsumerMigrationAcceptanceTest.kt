package com.pintailconsultingllc.cdcdebezium.acceptance

import com.pintailconsultingllc.cdcdebezium.document.CdcOperation
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
import kotlin.test.assertTrue

@DisplayName("MongoDB Consumer Migration (PLAN-013)")
class MongoDbConsumerMigrationAcceptanceTest : AbstractAcceptanceTest() {

    @Autowired
    private lateinit var kafkaTemplate: KafkaTemplate<String, String>

    @Autowired
    private lateinit var customerRepository: CustomerMongoRepository

    @BeforeEach
    fun setUp() {
        customerRepository.deleteAll().block()
    }

    @Nested
    @DisplayName("INSERT Event CDC Metadata")
    inner class InsertEventCdcMetadata {

        @Test
        @DisplayName("should create document with cdcMetadata.operation INSERT")
        fun shouldCreateDocumentWithInsertOperation() {
            val customerId = UUID.randomUUID()
            val sourceTimestamp = System.currentTimeMillis()

            val cdcEvent = buildCdcEventJson(
                id = customerId,
                email = "insert-metadata-$customerId@example.com",
                status = "active",
                updatedAt = Instant.now(),
                operation = "c",
                sourceTimestamp = sourceTimestamp
            )

            kafkaTemplate.send(CUSTOMER_CDC_TOPIC, customerId.toString(), cdcEvent).get()

            await.atMost(Duration.ofSeconds(10)).untilAsserted {
                val customer = customerRepository.findById(customerId.toString()).block()
                assertNotNull(customer)
                assertNotNull(customer.cdcMetadata)
                assertEquals(CdcOperation.INSERT, customer.cdcMetadata.operation)
            }
        }

        @Test
        @DisplayName("should record sourceTimestamp from CDC event")
        fun shouldRecordSourceTimestamp() {
            val customerId = UUID.randomUUID()
            val sourceTimestamp = System.currentTimeMillis()

            val cdcEvent = buildCdcEventJson(
                id = customerId,
                email = "source-ts-$customerId@example.com",
                status = "active",
                updatedAt = Instant.now(),
                operation = "c",
                sourceTimestamp = sourceTimestamp
            )

            kafkaTemplate.send(CUSTOMER_CDC_TOPIC, customerId.toString(), cdcEvent).get()

            await.atMost(Duration.ofSeconds(10)).untilAsserted {
                val customer = customerRepository.findById(customerId.toString()).block()
                assertNotNull(customer)
                assertEquals(sourceTimestamp, customer.cdcMetadata.sourceTimestamp)
            }
        }

        @Test
        @DisplayName("should record processedAt timestamp")
        fun shouldRecordProcessedAtTimestamp() {
            val customerId = UUID.randomUUID()
            val beforeSend = Instant.now().minusSeconds(1)

            val cdcEvent = buildCdcEventJson(
                id = customerId,
                email = "processed-at-$customerId@example.com",
                status = "active",
                updatedAt = Instant.now(),
                operation = "c",
                sourceTimestamp = System.currentTimeMillis()
            )

            kafkaTemplate.send(CUSTOMER_CDC_TOPIC, customerId.toString(), cdcEvent).get()
            val afterSend = Instant.now().plusSeconds(10)

            await.atMost(Duration.ofSeconds(10)).untilAsserted {
                val customer = customerRepository.findById(customerId.toString()).block()
                assertNotNull(customer)
                assertNotNull(customer.cdcMetadata.processedAt)
                assertTrue(customer.cdcMetadata.processedAt.isAfter(beforeSend))
                assertTrue(customer.cdcMetadata.processedAt.isBefore(afterSend))
            }
        }

        @Test
        @DisplayName("should record kafka offset and partition")
        fun shouldRecordKafkaOffsetAndPartition() {
            val customerId = UUID.randomUUID()

            val cdcEvent = buildCdcEventJson(
                id = customerId,
                email = "kafka-info-$customerId@example.com",
                status = "active",
                updatedAt = Instant.now(),
                operation = "c",
                sourceTimestamp = System.currentTimeMillis()
            )

            kafkaTemplate.send(CUSTOMER_CDC_TOPIC, customerId.toString(), cdcEvent).get()

            await.atMost(Duration.ofSeconds(10)).untilAsserted {
                val customer = customerRepository.findById(customerId.toString()).block()
                assertNotNull(customer)
                // Kafka offset should be >= 0
                assertTrue(customer.cdcMetadata.kafkaOffset >= 0)
                // Kafka partition should be >= 0
                assertTrue(customer.cdcMetadata.kafkaPartition >= 0)
            }
        }
    }

    @Nested
    @DisplayName("UPDATE Event CDC Metadata")
    inner class UpdateEventCdcMetadata {

        @Test
        @DisplayName("should update document with cdcMetadata.operation UPDATE")
        fun shouldUpdateDocumentWithUpdateOperation() {
            val customerId = UUID.randomUUID()
            val insertTimestamp = System.currentTimeMillis()
            val updateTimestamp = insertTimestamp + 1000

            val insertEvent = buildCdcEventJson(
                id = customerId,
                email = "update-op-$customerId@example.com",
                status = "active",
                updatedAt = Instant.now(),
                operation = "c",
                sourceTimestamp = insertTimestamp
            )
            kafkaTemplate.send(CUSTOMER_CDC_TOPIC, customerId.toString(), insertEvent).get()

            await.atMost(Duration.ofSeconds(10)).untilAsserted {
                val customer = customerRepository.findById(customerId.toString()).block()
                assertNotNull(customer)
                assertEquals(CdcOperation.INSERT, customer.cdcMetadata.operation)
            }

            val updateEvent = buildCdcEventJson(
                id = customerId,
                email = "update-op-$customerId@example.com",
                status = "inactive",
                updatedAt = Instant.now(),
                operation = "u",
                sourceTimestamp = updateTimestamp
            )
            kafkaTemplate.send(CUSTOMER_CDC_TOPIC, customerId.toString(), updateEvent).get()

            await.atMost(Duration.ofSeconds(10)).untilAsserted {
                val customer = customerRepository.findById(customerId.toString()).block()
                assertNotNull(customer)
                assertEquals("inactive", customer.status)
                assertEquals(CdcOperation.UPDATE, customer.cdcMetadata.operation)
                assertEquals(updateTimestamp, customer.cdcMetadata.sourceTimestamp)
            }
        }

        @Test
        @DisplayName("should update cdcMetadata when document is modified")
        fun shouldUpdateCdcMetadataWhenModified() {
            val customerId = UUID.randomUUID()
            val insertTimestamp = System.currentTimeMillis()
            val updateTimestamp = insertTimestamp + 1000

            val insertEvent = buildCdcEventJson(
                id = customerId,
                email = "metadata-update-$customerId@example.com",
                status = "active",
                updatedAt = Instant.now(),
                operation = "c",
                sourceTimestamp = insertTimestamp
            )
            kafkaTemplate.send(CUSTOMER_CDC_TOPIC, customerId.toString(), insertEvent).get()

            await.atMost(Duration.ofSeconds(10)).untilAsserted {
                val customer = customerRepository.findById(customerId.toString()).block()
                assertNotNull(customer)
            }

            val insertedCustomer = customerRepository.findById(customerId.toString()).block()
            assertNotNull(insertedCustomer)
            val originalProcessedAt = insertedCustomer.cdcMetadata.processedAt

            // Wait a bit to ensure processedAt will be different
            Thread.sleep(100)

            val updateEvent = buildCdcEventJson(
                id = customerId,
                email = "metadata-update-$customerId@example.com",
                status = "inactive",
                updatedAt = Instant.now(),
                operation = "u",
                sourceTimestamp = updateTimestamp
            )
            kafkaTemplate.send(CUSTOMER_CDC_TOPIC, customerId.toString(), updateEvent).get()

            await.atMost(Duration.ofSeconds(10)).untilAsserted {
                val customer = customerRepository.findById(customerId.toString()).block()
                assertNotNull(customer)
                assertEquals("inactive", customer.status)
                assertEquals(updateTimestamp, customer.cdcMetadata.sourceTimestamp)
                // processedAt should be updated
                assertTrue(customer.cdcMetadata.processedAt.isAfter(originalProcessedAt))
            }
        }
    }

    @Nested
    @DisplayName("DELETE Event Processing")
    inner class DeleteEventProcessing {

        @Test
        @DisplayName("should remove document from MongoDB on delete event")
        fun shouldRemoveDocumentOnDelete() {
            val customerId = UUID.randomUUID()
            val insertTimestamp = System.currentTimeMillis()
            val deleteTimestamp = insertTimestamp + 1000

            val insertEvent = buildCdcEventJson(
                id = customerId,
                email = "delete-doc-$customerId@example.com",
                status = "active",
                updatedAt = Instant.now(),
                operation = "c",
                sourceTimestamp = insertTimestamp
            )
            kafkaTemplate.send(CUSTOMER_CDC_TOPIC, customerId.toString(), insertEvent).get()

            await.atMost(Duration.ofSeconds(10)).untilAsserted {
                val customer = customerRepository.findById(customerId.toString()).block()
                assertNotNull(customer)
            }

            val deleteEvent = buildCdcEventJson(
                id = customerId,
                email = "delete-doc-$customerId@example.com",
                status = "active",
                updatedAt = Instant.now(),
                operation = "d",
                sourceTimestamp = deleteTimestamp
            )
            kafkaTemplate.send(CUSTOMER_CDC_TOPIC, customerId.toString(), deleteEvent).get()

            await.atMost(Duration.ofSeconds(10)).untilAsserted {
                val customer = customerRepository.findById(customerId.toString()).block()
                assertEquals(null, customer)
            }
        }
    }

    @Nested
    @DisplayName("Idempotent Processing")
    inner class IdempotentProcessing {

        @Test
        @DisplayName("should handle duplicate INSERT idempotently with single document")
        fun shouldHandleDuplicateInsertIdempotently() {
            val customerId = UUID.randomUUID()
            val timestamp = System.currentTimeMillis()

            val cdcEvent = buildCdcEventJson(
                id = customerId,
                email = "idempotent-$customerId@example.com",
                status = "active",
                updatedAt = Instant.now(),
                operation = "c",
                sourceTimestamp = timestamp
            )

            // Send the same event multiple times
            kafkaTemplate.send(CUSTOMER_CDC_TOPIC, customerId.toString(), cdcEvent).get()
            kafkaTemplate.send(CUSTOMER_CDC_TOPIC, customerId.toString(), cdcEvent).get()
            kafkaTemplate.send(CUSTOMER_CDC_TOPIC, customerId.toString(), cdcEvent).get()

            await.atMost(Duration.ofSeconds(10)).untilAsserted {
                val customer = customerRepository.findById(customerId.toString()).block()
                assertNotNull(customer)
            }

            // Should have exactly one document
            val count = customerRepository.count().block()
            assertEquals(1L, count)

            val customer = customerRepository.findById(customerId.toString()).block()
            assertNotNull(customer)
            assertEquals("idempotent-$customerId@example.com", customer.email)
        }

        @Test
        @DisplayName("should skip out-of-order events based on sourceTimestamp")
        fun shouldSkipOutOfOrderEvents() {
            val customerId = UUID.randomUUID()
            val newerTimestamp = System.currentTimeMillis() + 10000
            val olderTimestamp = System.currentTimeMillis()

            // Send newer event first
            val newerEvent = buildCdcEventJson(
                id = customerId,
                email = "out-of-order-$customerId@example.com",
                status = "active",
                updatedAt = Instant.now(),
                operation = "c",
                sourceTimestamp = newerTimestamp
            )
            kafkaTemplate.send(CUSTOMER_CDC_TOPIC, customerId.toString(), newerEvent).get()

            await.atMost(Duration.ofSeconds(10)).untilAsserted {
                val customer = customerRepository.findById(customerId.toString()).block()
                assertNotNull(customer)
                assertEquals(newerTimestamp, customer.cdcMetadata.sourceTimestamp)
            }

            // Send older event (should be skipped)
            val olderEvent = buildCdcEventJson(
                id = customerId,
                email = "out-of-order-$customerId@example.com",
                status = "should-not-update",
                updatedAt = Instant.now(),
                operation = "u",
                sourceTimestamp = olderTimestamp
            )
            kafkaTemplate.send(CUSTOMER_CDC_TOPIC, customerId.toString(), olderEvent).get()

            // Wait for potential processing
            Thread.sleep(2000)

            // Status should still be "active" from the newer event
            val customer = customerRepository.findById(customerId.toString()).block()
            assertNotNull(customer)
            assertEquals("active", customer.status)
            assertEquals(newerTimestamp, customer.cdcMetadata.sourceTimestamp)
        }
    }

    @Nested
    @DisplayName("Delete on Non-Existent Document")
    inner class DeleteNonExistent {

        @Test
        @DisplayName("should succeed without error when deleting non-existent document")
        fun shouldSucceedWithoutErrorOnNonExistentDelete() {
            val customerId = UUID.randomUUID()

            // Send delete event for a document that doesn't exist
            val deleteEvent = buildCdcEventJson(
                id = customerId,
                email = "non-existent-$customerId@example.com",
                status = "active",
                updatedAt = Instant.now(),
                operation = "d",
                sourceTimestamp = System.currentTimeMillis()
            )
            kafkaTemplate.send(CUSTOMER_CDC_TOPIC, customerId.toString(), deleteEvent).get()

            // Wait for processing
            Thread.sleep(2000)

            // Should not throw error and document should not exist
            val customer = customerRepository.findById(customerId.toString()).block()
            assertEquals(null, customer)
        }
    }

    @Nested
    @DisplayName("Complete CDC Metadata Recording")
    inner class CompleteCdcMetadataRecording {

        @Test
        @DisplayName("should record all CDC metadata fields correctly")
        fun shouldRecordAllCdcMetadataFields() {
            val customerId = UUID.randomUUID()
            val sourceTimestamp = System.currentTimeMillis()
            val beforeSend = Instant.now()

            val cdcEvent = buildCdcEventJson(
                id = customerId,
                email = "complete-metadata-$customerId@example.com",
                status = "active",
                updatedAt = Instant.now(),
                operation = "c",
                sourceTimestamp = sourceTimestamp
            )

            kafkaTemplate.send(CUSTOMER_CDC_TOPIC, customerId.toString(), cdcEvent).get()

            await.atMost(Duration.ofSeconds(10)).untilAsserted {
                val customer = customerRepository.findById(customerId.toString()).block()
                assertNotNull(customer)

                val metadata = customer.cdcMetadata
                assertNotNull(metadata)

                // Verify sourceTimestamp
                assertEquals(sourceTimestamp, metadata.sourceTimestamp)

                // Verify operation
                assertEquals(CdcOperation.INSERT, metadata.operation)

                // Verify kafkaOffset is recorded (should be non-negative)
                assertTrue(metadata.kafkaOffset >= 0, "kafkaOffset should be non-negative")

                // Verify kafkaPartition is recorded (should be non-negative)
                assertTrue(metadata.kafkaPartition >= 0, "kafkaPartition should be non-negative")

                // Verify processedAt is recorded and reasonable
                assertNotNull(metadata.processedAt)
                assertTrue(metadata.processedAt.isAfter(beforeSend.minusSeconds(1)))
                assertTrue(metadata.processedAt.isBefore(Instant.now().plusSeconds(10)))
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
