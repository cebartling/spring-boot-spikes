package com.pintailconsultingllc.cdcdebezium.service

import com.pintailconsultingllc.cdcdebezium.TestFixtures.createAddressEvent
import com.pintailconsultingllc.cdcdebezium.document.AddressDocument
import com.pintailconsultingllc.cdcdebezium.document.AddressType
import com.pintailconsultingllc.cdcdebezium.document.CdcMetadata
import com.pintailconsultingllc.cdcdebezium.document.CdcOperation
import com.pintailconsultingllc.cdcdebezium.repository.AddressMongoRepository
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

class AddressMongoServiceTest {

    private lateinit var addressRepository: AddressMongoRepository
    private lateinit var addressMongoService: AddressMongoService

    @BeforeEach
    fun setUp() {
        addressRepository = mockk()
        addressMongoService = AddressMongoService(addressRepository)
    }

    private fun createDocument(
        id: String = UUID.randomUUID().toString(),
        customerId: String = UUID.randomUUID().toString(),
        type: AddressType = AddressType.SHIPPING,
        street: String = "123 Test St",
        city: String = "TestCity",
        state: String? = "TS",
        postalCode: String = "12345",
        country: String = "USA",
        isDefault: Boolean = false,
        updatedAt: Instant = Instant.now(),
        sourceTimestamp: Long = 1000L,
        operation: CdcOperation = CdcOperation.INSERT,
        kafkaOffset: Long = 0L,
        kafkaPartition: Int = 0
    ) = AddressDocument(
        id = id,
        customerId = customerId,
        type = type,
        street = street,
        city = city,
        state = state,
        postalCode = postalCode,
        country = country,
        isDefault = isDefault,
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
        fun `inserts new address when not found`() {
            val event = createAddressEvent(sourceTimestamp = 1000L)
            every { addressRepository.findById(event.id.toString()) } returns Mono.empty()
            every { addressRepository.save(any()) } answers { Mono.just(firstArg()) }

            StepVerifier.create(addressMongoService.upsert(event, 100L, 0))
                .expectNextMatches { it.id == event.id.toString() && it.customerId == event.customerId.toString() }
                .verifyComplete()

            verify(exactly = 1) { addressRepository.findById(event.id.toString()) }
            verify(exactly = 1) { addressRepository.save(any()) }
        }

        @Test
        fun `inserts with INSERT operation when document is new`() {
            val event = createAddressEvent(sourceTimestamp = 1000L)
            every { addressRepository.findById(event.id.toString()) } returns Mono.empty()
            every { addressRepository.save(any()) } answers { Mono.just(firstArg()) }

            StepVerifier.create(addressMongoService.upsert(event, 100L, 0))
                .expectNextMatches { it.cdcMetadata.operation == CdcOperation.INSERT }
                .verifyComplete()
        }

        @Test
        fun `updates existing address when incoming event is newer`() {
            val id = UUID.randomUUID()
            val customerId = UUID.randomUUID()
            val existing = createDocument(id = id.toString(), customerId = customerId.toString(), sourceTimestamp = 1000L)
            val event = createAddressEvent(id = id, customerId = customerId, sourceTimestamp = 2000L)

            every { addressRepository.findById(id.toString()) } returns Mono.just(existing)
            every { addressRepository.save(any()) } answers { Mono.just(firstArg()) }

            StepVerifier.create(addressMongoService.upsert(event, 200L, 1))
                .expectNextMatches { it.cdcMetadata.sourceTimestamp == 2000L }
                .verifyComplete()

            verify(exactly = 1) { addressRepository.save(any()) }
        }

        @Test
        fun `skips update when incoming event is older`() {
            val id = UUID.randomUUID()
            val customerId = UUID.randomUUID()
            val existing = createDocument(id = id.toString(), customerId = customerId.toString(), sourceTimestamp = 2000L)
            val event = createAddressEvent(id = id, customerId = customerId, sourceTimestamp = 1000L)

            every { addressRepository.findById(id.toString()) } returns Mono.just(existing)

            StepVerifier.create(addressMongoService.upsert(event, 100L, 0))
                .expectNextMatches { it.cdcMetadata.sourceTimestamp == 2000L }
                .verifyComplete()

            verify(exactly = 0) { addressRepository.save(any()) }
        }

        @Test
        fun `skips update when incoming event has same timestamp`() {
            val id = UUID.randomUUID()
            val customerId = UUID.randomUUID()
            val existing = createDocument(id = id.toString(), customerId = customerId.toString(), sourceTimestamp = 1000L)
            val event = createAddressEvent(id = id, customerId = customerId, sourceTimestamp = 1000L)

            every { addressRepository.findById(id.toString()) } returns Mono.just(existing)

            StepVerifier.create(addressMongoService.upsert(event, 100L, 0))
                .expectNextMatches { it.cdcMetadata.sourceTimestamp == 1000L }
                .verifyComplete()

            verify(exactly = 0) { addressRepository.save(any()) }
        }

        @Test
        fun `uses empty string when street is null`() {
            val event = createAddressEvent(street = null, sourceTimestamp = 1000L)
            every { addressRepository.findById(event.id.toString()) } returns Mono.empty()
            every { addressRepository.save(any()) } answers { Mono.just(firstArg()) }

            StepVerifier.create(addressMongoService.upsert(event, 100L, 0))
                .expectNextMatches { it.street == "" }
                .verifyComplete()
        }

        @Test
        fun `uses empty string when city is null`() {
            val event = createAddressEvent(city = null, sourceTimestamp = 1000L)
            every { addressRepository.findById(event.id.toString()) } returns Mono.empty()
            every { addressRepository.save(any()) } answers { Mono.just(firstArg()) }

            StepVerifier.create(addressMongoService.upsert(event, 100L, 0))
                .expectNextMatches { it.city == "" }
                .verifyComplete()
        }

        @Test
        fun `uses empty string when postalCode is null`() {
            val event = createAddressEvent(postalCode = null, sourceTimestamp = 1000L)
            every { addressRepository.findById(event.id.toString()) } returns Mono.empty()
            every { addressRepository.save(any()) } answers { Mono.just(firstArg()) }

            StepVerifier.create(addressMongoService.upsert(event, 100L, 0))
                .expectNextMatches { it.postalCode == "" }
                .verifyComplete()
        }

        @Test
        fun `uses default country when country is null`() {
            val event = createAddressEvent(country = null, sourceTimestamp = 1000L)
            every { addressRepository.findById(event.id.toString()) } returns Mono.empty()
            every { addressRepository.save(any()) } answers { Mono.just(firstArg()) }

            StepVerifier.create(addressMongoService.upsert(event, 100L, 0))
                .expectNextMatches { it.country == "USA" }
                .verifyComplete()
        }

        @Test
        fun `defaults isDefault to false when null`() {
            val event = createAddressEvent(isDefault = null, sourceTimestamp = 1000L)
            every { addressRepository.findById(event.id.toString()) } returns Mono.empty()
            every { addressRepository.save(any()) } answers { Mono.just(firstArg()) }

            StepVerifier.create(addressMongoService.upsert(event, 100L, 0))
                .expectNextMatches { !it.isDefault }
                .verifyComplete()
        }

        @Test
        fun `captures kafka offset and partition`() {
            val event = createAddressEvent(sourceTimestamp = 1000L)
            every { addressRepository.findById(event.id.toString()) } returns Mono.empty()
            every { addressRepository.save(any()) } answers { Mono.just(firstArg()) }

            StepVerifier.create(addressMongoService.upsert(event, 12345L, 3))
                .expectNextMatches {
                    it.cdcMetadata.kafkaOffset == 12345L && it.cdcMetadata.kafkaPartition == 3
                }
                .verifyComplete()
        }

        @Test
        fun `maps address type correctly`() {
            val event = createAddressEvent(type = "billing", sourceTimestamp = 1000L)
            every { addressRepository.findById(event.id.toString()) } returns Mono.empty()
            every { addressRepository.save(any()) } answers { Mono.just(firstArg()) }

            StepVerifier.create(addressMongoService.upsert(event, 100L, 0))
                .expectNextMatches { it.type == AddressType.BILLING }
                .verifyComplete()
        }
    }

    @Nested
    inner class Delete {

        @Test
        fun `deletes existing address when event is newer`() {
            val id = UUID.randomUUID().toString()
            val existing = createDocument(id = id, sourceTimestamp = 1000L)

            every { addressRepository.findById(id) } returns Mono.just(existing)
            every { addressRepository.deleteById(id) } returns Mono.empty()

            StepVerifier.create(
                addressMongoService.delete(id, 2000L)
            ).verifyComplete()

            verify(exactly = 1) { addressRepository.findById(id) }
            verify(exactly = 1) { addressRepository.deleteById(id) }
        }

        @Test
        fun `succeeds when address does not exist`() {
            val id = UUID.randomUUID().toString()

            every { addressRepository.findById(id) } returns Mono.empty()

            StepVerifier.create(
                addressMongoService.delete(id, 1000L)
            ).verifyComplete()

            verify(exactly = 1) { addressRepository.findById(id) }
            verify(exactly = 0) { addressRepository.deleteById(any<String>()) }
        }

        @Test
        fun `skips delete when event is stale`() {
            val id = UUID.randomUUID().toString()
            val existing = createDocument(id = id, sourceTimestamp = 2000L)

            every { addressRepository.findById(id) } returns Mono.just(existing)

            StepVerifier.create(
                addressMongoService.delete(id, 1000L)
            ).verifyComplete()

            verify(exactly = 0) { addressRepository.deleteById(any<String>()) }
        }

        @Test
        fun `deletes when event has same timestamp as existing`() {
            val id = UUID.randomUUID().toString()
            val existing = createDocument(id = id, sourceTimestamp = 1000L)

            every { addressRepository.findById(id) } returns Mono.just(existing)
            every { addressRepository.deleteById(id) } returns Mono.empty()

            StepVerifier.create(
                addressMongoService.delete(id, 1000L)
            ).verifyComplete()

            verify(exactly = 1) { addressRepository.deleteById(id) }
        }
    }
}
