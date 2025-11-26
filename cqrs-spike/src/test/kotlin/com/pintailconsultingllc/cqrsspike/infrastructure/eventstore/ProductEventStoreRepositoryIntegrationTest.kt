package com.pintailconsultingllc.cqrsspike.infrastructure.eventstore

import com.pintailconsultingllc.cqrsspike.product.command.model.ProductStatus
import com.pintailconsultingllc.cqrsspike.product.event.ProductActivated
import com.pintailconsultingllc.cqrsspike.product.event.ProductCreated
import com.pintailconsultingllc.cqrsspike.product.event.ProductPriceChanged
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import reactor.test.StepVerifier
import java.util.UUID

/**
 * Integration tests for ProductEventStoreRepository with PostgreSQL via Testcontainers.
 *
 * These tests require Docker to be running. They will be skipped if Docker is not available.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("ProductEventStoreRepository Integration Tests")
class ProductEventStoreRepositoryIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:18-alpine")
            .withDatabaseName("cqrs_test")
            .withUsername("test")
            .withPassword("test")
            .withInitScript("init-test-schema.sql")

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.r2dbc.url") {
                "r2dbc:postgresql://${postgres.host}:${postgres.firstMappedPort}/${postgres.databaseName}"
            }
            registry.add("spring.r2dbc.username", postgres::getUsername)
            registry.add("spring.r2dbc.password", postgres::getPassword)

            // JDBC for Flyway (if needed)
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)

            // Disable Flyway for tests - we use init script
            registry.add("spring.flyway.enabled") { "false" }

            // Disable Vault for tests
            registry.add("spring.cloud.vault.enabled") { "false" }
        }
    }

    @Autowired
    private lateinit var eventStoreRepository: ProductEventStoreRepository

    @Nested
    @DisplayName("Save Events")
    inner class SaveEvents {

        @Test
        @DisplayName("should save single event to event store")
        fun shouldSaveSingleEvent() {
            val productId = UUID.randomUUID()
            val event = ProductCreated(
                productId = productId,
                version = 1,
                sku = "SAVE-${productId.toString().take(8)}",
                name = "Test Product",
                description = "Test description",
                priceCents = 1999
            )

            StepVerifier.create(eventStoreRepository.saveEvents(listOf(event)))
                .verifyComplete()

            StepVerifier.create(eventStoreRepository.findEventsByAggregateId(productId))
                .expectNextCount(1)
                .verifyComplete()
        }

        @Test
        @DisplayName("should save multiple events atomically")
        fun shouldSaveMultipleEventsAtomically() {
            val productId = UUID.randomUUID()
            val events = listOf(
                ProductCreated(
                    productId = productId,
                    version = 1,
                    sku = "MULTI-${productId.toString().take(8)}",
                    name = "Test Product",
                    description = null,
                    priceCents = 1000
                ),
                ProductPriceChanged(
                    productId = productId,
                    version = 2,
                    newPriceCents = 1500,
                    previousPriceCents = 1000,
                    changePercentage = 50.0
                ),
                ProductActivated(
                    productId = productId,
                    version = 3,
                    previousStatus = ProductStatus.DRAFT
                )
            )

            StepVerifier.create(eventStoreRepository.saveEvents(events))
                .verifyComplete()

            StepVerifier.create(eventStoreRepository.findEventsByAggregateId(productId))
                .expectNextCount(3)
                .verifyComplete()
        }

        @Test
        @DisplayName("should handle empty events list")
        fun shouldHandleEmptyEventsList() {
            StepVerifier.create(eventStoreRepository.saveEvents(emptyList()))
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("Find Events")
    inner class FindEvents {

        @Test
        @DisplayName("should return events in version order")
        fun shouldReturnEventsInVersionOrder() {
            val productId = UUID.randomUUID()
            val events = listOf(
                ProductCreated(
                    productId = productId,
                    version = 1,
                    sku = "ORDER-${productId.toString().take(8)}",
                    name = "Test",
                    description = null,
                    priceCents = 100
                ),
                ProductPriceChanged(
                    productId = productId,
                    version = 2,
                    newPriceCents = 200,
                    previousPriceCents = 100,
                    changePercentage = 100.0
                )
            )

            StepVerifier.create(
                eventStoreRepository.saveEvents(events)
                    .thenMany(eventStoreRepository.findEventsByAggregateId(productId))
            )
                .expectNextMatches { it.version == 1L }
                .expectNextMatches { it.version == 2L }
                .verifyComplete()
        }

        @Test
        @DisplayName("should return empty for non-existent aggregate")
        fun shouldReturnEmptyForNonExistentAggregate() {
            val nonExistentId = UUID.randomUUID()

            StepVerifier.create(eventStoreRepository.findEventsByAggregateId(nonExistentId))
                .verifyComplete()
        }

        @Test
        @DisplayName("should deserialize ProductCreated event correctly")
        fun shouldDeserializeProductCreatedEventCorrectly() {
            val productId = UUID.randomUUID()
            val originalEvent = ProductCreated(
                productId = productId,
                version = 1,
                sku = "DESER-${productId.toString().take(8)}",
                name = "Deserialize Test Product",
                description = "Test description",
                priceCents = 2999,
                status = ProductStatus.DRAFT
            )

            StepVerifier.create(
                eventStoreRepository.saveEvents(listOf(originalEvent))
                    .thenMany(eventStoreRepository.findEventsByAggregateId(productId))
            )
                .expectNextMatches { event ->
                    event is ProductCreated &&
                    event.productId == productId &&
                    event.sku == originalEvent.sku &&
                    event.name == "Deserialize Test Product" &&
                    event.description == "Test description" &&
                    event.priceCents == 2999
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("should deserialize ProductPriceChanged event correctly")
        fun shouldDeserializeProductPriceChangedEventCorrectly() {
            val productId = UUID.randomUUID()
            val events = listOf(
                ProductCreated(
                    productId = productId,
                    version = 1,
                    sku = "PRICE-${productId.toString().take(8)}",
                    name = "Price Test",
                    description = null,
                    priceCents = 1000
                ),
                ProductPriceChanged(
                    productId = productId,
                    version = 2,
                    newPriceCents = 1999,
                    previousPriceCents = 1000,
                    changePercentage = 99.9
                )
            )

            StepVerifier.create(
                eventStoreRepository.saveEvents(events)
                    .thenMany(eventStoreRepository.findEventsByAggregateId(productId))
            )
                .expectNextMatches { it is ProductCreated }
                .expectNextMatches { event ->
                    event is ProductPriceChanged &&
                    event.newPriceCents == 1999 &&
                    event.previousPriceCents == 1000 &&
                    event.changePercentage == 99.9
                }
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("Stream Version")
    inner class StreamVersion {

        @Test
        @DisplayName("should track stream version correctly")
        fun shouldTrackStreamVersionCorrectly() {
            val productId = UUID.randomUUID()
            val event = ProductCreated(
                productId = productId,
                version = 1,
                sku = "VER-${productId.toString().take(8)}",
                name = "Test",
                description = null,
                priceCents = 100
            )

            StepVerifier.create(
                eventStoreRepository.saveEvents(listOf(event))
                    .then(eventStoreRepository.getStreamVersion(productId))
            )
                .expectNext(1)
                .verifyComplete()
        }

        @Test
        @DisplayName("should return 0 for non-existent stream")
        fun shouldReturnZeroForNonExistentStream() {
            val nonExistentId = UUID.randomUUID()

            StepVerifier.create(eventStoreRepository.getStreamVersion(nonExistentId))
                .expectNext(0)
                .verifyComplete()
        }

        @Test
        @DisplayName("should increment version with multiple events")
        fun shouldIncrementVersionWithMultipleEvents() {
            val productId = UUID.randomUUID()
            val events = listOf(
                ProductCreated(
                    productId = productId,
                    version = 1,
                    sku = "INCR-${productId.toString().take(8)}",
                    name = "Test",
                    description = null,
                    priceCents = 100
                ),
                ProductPriceChanged(
                    productId = productId,
                    version = 2,
                    newPriceCents = 150,
                    previousPriceCents = 100,
                    changePercentage = 50.0
                ),
                ProductActivated(
                    productId = productId,
                    version = 3,
                    previousStatus = ProductStatus.DRAFT
                )
            )

            StepVerifier.create(
                eventStoreRepository.saveEvents(events)
                    .then(eventStoreRepository.getStreamVersion(productId))
            )
                .expectNext(3)
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("Stream Exists")
    inner class StreamExists {

        @Test
        @DisplayName("should return true for existing stream")
        fun shouldReturnTrueForExistingStream() {
            val productId = UUID.randomUUID()
            val event = ProductCreated(
                productId = productId,
                version = 1,
                sku = "EXIST-${productId.toString().take(8)}",
                name = "Test",
                description = null,
                priceCents = 100
            )

            StepVerifier.create(
                eventStoreRepository.saveEvents(listOf(event))
                    .then(eventStoreRepository.streamExists(productId))
            )
                .expectNext(true)
                .verifyComplete()
        }

        @Test
        @DisplayName("should return false for non-existent stream")
        fun shouldReturnFalseForNonExistentStream() {
            val nonExistentId = UUID.randomUUID()

            StepVerifier.create(eventStoreRepository.streamExists(nonExistentId))
                .expectNext(false)
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("Find Events From Version")
    inner class FindEventsFromVersion {

        @Test
        @DisplayName("should return events after specified version")
        fun shouldReturnEventsAfterSpecifiedVersion() {
            val productId = UUID.randomUUID()
            val events = listOf(
                ProductCreated(
                    productId = productId,
                    version = 1,
                    sku = "FROM-${productId.toString().take(8)}",
                    name = "Test",
                    description = null,
                    priceCents = 100
                ),
                ProductPriceChanged(
                    productId = productId,
                    version = 2,
                    newPriceCents = 200,
                    previousPriceCents = 100,
                    changePercentage = 100.0
                ),
                ProductActivated(
                    productId = productId,
                    version = 3,
                    previousStatus = ProductStatus.DRAFT
                )
            )

            StepVerifier.create(
                eventStoreRepository.saveEvents(events)
                    .thenMany(eventStoreRepository.findEventsByAggregateIdFromVersion(productId, 1))
            )
                .expectNextMatches { it.version == 2L && it is ProductPriceChanged }
                .expectNextMatches { it.version == 3L && it is ProductActivated }
                .verifyComplete()
        }

        @Test
        @DisplayName("should return empty when all events are before specified version")
        fun shouldReturnEmptyWhenAllEventsBeforeVersion() {
            val productId = UUID.randomUUID()
            val event = ProductCreated(
                productId = productId,
                version = 1,
                sku = "BEFORE-${productId.toString().take(8)}",
                name = "Test",
                description = null,
                priceCents = 100
            )

            StepVerifier.create(
                eventStoreRepository.saveEvents(listOf(event))
                    .thenMany(eventStoreRepository.findEventsByAggregateIdFromVersion(productId, 10))
            )
                .verifyComplete()
        }
    }
}
