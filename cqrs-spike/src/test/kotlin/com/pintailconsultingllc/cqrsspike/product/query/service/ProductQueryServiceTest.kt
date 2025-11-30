package com.pintailconsultingllc.cqrsspike.product.query.service

import com.pintailconsultingllc.cqrsspike.product.query.dto.SortDirection
import com.pintailconsultingllc.cqrsspike.product.query.dto.SortField
import com.pintailconsultingllc.cqrsspike.product.query.model.ProductReadModel
import com.pintailconsultingllc.cqrsspike.product.query.model.ProductStatusView
import com.pintailconsultingllc.cqrsspike.product.query.repository.ProductReadModelRepository
import com.pintailconsultingllc.cqrsspike.testutil.builders.ProductReadModelBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
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
        aggregateVersion = 1,
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

            StepVerifier.create(queryService.findAllPaginated(0, 200))
                .expectNextMatches { page -> page.size == 100 && page.content.isEmpty() }
                .verifyComplete()

            // Verify the repository was called with the capped limit of 100, not 200
            verify(repository).findAllPaginated(100, 0)
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
    @DisplayName("findAllSortedPaginated")
    inner class FindAllSortedPaginated {

        @Test
        @DisplayName("should sort by name ASC when direction is ASC")
        fun shouldSortByNameAscWhenDirectionIsAsc() {
            val products = listOf(testProduct)
            whenever(repository.findAllSortedByNameAscPaginated(20, 0))
                .thenReturn(Flux.fromIterable(products))
            whenever(repository.countAllNotDeleted())
                .thenReturn(Mono.just(1L))

            StepVerifier.create(queryService.findAllSortedPaginated(0, 20, SortField.NAME, SortDirection.ASC))
                .expectNextMatches { page ->
                    page.content.size == 1 &&
                        page.page == 0 &&
                        page.size == 20
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("should sort by name DESC when direction is DESC")
        fun shouldSortByNameDescWhenDirectionIsDesc() {
            val products = listOf(testProduct)
            whenever(repository.findAllSortedByNameDescPaginated(20, 0))
                .thenReturn(Flux.fromIterable(products))
            whenever(repository.countAllNotDeleted())
                .thenReturn(Mono.just(1L))

            StepVerifier.create(queryService.findAllSortedPaginated(0, 20, SortField.NAME, SortDirection.DESC))
                .expectNextMatches { page ->
                    page.content.size == 1 &&
                        page.page == 0 &&
                        page.size == 20
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("should sort by price ASC when direction is ASC")
        fun shouldSortByPriceAscWhenDirectionIsAsc() {
            val products = listOf(testProduct)
            whenever(repository.findAllSortedByPriceAscPaginated(20, 0))
                .thenReturn(Flux.fromIterable(products))
            whenever(repository.countAllNotDeleted())
                .thenReturn(Mono.just(1L))

            StepVerifier.create(queryService.findAllSortedPaginated(0, 20, SortField.PRICE, SortDirection.ASC))
                .expectNextMatches { page ->
                    page.content.size == 1
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("should sort by price DESC when direction is DESC")
        fun shouldSortByPriceDescWhenDirectionIsDesc() {
            val products = listOf(testProduct)
            whenever(repository.findAllSortedByPriceDescPaginated(20, 0))
                .thenReturn(Flux.fromIterable(products))
            whenever(repository.countAllNotDeleted())
                .thenReturn(Mono.just(1L))

            StepVerifier.create(queryService.findAllSortedPaginated(0, 20, SortField.PRICE, SortDirection.DESC))
                .expectNextMatches { page ->
                    page.content.size == 1
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("should sort by created_at ASC when direction is ASC")
        fun shouldSortByCreatedAtAscWhenDirectionIsAsc() {
            val products = listOf(testProduct)
            whenever(repository.findAllSortedByCreatedAtAscPaginated(20, 0))
                .thenReturn(Flux.fromIterable(products))
            whenever(repository.countAllNotDeleted())
                .thenReturn(Mono.just(1L))

            StepVerifier.create(queryService.findAllSortedPaginated(0, 20, SortField.CREATED_AT, SortDirection.ASC))
                .expectNextMatches { page ->
                    page.content.size == 1
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("should sort by created_at DESC when direction is DESC")
        fun shouldSortByCreatedAtDescWhenDirectionIsDesc() {
            val products = listOf(testProduct)
            whenever(repository.findAllPaginated(20, 0))
                .thenReturn(Flux.fromIterable(products))
            whenever(repository.countAllNotDeleted())
                .thenReturn(Mono.just(1L))

            StepVerifier.create(queryService.findAllSortedPaginated(0, 20, SortField.CREATED_AT, SortDirection.DESC))
                .expectNextMatches { page ->
                    page.content.size == 1
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("should default to ASC when direction not specified")
        fun shouldDefaultToAscWhenDirectionNotSpecified() {
            val products = listOf(testProduct)
            whenever(repository.findAllSortedByNameAscPaginated(20, 0))
                .thenReturn(Flux.fromIterable(products))
            whenever(repository.countAllNotDeleted())
                .thenReturn(Mono.just(1L))

            StepVerifier.create(queryService.findAllSortedPaginated(0, 20, SortField.NAME))
                .expectNextMatches { page ->
                    page.content.size == 1
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
            org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
                queryService.findByPriceRange(-100, 3000)
            }
        }

        @Test
        @DisplayName("should reject max less than min")
        fun shouldRejectMaxLessThanMin() {
            org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
                queryService.findByPriceRange(3000, 1000)
            }
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

    @Nested
    @DisplayName("AC12: Edge Cases - Error Handling")
    inner class ErrorHandling {

        @Test
        @DisplayName("should handle repository exception gracefully")
        fun shouldHandleRepositoryExceptionGracefully() {
            val error = RuntimeException("Database connection failed")

            whenever(repository.findByIdNotDeleted(any()))
                .thenReturn(Mono.error(error))

            StepVerifier.create(queryService.findById(UUID.randomUUID()))
                .expectError(RuntimeException::class.java)
                .verify()
        }

        @Test
        @DisplayName("should handle repository exception in search")
        fun shouldHandleRepositoryExceptionInSearch() {
            val error = RuntimeException("Search failed")

            whenever(repository.searchByText(any(), any()))
                .thenReturn(Flux.error(error))
            whenever(repository.countBySearchTerm(any()))
                .thenReturn(Mono.just(0L))

            StepVerifier.create(queryService.search("test"))
                .expectError(RuntimeException::class.java)
                .verify()
        }

        @Test
        @DisplayName("should return empty when searching with special characters")
        fun shouldReturnEmptyForSpecialCharacterSearch() {
            whenever(repository.searchByText(any(), any()))
                .thenReturn(Flux.empty())
            whenever(repository.countBySearchTerm(any()))
                .thenReturn(Mono.just(0L))

            // Test with special characters that might cause issues
            StepVerifier.create(queryService.search("'; DROP TABLE products; --"))
                .expectNextMatches { response ->
                    response.content.isEmpty() &&
                    response.totalMatches == 0L
                }
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("AC12: Edge Cases - Boundary Conditions")
    inner class BoundaryConditions {

        @Test
        @DisplayName("should handle maximum page size")
        fun shouldHandleMaximumPageSize() {
            whenever(repository.findAllPaginated(100, 0))
                .thenReturn(Flux.empty())
            whenever(repository.countAllNotDeleted())
                .thenReturn(Mono.just(0L))

            StepVerifier.create(queryService.findAllPaginated(0, Int.MAX_VALUE))
                .expectNextMatches { page -> page.size == 100 } // Capped at max
                .verifyComplete()
        }

        @Test
        @DisplayName("should handle price range with same min and max")
        fun shouldHandlePriceRangeWithSameMinMax() {
            val price = 1999
            val product = ProductReadModelBuilder.aProductReadModel()
                .withPrice(price)
                .build()

            whenever(repository.findByPriceRange(price, price))
                .thenReturn(Flux.just(product))

            StepVerifier.create(queryService.findByPriceRange(price, price))
                .expectNextMatches { it.priceCents == price }
                .verifyComplete()
        }

        @Test
        @DisplayName("should handle zero minimum page size")
        fun shouldHandleZeroMinimumPageSize() {
            whenever(repository.findAllPaginated(1, 0))
                .thenReturn(Flux.empty())
            whenever(repository.countAllNotDeleted())
                .thenReturn(Mono.just(0L))

            // Size of 0 should be converted to minimum of 1
            StepVerifier.create(queryService.findAllPaginated(0, 0))
                .expectNextMatches { page -> page.size == 1 }
                .verifyComplete()
        }

        @Test
        @DisplayName("should handle very large page number")
        fun shouldHandleVeryLargePageNumber() {
            whenever(repository.findAllPaginated(any(), any()))
                .thenReturn(Flux.empty())
            whenever(repository.countAllNotDeleted())
                .thenReturn(Mono.just(10L))

            // Large page number should return empty results but not fail
            StepVerifier.create(queryService.findAllPaginated(1000000, 20))
                .expectNextMatches { page ->
                    page.content.isEmpty() &&
                    page.page == 1000000 &&
                    page.totalElements == 10L
                }
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("AC12: Edge Cases - Using Test Builders")
    inner class TestBuilderUsageExamples {

        @Test
        @DisplayName("should find product using builder-created read model")
        fun shouldFindProductUsingBuilderCreatedReadModel() {
            val product = ProductReadModelBuilder.anActiveProductReadModel()
                .withSku("BUILDER-TEST-001")
                .withName("Builder Test Product")
                .withPrice(4999)
                .build()

            whenever(repository.findByIdNotDeleted(product.id))
                .thenReturn(Mono.just(product))

            StepVerifier.create(queryService.findById(product.id))
                .expectNextMatches { response ->
                    response.id == product.id &&
                    response.sku == "BUILDER-TEST-001" &&
                    response.name == "Builder Test Product" &&
                    response.priceCents == 4999
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("should handle multiple products from builder")
        fun shouldHandleMultipleProductsFromBuilder() {
            val products = listOf(
                ProductReadModelBuilder.anActiveProductReadModel()
                    .withSku("PROD-001")
                    .withName("Product One")
                    .withPrice(1999)
                    .build(),
                ProductReadModelBuilder.anActiveProductReadModel()
                    .withSku("PROD-002")
                    .withName("Product Two")
                    .withPrice(2999)
                    .build(),
                ProductReadModelBuilder.aDraftProductReadModel()
                    .withSku("PROD-003")
                    .withName("Draft Product")
                    .withPrice(999)
                    .build()
            )

            whenever(repository.findAllPaginated(20, 0))
                .thenReturn(Flux.fromIterable(products))
            whenever(repository.countAllNotDeleted())
                .thenReturn(Mono.just(3L))

            StepVerifier.create(queryService.findAllPaginated(0, 20))
                .expectNextMatches { page ->
                    page.content.size == 3 &&
                    page.totalElements == 3L &&
                    page.content.any { it.sku == "PROD-001" } &&
                    page.content.any { it.sku == "PROD-002" } &&
                    page.content.any { it.sku == "PROD-003" }
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("should filter deleted products correctly")
        fun shouldFilterDeletedProductsCorrectly() {
            // Deleted products should not be returned by findByIdNotDeleted
            val deletedProduct = ProductReadModelBuilder.aDeletedProductReadModel()
                .withSku("DELETED-001")
                .build()

            whenever(repository.findByIdNotDeleted(deletedProduct.id))
                .thenReturn(Mono.empty())

            StepVerifier.create(queryService.findById(deletedProduct.id))
                .verifyComplete()
        }
    }
}
