package com.pintailconsultingllc.cqrsspike.product.query.service

import com.pintailconsultingllc.cqrsspike.product.query.model.ProductReadModel
import com.pintailconsultingllc.cqrsspike.product.query.model.ProductStatusView
import com.pintailconsultingllc.cqrsspike.product.query.repository.ProductReadModelRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.OffsetDateTime
import java.util.UUID

@ExtendWith(MockitoExtension::class)
@DisplayName("ProductQueryService")
class ProductQueryServiceTest {

    @Mock
    private lateinit var repository: ProductReadModelRepository

    private lateinit var queryService: ProductQueryService

    private val testProduct = ProductReadModel(
        id = UUID.randomUUID(),
        sku = "TEST-001",
        name = "Test Product",
        description = "A test product description",
        priceCents = 1999,
        status = "ACTIVE",
        createdAt = OffsetDateTime.now(),
        updatedAt = OffsetDateTime.now(),
        version = 1,
        isDeleted = false,
        priceDisplay = "$19.99",
        searchText = "Test Product A test product description"
    )

    @BeforeEach
    fun setUp() {
        queryService = ProductQueryService(repository)
    }

    @Nested
    @DisplayName("findById")
    inner class FindById {

        @Test
        @DisplayName("should return product when found")
        fun shouldReturnProductWhenFound() {
            whenever(repository.findByIdNotDeleted(testProduct.id))
                .thenReturn(Mono.just(testProduct))

            StepVerifier.create(queryService.findById(testProduct.id))
                .expectNextMatches { response ->
                    response.id == testProduct.id &&
                        response.sku == testProduct.sku &&
                        response.name == testProduct.name
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("should return empty when not found")
        fun shouldReturnEmptyWhenNotFound() {
            val nonExistentId = UUID.randomUUID()
            whenever(repository.findByIdNotDeleted(nonExistentId))
                .thenReturn(Mono.empty())

            StepVerifier.create(queryService.findById(nonExistentId))
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("findBySku")
    inner class FindBySku {

        @Test
        @DisplayName("should return product when found by SKU")
        fun shouldReturnProductWhenFoundBySku() {
            whenever(repository.findBySku("TEST-001"))
                .thenReturn(Mono.just(testProduct))

            StepVerifier.create(queryService.findBySku("test-001"))
                .expectNextMatches { response ->
                    response.sku == testProduct.sku
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("should return empty when SKU not found")
        fun shouldReturnEmptyWhenSkuNotFound() {
            whenever(repository.findBySku("NONEXISTENT"))
                .thenReturn(Mono.empty())

            StepVerifier.create(queryService.findBySku("nonexistent"))
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("exists")
    inner class Exists {

        @Test
        @DisplayName("should return true when product exists")
        fun shouldReturnTrueWhenProductExists() {
            whenever(repository.existsByIdNotDeleted(testProduct.id))
                .thenReturn(Mono.just(true))

            StepVerifier.create(queryService.exists(testProduct.id))
                .expectNext(true)
                .verifyComplete()
        }

        @Test
        @DisplayName("should return false when product does not exist")
        fun shouldReturnFalseWhenProductDoesNotExist() {
            val nonExistentId = UUID.randomUUID()
            whenever(repository.existsByIdNotDeleted(nonExistentId))
                .thenReturn(Mono.just(false))

            StepVerifier.create(queryService.exists(nonExistentId))
                .expectNext(false)
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("findByStatus")
    inner class FindByStatus {

        @Test
        @DisplayName("should return products with matching status")
        fun shouldReturnProductsWithMatchingStatus() {
            whenever(repository.findByStatus("ACTIVE"))
                .thenReturn(Flux.just(testProduct))

            StepVerifier.create(queryService.findByStatus(ProductStatusView.ACTIVE))
                .expectNextMatches { it.status == "ACTIVE" }
                .verifyComplete()
        }

        @Test
        @DisplayName("should return empty flux when no products match status")
        fun shouldReturnEmptyFluxWhenNoProductsMatchStatus() {
            whenever(repository.findByStatus("DRAFT"))
                .thenReturn(Flux.empty())

            StepVerifier.create(queryService.findByStatus(ProductStatusView.DRAFT))
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("findAllActive")
    inner class FindAllActive {

        @Test
        @DisplayName("should return all active products")
        fun shouldReturnAllActiveProducts() {
            whenever(repository.findAllActive())
                .thenReturn(Flux.just(testProduct))

            StepVerifier.create(queryService.findAllActive())
                .expectNextMatches { it.status == "ACTIVE" }
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("findAllPaginated")
    inner class FindAllPaginated {

        @Test
        @DisplayName("should return paginated results")
        fun shouldReturnPaginatedResults() {
            val products = listOf(testProduct)
            whenever(repository.findAllPaginated(20, 0))
                .thenReturn(Flux.fromIterable(products))
            whenever(repository.countAllNotDeleted())
                .thenReturn(Mono.just(1L))

            StepVerifier.create(queryService.findAllPaginated(0, 20))
                .expectNextMatches { page ->
                    page.content.size == 1 &&
                        page.page == 0 &&
                        page.size == 20 &&
                        page.totalElements == 1L &&
                        page.first &&
                        page.last
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("should validate page and size parameters")
        fun shouldValidatePageAndSizeParameters() {
            whenever(repository.findAllPaginated(any(), any()))
                .thenReturn(Flux.empty())
            whenever(repository.countAllNotDeleted())
                .thenReturn(Mono.just(0L))

            // Negative page should be converted to 0
            StepVerifier.create(queryService.findAllPaginated(-1, 20))
                .expectNextMatches { page -> page.page == 0 }
                .verifyComplete()
        }

        @Test
        @DisplayName("should cap page size at maximum")
        fun shouldCapPageSizeAtMaximum() {
            whenever(repository.findAllPaginated(100, 0))
                .thenReturn(Flux.empty())
            whenever(repository.countAllNotDeleted())
                .thenReturn(Mono.just(0L))

            // Size over max should be capped at 100
            StepVerifier.create(queryService.findAllPaginated(0, 200))
                .expectNextMatches { page -> page.size == 0 } // No content
                .verifyComplete()
        }

        @Test
        @DisplayName("should calculate pagination metadata correctly")
        fun shouldCalculatePaginationMetadataCorrectly() {
            val products = (1..20).map { i ->
                testProduct.copy(id = UUID.randomUUID(), name = "Product $i")
            }
            whenever(repository.findAllPaginated(20, 20))
                .thenReturn(Flux.fromIterable(products))
            whenever(repository.countAllNotDeleted())
                .thenReturn(Mono.just(50L))

            StepVerifier.create(queryService.findAllPaginated(1, 20))
                .expectNextMatches { page ->
                    page.page == 1 &&
                        page.totalPages == 3 &&
                        !page.first &&
                        !page.last &&
                        page.hasNext &&
                        page.hasPrevious
                }
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("findByPriceRange")
    inner class FindByPriceRange {

        @Test
        @DisplayName("should return products within price range")
        fun shouldReturnProductsWithinPriceRange() {
            whenever(repository.findByPriceRange(1000, 3000))
                .thenReturn(Flux.just(testProduct))

            StepVerifier.create(queryService.findByPriceRange(1000, 3000))
                .expectNextMatches { it.priceCents in 1000..3000 }
                .verifyComplete()
        }

        @Test
        @DisplayName("should reject negative minimum price")
        fun shouldRejectNegativeMinimumPrice() {
            StepVerifier.create(queryService.findByPriceRange(-100, 3000))
                .expectError(IllegalArgumentException::class.java)
                .verify()
        }

        @Test
        @DisplayName("should reject max less than min")
        fun shouldRejectMaxLessThanMin() {
            StepVerifier.create(queryService.findByPriceRange(3000, 1000))
                .expectError(IllegalArgumentException::class.java)
                .verify()
        }
    }

    @Nested
    @DisplayName("search")
    inner class Search {

        @Test
        @DisplayName("should return search results")
        fun shouldReturnSearchResults() {
            whenever(repository.searchByText("test", 51))
                .thenReturn(Flux.just(testProduct))
            whenever(repository.countBySearchTerm("test"))
                .thenReturn(Mono.just(1L))

            StepVerifier.create(queryService.search("test"))
                .expectNextMatches { response ->
                    response.content.isNotEmpty() &&
                        response.query == "test" &&
                        response.totalMatches == 1L
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("should return empty result for blank query")
        fun shouldReturnEmptyResultForBlankQuery() {
            StepVerifier.create(queryService.search("   "))
                .expectNextMatches { response ->
                    response.content.isEmpty() &&
                        response.totalMatches == 0L
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("should indicate hasMore when more results exist")
        fun shouldIndicateHasMoreWhenMoreResultsExist() {
            val manyProducts = (1..52).map { i ->
                testProduct.copy(id = UUID.randomUUID(), name = "Product $i")
            }
            whenever(repository.searchByText("product", 51))
                .thenReturn(Flux.fromIterable(manyProducts.take(51)))
            whenever(repository.countBySearchTerm("product"))
                .thenReturn(Mono.just(52L))

            StepVerifier.create(queryService.search("product"))
                .expectNextMatches { response ->
                    response.hasMore && response.content.size == 50
                }
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("autocomplete")
    inner class Autocomplete {

        @Test
        @DisplayName("should return autocomplete results")
        fun shouldReturnAutocompleteResults() {
            whenever(repository.findByNameStartingWith("Test", 10))
                .thenReturn(Flux.just(testProduct))

            StepVerifier.create(queryService.autocomplete("Test"))
                .expectNextMatches { it.name.startsWith("Test") }
                .verifyComplete()
        }

        @Test
        @DisplayName("should return empty for blank prefix")
        fun shouldReturnEmptyForBlankPrefix() {
            StepVerifier.create(queryService.autocomplete("  "))
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("count")
    inner class Count {

        @Test
        @DisplayName("should return total count")
        fun shouldReturnTotalCount() {
            whenever(repository.countAllNotDeleted())
                .thenReturn(Mono.just(42L))

            StepVerifier.create(queryService.count())
                .expectNext(42L)
                .verifyComplete()
        }

        @Test
        @DisplayName("should return count by status")
        fun shouldReturnCountByStatus() {
            whenever(repository.countByStatus("ACTIVE"))
                .thenReturn(Mono.just(10L))

            StepVerifier.create(queryService.countByStatus(ProductStatusView.ACTIVE))
                .expectNext(10L)
                .verifyComplete()
        }
    }
}
