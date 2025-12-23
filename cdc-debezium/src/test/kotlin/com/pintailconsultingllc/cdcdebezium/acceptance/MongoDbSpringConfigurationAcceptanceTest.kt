package com.pintailconsultingllc.cdcdebezium.acceptance

import com.pintailconsultingllc.cdcdebezium.document.CdcMetadata
import com.pintailconsultingllc.cdcdebezium.document.CdcOperation
import com.pintailconsultingllc.cdcdebezium.document.CustomerDocument
import com.pintailconsultingllc.cdcdebezium.repository.CustomerMongoRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@DisplayName("MongoDB Spring Configuration (PLAN-012)")
class MongoDbSpringConfigurationAcceptanceTest : AbstractAcceptanceTest() {

    @Autowired
    private lateinit var applicationContext: ApplicationContext

    @Autowired
    private lateinit var customerRepository: CustomerMongoRepository

    @Autowired
    private lateinit var mongoTemplate: ReactiveMongoTemplate

    @BeforeEach
    fun setUp() {
        customerRepository.deleteAll().block()
    }

    @Nested
    @DisplayName("Application Startup and Health")
    inner class ApplicationStartupAndHealth {

        @Test
        @DisplayName("should start application with MongoDB connection")
        fun shouldStartApplicationWithMongoDbConnection() {
            // If we reach here, the application started successfully with MongoDB
            assertNotNull(applicationContext)
            assertTrue(applicationContext.containsBean("reactiveMongoTemplate"))
        }

        @Test
        @DisplayName("should have ReactiveMongoTemplate bean available")
        fun shouldHaveReactiveMongoTemplateBean() {
            val template = applicationContext.getBean(ReactiveMongoTemplate::class.java)
            assertNotNull(template)
        }

        @Test
        @DisplayName("should be able to ping MongoDB database")
        fun shouldPingMongoDbDatabase() {
            val result = mongoTemplate.executeCommand("""{"ping": 1}""").block()
            assertNotNull(result)
            assertEquals(1.0, result.getDouble("ok"))
        }
    }

    @Nested
    @DisplayName("Repository Bean Availability")
    inner class RepositoryBeanAvailability {

        @Test
        @DisplayName("should have CustomerMongoRepository bean available")
        fun shouldHaveCustomerMongoRepositoryBean() {
            assertTrue(applicationContext.containsBean("customerMongoRepository"))
        }

        @Test
        @DisplayName("should autowire CustomerMongoRepository successfully")
        fun shouldAutowireCustomerMongoRepository() {
            assertNotNull(customerRepository)
        }

        @Test
        @DisplayName("should have ReactiveMongoTemplate properly configured")
        fun shouldHaveReactiveMongoTemplateConfigured() {
            assertNotNull(mongoTemplate)
            val databaseName = mongoTemplate.mongoDatabaseFactory.mongoDatabase.block()?.name
            assertEquals("cdc_materialized", databaseName)
        }
    }

    @Nested
    @DisplayName("Document Save and Retrieve")
    inner class DocumentSaveAndRetrieve {

        @Test
        @DisplayName("should save document and retrieve by id")
        fun shouldSaveAndRetrieveById() {
            val customerId = UUID.randomUUID().toString()
            val customer = createTestCustomer(customerId, "save-retrieve-$customerId@example.com")

            customerRepository.save(customer).block()

            val retrieved = customerRepository.findById(customerId).block()
            assertNotNull(retrieved)
            assertEquals(customerId, retrieved.id)
            assertEquals(customer.email, retrieved.email)
            assertEquals(customer.status, retrieved.status)
        }

        @Test
        @DisplayName("should save document using ReactiveMongoTemplate")
        fun shouldSaveDocumentUsingTemplate() {
            val customerId = UUID.randomUUID().toString()
            val customer = createTestCustomer(customerId, "template-save-$customerId@example.com")

            mongoTemplate.save(customer).block()

            val retrieved = mongoTemplate.findById(customerId, CustomerDocument::class.java).block()
            assertNotNull(retrieved)
            assertEquals(customerId, retrieved.id)
        }

        @Test
        @DisplayName("should return null for non-existent document")
        fun shouldReturnNullForNonExistent() {
            val nonExistentId = UUID.randomUUID().toString()

            val retrieved = customerRepository.findById(nonExistentId).block()
            assertEquals(null, retrieved)
        }

        @Test
        @DisplayName("should update existing document")
        fun shouldUpdateExistingDocument() {
            val customerId = UUID.randomUUID().toString()
            val original = createTestCustomer(customerId, "update-test-$customerId@example.com", "active")

            customerRepository.save(original).block()

            val updated = original.copy(status = "inactive")
            customerRepository.save(updated).block()

            val retrieved = customerRepository.findById(customerId).block()
            assertNotNull(retrieved)
            assertEquals("inactive", retrieved.status)
        }

        @Test
        @DisplayName("should delete document by id")
        fun shouldDeleteDocumentById() {
            val customerId = UUID.randomUUID().toString()
            val customer = createTestCustomer(customerId, "delete-test-$customerId@example.com")

            customerRepository.save(customer).block()
            assertNotNull(customerRepository.findById(customerId).block())

            customerRepository.deleteById(customerId).block()

            assertEquals(null, customerRepository.findById(customerId).block())
        }
    }

    @Nested
    @DisplayName("CdcMetadata Embedding")
    inner class CdcMetadataEmbedding {

        @Test
        @DisplayName("should embed CdcMetadata with sourceTimestamp")
        fun shouldEmbedCdcMetadataWithSourceTimestamp() {
            val customerId = UUID.randomUUID().toString()
            val sourceTimestamp = System.currentTimeMillis()
            val customer = createTestCustomer(
                customerId,
                "metadata-source-ts-$customerId@example.com",
                sourceTimestamp = sourceTimestamp
            )

            customerRepository.save(customer).block()

            val retrieved = customerRepository.findById(customerId).block()
            assertNotNull(retrieved)
            assertNotNull(retrieved.cdcMetadata)
            assertEquals(sourceTimestamp, retrieved.cdcMetadata.sourceTimestamp)
        }

        @Test
        @DisplayName("should embed CdcMetadata with operation type")
        fun shouldEmbedCdcMetadataWithOperation() {
            val customerId = UUID.randomUUID().toString()
            val customer = createTestCustomer(
                customerId,
                "metadata-operation-$customerId@example.com",
                operation = CdcOperation.UPDATE
            )

            customerRepository.save(customer).block()

            val retrieved = customerRepository.findById(customerId).block()
            assertNotNull(retrieved)
            assertEquals(CdcOperation.UPDATE, retrieved.cdcMetadata.operation)
        }

        @Test
        @DisplayName("should embed CdcMetadata with processedAt timestamp")
        fun shouldEmbedCdcMetadataWithProcessedAt() {
            val customerId = UUID.randomUUID().toString()
            val beforeSave = Instant.now().minusSeconds(1)
            val customer = createTestCustomer(customerId, "metadata-processed-$customerId@example.com")

            customerRepository.save(customer).block()
            val afterSave = Instant.now().plusSeconds(1)

            val retrieved = customerRepository.findById(customerId).block()
            assertNotNull(retrieved)
            assertNotNull(retrieved.cdcMetadata.processedAt)
            assertTrue(retrieved.cdcMetadata.processedAt.isAfter(beforeSave))
            assertTrue(retrieved.cdcMetadata.processedAt.isBefore(afterSave))
        }

        @Test
        @DisplayName("should embed CdcMetadata with kafka offset and partition")
        fun shouldEmbedCdcMetadataWithKafkaInfo() {
            val customerId = UUID.randomUUID().toString()
            val kafkaOffset = 12345L
            val kafkaPartition = 2
            val customer = createTestCustomer(
                customerId,
                "metadata-kafka-$customerId@example.com",
                kafkaOffset = kafkaOffset,
                kafkaPartition = kafkaPartition
            )

            customerRepository.save(customer).block()

            val retrieved = customerRepository.findById(customerId).block()
            assertNotNull(retrieved)
            assertEquals(kafkaOffset, retrieved.cdcMetadata.kafkaOffset)
            assertEquals(kafkaPartition, retrieved.cdcMetadata.kafkaPartition)
        }
    }

    @Nested
    @DisplayName("Unique Email Constraint")
    inner class UniqueEmailConstraint {

        @Test
        @DisplayName("should enforce unique email constraint")
        fun shouldEnforceUniqueEmailConstraint() {
            val email = "unique-constraint-${UUID.randomUUID()}@example.com"
            val customer1 = createTestCustomer(UUID.randomUUID().toString(), email)
            val customer2 = createTestCustomer(UUID.randomUUID().toString(), email)

            customerRepository.save(customer1).block()

            assertFailsWith<DuplicateKeyException> {
                customerRepository.save(customer2).block()
            }
        }

        @Test
        @DisplayName("should allow different emails for different customers")
        fun shouldAllowDifferentEmails() {
            val customer1 = createTestCustomer(
                UUID.randomUUID().toString(),
                "different-email-1-${UUID.randomUUID()}@example.com"
            )
            val customer2 = createTestCustomer(
                UUID.randomUUID().toString(),
                "different-email-2-${UUID.randomUUID()}@example.com"
            )

            customerRepository.save(customer1).block()
            customerRepository.save(customer2).block()

            val count = customerRepository.count().block()
            assertEquals(2L, count)
        }

        @Test
        @DisplayName("should allow updating same customer with same email")
        fun shouldAllowUpdatingSameCustomerWithSameEmail() {
            val customerId = UUID.randomUUID().toString()
            val email = "same-email-update-$customerId@example.com"
            val customer = createTestCustomer(customerId, email, "active")

            customerRepository.save(customer).block()

            val updated = customer.copy(status = "inactive")
            customerRepository.save(updated).block()

            val retrieved = customerRepository.findById(customerId).block()
            assertNotNull(retrieved)
            assertEquals("inactive", retrieved.status)
            assertEquals(email, retrieved.email)
        }
    }

    @Nested
    @DisplayName("Query by Status")
    inner class QueryByStatus {

        @Test
        @DisplayName("should find customers by status")
        fun shouldFindCustomersByStatus() {
            val activeCustomer1 = createTestCustomer(
                UUID.randomUUID().toString(),
                "active-1-${UUID.randomUUID()}@example.com",
                "active"
            )
            val activeCustomer2 = createTestCustomer(
                UUID.randomUUID().toString(),
                "active-2-${UUID.randomUUID()}@example.com",
                "active"
            )
            val inactiveCustomer = createTestCustomer(
                UUID.randomUUID().toString(),
                "inactive-${UUID.randomUUID()}@example.com",
                "inactive"
            )

            customerRepository.saveAll(listOf(activeCustomer1, activeCustomer2, inactiveCustomer))
                .collectList().block()

            val activeCustomers = customerRepository.findByStatus("active").collectList().block()
            assertNotNull(activeCustomers)
            assertEquals(2, activeCustomers.size)
            assertTrue(activeCustomers.all { it.status == "active" })
        }

        @Test
        @DisplayName("should find customers by status ordered by updatedAt descending")
        fun shouldFindCustomersByStatusOrderedByUpdatedAtDesc() {
            val now = Instant.now()
            val older = createTestCustomer(
                UUID.randomUUID().toString(),
                "older-${UUID.randomUUID()}@example.com",
                "pending",
                updatedAt = now.minusSeconds(100)
            )
            val newer = createTestCustomer(
                UUID.randomUUID().toString(),
                "newer-${UUID.randomUUID()}@example.com",
                "pending",
                updatedAt = now
            )
            val middle = createTestCustomer(
                UUID.randomUUID().toString(),
                "middle-${UUID.randomUUID()}@example.com",
                "pending",
                updatedAt = now.minusSeconds(50)
            )

            customerRepository.saveAll(listOf(older, newer, middle)).collectList().block()

            val results = customerRepository.findByStatusOrderByUpdatedAtDesc("pending")
                .collectList().block()
            assertNotNull(results)
            assertEquals(3, results.size)
            assertEquals(newer.id, results[0].id)
            assertEquals(middle.id, results[1].id)
            assertEquals(older.id, results[2].id)
        }

        @Test
        @DisplayName("should return empty list when no customers match status")
        fun shouldReturnEmptyListWhenNoMatch() {
            val customer = createTestCustomer(
                UUID.randomUUID().toString(),
                "no-match-${UUID.randomUUID()}@example.com",
                "active"
            )
            customerRepository.save(customer).block()

            val results = customerRepository.findByStatus("nonexistent").collectList().block()
            assertNotNull(results)
            assertTrue(results.isEmpty())
        }
    }

    @Nested
    @DisplayName("Find by Email")
    inner class FindByEmail {

        @Test
        @DisplayName("should find customer by email")
        fun shouldFindCustomerByEmail() {
            val email = "find-by-email-${UUID.randomUUID()}@example.com"
            val customer = createTestCustomer(UUID.randomUUID().toString(), email)

            customerRepository.save(customer).block()

            val found = customerRepository.findByEmail(email).block()
            assertNotNull(found)
            assertEquals(email, found.email)
        }

        @Test
        @DisplayName("should check email existence")
        fun shouldCheckEmailExistence() {
            val email = "exists-check-${UUID.randomUUID()}@example.com"
            val customer = createTestCustomer(UUID.randomUUID().toString(), email)

            customerRepository.save(customer).block()

            val exists = customerRepository.existsByEmail(email).block()
            val notExists = customerRepository.existsByEmail("nonexistent@example.com").block()

            assertEquals(true, exists)
            assertEquals(false, notExists)
        }
    }

    private fun createTestCustomer(
        id: String,
        email: String,
        status: String = "active",
        updatedAt: Instant = Instant.now(),
        sourceTimestamp: Long = System.currentTimeMillis(),
        operation: CdcOperation = CdcOperation.INSERT,
        kafkaOffset: Long = 0L,
        kafkaPartition: Int = 0
    ): CustomerDocument = CustomerDocument(
        id = id,
        email = email,
        status = status,
        updatedAt = updatedAt,
        cdcMetadata = CdcMetadata(
            sourceTimestamp = sourceTimestamp,
            operation = operation,
            kafkaOffset = kafkaOffset,
            kafkaPartition = kafkaPartition
        )
    )
}
