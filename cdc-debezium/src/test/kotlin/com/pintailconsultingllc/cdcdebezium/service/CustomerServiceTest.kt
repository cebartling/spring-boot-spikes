package com.pintailconsultingllc.cdcdebezium.service

import com.pintailconsultingllc.cdcdebezium.TestFixtures.createEvent
import com.pintailconsultingllc.cdcdebezium.entity.CustomerEntity
import com.pintailconsultingllc.cdcdebezium.repository.CustomerRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant
import java.util.UUID

class CustomerServiceTest {

    private lateinit var customerRepository: CustomerRepository
    private lateinit var customerService: CustomerService

    @BeforeEach
    fun setUp() {
        customerRepository = mockk()
        customerService = CustomerService(customerRepository)
    }

    private fun createEntity(
        id: UUID = UUID.randomUUID(),
        email: String = "test@example.com",
        status: String = "active",
        updatedAt: Instant = Instant.now(),
        sourceTimestamp: Long? = null,
        isNewEntity: Boolean = false
    ) = CustomerEntity.create(
        id = id,
        email = email,
        status = status,
        updatedAt = updatedAt,
        sourceTimestamp = sourceTimestamp,
        isNewEntity = isNewEntity
    )

    @Nested
    inner class Upsert {

        @Test
        fun `inserts new customer when not found`() {
            val event = createEvent(sourceTimestamp = 1000L)
            every { customerRepository.findById(event.id) } returns Mono.empty()
            every { customerRepository.save(any()) } answers { Mono.just(firstArg()) }

            StepVerifier.create(customerService.upsert(event))
                .expectNextMatches { it.id == event.id && it.email == event.email }
                .verifyComplete()

            verify(exactly = 1) { customerRepository.findById(event.id) }
            verify(exactly = 1) { customerRepository.save(any()) }
        }

        @Test
        fun `updates existing customer when incoming event is newer`() {
            val existingId = UUID.randomUUID()
            val existing = createEntity(id = existingId, sourceTimestamp = 1000L)
            val event = createEvent(id = existingId, sourceTimestamp = 2000L)

            every { customerRepository.findById(existingId) } returns Mono.just(existing)
            every { customerRepository.save(any()) } answers { Mono.just(firstArg()) }

            StepVerifier.create(customerService.upsert(event))
                .expectNextMatches { it.sourceTimestamp == 2000L }
                .verifyComplete()

            verify(exactly = 1) { customerRepository.save(any()) }
        }

        @Test
        fun `skips update when incoming event is older`() {
            val existingId = UUID.randomUUID()
            val existing = createEntity(id = existingId, sourceTimestamp = 2000L)
            val event = createEvent(id = existingId, sourceTimestamp = 1000L)

            every { customerRepository.findById(existingId) } returns Mono.just(existing)

            StepVerifier.create(customerService.upsert(event))
                .expectNextMatches { it.sourceTimestamp == 2000L }
                .verifyComplete()

            verify(exactly = 0) { customerRepository.save(any()) }
        }

        @Test
        fun `updates when existing has null timestamp`() {
            val existingId = UUID.randomUUID()
            val existing = createEntity(id = existingId, sourceTimestamp = null)
            val event = createEvent(id = existingId, sourceTimestamp = 1000L)

            every { customerRepository.findById(existingId) } returns Mono.just(existing)
            every { customerRepository.save(any()) } answers { Mono.just(firstArg()) }

            StepVerifier.create(customerService.upsert(event))
                .expectNextMatches { it.sourceTimestamp == 1000L }
                .verifyComplete()

            verify(exactly = 1) { customerRepository.save(any()) }
        }

        @Test
        fun `updates when incoming has null timestamp`() {
            val existingId = UUID.randomUUID()
            val existing = createEntity(id = existingId, sourceTimestamp = 1000L)
            val event = createEvent(id = existingId, sourceTimestamp = null)

            every { customerRepository.findById(existingId) } returns Mono.just(existing)
            every { customerRepository.save(any()) } answers { Mono.just(firstArg()) }

            StepVerifier.create(customerService.upsert(event))
                .expectNextMatches { it.sourceTimestamp == null }
                .verifyComplete()

            verify(exactly = 1) { customerRepository.save(any()) }
        }

        @Test
        fun `handles event with same timestamp as existing`() {
            val existingId = UUID.randomUUID()
            val existing = createEntity(id = existingId, sourceTimestamp = 1000L)
            val event = createEvent(id = existingId, sourceTimestamp = 1000L)

            every { customerRepository.findById(existingId) } returns Mono.just(existing)

            StepVerifier.create(customerService.upsert(event))
                .expectNextMatches { it.sourceTimestamp == 1000L }
                .verifyComplete()

            verify(exactly = 0) { customerRepository.save(any()) }
        }

        @Test
        fun `uses empty string when email is null`() {
            val event = createEvent(email = null)
            every { customerRepository.findById(event.id) } returns Mono.empty()
            every { customerRepository.save(any()) } answers { Mono.just(firstArg()) }

            StepVerifier.create(customerService.upsert(event))
                .expectNextMatches { it.email == "" }
                .verifyComplete()
        }

        @Test
        fun `uses empty string when status is null`() {
            val event = createEvent(status = null)
            every { customerRepository.findById(event.id) } returns Mono.empty()
            every { customerRepository.save(any()) } answers { Mono.just(firstArg()) }

            StepVerifier.create(customerService.upsert(event))
                .expectNextMatches { it.status == "" }
                .verifyComplete()
        }
    }

    @Nested
    inner class Delete {

        @Test
        fun `deletes existing customer`() {
            val id = UUID.randomUUID()
            val existing = createEntity(id = id)

            every { customerRepository.findById(id) } returns Mono.just(existing)
            every { customerRepository.delete(existing) } returns Mono.empty()

            StepVerifier.create(customerService.delete(id))
                .verifyComplete()

            verify(exactly = 1) { customerRepository.findById(id) }
            verify(exactly = 1) { customerRepository.delete(existing) }
        }

        @Test
        fun `succeeds when customer does not exist`() {
            val id = UUID.randomUUID()

            every { customerRepository.findById(id) } returns Mono.empty()

            StepVerifier.create(customerService.delete(id))
                .verifyComplete()

            verify(exactly = 1) { customerRepository.findById(id) }
            verify(exactly = 0) { customerRepository.delete(any()) }
        }
    }
}
