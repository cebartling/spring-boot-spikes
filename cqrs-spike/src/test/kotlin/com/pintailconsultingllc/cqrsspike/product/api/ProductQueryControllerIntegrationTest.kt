package com.pintailconsultingllc.cqrsspike.product.api

import com.pintailconsultingllc.cqrsspike.product.query.model.ProductReadModel
import com.pintailconsultingllc.cqrsspike.product.query.repository.ProductReadModelRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.OffsetDateTime
import java.util.UUID

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("ProductQueryController Integration Tests")
class ProductQueryControllerIntegrationTest {

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
            registry.add("spring.flyway.enabled") { "false" }
            registry.add("spring.cloud.vault.enabled") { "false" }
        }
    }

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var repository: ProductReadModelRepository

    @BeforeEach
    fun setUp() {
        repository.deleteAll().block()
    }

    @Nested
    @DisplayName("GET /api/products/{id}")
    inner class GetProductById {

        @Test
        @DisplayName("should return product when found")
        fun shouldReturnProductWhenFound() {
            val product = createAndSaveProduct("INTEG-001", "Integration Test Product", 1999)

            webTestClient.get()
                .uri("/api/products/${product.id}")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.id").isEqualTo(product.id.toString())
                .jsonPath("$.sku").isEqualTo("INTEG-001")
                .jsonPath("$.name").isEqualTo("Integration Test Product")
                .jsonPath("$.priceCents").isEqualTo(1999)
        }

        @Test
        @DisplayName("should return 404 when product not found")
        fun shouldReturn404WhenNotFound() {
            val nonExistentId = UUID.randomUUID()

            webTestClient.get()
                .uri("/api/products/$nonExistentId")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isNotFound
        }

        @Test
        @DisplayName("should return 400 for invalid UUID")
        fun shouldReturn400ForInvalidUuid() {
            webTestClient.get()
                .uri("/api/products/not-a-uuid")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isBadRequest
        }
    }

    @Nested
    @DisplayName("GET /api/products/sku/{sku}")
    inner class GetProductBySku {

        @Test
        @DisplayName("should return product when found by SKU")
        fun shouldReturnProductWhenFoundBySku() {
            createAndSaveProduct("SKU-TEST-001", "SKU Test Product", 2999)

            webTestClient.get()
                .uri("/api/products/sku/SKU-TEST-001")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.sku").isEqualTo("SKU-TEST-001")
                .jsonPath("$.name").isEqualTo("SKU Test Product")
        }

        @Test
        @DisplayName("should return 404 when SKU not found")
        fun shouldReturn404WhenSkuNotFound() {
            webTestClient.get()
                .uri("/api/products/sku/NONEXISTENT")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isNotFound
        }
    }

    @Nested
    @DisplayName("GET /api/products")
    inner class ListProducts {

        @Test
        @DisplayName("should return empty page when no products with links")
        fun shouldReturnEmptyPageWithLinks() {
            webTestClient.get()
                .uri("/api/products")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.content").isArray
                .jsonPath("$.content.length()").isEqualTo(0)
                .jsonPath("$.totalElements").isEqualTo(0)
                .jsonPath("$.links").exists()
                .jsonPath("$.links.self").exists()
        }

        @Test
        @DisplayName("should return paginated products with navigation links")
        fun shouldReturnPaginatedProductsWithLinks() {
            repeat(5) { i ->
                createAndSaveProduct("PAGE-$i", "Product $i", 1000 + i * 100)
            }

            webTestClient.get()
                .uri("/api/products?page=0&size=3")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.content.length()").isEqualTo(3)
                .jsonPath("$.totalElements").isEqualTo(5)
                .jsonPath("$.totalPages").isEqualTo(2)
                .jsonPath("$.hasNext").isEqualTo(true)
                .jsonPath("$.first").isEqualTo(true)
                .jsonPath("$.links").exists()
                .jsonPath("$.links.self").exists()
                .jsonPath("$.links.first").exists()
                .jsonPath("$.links.next").exists()
                .jsonPath("$.links.last").exists()
        }

        @Test
        @DisplayName("should filter by status")
        fun shouldFilterByStatus() {
            createAndSaveProduct("ACTIVE-1", "Active Product", 1000, "ACTIVE")
            createAndSaveProduct("DRAFT-1", "Draft Product", 2000, "DRAFT")

            webTestClient.get()
                .uri("/api/products?status=ACTIVE")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.content.length()").isEqualTo(1)
                .jsonPath("$.content[0].status").isEqualTo("ACTIVE")
        }

        @Test
        @DisplayName("should filter by price range")
        fun shouldFilterByPriceRange() {
            createAndSaveProduct("CHEAP-1", "Cheap Product", 500)
            createAndSaveProduct("MID-1", "Mid Product", 1500)
            createAndSaveProduct("EXPENSIVE-1", "Expensive Product", 5000)

            webTestClient.get()
                .uri("/api/products?minPrice=1000&maxPrice=2000")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.content.length()").isEqualTo(1)
                .jsonPath("$.content[0].name").isEqualTo("Mid Product")
        }

        @Test
        @DisplayName("should sort by name ascending")
        fun shouldSortByNameAscending() {
            createAndSaveProduct("SORT-C", "Charlie Product", 1000)
            createAndSaveProduct("SORT-A", "Alpha Product", 2000)
            createAndSaveProduct("SORT-B", "Bravo Product", 3000)

            webTestClient.get()
                .uri("/api/products?sort=name&direction=asc")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.content[0].name").isEqualTo("Alpha Product")
                .jsonPath("$.content[1].name").isEqualTo("Bravo Product")
                .jsonPath("$.content[2].name").isEqualTo("Charlie Product")
        }

        @Test
        @DisplayName("should sort by price descending")
        fun shouldSortByPriceDescending() {
            createAndSaveProduct("PRICE-1", "Product 1", 1000)
            createAndSaveProduct("PRICE-2", "Product 2", 3000)
            createAndSaveProduct("PRICE-3", "Product 3", 2000)

            webTestClient.get()
                .uri("/api/products?sort=price&direction=desc")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.content[0].priceCents").isEqualTo(3000)
                .jsonPath("$.content[1].priceCents").isEqualTo(2000)
                .jsonPath("$.content[2].priceCents").isEqualTo(1000)
        }
    }

    @Nested
    @DisplayName("GET /api/products/cursor")
    inner class CursorPagination {

        @Test
        @DisplayName("should return cursor-paginated products")
        fun shouldReturnCursorPaginatedProducts() {
            repeat(5) { i ->
                createAndSaveProduct("CURSOR-$i", "Cursor Product $i", 1000 + i * 100)
            }

            webTestClient.get()
                .uri("/api/products/cursor?size=3")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.content.length()").isEqualTo(3)
                .jsonPath("$.hasNext").isEqualTo(true)
                .jsonPath("$.nextCursor").isNotEmpty
        }
    }

    @Nested
    @DisplayName("GET /api/products/by-status/{status}")
    inner class GetProductsByStatus {

        @Test
        @DisplayName("should return products by status with links")
        fun shouldReturnProductsByStatusWithLinks() {
            createAndSaveProduct("ACTIVE-1", "Active 1", 1000, "ACTIVE")
            createAndSaveProduct("ACTIVE-2", "Active 2", 2000, "ACTIVE")
            createAndSaveProduct("DRAFT-1", "Draft 1", 3000, "DRAFT")

            webTestClient.get()
                .uri("/api/products/by-status/ACTIVE")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.content.length()").isEqualTo(2)
                .jsonPath("$.content[0].status").isEqualTo("ACTIVE")
                .jsonPath("$.content[1].status").isEqualTo("ACTIVE")
                .jsonPath("$.links").exists()
                .jsonPath("$.links.self").exists()
        }

        @Test
        @DisplayName("should handle lowercase status")
        fun shouldHandleLowercaseStatus() {
            createAndSaveProduct("DRAFT-1", "Draft Product", 1000, "DRAFT")

            webTestClient.get()
                .uri("/api/products/by-status/draft")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.content.length()").isEqualTo(1)
        }
    }

    @Nested
    @DisplayName("GET /api/products/search")
    inner class SearchProducts {

        @Test
        @DisplayName("should return matching products")
        fun shouldReturnMatchingProducts() {
            createAndSaveProduct("SRCH-1", "Amazing Widget Pro", 999)
            createAndSaveProduct("SRCH-2", "Basic Gadget", 1999)
            createAndSaveProduct("SRCH-3", "Super Widget Deluxe", 2999)

            webTestClient.get()
                .uri("/api/products/search?q=widget")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.query").isEqualTo("widget")
                .jsonPath("$.totalMatches").isEqualTo(2)
        }

        @Test
        @DisplayName("should return empty for no matches")
        fun shouldReturnEmptyForNoMatches() {
            createAndSaveProduct("SRCH-1", "Product One", 999)

            webTestClient.get()
                .uri("/api/products/search?q=nonexistent")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.content.length()").isEqualTo(0)
                .jsonPath("$.totalMatches").isEqualTo(0)
        }

        @Test
        @DisplayName("should filter search by status")
        fun shouldFilterSearchByStatus() {
            createAndSaveProduct("SRCH-1", "Widget Active", 999, "ACTIVE")
            createAndSaveProduct("SRCH-2", "Widget Draft", 1999, "DRAFT")

            webTestClient.get()
                .uri("/api/products/search?q=widget&status=ACTIVE")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.content.length()").isEqualTo(1)
                .jsonPath("$.content[0].status").isEqualTo("ACTIVE")
        }
    }

    @Nested
    @DisplayName("GET /api/products/autocomplete")
    inner class Autocomplete {

        @Test
        @DisplayName("should return autocomplete suggestions")
        fun shouldReturnAutocompleteSuggestions() {
            createAndSaveProduct("AUTO-1", "Premium Widget", 999)
            createAndSaveProduct("AUTO-2", "Premium Gadget", 1999)
            createAndSaveProduct("AUTO-3", "Basic Item", 499)

            webTestClient.get()
                .uri("/api/products/autocomplete?prefix=Premium")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.length()").isEqualTo(2)
        }

        @Test
        @DisplayName("should respect limit parameter")
        fun shouldRespectLimitParameter() {
            repeat(5) { i ->
                createAndSaveProduct("AUTO-$i", "Test Product $i", 1000 + i)
            }

            webTestClient.get()
                .uri("/api/products/autocomplete?prefix=Test&limit=2")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.length()").isEqualTo(2)
        }
    }

    @Nested
    @DisplayName("GET /api/products/count")
    inner class CountProducts {

        @Test
        @DisplayName("should return correct total count")
        fun shouldReturnCorrectTotalCount() {
            repeat(5) { i ->
                createAndSaveProduct("CNT-$i", "Count Product $i", 1000)
            }

            webTestClient.get()
                .uri("/api/products/count")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.count").isEqualTo(5)
        }

        @Test
        @DisplayName("should return count by status")
        fun shouldReturnCountByStatus() {
            createAndSaveProduct("CNT-1", "Active 1", 1000, "ACTIVE")
            createAndSaveProduct("CNT-2", "Active 2", 2000, "ACTIVE")
            createAndSaveProduct("CNT-3", "Draft 1", 3000, "DRAFT")

            webTestClient.get()
                .uri("/api/products/count?status=ACTIVE")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.count").isEqualTo(2)
                .jsonPath("$.status").isEqualTo("ACTIVE")
        }
    }

    @Nested
    @DisplayName("Validation Errors")
    inner class ValidationErrors {

        @Test
        @DisplayName("should return 400 for invalid status")
        fun shouldReturn400ForInvalidStatus() {
            webTestClient.get()
                .uri("/api/products?status=INVALID")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isBadRequest
        }

        @Test
        @DisplayName("should return 400 for page size over limit")
        fun shouldReturn400ForSizeOverLimit() {
            webTestClient.get()
                .uri("/api/products?size=500")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isBadRequest
        }

        @Test
        @DisplayName("should return 400 for negative page")
        fun shouldReturn400ForNegativePage() {
            webTestClient.get()
                .uri("/api/products?page=-1")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isBadRequest
        }
    }

    @Nested
    @DisplayName("Cache Headers")
    inner class CacheHeaders {

        @Test
        @DisplayName("should include Cache-Control header on product response")
        fun shouldIncludeCacheControlHeader() {
            val product = createAndSaveProduct("CACHE-001", "Cached Product", 1999)

            webTestClient.get()
                .uri("/api/products/${product.id}")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk
                .expectHeader().exists("Cache-Control")
        }
    }

    // Helper methods

    private fun createAndSaveProduct(
        sku: String,
        name: String,
        priceCents: Int,
        status: String = "ACTIVE"
    ): ProductReadModel {
        val now = OffsetDateTime.now()
        val product = ProductReadModel(
            id = UUID.randomUUID(),
            sku = sku,
            name = name,
            description = "Test description for $name",
            priceCents = priceCents,
            priceDisplay = null,
            status = status,
            createdAt = now,
            updatedAt = now,
            aggregateVersion = 1L,
            isDeleted = false,
            searchText = "$name Test description for $name"
        )
        return repository.save(product).block()!!
    }
}
