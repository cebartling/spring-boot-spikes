package com.pintailconsultingllc.resiliencyspike.controller

import com.pintailconsultingllc.resiliencyspike.domain.Product
import com.pintailconsultingllc.resiliencyspike.dto.CreateProductRequest
import com.pintailconsultingllc.resiliencyspike.dto.UpdateProductRequest
import com.pintailconsultingllc.resiliencyspike.dto.UpdateProductStockRequest
import com.pintailconsultingllc.resiliencyspike.service.ProductService
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.*

@ExtendWith(SpringExtension::class, MockitoExtension::class)
@WebFluxTest(ProductController::class)
@DisplayName("ProductController API Contract Tests")
class ProductControllerTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @MockBean
    private lateinit var productService: ProductService

    private val productId = UUID.randomUUID()
    private val categoryId = UUID.randomUUID()

    private val testProduct = Product(
        id = productId,
        sku = "WIDGET-001",
        name = "Test Widget",
        description = "A test product",
        categoryId = categoryId,
        price = BigDecimal("29.99"),
        stockQuantity = 100,
        isActive = true,
        metadata = """{"color": "blue"}""",
        createdAt = OffsetDateTime.now(),
        updatedAt = OffsetDateTime.now()
    )

    @Test
    @DisplayName("POST /api/v1/products - should create a new product")
    fun testCreateProduct() {
        val request = CreateProductRequest(
            sku = "WIDGET-001",
            name = "Test Widget",
            description = "A test product",
            categoryId = categoryId,
            price = BigDecimal("29.99"),
            stockQuantity = 100,
            metadata = """{"color": "blue"}"""
        )

        whenever(productService.createProduct(any()))
            .thenReturn(Mono.just(testProduct))

        webTestClient.post()
            .uri("/api/v1/products")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isCreated
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.id").isNotEmpty
            .jsonPath("$.sku").isEqualTo("WIDGET-001")
            .jsonPath("$.name").isEqualTo("Test Widget")
            .jsonPath("$.price").isEqualTo(29.99)

        verify(productService).createProduct(any())
    }

    @Test
    @DisplayName("GET /api/v1/products/{productId} - should get product by ID")
    fun testGetProductById() {
        whenever(productService.findProductById(productId))
            .thenReturn(Mono.just(testProduct))

        webTestClient.get()
            .uri("/api/v1/products/$productId")
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.id").isEqualTo(productId.toString())
            .jsonPath("$.sku").isEqualTo("WIDGET-001")
            .jsonPath("$.name").isEqualTo("Test Widget")

        verify(productService).findProductById(productId)
    }

    @Test
    @DisplayName("GET /api/v1/products/sku/{sku} - should get product by SKU")
    fun testGetProductBySku() {
        whenever(productService.findProductBySku("WIDGET-001"))
            .thenReturn(Mono.just(testProduct))

        webTestClient.get()
            .uri("/api/v1/products/sku/WIDGET-001")
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.sku").isEqualTo("WIDGET-001")

        verify(productService).findProductBySku("WIDGET-001")
    }

    @Test
    @DisplayName("GET /api/v1/products - should get all products")
    fun testGetAllProducts() {
        whenever(productService.findAllProducts())
            .thenReturn(Flux.just(testProduct))

        webTestClient.get()
            .uri("/api/v1/products")
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBodyList(Any::class.java)
            .hasSize(1)

        verify(productService).findAllProducts()
    }

    @Test
    @DisplayName("GET /api/v1/products?activeOnly=true - should get only active products")
    fun testGetActiveProducts() {
        whenever(productService.findActiveProducts())
            .thenReturn(Flux.just(testProduct))

        webTestClient.get()
            .uri("/api/v1/products?activeOnly=true")
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBodyList(Any::class.java)
            .hasSize(1)

        verify(productService).findActiveProducts()
    }

    @Test
    @DisplayName("GET /api/v1/products/category/{categoryId} - should get products by category")
    fun testGetProductsByCategory() {
        whenever(productService.findProductsByCategory(categoryId))
            .thenReturn(Flux.just(testProduct))

        webTestClient.get()
            .uri("/api/v1/products/category/$categoryId")
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBodyList(Any::class.java)
            .hasSize(1)

        verify(productService).findProductsByCategory(categoryId)
    }

    @Test
    @DisplayName("GET /api/v1/products/category/{categoryId}?activeOnly=true - should get active products by category")
    fun testGetActiveProductsByCategory() {
        whenever(productService.findActiveProductsByCategory(categoryId))
            .thenReturn(Flux.just(testProduct))

        webTestClient.get()
            .uri("/api/v1/products/category/$categoryId?activeOnly=true")
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBodyList(Any::class.java)
            .hasSize(1)

        verify(productService).findActiveProductsByCategory(categoryId)
    }

    @Test
    @DisplayName("GET /api/v1/products/search?searchTerm=widget - should search products by name")
    fun testSearchProducts() {
        whenever(productService.searchProductsByName("widget"))
            .thenReturn(Flux.just(testProduct))

        webTestClient.get()
            .uri("/api/v1/products/search?searchTerm=widget")
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBodyList(Any::class.java)
            .hasSize(1)

        verify(productService).searchProductsByName("widget")
    }

    @Test
    @DisplayName("GET /api/v1/products/price-range - should get products by price range")
    fun testGetProductsByPriceRange() {
        val minPrice = BigDecimal("10.00")
        val maxPrice = BigDecimal("50.00")

        whenever(productService.findProductsByPriceRange(minPrice, maxPrice))
            .thenReturn(Flux.just(testProduct))

        webTestClient.get()
            .uri("/api/v1/products/price-range?minPrice=10.00&maxPrice=50.00")
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBodyList(Any::class.java)
            .hasSize(1)

        verify(productService).findProductsByPriceRange(minPrice, maxPrice)
    }

    @Test
    @DisplayName("GET /api/v1/products/category/{categoryId}/price-range - should get products by category and price range")
    fun testGetProductsByCategoryAndPriceRange() {
        val minPrice = BigDecimal("10.00")
        val maxPrice = BigDecimal("50.00")

        whenever(productService.findProductsByCategoryAndPriceRange(categoryId, minPrice, maxPrice))
            .thenReturn(Flux.just(testProduct))

        webTestClient.get()
            .uri("/api/v1/products/category/$categoryId/price-range?minPrice=10.00&maxPrice=50.00")
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBodyList(Any::class.java)
            .hasSize(1)

        verify(productService).findProductsByCategoryAndPriceRange(categoryId, minPrice, maxPrice)
    }

    @Test
    @DisplayName("GET /api/v1/products/low-stock - should get low stock products with default threshold")
    fun testGetLowStockProductsDefaultThreshold() {
        whenever(productService.findLowStockProducts(10))
            .thenReturn(Flux.just(testProduct))

        webTestClient.get()
            .uri("/api/v1/products/low-stock")
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBodyList(Any::class.java)
            .hasSize(1)

        verify(productService).findLowStockProducts(10)
    }

    @Test
    @DisplayName("GET /api/v1/products/low-stock?threshold=20 - should get low stock products with custom threshold")
    fun testGetLowStockProductsCustomThreshold() {
        whenever(productService.findLowStockProducts(20))
            .thenReturn(Flux.just(testProduct))

        webTestClient.get()
            .uri("/api/v1/products/low-stock?threshold=20")
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBodyList(Any::class.java)
            .hasSize(1)

        verify(productService).findLowStockProducts(20)
    }

    @Test
    @DisplayName("PUT /api/v1/products/{productId} - should update a product")
    fun testUpdateProduct() {
        val request = UpdateProductRequest(
            name = "Updated Widget",
            price = BigDecimal("39.99")
        )

        val updatedProduct = testProduct.copy(
            name = "Updated Widget",
            price = BigDecimal("39.99"),
            updatedAt = OffsetDateTime.now()
        )

        whenever(productService.findProductById(productId))
            .thenReturn(Mono.just(testProduct))
        whenever(productService.updateProduct(any()))
            .thenReturn(Mono.just(updatedProduct))

        webTestClient.put()
            .uri("/api/v1/products/$productId")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.name").isEqualTo("Updated Widget")
            .jsonPath("$.price").isEqualTo(39.99)

        verify(productService).findProductById(productId)
        verify(productService).updateProduct(any())
    }

    @Test
    @DisplayName("PUT /api/v1/products/{productId}/stock - should update product stock")
    fun testUpdateProductStock() {
        val request = UpdateProductStockRequest(stockQuantity = 200)
        val updatedProduct = testProduct.copy(stockQuantity = 200)

        whenever(productService.updateProductStock(productId, 200))
            .thenReturn(Mono.just(updatedProduct))

        webTestClient.put()
            .uri("/api/v1/products/$productId/stock")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.stockQuantity").isEqualTo(200)

        verify(productService).updateProductStock(productId, 200)
    }

    @Test
    @DisplayName("POST /api/v1/products/{productId}/activate - should activate a product")
    fun testActivateProduct() {
        val activatedProduct = testProduct.copy(isActive = true)

        whenever(productService.activateProduct(productId))
            .thenReturn(Mono.just(activatedProduct))

        webTestClient.post()
            .uri("/api/v1/products/$productId/activate")
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.isActive").isEqualTo(true)

        verify(productService).activateProduct(productId)
    }

    @Test
    @DisplayName("POST /api/v1/products/{productId}/deactivate - should deactivate a product")
    fun testDeactivateProduct() {
        val deactivatedProduct = testProduct.copy(isActive = false)

        whenever(productService.deactivateProduct(productId))
            .thenReturn(Mono.just(deactivatedProduct))

        webTestClient.post()
            .uri("/api/v1/products/$productId/deactivate")
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.isActive").isEqualTo(false)

        verify(productService).deactivateProduct(productId)
    }

    @Test
    @DisplayName("DELETE /api/v1/products/{productId} - should delete a product")
    fun testDeleteProduct() {
        whenever(productService.deleteProduct(productId))
            .thenReturn(Mono.empty())

        webTestClient.delete()
            .uri("/api/v1/products/$productId")
            .exchange()
            .expectStatus().isNoContent

        verify(productService).deleteProduct(productId)
    }

    @Test
    @DisplayName("GET /api/v1/products/category/{categoryId}/count - should count products by category")
    fun testCountProductsByCategory() {
        whenever(productService.countProductsByCategory(categoryId))
            .thenReturn(Mono.just(42L))

        webTestClient.get()
            .uri("/api/v1/products/category/$categoryId/count")
            .exchange()
            .expectStatus().isOk
            .expectBody(Long::class.java)
            .isEqualTo(42L)

        verify(productService).countProductsByCategory(categoryId)
    }

    @Test
    @DisplayName("GET /api/v1/products/count/active - should count active products")
    fun testCountActiveProducts() {
        whenever(productService.countActiveProducts())
            .thenReturn(Mono.just(100L))

        webTestClient.get()
            .uri("/api/v1/products/count/active")
            .exchange()
            .expectStatus().isOk
            .expectBody(Long::class.java)
            .isEqualTo(100L)

        verify(productService).countActiveProducts()
    }
}
