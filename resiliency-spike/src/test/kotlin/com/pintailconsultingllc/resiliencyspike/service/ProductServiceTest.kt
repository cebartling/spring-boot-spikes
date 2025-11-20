package com.pintailconsultingllc.resiliencyspike.service

import com.pintailconsultingllc.resiliencyspike.fixtures.TestFixtures
import com.pintailconsultingllc.resiliencyspike.repository.ProductRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
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
import java.math.BigDecimal
import java.util.*

@ExtendWith(MockitoExtension::class)
@DisplayName("ProductService Unit Tests")
class ProductServiceTest {

    @Mock
    private lateinit var productRepository: ProductRepository

    private lateinit var productService: ProductService

    private lateinit var testCategoryId: UUID

    @BeforeEach
    fun setUp() {
        productService = ProductService(productRepository)
        testCategoryId = UUID.randomUUID()
    }

    @Test
    @DisplayName("Should create a new product successfully")
    fun shouldCreateProductSuccessfully() {
        // Given
        val product = TestFixtures.createProduct(
            id = null,
            sku = "LAPTOP-001",
            name = "Gaming Laptop",
            categoryId = testCategoryId
        )
        val savedProduct = product.copy(id = UUID.randomUUID())

        whenever(productRepository.save(any())).thenReturn(Mono.just(savedProduct))

        // When
        val result = productService.createProduct(product)

        // Then
        StepVerifier.create(result)
            .expectNextMatches { it.id != null && it.sku == "LAPTOP-001" }
            .verifyComplete()

        verify(productRepository).save(product)
    }

    @Test
    @DisplayName("Should update an existing product successfully")
    fun shouldUpdateProductSuccessfully() {
        // Given
        val productId = UUID.randomUUID()
        val product = TestFixtures.createProduct(
            id = productId,
            name = "Updated Product Name",
            price = BigDecimal("199.99")
        )

        whenever(productRepository.save(any())).thenReturn(Mono.just(product))

        // When
        val result = productService.updateProduct(product)

        // Then
        StepVerifier.create(result)
            .expectNextMatches { it.id == productId && it.name == "Updated Product Name" }
            .verifyComplete()

        verify(productRepository).save(product)
    }

    @Test
    @DisplayName("Should find product by ID when product exists")
    fun shouldFindProductByIdWhenProductExists() {
        // Given
        val productId = UUID.randomUUID()
        val product = TestFixtures.createProduct(id = productId, name = "Test Product")

        whenever(productRepository.findById(productId)).thenReturn(Mono.just(product))

        // When
        val result = productService.findProductById(productId)

        // Then
        StepVerifier.create(result)
            .expectNext(product)
            .verifyComplete()

        verify(productRepository).findById(productId)
    }

    @Test
    @DisplayName("Should return empty Mono when product ID does not exist")
    fun shouldReturnEmptyMonoWhenProductIdDoesNotExist() {
        // Given
        val productId = UUID.randomUUID()

        whenever(productRepository.findById(productId)).thenReturn(Mono.empty())

        // When
        val result = productService.findProductById(productId)

        // Then
        StepVerifier.create(result)
            .verifyComplete()

        verify(productRepository).findById(productId)
    }

    @Test
    @DisplayName("Should find product by SKU successfully")
    fun shouldFindProductBySkuSuccessfully() {
        // Given
        val sku = "LAPTOP-001"
        val product = TestFixtures.createProduct(sku = sku)

        whenever(productRepository.findBySku(sku)).thenReturn(Mono.just(product))

        // When
        val result = productService.findProductBySku(sku)

        // Then
        StepVerifier.create(result)
            .expectNext(product)
            .verifyComplete()

        verify(productRepository).findBySku(sku)
    }

    @Test
    @DisplayName("Should find all products successfully")
    fun shouldFindAllProductsSuccessfully() {
        // Given
        val products = listOf(
            TestFixtures.createProduct(sku = "SKU-001", name = "Product 1"),
            TestFixtures.createProduct(sku = "SKU-002", name = "Product 2"),
            TestFixtures.createProduct(sku = "SKU-003", name = "Product 3")
        )

        whenever(productRepository.findAll()).thenReturn(Flux.fromIterable(products))

        // When
        val result = productService.findAllProducts()

        // Then
        StepVerifier.create(result)
            .expectNext(products[0])
            .expectNext(products[1])
            .expectNext(products[2])
            .verifyComplete()

        verify(productRepository).findAll()
    }

    @Test
    @DisplayName("Should find all active products")
    fun shouldFindAllActiveProducts() {
        // Given
        val activeProducts = listOf(
            TestFixtures.createProduct(sku = "SKU-001", isActive = true),
            TestFixtures.createProduct(sku = "SKU-002", isActive = true)
        )

        whenever(productRepository.findByIsActive(true)).thenReturn(Flux.fromIterable(activeProducts))

        // When
        val result = productService.findActiveProducts()

        // Then
        StepVerifier.create(result)
            .expectNextCount(2)
            .verifyComplete()

        verify(productRepository).findByIsActive(true)
    }

    @Test
    @DisplayName("Should find products by category")
    fun shouldFindProductsByCategory() {
        // Given
        val products = listOf(
            TestFixtures.createProduct(categoryId = testCategoryId, name = "Product 1"),
            TestFixtures.createProduct(categoryId = testCategoryId, name = "Product 2")
        )

        whenever(productRepository.findByCategoryId(testCategoryId))
            .thenReturn(Flux.fromIterable(products))

        // When
        val result = productService.findProductsByCategory(testCategoryId)

        // Then
        StepVerifier.create(result)
            .expectNextCount(2)
            .verifyComplete()

        verify(productRepository).findByCategoryId(testCategoryId)
    }

    @Test
    @DisplayName("Should find active products by category")
    fun shouldFindActiveProductsByCategory() {
        // Given
        val products = listOf(
            TestFixtures.createProduct(categoryId = testCategoryId, isActive = true)
        )

        whenever(productRepository.findByCategoryIdAndIsActive(testCategoryId, true))
            .thenReturn(Flux.fromIterable(products))

        // When
        val result = productService.findActiveProductsByCategory(testCategoryId)

        // Then
        StepVerifier.create(result)
            .expectNextCount(1)
            .verifyComplete()

        verify(productRepository).findByCategoryIdAndIsActive(testCategoryId, true)
    }

    @Test
    @DisplayName("Should search products by name")
    fun shouldSearchProductsByName() {
        // Given
        val searchTerm = "laptop"
        val products = listOf(
            TestFixtures.createProduct(name = "Gaming Laptop"),
            TestFixtures.createProduct(name = "Business Laptop")
        )

        whenever(productRepository.searchByName(searchTerm)).thenReturn(Flux.fromIterable(products))

        // When
        val result = productService.searchProductsByName(searchTerm)

        // Then
        StepVerifier.create(result)
            .expectNextCount(2)
            .verifyComplete()

        verify(productRepository).searchByName(searchTerm)
    }

    @Test
    @DisplayName("Should find products by price range")
    fun shouldFindProductsByPriceRange() {
        // Given
        val minPrice = BigDecimal("100.00")
        val maxPrice = BigDecimal("500.00")
        val products = listOf(
            TestFixtures.createProduct(price = BigDecimal("199.99")),
            TestFixtures.createProduct(price = BigDecimal("299.99"))
        )

        whenever(productRepository.findByPriceRange(minPrice, maxPrice))
            .thenReturn(Flux.fromIterable(products))

        // When
        val result = productService.findProductsByPriceRange(minPrice, maxPrice)

        // Then
        StepVerifier.create(result)
            .expectNextCount(2)
            .verifyComplete()

        verify(productRepository).findByPriceRange(minPrice, maxPrice)
    }

    @Test
    @DisplayName("Should find products by category and price range")
    fun shouldFindProductsByCategoryAndPriceRange() {
        // Given
        val minPrice = BigDecimal("100.00")
        val maxPrice = BigDecimal("500.00")
        val products = listOf(
            TestFixtures.createProduct(categoryId = testCategoryId, price = BigDecimal("299.99"))
        )

        whenever(productRepository.findByCategoryAndPriceRange(testCategoryId, minPrice, maxPrice))
            .thenReturn(Flux.fromIterable(products))

        // When
        val result = productService.findProductsByCategoryAndPriceRange(testCategoryId, minPrice, maxPrice)

        // Then
        StepVerifier.create(result)
            .expectNextCount(1)
            .verifyComplete()

        verify(productRepository).findByCategoryAndPriceRange(testCategoryId, minPrice, maxPrice)
    }

    @Test
    @DisplayName("Should find low stock products with default threshold")
    fun shouldFindLowStockProductsWithDefaultThreshold() {
        // Given
        val lowStockProducts = listOf(
            TestFixtures.createProduct(stockQuantity = 5),
            TestFixtures.createProduct(stockQuantity = 3)
        )

        whenever(productRepository.findLowStockProducts(10))
            .thenReturn(Flux.fromIterable(lowStockProducts))

        // When
        val result = productService.findLowStockProducts()

        // Then
        StepVerifier.create(result)
            .expectNextCount(2)
            .verifyComplete()

        verify(productRepository).findLowStockProducts(10)
    }

    @Test
    @DisplayName("Should find low stock products with custom threshold")
    fun shouldFindLowStockProductsWithCustomThreshold() {
        // Given
        val threshold = 20
        val lowStockProducts = listOf(
            TestFixtures.createProduct(stockQuantity = 15)
        )

        whenever(productRepository.findLowStockProducts(threshold))
            .thenReturn(Flux.fromIterable(lowStockProducts))

        // When
        val result = productService.findLowStockProducts(threshold)

        // Then
        StepVerifier.create(result)
            .expectNextCount(1)
            .verifyComplete()

        verify(productRepository).findLowStockProducts(threshold)
    }

    @Test
    @DisplayName("Should update product stock quantity successfully")
    fun shouldUpdateProductStockQuantitySuccessfully() {
        // Given
        val productId = UUID.randomUUID()
        val product = TestFixtures.createProduct(id = productId, stockQuantity = 50)
        val updatedProduct = product.copy(stockQuantity = 75)

        whenever(productRepository.findById(productId)).thenReturn(Mono.just(product))
        whenever(productRepository.save(any())).thenReturn(Mono.just(updatedProduct))

        // When
        val result = productService.updateProductStock(productId, 75)

        // Then
        StepVerifier.create(result)
            .expectNextMatches { it.stockQuantity == 75 }
            .verifyComplete()

        verify(productRepository).findById(productId)
        verify(productRepository).save(any())
    }

    @Test
    @DisplayName("Should deactivate a product successfully")
    fun shouldDeactivateProductSuccessfully() {
        // Given
        val productId = UUID.randomUUID()
        val product = TestFixtures.createProduct(id = productId, isActive = true)
        val deactivatedProduct = product.copy(isActive = false)

        whenever(productRepository.findById(productId)).thenReturn(Mono.just(product))
        whenever(productRepository.save(any())).thenReturn(Mono.just(deactivatedProduct))

        // When
        val result = productService.deactivateProduct(productId)

        // Then
        StepVerifier.create(result)
            .expectNextMatches { !it.isActive }
            .verifyComplete()

        verify(productRepository).findById(productId)
        verify(productRepository).save(any())
    }

    @Test
    @DisplayName("Should activate a product successfully")
    fun shouldActivateProductSuccessfully() {
        // Given
        val productId = UUID.randomUUID()
        val product = TestFixtures.createProduct(id = productId, isActive = false)
        val activatedProduct = product.copy(isActive = true)

        whenever(productRepository.findById(productId)).thenReturn(Mono.just(product))
        whenever(productRepository.save(any())).thenReturn(Mono.just(activatedProduct))

        // When
        val result = productService.activateProduct(productId)

        // Then
        StepVerifier.create(result)
            .expectNextMatches { it.isActive }
            .verifyComplete()

        verify(productRepository).findById(productId)
        verify(productRepository).save(any())
    }

    @Test
    @DisplayName("Should delete a product successfully")
    fun shouldDeleteProductSuccessfully() {
        // Given
        val productId = UUID.randomUUID()

        whenever(productRepository.deleteById(productId)).thenReturn(Mono.empty())

        // When
        val result = productService.deleteProduct(productId)

        // Then
        StepVerifier.create(result)
            .verifyComplete()

        verify(productRepository).deleteById(productId)
    }

    @Test
    @DisplayName("Should count products by category")
    fun shouldCountProductsByCategory() {
        // Given
        val count = 5L

        whenever(productRepository.countByCategoryId(testCategoryId)).thenReturn(Mono.just(count))

        // When
        val result = productService.countProductsByCategory(testCategoryId)

        // Then
        StepVerifier.create(result)
            .expectNext(count)
            .verifyComplete()

        verify(productRepository).countByCategoryId(testCategoryId)
    }

    @Test
    @DisplayName("Should count active products")
    fun shouldCountActiveProducts() {
        // Given
        val count = 10L

        whenever(productRepository.countByIsActive(true)).thenReturn(Mono.just(count))

        // When
        val result = productService.countActiveProducts()

        // Then
        StepVerifier.create(result)
            .expectNext(count)
            .verifyComplete()

        verify(productRepository).countByIsActive(true)
    }

    @Test
    @DisplayName("Should handle repository error when creating product")
    fun shouldHandleRepositoryErrorWhenCreatingProduct() {
        // Given
        val product = TestFixtures.createProduct()
        val error = RuntimeException("Database connection failed")

        whenever(productRepository.save(any())).thenReturn(Mono.error(error))

        // When
        val result = productService.createProduct(product)

        // Then
        StepVerifier.create(result)
            .expectError(RuntimeException::class.java)
            .verify()

        verify(productRepository).save(product)
    }

    @Test
    @DisplayName("Should return empty Flux when no products found by category")
    fun shouldReturnEmptyFluxWhenNoProductsFoundByCategory() {
        // Given
        whenever(productRepository.findByCategoryId(testCategoryId)).thenReturn(Flux.empty())

        // When
        val result = productService.findProductsByCategory(testCategoryId)

        // Then
        StepVerifier.create(result)
            .verifyComplete()

        verify(productRepository).findByCategoryId(testCategoryId)
    }
}
