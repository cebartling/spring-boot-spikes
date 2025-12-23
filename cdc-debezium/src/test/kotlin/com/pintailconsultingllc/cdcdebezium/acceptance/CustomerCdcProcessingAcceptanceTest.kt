package com.pintailconsultingllc.cdcdebezium.acceptance

import com.pintailconsultingllc.cdcdebezium.repository.CustomerMongoRepository
import org.awaitility.kotlin.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.test.utils.KafkaTestUtils
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@DisplayName("Customer CDC Event Processing")
class CustomerCdcProcessingAcceptanceTest : AbstractAcceptanceTest() {

    @Autowired
    private lateinit var kafkaTemplate: KafkaTemplate<String, String>

    @Autowired
    private lateinit var customerRepository: CustomerMongoRepository

    @BeforeEach
    fun setUp() {
        customerRepository.deleteAll().block()
    }

    @Nested
    @DisplayName("Insert Events")
    inner class InsertEvents {

        @Test
        @DisplayName("should persist customer when insert CDC event is received")
        fun shouldPersistCustomerOnInsert() {
            val customerId = UUID.randomUUID()
            val email = "acceptance-test-${customerId}@example.com"
            val status = "active"
            val updatedAt = Instant.now()
            val sourceTimestamp = System.currentTimeMillis()

            val cdcEvent = buildCdcEventJson(
                id = customerId,
                email = email,
                status = status,
                updatedAt = updatedAt,
                operation = "c",
                sourceTimestamp = sourceTimestamp
            )

            kafkaTemplate.send(CUSTOMER_CDC_TOPIC, customerId.toString(), cdcEvent).get()

            await.atMost(Duration.ofSeconds(10)).untilAsserted {
                val customer = customerRepository.findById(customerId.toString()).block()
                assertNotNull(customer)
                assertEquals(email, customer.email)
                assertEquals(status, customer.status)
            }
        }

        @Test
        @DisplayName("should persist customer when snapshot read event is received")
        fun shouldPersistCustomerOnSnapshotRead() {
            val customerId = UUID.randomUUID()
            val email = "snapshot-${customerId}@example.com"

            val cdcEvent = buildCdcEventJson(
                id = customerId,
                email = email,
                status = "active",
                updatedAt = Instant.now(),
                operation = "r",
                sourceTimestamp = System.currentTimeMillis()
            )

            kafkaTemplate.send(CUSTOMER_CDC_TOPIC, customerId.toString(), cdcEvent).get()

            await.atMost(Duration.ofSeconds(10)).untilAsserted {
                val customer = customerRepository.findById(customerId.toString()).block()
                assertNotNull(customer)
                assertEquals(email, customer.email)
            }
        }
    }

    @Nested
    @DisplayName("Update Events")
    inner class UpdateEvents {

        @Test
        @DisplayName("should update existing customer when update CDC event is received")
        fun shouldUpdateCustomerOnUpdate() {
            val customerId = UUID.randomUUID()
            val originalEmail = "original-${customerId}@example.com"
            val updatedEmail = "updated-${customerId}@example.com"
            val originalTimestamp = System.currentTimeMillis()
            val updateTimestamp = originalTimestamp + 1000

            val insertEvent = buildCdcEventJson(
                id = customerId,
                email = originalEmail,
                status = "active",
                updatedAt = Instant.now(),
                operation = "c",
                sourceTimestamp = originalTimestamp
            )
            kafkaTemplate.send(CUSTOMER_CDC_TOPIC, customerId.toString(), insertEvent).get()

            await.atMost(Duration.ofSeconds(10)).untilAsserted {
                val customer = customerRepository.findById(customerId.toString()).block()
                assertNotNull(customer)
                assertEquals(originalEmail, customer.email)
            }

            val updateEvent = buildCdcEventJson(
                id = customerId,
                email = updatedEmail,
                status = "inactive",
                updatedAt = Instant.now(),
                operation = "u",
                sourceTimestamp = updateTimestamp
            )
            kafkaTemplate.send(CUSTOMER_CDC_TOPIC, customerId.toString(), updateEvent).get()

            await.atMost(Duration.ofSeconds(10)).untilAsserted {
                val customer = customerRepository.findById(customerId.toString()).block()
                assertNotNull(customer)
                assertEquals(updatedEmail, customer.email)
                assertEquals("inactive", customer.status)
            }
        }

        @Test
        @DisplayName("should skip stale update when older event arrives after newer event")
        fun shouldSkipStaleUpdate() {
            val customerId = UUID.randomUUID()
            val newerTimestamp = System.currentTimeMillis() + 5000
            val olderTimestamp = System.currentTimeMillis()

            val newerEvent = buildCdcEventJson(
                id = customerId,
                email = "newer-${customerId}@example.com",
                status = "active",
                updatedAt = Instant.now(),
                operation = "c",
                sourceTimestamp = newerTimestamp
            )
            kafkaTemplate.send(CUSTOMER_CDC_TOPIC, customerId.toString(), newerEvent).get()

            await.atMost(Duration.ofSeconds(10)).untilAsserted {
                val customer = customerRepository.findById(customerId.toString()).block()
                assertNotNull(customer)
                assertEquals("newer-${customerId}@example.com", customer.email)
            }

            val staleEvent = buildCdcEventJson(
                id = customerId,
                email = "stale-${customerId}@example.com",
                status = "inactive",
                updatedAt = Instant.now(),
                operation = "u",
                sourceTimestamp = olderTimestamp
            )
            kafkaTemplate.send(CUSTOMER_CDC_TOPIC, customerId.toString(), staleEvent).get()

            Thread.sleep(2000)

            val customer = customerRepository.findById(customerId.toString()).block()
            assertNotNull(customer)
            assertEquals("newer-${customerId}@example.com", customer.email)
            assertEquals("active", customer.status)
        }
    }

    @Nested
    @DisplayName("Delete Events")
    inner class DeleteEvents {

        @Test
        @DisplayName("should delete customer when delete CDC event with __deleted flag is received")
        fun shouldDeleteCustomerWithDeletedFlag() {
            val customerId = UUID.randomUUID()
            val insertTimestamp = System.currentTimeMillis()
            val deleteTimestamp = insertTimestamp + 1000

            val insertEvent = buildCdcEventJson(
                id = customerId,
                email = "delete-test-${customerId}@example.com",
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
                email = "delete-test-${customerId}@example.com",
                status = "active",
                updatedAt = Instant.now(),
                deleted = "true",
                sourceTimestamp = deleteTimestamp
            )
            kafkaTemplate.send(CUSTOMER_CDC_TOPIC, customerId.toString(), deleteEvent).get()

            await.atMost(Duration.ofSeconds(10)).untilAsserted {
                val customer = customerRepository.findById(customerId.toString()).block()
                assertTrue(customer == null)
            }
        }

        @Test
        @DisplayName("should delete customer when delete CDC event with __op=d is received")
        fun shouldDeleteCustomerWithOpDelete() {
            val customerId = UUID.randomUUID()
            val insertTimestamp = System.currentTimeMillis()
            val deleteTimestamp = insertTimestamp + 1000

            val insertEvent = buildCdcEventJson(
                id = customerId,
                email = "op-delete-${customerId}@example.com",
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
                email = "op-delete-${customerId}@example.com",
                status = "active",
                updatedAt = Instant.now(),
                operation = "d",
                sourceTimestamp = deleteTimestamp
            )
            kafkaTemplate.send(CUSTOMER_CDC_TOPIC, customerId.toString(), deleteEvent).get()

            await.atMost(Duration.ofSeconds(10)).untilAsserted {
                val customer = customerRepository.findById(customerId.toString()).block()
                assertTrue(customer == null)
            }
        }

        @Test
        @DisplayName("should handle delete of non-existent customer gracefully")
        fun shouldHandleDeleteOfNonExistentCustomer() {
            val customerId = UUID.randomUUID()

            val deleteEvent = buildCdcEventJson(
                id = customerId,
                email = "non-existent-${customerId}@example.com",
                status = "active",
                updatedAt = Instant.now(),
                deleted = "true",
                sourceTimestamp = System.currentTimeMillis()
            )
            kafkaTemplate.send(CUSTOMER_CDC_TOPIC, customerId.toString(), deleteEvent).get()

            Thread.sleep(2000)

            val customer = customerRepository.findById(customerId.toString()).block()
            assertTrue(customer == null)
        }
    }

    @Nested
    @DisplayName("Tombstone Events")
    inner class TombstoneEvents {

        @Test
        @DisplayName("should handle tombstone (null value) message gracefully")
        fun shouldHandleTombstoneMessage() {
            val customerId = UUID.randomUUID()
            val insertTimestamp = System.currentTimeMillis()

            val insertEvent = buildCdcEventJson(
                id = customerId,
                email = "tombstone-test-${customerId}@example.com",
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

            kafkaTemplate.send(CUSTOMER_CDC_TOPIC, customerId.toString(), null as String?).get()

            Thread.sleep(2000)

            val customer = customerRepository.findById(customerId.toString()).block()
            assertNotNull(customer)
            assertEquals("tombstone-test-${customerId}@example.com", customer.email)
        }
    }

    @Nested
    @DisplayName("Idempotency")
    inner class IdempotencyTests {

        @Test
        @DisplayName("should handle duplicate insert events idempotently")
        fun shouldHandleDuplicateInserts() {
            val customerId = UUID.randomUUID()
            val timestamp = System.currentTimeMillis()

            val cdcEvent = buildCdcEventJson(
                id = customerId,
                email = "duplicate-${customerId}@example.com",
                status = "active",
                updatedAt = Instant.now(),
                operation = "c",
                sourceTimestamp = timestamp
            )

            kafkaTemplate.send(CUSTOMER_CDC_TOPIC, customerId.toString(), cdcEvent).get()
            kafkaTemplate.send(CUSTOMER_CDC_TOPIC, customerId.toString(), cdcEvent).get()
            kafkaTemplate.send(CUSTOMER_CDC_TOPIC, customerId.toString(), cdcEvent).get()

            await.atMost(Duration.ofSeconds(10)).untilAsserted {
                val customer = customerRepository.findById(customerId.toString()).block()
                assertNotNull(customer)
            }

            val count = customerRepository.count().block()
            assertEquals(1L, count)
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
