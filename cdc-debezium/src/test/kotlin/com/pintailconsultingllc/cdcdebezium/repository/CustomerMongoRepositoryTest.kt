package com.pintailconsultingllc.cdcdebezium.repository

import com.pintailconsultingllc.cdcdebezium.document.CdcMetadata
import com.pintailconsultingllc.cdcdebezium.document.CdcOperation
import com.pintailconsultingllc.cdcdebezium.document.CustomerDocument
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import reactor.test.StepVerifier
import java.time.Instant

@DataMongoTest
class CustomerMongoRepositoryTest {

    @Autowired
    private lateinit var repository: CustomerMongoRepository

    @Autowired
    private lateinit var mongoTemplate: ReactiveMongoTemplate

    @BeforeEach
    fun setUp() {
        mongoTemplate.dropCollection(CustomerDocument::class.java).block()
    }

    @Nested
    inner class SaveAndFind {

        @Test
        fun `saves and retrieves document by id`() {
            val document = createDocument(id = "test-id-1")

            StepVerifier.create(repository.save(document))
                .expectNextMatches { it.id == "test-id-1" }
                .verifyComplete()

            StepVerifier.create(repository.findById("test-id-1"))
                .expectNextMatches { it.email == "test1@example.com" }
                .verifyComplete()
        }

        @Test
        fun `returns empty when document not found`() {
            StepVerifier.create(repository.findById("non-existent"))
                .verifyComplete()
        }
    }

    @Nested
    inner class FindByEmail {

        @Test
        fun `finds document by email`() {
            val document = createDocument(email = "unique@example.com")
            repository.save(document).block()

            StepVerifier.create(repository.findByEmail("unique@example.com"))
                .expectNextMatches { it.id == document.id }
                .verifyComplete()
        }

        @Test
        fun `returns empty when email not found`() {
            StepVerifier.create(repository.findByEmail("notfound@example.com"))
                .verifyComplete()
        }
    }

    @Nested
    inner class FindByStatus {

        @Test
        fun `finds all documents with given status`() {
            val active1 = createDocument(id = "1", status = "active")
            val active2 = createDocument(id = "2", status = "active", email = "active2@example.com")
            val inactive = createDocument(id = "3", status = "inactive", email = "inactive@example.com")

            repository.saveAll(listOf(active1, active2, inactive)).collectList().block()

            StepVerifier.create(repository.findByStatus("active").collectList())
                .expectNextMatches { documents ->
                    documents.size == 2 && documents.all { it.status == "active" }
                }
                .verifyComplete()
        }

        @Test
        fun `returns empty flux when no documents match status`() {
            StepVerifier.create(repository.findByStatus("nonexistent").collectList())
                .expectNextMatches { it.isEmpty() }
                .verifyComplete()
        }
    }

    @Nested
    inner class FindByStatusOrderByUpdatedAtDesc {

        @Test
        fun `returns documents ordered by updatedAt descending`() {
            val older = createDocument(
                id = "older",
                status = "active",
                email = "older@example.com",
                updatedAt = Instant.parse("2024-01-01T00:00:00Z")
            )
            val newer = createDocument(
                id = "newer",
                status = "active",
                email = "newer@example.com",
                updatedAt = Instant.parse("2024-01-02T00:00:00Z")
            )

            repository.saveAll(listOf(older, newer)).collectList().block()

            StepVerifier.create(repository.findByStatusOrderByUpdatedAtDesc("active").collectList())
                .expectNextMatches { documents ->
                    documents.size == 2 &&
                        documents[0].id == "newer" &&
                        documents[1].id == "older"
                }
                .verifyComplete()
        }
    }

    @Nested
    inner class ExistsByEmail {

        @Test
        fun `returns true when email exists`() {
            val document = createDocument(email = "exists@example.com")
            repository.save(document).block()

            StepVerifier.create(repository.existsByEmail("exists@example.com"))
                .expectNext(true)
                .verifyComplete()
        }

        @Test
        fun `returns false when email does not exist`() {
            StepVerifier.create(repository.existsByEmail("notexists@example.com"))
                .expectNext(false)
                .verifyComplete()
        }
    }

    @Nested
    inner class CdcMetadataEmbedding {

        @Test
        fun `preserves CDC metadata when saving and retrieving`() {
            val document = createDocument(
                sourceTimestamp = 1705315800000L,
                operation = CdcOperation.UPDATE,
                kafkaOffset = 42L,
                kafkaPartition = 3
            )

            repository.save(document).block()

            StepVerifier.create(repository.findById(document.id))
                .expectNextMatches { retrieved ->
                    retrieved.cdcMetadata.sourceTimestamp == 1705315800000L &&
                        retrieved.cdcMetadata.operation == CdcOperation.UPDATE &&
                        retrieved.cdcMetadata.kafkaOffset == 42L &&
                        retrieved.cdcMetadata.kafkaPartition == 3
                }
                .verifyComplete()
        }
    }

    private fun createDocument(
        id: String = "test-id",
        email: String = "test1@example.com",
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
