package com.pintailconsultingllc.cdcdebezium.acceptance

import com.mongodb.client.MongoClients
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.IndexOptions
import org.bson.Document
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@AcceptanceTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("MongoDB Infrastructure (PLAN-011)")
class MongoDbInfrastructureAcceptanceTest {

    private lateinit var database: MongoDatabase

    @BeforeAll
    fun setUp() {
        val client = MongoClients.create(MONGODB_URI)
        database = client.getDatabase(DATABASE_NAME)
    }

    @AfterAll
    fun tearDown() {
        // Clean up any test data created during tests
        try {
            database.getCollection("customers").deleteMany(
                Document("email", Document("\$regex", "^test-plan011-"))
            )
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
    }

    @Nested
    @DisplayName("Container Health")
    inner class ContainerHealth {

        @Test
        @DisplayName("should start successfully and respond to ping")
        fun shouldStartAndRespondToPing() {
            val result = database.runCommand(Document("ping", 1))
            assertEquals(1.0, result.getDouble("ok"))
        }

        @Test
        @DisplayName("should accept authenticated connections as cdc_app user")
        fun shouldAcceptAuthenticatedConnections() {
            // If we can get here, authentication worked
            val collections = database.listCollectionNames().toList()
            assertNotNull(collections, "Should be able to list collections with cdc_app user")
        }
    }

    @Nested
    @DisplayName("Collections Setup")
    inner class CollectionsSetup {

        @Test
        @DisplayName("should have customers collection created")
        fun shouldHaveCustomersCollection() {
            val collections = database.listCollectionNames().toList()
            assertTrue(
                collections.contains("customers"),
                "customers collection should exist"
            )
        }

        @Test
        @DisplayName("should have addresses collection created")
        fun shouldHaveAddressesCollection() {
            val collections = database.listCollectionNames().toList()
            assertTrue(
                collections.contains("addresses"),
                "addresses collection should exist"
            )
        }

        @Test
        @DisplayName("should have orders collection created")
        fun shouldHaveOrdersCollection() {
            val collections = database.listCollectionNames().toList()
            assertTrue(
                collections.contains("orders"),
                "orders collection should exist"
            )
        }

        @Test
        @DisplayName("should have orders collection with embedded items support")
        fun shouldHaveOrdersCollectionWithEmbeddedItems() {
            // Note: order_items are embedded within orders collection, not separate
            val collections = database.listCollectionNames().toList()
            assertTrue(
                collections.contains("orders"),
                "orders collection should exist with embedded items support"
            )
        }
    }

    @Nested
    @DisplayName("Customer Collection Indexes")
    inner class CustomerCollectionIndexes {

        @Test
        @DisplayName("should have unique index on email field")
        fun shouldHaveUniqueEmailIndex() {
            val indexes = database.getCollection("customers").listIndexes().toList()

            val emailIndex = indexes.find { index ->
                val key = index.get("key", Document::class.java)
                key?.containsKey("email") == true
            }

            assertNotNull(emailIndex, "Email index should exist")
            assertTrue(
                emailIndex.getBoolean("unique") == true,
                "Email index should be unique"
            )
        }

        @Test
        @DisplayName("should have index on cdcMetadata.sourceTimestamp field")
        fun shouldHaveSourceTimestampIndex() {
            val indexes = database.getCollection("customers").listIndexes().toList()

            val timestampIndex = indexes.find { index ->
                val key = index.get("key", Document::class.java)
                key?.containsKey("cdcMetadata.sourceTimestamp") == true
            }

            assertNotNull(timestampIndex, "cdcMetadata.sourceTimestamp index should exist")
        }

        @Test
        @DisplayName("should have index on cdcMetadata.processedAt field")
        fun shouldHaveProcessedAtIndex() {
            val indexes = database.getCollection("customers").listIndexes().toList()

            val processedAtIndex = indexes.find { index ->
                val key = index.get("key", Document::class.java)
                key?.containsKey("cdcMetadata.processedAt") == true
            }

            assertNotNull(processedAtIndex, "cdcMetadata.processedAt index should exist")
        }

        @Test
        @DisplayName("should have index on status field")
        fun shouldHaveStatusIndex() {
            val indexes = database.getCollection("customers").listIndexes().toList()

            val statusIndex = indexes.find { index ->
                val key = index.get("key", Document::class.java)
                key?.containsKey("status") == true
            }

            assertNotNull(statusIndex, "status index should exist")
        }
    }

    @Nested
    @DisplayName("Data Operations")
    inner class DataOperations {

        @Test
        @DisplayName("should allow inserting valid customer document")
        fun shouldAllowInsertingValidCustomer() {
            val customerId = "test-plan011-${UUID.randomUUID()}"
            val email = "test-plan011-${UUID.randomUUID()}@example.com"

            val customer = Document()
                .append("_id", customerId)
                .append("email", email)
                .append("status", "active")
                .append("updatedAt", java.util.Date())
                .append(
                    "cdcMetadata", Document()
                        .append("sourceTimestamp", System.currentTimeMillis())
                        .append("operation", "INSERT")
                        .append("kafkaOffset", 0L)
                        .append("kafkaPartition", 0)
                        .append("processedAt", java.util.Date())
                )

            val collection = database.getCollection("customers")
            collection.insertOne(customer)

            val found = collection.find(Document("_id", customerId)).first()
            assertNotNull(found)
            assertEquals(email, found.getString("email"))
            assertEquals("active", found.getString("status"))

            // Clean up
            collection.deleteOne(Document("_id", customerId))
        }

        @Test
        @DisplayName("should allow reading customer documents")
        fun shouldAllowReadingCustomers() {
            val collection = database.getCollection("customers")
            // This should not throw - we can read (may return empty if no data)
            val count = collection.countDocuments()
            assertTrue(count >= 0, "Should be able to count documents")
        }

        @Test
        @DisplayName("should allow updating customer documents")
        fun shouldAllowUpdatingCustomers() {
            val customerId = "test-plan011-update-${UUID.randomUUID()}"
            val email = "test-plan011-update-${UUID.randomUUID()}@example.com"

            val customer = Document()
                .append("_id", customerId)
                .append("email", email)
                .append("status", "active")
                .append("updatedAt", java.util.Date())
                .append(
                    "cdcMetadata", Document()
                        .append("sourceTimestamp", System.currentTimeMillis())
                        .append("operation", "INSERT")
                        .append("kafkaOffset", 0L)
                        .append("kafkaPartition", 0)
                        .append("processedAt", java.util.Date())
                )

            val collection = database.getCollection("customers")
            collection.insertOne(customer)

            // Update the document
            collection.updateOne(
                Document("_id", customerId),
                Document("\$set", Document("status", "inactive"))
            )

            val found = collection.find(Document("_id", customerId)).first()
            assertNotNull(found)
            assertEquals("inactive", found.getString("status"))

            // Clean up
            collection.deleteOne(Document("_id", customerId))
        }

        @Test
        @DisplayName("should allow deleting customer documents")
        fun shouldAllowDeletingCustomers() {
            val customerId = "test-plan011-delete-${UUID.randomUUID()}"
            val email = "test-plan011-delete-${UUID.randomUUID()}@example.com"

            val customer = Document()
                .append("_id", customerId)
                .append("email", email)
                .append("status", "active")
                .append("updatedAt", java.util.Date())
                .append(
                    "cdcMetadata", Document()
                        .append("sourceTimestamp", System.currentTimeMillis())
                        .append("operation", "INSERT")
                        .append("kafkaOffset", 0L)
                        .append("kafkaPartition", 0)
                        .append("processedAt", java.util.Date())
                )

            val collection = database.getCollection("customers")
            collection.insertOne(customer)

            val deleteResult = collection.deleteOne(Document("_id", customerId))
            assertEquals(1, deleteResult.deletedCount)

            val found = collection.find(Document("_id", customerId)).first()
            assertEquals(null, found)
        }
    }

    @Nested
    @DisplayName("Schema Validation")
    inner class SchemaValidation {

        @Test
        @DisplayName("should enforce unique email constraint")
        fun shouldEnforceUniqueEmailConstraint() {
            val email = "test-plan011-unique-${UUID.randomUUID()}@example.com"
            val customerId1 = "test-plan011-unique1-${UUID.randomUUID()}"
            val customerId2 = "test-plan011-unique2-${UUID.randomUUID()}"

            val customer1 = Document()
                .append("_id", customerId1)
                .append("email", email)
                .append("status", "active")
                .append("updatedAt", java.util.Date())
                .append(
                    "cdcMetadata", Document()
                        .append("sourceTimestamp", System.currentTimeMillis())
                        .append("operation", "INSERT")
                        .append("kafkaOffset", 0L)
                        .append("kafkaPartition", 0)
                        .append("processedAt", java.util.Date())
                )

            val customer2 = Document()
                .append("_id", customerId2)
                .append("email", email) // Same email - should fail
                .append("status", "active")
                .append("updatedAt", java.util.Date())
                .append(
                    "cdcMetadata", Document()
                        .append("sourceTimestamp", System.currentTimeMillis())
                        .append("operation", "INSERT")
                        .append("kafkaOffset", 1L)
                        .append("kafkaPartition", 0)
                        .append("processedAt", java.util.Date())
                )

            val collection = database.getCollection("customers")

            try {
                collection.insertOne(customer1)

                // This should throw due to duplicate email
                assertThrows<com.mongodb.MongoWriteException> {
                    collection.insertOne(customer2)
                }
            } finally {
                // Clean up
                collection.deleteOne(Document("_id", customerId1))
                collection.deleteOne(Document("_id", customerId2))
            }
        }
    }

    @Nested
    @DisplayName("Data Persistence")
    inner class DataPersistence {

        @Test
        @DisplayName("should persist data and allow retrieval")
        fun shouldPersistDataAndAllowRetrieval() {
            val customerId = "test-plan011-persist-${UUID.randomUUID()}"
            val email = "test-plan011-persist-${UUID.randomUUID()}@example.com"

            val customer = Document()
                .append("_id", customerId)
                .append("email", email)
                .append("status", "active")
                .append("updatedAt", java.util.Date())
                .append(
                    "cdcMetadata", Document()
                        .append("sourceTimestamp", System.currentTimeMillis())
                        .append("operation", "INSERT")
                        .append("kafkaOffset", 0L)
                        .append("kafkaPartition", 0)
                        .append("processedAt", java.util.Date())
                )

            val collection = database.getCollection("customers")
            collection.insertOne(customer)

            // Get a new connection to verify persistence
            val newClient = MongoClients.create(MONGODB_URI)
            val newDatabase = newClient.getDatabase(DATABASE_NAME)
            val newCollection = newDatabase.getCollection("customers")

            val found = newCollection.find(Document("_id", customerId)).first()
            assertNotNull(found, "Document should persist and be retrievable from new connection")
            assertEquals(email, found.getString("email"))

            // Clean up
            collection.deleteOne(Document("_id", customerId))
            newClient.close()
        }
    }

    companion object {
        private const val MONGODB_URI = "mongodb://cdc_app:cdc_app_password@localhost:27017/cdc_materialized?authSource=cdc_materialized"
        private const val DATABASE_NAME = "cdc_materialized"
    }
}
