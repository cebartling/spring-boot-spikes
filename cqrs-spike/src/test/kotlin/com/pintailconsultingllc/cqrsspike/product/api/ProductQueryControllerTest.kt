package com.pintailconsultingllc.cqrsspike.product.api

import com.pintailconsultingllc.cqrsspike.product.query.dto.ProductCursorPageResponse
import com.pintailconsultingllc.cqrsspike.product.query.dto.ProductPageResponse
import com.pintailconsultingllc.cqrsspike.product.query.dto.ProductResponse
import com.pintailconsultingllc.cqrsspike.product.query.dto.ProductSearchResponse
import com.pintailconsultingllc.cqrsspike.product.query.dto.SortDirection
import com.pintailconsultingllc.cqrsspike.product.query.dto.SortField
import com.pintailconsultingllc.cqrsspike.product.query.model.ProductStatusView
import com.pintailconsultingllc.cqrsspike.product.query.service.ProductQueryService
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.OffsetDateTime
import java.util.UUID

@WebFluxTest(ProductQueryController::class)
@Import(QueryExceptionHandler::class)
@DisplayName("ProductQueryController")
class ProductQueryControllerTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @MockBean
    private lateinit var queryService: ProductQueryService

    @Nested
    @DisplayName("GET /api/products/{id}")
    inner class GetProductById {

        @Test
        @DisplayName("should return 200 with product when found")
        fun shouldReturnProductWhenFound() {
            val productId = UUID.randomUUID()
            val product = createProductResponse(productId)

            whenever(queryService.findById(productId))
                .thenReturn(Mono.just(product))

            webTestClient.get()
                .uri("/api/products/$productId")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.id").isEqualTo(productId.toString())
                .jsonPath("$.sku").isEqualTo(product.sku)
                .jsonPath("$.name").isEqualTo(product.name)
        }

        @Test
        @DisplayName("should return 404 when product not found")
        fun shouldReturn404WhenNotFound() {
            val productId = UUID.randomUUID()

            whenever(queryService.findById(productId))
                .thenReturn(Mono.empty())

            webTestClient.get()
                .uri("/api/products/$productId")
                .exchange()
                .expectStatus().isNotFound
        }

        @Test
        @DisplayName("should return 400 for invalid UUID format")
        fun shouldReturn400ForInvalidUuid() {
            webTestClient.get()
                .uri("/api/products/invalid-uuid")
                .exchange()
                .expectStatus().isBadRequest
        }
    }

    @Nested
    @DisplayName("GET /api/products/sku/{sku}")
    inner class GetProductBySku {

        @Test
        @DisplayName("should return 200 with product when found by SKU")
        fun shouldReturnProductWhenFoundBySku() {
            val product = createProductResponse()

            whenever(queryService.findBySku("TEST-001"))
                .thenReturn(Mono.just(product))

            webTestClient.get()
                .uri("/api/products/sku/TEST-001")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.sku").isEqualTo("TEST-001")
        }

        @Test
        @DisplayName("should return 404 when SKU not found")
        fun shouldReturn404WhenSkuNotFound() {
            whenever(queryService.findBySku("NONEXISTENT"))
                .thenReturn(Mono.empty())

            webTestClient.get()
                .uri("/api/products/sku/NONEXISTENT")
                .exchange()
                .expectStatus().isNotFound
        }
    }

    @Nested
    @DisplayName("GET /api/products")
    inner class ListProducts {

        @Test
        @DisplayName("should return paginated products")
        fun shouldReturnPaginatedProducts() {
            val pageResponse = ProductPageResponse(
                content = listOf(createProductResponse()),
                page = 0,
                size = 20,
                totalElements = 1,
                totalPages = 1,
                first = true,
                last = true,
                hasNext = false,
                hasPrevious = false
            )

            whenever(queryService.findAllSortedPaginated(
                eq(0), eq(20), eq(SortField.CREATED_AT), eq(SortDirection.DESC)
            )).thenReturn(Mono.just(pageResponse))

            webTestClient.get()
                .uri("/api/products")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.content").isArray
                .jsonPath("$.page").isEqualTo(0)
                .jsonPath("$.size").isEqualTo(20)
                .jsonPath("$.totalElements").isEqualTo(1)
        }

        @Test
        @DisplayName("should apply pagination parameters")
        fun shouldApplyPaginationParameters() {
            val pageResponse = ProductPageResponse(
                content = emptyList(),
                page = 2,
                size = 10,
                totalElements = 100,
                totalPages = 10,
                first = false,
                last = false,
                hasNext = true,
                hasPrevious = true
            )

            whenever(queryService.findAllSortedPaginated(
                eq(2), eq(10), eq(SortField.CREATED_AT), eq(SortDirection.DESC)
            )).thenReturn(Mono.just(pageResponse))

            webTestClient.get()
                .uri("/api/products?page=2&size=10")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.page").isEqualTo(2)
                .jsonPath("$.size").isEqualTo(10)
        }

        @Test
        @DisplayName("should filter by status")
        fun shouldFilterByStatus() {
            val pageResponse = ProductPageResponse(
                content = listOf(createProductResponse()),
                page = 0,
                size = 20,
                totalElements = 1,
                totalPages = 1,
                first = true,
                last = true,
                hasNext = false,
                hasPrevious = false
            )

            whenever(queryService.findByStatusPaginated(
                eq(ProductStatusView.ACTIVE), eq(0), eq(20)
            )).thenReturn(Mono.just(pageResponse))

            webTestClient.get()
                .uri("/api/products?status=ACTIVE")
                .exchange()
                .expectStatus().isOk
        }

        @Test
        @DisplayName("should apply sort and direction")
        fun shouldApplySortAndDirection() {
            val pageResponse = ProductPageResponse(
                content = listOf(createProductResponse()),
                page = 0,
                size = 20,
                totalElements = 1,
                totalPages = 1,
                first = true,
                last = true,
                hasNext = false,
                hasPrevious = false
            )

            whenever(queryService.findAllSortedPaginated(
                eq(0), eq(20), eq(SortField.NAME), eq(SortDirection.ASC)
            )).thenReturn(Mono.just(pageResponse))

            webTestClient.get()
                .uri("/api/products?sort=name&direction=asc")
                .exchange()
                .expectStatus().isOk
        }

        @Test
        @DisplayName("should filter by price range")
        fun shouldFilterByPriceRange() {
            val products = listOf(createProductResponse())

            whenever(queryService.findByPriceRange(eq(1000), eq(5000)))
                .thenReturn(Flux.fromIterable(products))

            webTestClient.get()
                .uri("/api/products?minPrice=1000&maxPrice=5000")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.content").isArray
        }
    }

    @Nested
    @DisplayName("GET /api/products/cursor")
    inner class ListProductsWithCursor {

        @Test
        @DisplayName("should return cursor-paginated products")
        fun shouldReturnCursorPaginatedProducts() {
            val cursorResponse = ProductCursorPageResponse(
                content = listOf(createProductResponse()),
                size = 1,
                hasNext = false,
                nextCursor = null
            )

            whenever(queryService.findWithCursor(eq(null), eq(20), eq(SortField.CREATED_AT)))
                .thenReturn(Mono.just(cursorResponse))

            webTestClient.get()
                .uri("/api/products/cursor")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.content").isArray
                .jsonPath("$.hasNext").isEqualTo(false)
        }

        @Test
        @DisplayName("should accept cursor parameter")
        fun shouldAcceptCursorParameter() {
            val cursor = "dGVzdC1jdXJzb3I="
            val cursorResponse = ProductCursorPageResponse(
                content = listOf(createProductResponse()),
                size = 1,
                hasNext = false,
                nextCursor = null
            )

            whenever(queryService.findWithCursor(eq(cursor), eq(20), eq(SortField.CREATED_AT)))
                .thenReturn(Mono.just(cursorResponse))

            webTestClient.get()
                .uri("/api/products/cursor?cursor=$cursor")
                .exchange()
                .expectStatus().isOk
        }
    }

    @Nested
    @DisplayName("GET /api/products/by-status/{status}")
    inner class GetProductsByStatus {

        @Test
        @DisplayName("should return products by status")
        fun shouldReturnProductsByStatus() {
            val pageResponse = ProductPageResponse(
                content = listOf(createProductResponse()),
                page = 0,
                size = 20,
                totalElements = 1,
                totalPages = 1,
                first = true,
                last = true,
                hasNext = false,
                hasPrevious = false
            )

            whenever(queryService.findByStatusPaginated(
                eq(ProductStatusView.ACTIVE), eq(0), eq(20)
            )).thenReturn(Mono.just(pageResponse))

            webTestClient.get()
                .uri("/api/products/by-status/ACTIVE")
                .exchange()
                .expectStatus().isOk
        }

        @Test
        @DisplayName("should handle lowercase status")
        fun shouldHandleLowercaseStatus() {
            val pageResponse = ProductPageResponse(
                content = listOf(createProductResponse()),
                page = 0,
                size = 20,
                totalElements = 1,
                totalPages = 1,
                first = true,
                last = true,
                hasNext = false,
                hasPrevious = false
            )

            whenever(queryService.findByStatusPaginated(
                eq(ProductStatusView.DRAFT), eq(0), eq(20)
            )).thenReturn(Mono.just(pageResponse))

            webTestClient.get()
                .uri("/api/products/by-status/draft")
                .exchange()
                .expectStatus().isOk
        }
    }

    @Nested
    @DisplayName("GET /api/products/search")
    inner class SearchProducts {

        @Test
        @DisplayName("should return search results")
        fun shouldReturnSearchResults() {
            val searchResponse = ProductSearchResponse(
                content = listOf(createProductResponse()),
                query = "test",
                totalMatches = 1,
                hasMore = false
            )

            whenever(queryService.search(eq("test"), eq(50)))
                .thenReturn(Mono.just(searchResponse))

            webTestClient.get()
                .uri("/api/products/search?q=test")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.query").isEqualTo("test")
                .jsonPath("$.totalMatches").isEqualTo(1)
        }

        @Test
        @DisplayName("should apply limit parameter")
        fun shouldApplyLimitParameter() {
            val searchResponse = ProductSearchResponse(
                content = listOf(createProductResponse()),
                query = "widget",
                totalMatches = 1,
                hasMore = false
            )

            whenever(queryService.search(eq("widget"), eq(25)))
                .thenReturn(Mono.just(searchResponse))

            webTestClient.get()
                .uri("/api/products/search?q=widget&limit=25")
                .exchange()
                .expectStatus().isOk
        }

        @Test
        @DisplayName("should filter by status")
        fun shouldFilterByStatus() {
            val products = listOf(createProductResponse())

            whenever(queryService.searchByStatus(eq("widget"), eq(ProductStatusView.ACTIVE), eq(50)))
                .thenReturn(Flux.fromIterable(products))

            webTestClient.get()
                .uri("/api/products/search?q=widget&status=ACTIVE")
                .exchange()
                .expectStatus().isOk
        }
    }

    @Nested
    @DisplayName("GET /api/products/autocomplete")
    inner class Autocomplete {

        @Test
        @DisplayName("should return autocomplete suggestions")
        fun shouldReturnAutocompleteSuggestions() {
            val products = listOf(createProductResponse())

            whenever(queryService.autocomplete(eq("Test"), eq(10)))
                .thenReturn(Flux.fromIterable(products))

            webTestClient.get()
                .uri("/api/products/autocomplete?prefix=Test")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$").isArray
                .jsonPath("$[0].name").isEqualTo("Test Product")
        }

        @Test
        @DisplayName("should apply limit parameter")
        fun shouldApplyLimitParameter() {
            val products = listOf(createProductResponse())

            whenever(queryService.autocomplete(eq("Prod"), eq(5)))
                .thenReturn(Flux.fromIterable(products))

            webTestClient.get()
                .uri("/api/products/autocomplete?prefix=Prod&limit=5")
                .exchange()
                .expectStatus().isOk
        }
    }

    @Nested
    @DisplayName("GET /api/products/count")
    inner class CountProducts {

        @Test
        @DisplayName("should return total count")
        fun shouldReturnTotalCount() {
            whenever(queryService.count())
                .thenReturn(Mono.just(42L))

            webTestClient.get()
                .uri("/api/products/count")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.count").isEqualTo(42)
        }

        @Test
        @DisplayName("should return count by status")
        fun shouldReturnCountByStatus() {
            whenever(queryService.countByStatus(eq(ProductStatusView.ACTIVE)))
                .thenReturn(Mono.just(10L))

            webTestClient.get()
                .uri("/api/products/count?status=ACTIVE")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.count").isEqualTo(10)
                .jsonPath("$.status").isEqualTo("ACTIVE")
        }
    }

    @Nested
    @DisplayName("Validation")
    inner class Validation {

        @Test
        @DisplayName("should return 400 for invalid status value")
        fun shouldReturn400ForInvalidStatus() {
            webTestClient.get()
                .uri("/api/products?status=INVALID")
                .exchange()
                .expectStatus().isBadRequest
        }

        @Test
        @DisplayName("should return 400 for invalid sort field")
        fun shouldReturn400ForInvalidSortField() {
            webTestClient.get()
                .uri("/api/products?sort=invalid")
                .exchange()
                .expectStatus().isBadRequest
        }

        @Test
        @DisplayName("should return 400 for invalid direction")
        fun shouldReturn400ForInvalidDirection() {
            webTestClient.get()
                .uri("/api/products?direction=invalid")
                .exchange()
                .expectStatus().isBadRequest
        }

        @Test
        @DisplayName("should return 400 for page size over limit")
        fun shouldReturn400ForSizeOverLimit() {
            webTestClient.get()
                .uri("/api/products?size=500")
                .exchange()
                .expectStatus().isBadRequest
        }

        @Test
        @DisplayName("should return 400 for negative page")
        fun shouldReturn400ForNegativePage() {
            webTestClient.get()
                .uri("/api/products?page=-1")
                .exchange()
                .expectStatus().isBadRequest
        }
    }

    // Helper methods

    private fun createProductResponse(id: UUID = UUID.randomUUID()): ProductResponse {
        val now = OffsetDateTime.now()
        return ProductResponse(
            id = id,
            sku = "TEST-001",
            name = "Test Product",
            description = "A test product",
            priceCents = 1999,
            priceDisplay = "$19.99",
            status = "ACTIVE",
            createdAt = now,
            updatedAt = now,
            version = 1
        )
    }
}
