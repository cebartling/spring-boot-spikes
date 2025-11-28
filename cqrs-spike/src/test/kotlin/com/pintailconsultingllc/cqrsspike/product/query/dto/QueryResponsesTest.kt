package com.pintailconsultingllc.cqrsspike.product.query.dto

import com.pintailconsultingllc.cqrsspike.product.query.model.ProductReadModel
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@DisplayName("Query Response DTOs")
class QueryResponsesTest {

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

    @Nested
    @DisplayName("ProductResponse")
    inner class ProductResponseTests {

        @Test
        @DisplayName("should create response from read model")
        fun shouldCreateResponseFromReadModel() {
            val response = ProductResponse.from(testProduct)

            assertEquals(testProduct.id, response.id)
            assertEquals(testProduct.sku, response.sku)
            assertEquals(testProduct.name, response.name)
            assertEquals(testProduct.description, response.description)
            assertEquals(testProduct.priceCents, response.priceCents)
            assertEquals(testProduct.priceDisplay, response.priceDisplay)
            assertEquals(testProduct.status, response.status)
            assertEquals(testProduct.aggregateVersion, response.version)
        }

        @Test
        @DisplayName("should format price when priceDisplay is null")
        fun shouldFormatPriceWhenPriceDisplayIsNull() {
            val productWithoutPriceDisplay = testProduct.copy(priceDisplay = null)
            val response = ProductResponse.from(productWithoutPriceDisplay)

            assertEquals("$19.99", response.priceDisplay)
        }

        @Test
        @DisplayName("should format various price amounts correctly")
        fun shouldFormatVariousPriceAmountsCorrectly() {
            // Test $0.05
            val product5cents = testProduct.copy(priceCents = 5, priceDisplay = null)
            assertEquals("$0.05", ProductResponse.from(product5cents).priceDisplay)

            // Test $1.00
            val product100cents = testProduct.copy(priceCents = 100, priceDisplay = null)
            assertEquals("$1.00", ProductResponse.from(product100cents).priceDisplay)

            // Test $99.99
            val product9999cents = testProduct.copy(priceCents = 9999, priceDisplay = null)
            assertEquals("$99.99", ProductResponse.from(product9999cents).priceDisplay)

            // Test $1000.00
            val product100000cents = testProduct.copy(priceCents = 100000, priceDisplay = null)
            assertEquals("$1000.00", ProductResponse.from(product100000cents).priceDisplay)
        }
    }

    @Nested
    @DisplayName("ProductPageResponse")
    inner class ProductPageResponseTests {

        @Test
        @DisplayName("should create page response with correct metadata")
        fun shouldCreatePageResponseWithCorrectMetadata() {
            val products = listOf(testProduct)
            val page = ProductPageResponse.of(products, 0, 20, 1L)

            assertEquals(1, page.content.size)
            assertEquals(0, page.page)
            assertEquals(20, page.size)
            assertEquals(1L, page.totalElements)
            assertEquals(1, page.totalPages)
            assertTrue(page.first)
            assertTrue(page.last)
            assertFalse(page.hasNext)
            assertFalse(page.hasPrevious)
        }

        @Test
        @DisplayName("should calculate totalPages correctly")
        fun shouldCalculateTotalPagesCorrectly() {
            val products = listOf(testProduct)

            // 50 elements, 20 per page = 3 pages
            val page = ProductPageResponse.of(products, 0, 20, 50L)
            assertEquals(3, page.totalPages)

            // 40 elements, 20 per page = 2 pages
            val page2 = ProductPageResponse.of(products, 0, 20, 40L)
            assertEquals(2, page2.totalPages)

            // 0 elements = 1 page (empty)
            val page3 = ProductPageResponse.of(emptyList(), 0, 20, 0L)
            assertEquals(1, page3.totalPages)
        }

        @Test
        @DisplayName("should set hasNext and hasPrevious correctly")
        fun shouldSetHasNextAndHasPreviousCorrectly() {
            val products = listOf(testProduct)

            // First page
            val firstPage = ProductPageResponse.of(products, 0, 20, 60L)
            assertTrue(firstPage.first)
            assertFalse(firstPage.last)
            assertTrue(firstPage.hasNext)
            assertFalse(firstPage.hasPrevious)

            // Middle page
            val middlePage = ProductPageResponse.of(products, 1, 20, 60L)
            assertFalse(middlePage.first)
            assertFalse(middlePage.last)
            assertTrue(middlePage.hasNext)
            assertTrue(middlePage.hasPrevious)

            // Last page
            val lastPage = ProductPageResponse.of(products, 2, 20, 60L)
            assertFalse(lastPage.first)
            assertTrue(lastPage.last)
            assertFalse(lastPage.hasNext)
            assertTrue(lastPage.hasPrevious)
        }
    }

    @Nested
    @DisplayName("ProductCursor")
    inner class ProductCursorTests {

        @Test
        @DisplayName("should encode and decode cursor correctly")
        fun shouldEncodeAndDecodeCursorCorrectly() {
            val productId = UUID.randomUUID()
            val cursor = ProductCursor("2024-01-15T10:30:00Z", productId)

            val encoded = cursor.encode()
            assertNotNull(encoded)

            val decoded = ProductCursor.decode(encoded)
            assertNotNull(decoded)
            assertEquals("2024-01-15T10:30:00Z", decoded.value)
            assertEquals(productId, decoded.id)
        }

        @Test
        @DisplayName("should return null for invalid cursor")
        fun shouldReturnNullForInvalidCursor() {
            assertNull(ProductCursor.decode("invalid-base64"))
            assertNull(ProductCursor.decode(""))
        }

        @Test
        @DisplayName("should create cursor from product by created_at")
        fun shouldCreateCursorFromProductByCreatedAt() {
            val cursor = ProductCursor.fromProduct(testProduct, SortField.CREATED_AT)

            assertEquals(testProduct.createdAt.toString(), cursor.value)
            assertEquals(testProduct.id, cursor.id)
        }

        @Test
        @DisplayName("should create cursor from product by name")
        fun shouldCreateCursorFromProductByName() {
            val cursor = ProductCursor.fromProduct(testProduct, SortField.NAME)

            assertEquals(testProduct.name, cursor.value)
            assertEquals(testProduct.id, cursor.id)
        }

        @Test
        @DisplayName("should create cursor from product by price")
        fun shouldCreateCursorFromProductByPrice() {
            val cursor = ProductCursor.fromProduct(testProduct, SortField.PRICE)

            assertEquals(testProduct.priceCents.toString(), cursor.value)
            assertEquals(testProduct.id, cursor.id)
        }
    }

    @Nested
    @DisplayName("ProductCursorPageResponse")
    inner class ProductCursorPageResponseTests {

        @Test
        @DisplayName("should create cursor page response")
        fun shouldCreateCursorPageResponse() {
            val products = listOf(testProduct)
            val response = ProductCursorPageResponse.of(products, 10, SortField.CREATED_AT)

            assertEquals(1, response.content.size)
            assertEquals(1, response.size)
            assertFalse(response.hasNext)
            assertNull(response.nextCursor)
        }

        @Test
        @DisplayName("should indicate hasNext when more results exist")
        fun shouldIndicateHasNextWhenMoreResultsExist() {
            // Simulate fetch of 11 products (requestedSize + 1)
            val products = (1..11).map { i ->
                testProduct.copy(id = UUID.randomUUID(), name = "Product $i")
            }

            val response = ProductCursorPageResponse.of(products, 10, SortField.CREATED_AT)

            assertEquals(10, response.content.size)
            assertEquals(10, response.size)
            assertTrue(response.hasNext)
            assertNotNull(response.nextCursor)
        }

        @Test
        @DisplayName("should not include extra item in content")
        fun shouldNotIncludeExtraItemInContent() {
            val products = (1..11).map { i ->
                testProduct.copy(id = UUID.randomUUID(), name = "Product $i")
            }

            val response = ProductCursorPageResponse.of(products, 10, SortField.CREATED_AT)

            // Should only include first 10 products
            assertEquals(10, response.content.size)
            assertEquals("Product 1", response.content.first().name)
            assertEquals("Product 10", response.content.last().name)
        }
    }

    @Nested
    @DisplayName("ProductSearchResponse")
    inner class ProductSearchResponseTests {

        @Test
        @DisplayName("should create search response")
        fun shouldCreateSearchResponse() {
            val response = ProductSearchResponse(
                content = listOf(ProductResponse.from(testProduct)),
                query = "test",
                totalMatches = 1L,
                hasMore = false
            )

            assertEquals(1, response.content.size)
            assertEquals("test", response.query)
            assertEquals(1L, response.totalMatches)
            assertFalse(response.hasMore)
        }
    }
}
