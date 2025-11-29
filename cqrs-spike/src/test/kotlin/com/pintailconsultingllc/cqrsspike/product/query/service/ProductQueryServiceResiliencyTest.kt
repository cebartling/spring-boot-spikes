package com.pintailconsultingllc.cqrsspike.product.query.service

import com.pintailconsultingllc.cqrsspike.product.query.dto.ProductResponse
import com.pintailconsultingllc.cqrsspike.product.query.exception.QueryRateLimitException
import com.pintailconsultingllc.cqrsspike.product.query.exception.QueryServiceUnavailableException
import com.pintailconsultingllc.cqrsspike.product.query.model.ProductReadModel
import com.pintailconsultingllc.cqrsspike.product.query.repository.ProductReadModelRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.io.IOException
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Unit tests for ProductQueryService resiliency patterns.
 *
 * Tests AC10: Resiliency and Error Handling
 *
 * Note: These tests verify the fallback method behavior.
 * Integration tests with full Resilience4j configuration are in
 * ResiliencyIntegrationTest.kt
 */
@ExtendWith(MockitoExtension::class)
@DisplayName("ProductQueryService Resiliency - AC10")
class ProductQueryServiceResiliencyTest {

    @Mock
    private lateinit var repository: ProductReadModelRepository

    private lateinit var service: ProductQueryService

    private val testProduct = ProductReadModel(
        id = UUID.randomUUID(),
        sku = "TEST-RESILIENCY-001",
        name = "Resiliency Test Product",
        description = "A product for testing resiliency",
        priceCents = 2999,
        status = "ACTIVE",
        createdAt = OffsetDateTime.now(),
        updatedAt = OffsetDateTime.now(),
        aggregateVersion = 1,
        isDeleted = false,
        priceDisplay = "$29.99",
        searchText = "Resiliency Test Product A product for testing resiliency"
    )

    @BeforeEach
    fun setUp() {
        service = ProductQueryService(repository)
    }

    @Nested
    @DisplayName("AC10: findById resiliency")
    inner class FindByIdResiliency {

        @Test
        @DisplayName("should handle successful response")
        fun shouldHandleSuccessfulResponse() {
            whenever(repository.findByIdNotDeleted(testProduct.id))
                .thenReturn(Mono.just(testProduct))

            StepVerifier.create(service.findById(testProduct.id))
                .expectNextMatches { response ->
                    response.id == testProduct.id &&
                        response.sku == testProduct.sku
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("should handle IOException from repository")
        fun shouldHandleIOException() {
            whenever(repository.findByIdNotDeleted(testProduct.id))
                .thenReturn(Mono.error(IOException("Connection reset")))

            StepVerifier.create(service.findById(testProduct.id))
                .expectError(IOException::class.java)
                .verify()
        }

        @Test
        @DisplayName("should return empty for non-existent product")
        fun shouldReturnEmptyForNonExistent() {
            val nonExistentId = UUID.randomUUID()
            whenever(repository.findByIdNotDeleted(nonExistentId))
                .thenReturn(Mono.empty())

            StepVerifier.create(service.findById(nonExistentId))
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("AC10: findAllPaginated resiliency")
    inner class FindAllPaginatedResiliency {

        @Test
        @DisplayName("should handle IOException from repository")
        fun shouldHandleIOException() {
            whenever(repository.findAllPaginated(20, 0))
                .thenReturn(reactor.core.publisher.Flux.error(IOException("Database connection lost")))
            whenever(repository.countAllNotDeleted())
                .thenReturn(Mono.just(0L))

            StepVerifier.create(service.findAllPaginated(0, 20))
                .expectError(IOException::class.java)
                .verify()
        }
    }

    @Nested
    @DisplayName("AC10: search resiliency")
    inner class SearchResiliency {

        @Test
        @DisplayName("should handle IOException from repository")
        fun shouldHandleIOException() {
            whenever(repository.searchByText("test", 51))
                .thenReturn(reactor.core.publisher.Flux.error(IOException("Search timeout")))

            // Note: We need to also mock countBySearchTerm since it's called in Mono.zip
            whenever(repository.countBySearchTerm("test"))
                .thenReturn(Mono.just(0L))

            StepVerifier.create(service.search("test"))
                .expectError(IOException::class.java)
                .verify()
        }

        @Test
        @DisplayName("should return empty response for blank query")
        fun shouldReturnEmptyForBlankQuery() {
            StepVerifier.create(service.search("   "))
                .expectNextMatches { response ->
                    response.content.isEmpty() &&
                        response.totalMatches == 0L &&
                        !response.hasMore
                }
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("AC10: Fallback method behavior")
    inner class FallbackBehavior {

        @Test
        @DisplayName("QueryRateLimitException should have proper message")
        fun queryRateLimitExceptionShouldHaveMessage() {
            val exception = QueryRateLimitException("Too many requests. Please try again later.")
            assert(exception.message == "Too many requests. Please try again later.")
        }

        @Test
        @DisplayName("QueryServiceUnavailableException should have proper message")
        fun queryServiceUnavailableExceptionShouldHaveMessage() {
            val exception = QueryServiceUnavailableException("Query service temporarily unavailable. Please try again later.")
            assert(exception.message == "Query service temporarily unavailable. Please try again later.")
        }
    }
}
