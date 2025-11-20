package com.pintailconsultingllc.resiliencyspike.service

import com.pintailconsultingllc.resiliencyspike.fixtures.TestFixtures
import com.pintailconsultingllc.resiliencyspike.repository.CategoryRepository
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
import java.util.*

@ExtendWith(MockitoExtension::class)
@DisplayName("CategoryService Unit Tests")
class CategoryServiceTest {

    @Mock
    private lateinit var categoryRepository: CategoryRepository

    private lateinit var categoryService: CategoryService

    @BeforeEach
    fun setUp() {
        categoryService = CategoryService(categoryRepository)
    }

    @Test
    @DisplayName("Should create a new category successfully")
    fun shouldCreateCategorySuccessfully() {
        // Given
        val category = TestFixtures.createCategory(
            id = null,
            name = "Electronics",
            description = "Electronic devices and accessories"
        )
        val savedCategory = category.copy(id = UUID.randomUUID())

        whenever(categoryRepository.save(any())).thenReturn(Mono.just(savedCategory))

        // When
        val result = categoryService.createCategory(category)

        // Then
        StepVerifier.create(result)
            .expectNextMatches { it.id != null && it.name == "Electronics" }
            .verifyComplete()

        verify(categoryRepository).save(category)
    }

    @Test
    @DisplayName("Should update an existing category successfully")
    fun shouldUpdateCategorySuccessfully() {
        // Given
        val categoryId = UUID.randomUUID()
        val category = TestFixtures.createCategory(
            id = categoryId,
            name = "Updated Category"
        )

        whenever(categoryRepository.save(any())).thenReturn(Mono.just(category))

        // When
        val result = categoryService.updateCategory(category)

        // Then
        StepVerifier.create(result)
            .expectNextMatches { it.id == categoryId && it.name == "Updated Category" }
            .verifyComplete()

        verify(categoryRepository).save(category)
    }

    @Test
    @DisplayName("Should find category by ID when category exists")
    fun shouldFindCategoryByIdWhenCategoryExists() {
        // Given
        val categoryId = UUID.randomUUID()
        val category = TestFixtures.createCategory(id = categoryId, name = "Test Category")

        whenever(categoryRepository.findById(categoryId)).thenReturn(Mono.just(category))

        // When
        val result = categoryService.findCategoryById(categoryId)

        // Then
        StepVerifier.create(result)
            .expectNext(category)
            .verifyComplete()

        verify(categoryRepository).findById(categoryId)
    }

    @Test
    @DisplayName("Should return empty Mono when category ID does not exist")
    fun shouldReturnEmptyMonoWhenCategoryIdDoesNotExist() {
        // Given
        val categoryId = UUID.randomUUID()

        whenever(categoryRepository.findById(categoryId)).thenReturn(Mono.empty())

        // When
        val result = categoryService.findCategoryById(categoryId)

        // Then
        StepVerifier.create(result)
            .verifyComplete()

        verify(categoryRepository).findById(categoryId)
    }

    @Test
    @DisplayName("Should find category by name successfully")
    fun shouldFindCategoryByNameSuccessfully() {
        // Given
        val categoryName = "Electronics"
        val category = TestFixtures.createCategory(name = categoryName)

        whenever(categoryRepository.findByName(categoryName)).thenReturn(Mono.just(category))

        // When
        val result = categoryService.findCategoryByName(categoryName)

        // Then
        StepVerifier.create(result)
            .expectNext(category)
            .verifyComplete()

        verify(categoryRepository).findByName(categoryName)
    }

    @Test
    @DisplayName("Should find all categories successfully")
    fun shouldFindAllCategoriesSuccessfully() {
        // Given
        val categories = listOf(
            TestFixtures.createCategory(name = "Electronics"),
            TestFixtures.createCategory(name = "Books"),
            TestFixtures.createCategory(name = "Clothing")
        )

        whenever(categoryRepository.findAll()).thenReturn(Flux.fromIterable(categories))

        // When
        val result = categoryService.findAllCategories()

        // Then
        StepVerifier.create(result)
            .expectNext(categories[0])
            .expectNext(categories[1])
            .expectNext(categories[2])
            .verifyComplete()

        verify(categoryRepository).findAll()
    }

    @Test
    @DisplayName("Should find all active categories")
    fun shouldFindAllActiveCategories() {
        // Given
        val activeCategories = listOf(
            TestFixtures.createCategory(name = "Electronics", isActive = true),
            TestFixtures.createCategory(name = "Books", isActive = true)
        )

        whenever(categoryRepository.findByIsActive(true)).thenReturn(Flux.fromIterable(activeCategories))

        // When
        val result = categoryService.findActiveCategories()

        // Then
        StepVerifier.create(result)
            .expectNextCount(2)
            .verifyComplete()

        verify(categoryRepository).findByIsActive(true)
    }

    @Test
    @DisplayName("Should find all root categories")
    fun shouldFindAllRootCategories() {
        // Given
        val rootCategories = listOf(
            TestFixtures.createCategory(name = "Electronics", parentCategoryId = null),
            TestFixtures.createCategory(name = "Books", parentCategoryId = null)
        )

        whenever(categoryRepository.findByParentCategoryIdIsNull())
            .thenReturn(Flux.fromIterable(rootCategories))

        // When
        val result = categoryService.findRootCategories()

        // Then
        StepVerifier.create(result)
            .expectNextCount(2)
            .verifyComplete()

        verify(categoryRepository).findByParentCategoryIdIsNull()
    }

    @Test
    @DisplayName("Should find all active root categories")
    fun shouldFindAllActiveRootCategories() {
        // Given
        val activeRootCategories = listOf(
            TestFixtures.createCategory(name = "Electronics", parentCategoryId = null, isActive = true)
        )

        whenever(categoryRepository.findActiveRootCategories())
            .thenReturn(Flux.fromIterable(activeRootCategories))

        // When
        val result = categoryService.findActiveRootCategories()

        // Then
        StepVerifier.create(result)
            .expectNextCount(1)
            .verifyComplete()

        verify(categoryRepository).findActiveRootCategories()
    }

    @Test
    @DisplayName("Should find child categories of a parent")
    fun shouldFindChildCategoriesOfParent() {
        // Given
        val parentId = UUID.randomUUID()
        val childCategories = listOf(
            TestFixtures.createCategory(name = "Laptops", parentCategoryId = parentId),
            TestFixtures.createCategory(name = "Smartphones", parentCategoryId = parentId)
        )

        whenever(categoryRepository.findByParentCategoryId(parentId))
            .thenReturn(Flux.fromIterable(childCategories))

        // When
        val result = categoryService.findChildCategories(parentId)

        // Then
        StepVerifier.create(result)
            .expectNextCount(2)
            .verifyComplete()

        verify(categoryRepository).findByParentCategoryId(parentId)
    }

    @Test
    @DisplayName("Should find active child categories of a parent")
    fun shouldFindActiveChildCategoriesOfParent() {
        // Given
        val parentId = UUID.randomUUID()
        val activeChildCategories = listOf(
            TestFixtures.createCategory(name = "Laptops", parentCategoryId = parentId, isActive = true)
        )

        whenever(categoryRepository.findActiveChildCategories(parentId))
            .thenReturn(Flux.fromIterable(activeChildCategories))

        // When
        val result = categoryService.findActiveChildCategories(parentId)

        // Then
        StepVerifier.create(result)
            .expectNextCount(1)
            .verifyComplete()

        verify(categoryRepository).findActiveChildCategories(parentId)
    }

    @Test
    @DisplayName("Should search categories by name")
    fun shouldSearchCategoriesByName() {
        // Given
        val searchTerm = "elect"
        val categories = listOf(
            TestFixtures.createCategory(name = "Electronics"),
            TestFixtures.createCategory(name = "Electrical")
        )

        whenever(categoryRepository.searchByName(searchTerm))
            .thenReturn(Flux.fromIterable(categories))

        // When
        val result = categoryService.searchCategoriesByName(searchTerm)

        // Then
        StepVerifier.create(result)
            .expectNextCount(2)
            .verifyComplete()

        verify(categoryRepository).searchByName(searchTerm)
    }

    @Test
    @DisplayName("Should get category hierarchy")
    fun shouldGetCategoryHierarchy() {
        // Given
        val rootCategories = listOf(
            TestFixtures.createCategory(name = "Electronics", parentCategoryId = null),
            TestFixtures.createCategory(name = "Books", parentCategoryId = null)
        )

        whenever(categoryRepository.findByParentCategoryIdIsNull())
            .thenReturn(Flux.fromIterable(rootCategories))

        // When
        val result = categoryService.getCategoryHierarchy()

        // Then
        StepVerifier.create(result)
            .expectNextCount(2)
            .verifyComplete()

        verify(categoryRepository).findByParentCategoryIdIsNull()
    }

    @Test
    @DisplayName("Should deactivate a category successfully")
    fun shouldDeactivateCategorySuccessfully() {
        // Given
        val categoryId = UUID.randomUUID()
        val category = TestFixtures.createCategory(id = categoryId, isActive = true)
        val deactivatedCategory = category.copy(isActive = false)

        whenever(categoryRepository.findById(categoryId)).thenReturn(Mono.just(category))
        whenever(categoryRepository.save(any())).thenReturn(Mono.just(deactivatedCategory))

        // When
        val result = categoryService.deactivateCategory(categoryId)

        // Then
        StepVerifier.create(result)
            .expectNextMatches { !it.isActive }
            .verifyComplete()

        verify(categoryRepository).findById(categoryId)
        verify(categoryRepository).save(any())
    }

    @Test
    @DisplayName("Should activate a category successfully")
    fun shouldActivateCategorySuccessfully() {
        // Given
        val categoryId = UUID.randomUUID()
        val category = TestFixtures.createCategory(id = categoryId, isActive = false)
        val activatedCategory = category.copy(isActive = true)

        whenever(categoryRepository.findById(categoryId)).thenReturn(Mono.just(category))
        whenever(categoryRepository.save(any())).thenReturn(Mono.just(activatedCategory))

        // When
        val result = categoryService.activateCategory(categoryId)

        // Then
        StepVerifier.create(result)
            .expectNextMatches { it.isActive }
            .verifyComplete()

        verify(categoryRepository).findById(categoryId)
        verify(categoryRepository).save(any())
    }

    @Test
    @DisplayName("Should delete a category successfully")
    fun shouldDeleteCategorySuccessfully() {
        // Given
        val categoryId = UUID.randomUUID()

        whenever(categoryRepository.deleteById(categoryId)).thenReturn(Mono.empty())

        // When
        val result = categoryService.deleteCategory(categoryId)

        // Then
        StepVerifier.create(result)
            .verifyComplete()

        verify(categoryRepository).deleteById(categoryId)
    }

    @Test
    @DisplayName("Should count child categories of a parent")
    fun shouldCountChildCategoriesOfParent() {
        // Given
        val parentId = UUID.randomUUID()
        val count = 3L

        whenever(categoryRepository.countByParentCategoryId(parentId)).thenReturn(Mono.just(count))

        // When
        val result = categoryService.countChildCategories(parentId)

        // Then
        StepVerifier.create(result)
            .expectNext(count)
            .verifyComplete()

        verify(categoryRepository).countByParentCategoryId(parentId)
    }

    @Test
    @DisplayName("Should count active categories")
    fun shouldCountActiveCategories() {
        // Given
        val count = 10L

        whenever(categoryRepository.countByIsActive(true)).thenReturn(Mono.just(count))

        // When
        val result = categoryService.countActiveCategories()

        // Then
        StepVerifier.create(result)
            .expectNext(count)
            .verifyComplete()

        verify(categoryRepository).countByIsActive(true)
    }

    @Test
    @DisplayName("Should return true when category has child categories")
    fun shouldReturnTrueWhenCategoryHasChildCategories() {
        // Given
        val parentId = UUID.randomUUID()

        whenever(categoryRepository.countByParentCategoryId(parentId)).thenReturn(Mono.just(2L))

        // When
        val result = categoryService.hasChildCategories(parentId)

        // Then
        StepVerifier.create(result)
            .expectNext(true)
            .verifyComplete()

        verify(categoryRepository).countByParentCategoryId(parentId)
    }

    @Test
    @DisplayName("Should return false when category has no child categories")
    fun shouldReturnFalseWhenCategoryHasNoChildCategories() {
        // Given
        val parentId = UUID.randomUUID()

        whenever(categoryRepository.countByParentCategoryId(parentId)).thenReturn(Mono.just(0L))

        // When
        val result = categoryService.hasChildCategories(parentId)

        // Then
        StepVerifier.create(result)
            .expectNext(false)
            .verifyComplete()

        verify(categoryRepository).countByParentCategoryId(parentId)
    }

    @Test
    @DisplayName("Should handle repository error when creating category")
    fun shouldHandleRepositoryErrorWhenCreatingCategory() {
        // Given
        val category = TestFixtures.createCategory()
        val error = RuntimeException("Database connection failed")

        whenever(categoryRepository.save(any())).thenReturn(Mono.error(error))

        // When
        val result = categoryService.createCategory(category)

        // Then
        StepVerifier.create(result)
            .expectError(RuntimeException::class.java)
            .verify()

        verify(categoryRepository).save(category)
    }

    @Test
    @DisplayName("Should return empty Flux when no child categories found")
    fun shouldReturnEmptyFluxWhenNoChildCategoriesFound() {
        // Given
        val parentId = UUID.randomUUID()

        whenever(categoryRepository.findByParentCategoryId(parentId)).thenReturn(Flux.empty())

        // When
        val result = categoryService.findChildCategories(parentId)

        // Then
        StepVerifier.create(result)
            .verifyComplete()

        verify(categoryRepository).findByParentCategoryId(parentId)
    }
}
