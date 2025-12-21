package com.pintailconsultingllc.cdcdebezium.service

import com.pintailconsultingllc.cdcdebezium.TestFixtures.createEvent
import com.pintailconsultingllc.cdcdebezium.document.CdcMetadata
import com.pintailconsultingllc.cdcdebezium.document.CdcOperation
import com.pintailconsultingllc.cdcdebezium.document.CustomerDocument
import com.pintailconsultingllc.cdcdebezium.repository.CustomerMongoRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant
import java.util.UUID

class CustomerMongoServiceTest {

    private lateinit var customerRepository: CustomerMongoRepository
    private lateinit var mongoTemplate: ReactiveMongoTemplate
    private lateinit var customerMongoService: CustomerMongoService

    @BeforeEach
    fun setUp() {
        customerRepository = mockk()
        mongoTemplate = mockk()
        customerMongoService = CustomerMongoService(customerRepository, mongoTemplate)
    }

    private fun createDocument(
        id: String = UUID.randomUUID().toString(),
        email: String = "test@example.com",
        status: String = "active",
        updatedAt: Instant = Instant.now(),
        sourceTimestamp: Long = 1000L,
        operation: CdcOperation = CdcOperation.INSERT,
        kafkaOffset: Long = 0L,
        kafkaPartition: Int = 0
    ) = CustomerDocument(
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

    @Nested
    inner class Upsert {

        @Test
        fun `inserts new customer when not found`() {
            val event = createEvent(sourceTimestamp = 1000L)
            every { customerRepository.findById(event.id.toString()) } returns Mono.empty()
            every { customerRepository.save(any()) } answers { Mono.just(firstArg()) }

            StepVerifier.create(customerMongoService.upsert(event, 100L, 0))
                .expectNextMatches { it.id == event.id.toString() && it.email == event.email }
                .verifyComplete()

            verify(exactly = 1) { customerRepository.findById(event.id.toString()) }
            verify(exactly = 1) { customerRepository.save(any()) }
        }

        @Test
        fun `inserts with INSERT operation when document is new`() {
            val event = createEvent(sourceTimestamp = 1000L)
            every { customerRepository.findById(event.id.toString()) } returns Mono.empty()
            every { customerRepository.save(any()) } answers { Mono.just(firstArg()) }

            StepVerifier.create(customerMongoService.upsert(event, 100L, 0))
                .expectNextMatches { it.cdcMetadata.operation == CdcOperation.INSERT }
                .verifyComplete()
        }

        @Test
        fun `updates existing customer when incoming event is newer`() {
            val id = UUID.randomUUID()
            val existing = createDocument(id = id.toString(), sourceTimestamp = 1000L)
            val event = createEvent(id = id, sourceTimestamp = 2000L)

            every { customerRepository.findById(id.toString()) } returns Mono.just(existing)
            every { customerRepository.save(any()) } answers { Mono.just(firstArg()) }

            StepVerifier.create(customerMongoService.upsert(event, 200L, 1))
                .expectNextMatches { it.cdcMetadata.sourceTimestamp == 2000L }
                .verifyComplete()

            verify(exactly = 1) { customerRepository.save(any()) }
        }

        @Test
        fun `skips update when incoming event is older`() {
            val id = UUID.randomUUID()
            val existing = createDocument(id = id.toString(), sourceTimestamp = 2000L)
            val event = createEvent(id = id, sourceTimestamp = 1000L)

            every { customerRepository.findById(id.toString()) } returns Mono.just(existing)

            StepVerifier.create(customerMongoService.upsert(event, 100L, 0))
                .expectNextMatches { it.cdcMetadata.sourceTimestamp == 2000L }
                .verifyComplete()

            verify(exactly = 0) { customerRepository.save(any()) }
        }

        @Test
        fun `skips update when incoming event has same timestamp`() {
            val id = UUID.randomUUID()
            val existing = createDocument(id = id.toString(), sourceTimestamp = 1000L)
            val event = createEvent(id = id, sourceTimestamp = 1000L)

            every { customerRepository.findById(id.toString()) } returns Mono.just(existing)

            StepVerifier.create(customerMongoService.upsert(event, 100L, 0))
                .expectNextMatches { it.cdcMetadata.sourceTimestamp == 1000L }
                .verifyComplete()

            verify(exactly = 0) { customerRepository.save(any()) }
        }

        @Test
        fun `uses empty string when email is null`() {
            val event = createEvent(email = null, sourceTimestamp = 1000L)
            every { customerRepository.findById(event.id.toString()) } returns Mono.empty()
            every { customerRepository.save(any()) } answers { Mono.just(firstArg()) }

            StepVerifier.create(customerMongoService.upsert(event, 100L, 0))
                .expectNextMatches { it.email == "" }
                .verifyComplete()
        }

        @Test
        fun `uses empty string when status is null`() {
            val event = createEvent(status = null, sourceTimestamp = 1000L)
            every { customerRepository.findById(event.id.toString()) } returns Mono.empty()
            every { customerRepository.save(any()) } answers { Mono.just(firstArg()) }

            StepVerifier.create(customerMongoService.upsert(event, 100L, 0))
                .expectNextMatches { it.status == "" }
                .verifyComplete()
        }

        @Test
        fun `captures kafka offset and partition`() {
            val event = createEvent(sourceTimestamp = 1000L)
            every { customerRepository.findById(event.id.toString()) } returns Mono.empty()
            every { customerRepository.save(any()) } answers { Mono.just(firstArg()) }

            StepVerifier.create(customerMongoService.upsert(event, 12345L, 3))
                .expectNextMatches {
                    it.cdcMetadata.kafkaOffset == 12345L && it.cdcMetadata.kafkaPartition == 3
                }
                .verifyComplete()
        }
    }

    @Nested
    inner class Delete {

        @Test
        fun `deletes existing customer when event is newer`() {
            val id = UUID.randomUUID().toString()
            val existing = createDocument(id = id, sourceTimestamp = 1000L)

            every { customerRepository.findById(id) } returns Mono.just(existing)
            every { customerRepository.deleteById(id) } returns Mono.empty()

            StepVerifier.create(
                customerMongoService.delete(id, 2000L, 100L, 0)
            ).verifyComplete()

            verify(exactly = 1) { customerRepository.findById(id) }
            verify(exactly = 1) { customerRepository.deleteById(id) }
        }

        @Test
        fun `succeeds when customer does not exist`() {
            val id = UUID.randomUUID().toString()

            every { customerRepository.findById(id) } returns Mono.empty()

            StepVerifier.create(
                customerMongoService.delete(id, 1000L, 100L, 0)
            ).verifyComplete()

            verify(exactly = 1) { customerRepository.findById(id) }
            verify(exactly = 0) { customerRepository.deleteById(any<String>()) }
        }

        @Test
        fun `skips delete when event is stale`() {
            val id = UUID.randomUUID().toString()
            val existing = createDocument(id = id, sourceTimestamp = 2000L)

            every { customerRepository.findById(id) } returns Mono.just(existing)

            StepVerifier.create(
                customerMongoService.delete(id, 1000L, 100L, 0)
            ).verifyComplete()

            verify(exactly = 0) { customerRepository.deleteById(any<String>()) }
        }

        @Test
        fun `deletes when event has same timestamp as existing`() {
            val id = UUID.randomUUID().toString()
            val existing = createDocument(id = id, sourceTimestamp = 1000L)

            every { customerRepository.findById(id) } returns Mono.just(existing)
            every { customerRepository.deleteById(id) } returns Mono.empty()

            StepVerifier.create(
                customerMongoService.delete(id, 1000L, 100L, 0)
            ).verifyComplete()

            verify(exactly = 1) { customerRepository.deleteById(id) }
        }

        @Test
        fun `soft-deletes by updating status when softDelete is true`() {
            val id = UUID.randomUUID().toString()
            val existing = createDocument(id = id, sourceTimestamp = 1000L)

            every { customerRepository.findById(id) } returns Mono.just(existing)
            every { customerRepository.save(any()) } answers { Mono.just(firstArg()) }

            StepVerifier.create(
                customerMongoService.delete(id, 2000L, 100L, 0, softDelete = true)
            ).verifyComplete()

            verify(exactly = 1) {
                customerRepository.save(match {
                    it.status == "DELETED" && it.cdcMetadata.operation == CdcOperation.DELETE
                })
            }
            verify(exactly = 0) { customerRepository.deleteById(any<String>()) }
        }
    }
}
