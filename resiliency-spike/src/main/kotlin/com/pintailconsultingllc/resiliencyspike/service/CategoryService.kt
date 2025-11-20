package com.pintailconsultingllc.resiliencyspike.service

import com.pintailconsultingllc.resiliencyspike.domain.Category
import com.pintailconsultingllc.resiliencyspike.repository.CategoryRepository
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

/**
 * Service for managing product categories
 * Demonstrates reactive database operations with R2DBC and hierarchical data
 */
@Service
class CategoryService(
    private val categoryRepository: CategoryRepository
) {

    /**
     * Create a new category
     */
    fun createCategory(category: Category): Mono<Category> {
        return categoryRepository.save(category)
    }

    /**
     * Update an existing category
     */
    fun updateCategory(category: Category): Mono<Category> {
        return categoryRepository.save(category)
    }

    /**
     * Find a category by ID
     */
    fun findCategoryById(id: UUID): Mono<Category> {
        return categoryRepository.findById(id)
    }

    /**
     * Find a category by name
     */
    fun findCategoryByName(name: String): Mono<Category> {
        return categoryRepository.findByName(name)
    }

    /**
     * Find all categories
     */
    fun findAllCategories(): Flux<Category> {
        return categoryRepository.findAll()
    }

    /**
     * Find all active categories
     */
    fun findActiveCategories(): Flux<Category> {
        return categoryRepository.findByIsActive(true)
    }

    /**
     * Find all root categories (top-level categories with no parent)
     */
    fun findRootCategories(): Flux<Category> {
        return categoryRepository.findByParentCategoryIdIsNull()
    }

    /**
     * Find all active root categories
     */
    fun findActiveRootCategories(): Flux<Category> {
        return categoryRepository.findActiveRootCategories()
    }

    /**
     * Find child categories of a parent category
     */
    fun findChildCategories(parentCategoryId: UUID): Flux<Category> {
        return categoryRepository.findByParentCategoryId(parentCategoryId)
    }

    /**
     * Find active child categories of a parent category
     */
    fun findActiveChildCategories(parentCategoryId: UUID): Flux<Category> {
        return categoryRepository.findActiveChildCategories(parentCategoryId)
    }

    /**
     * Search categories by name
     */
    fun searchCategoriesByName(searchTerm: String): Flux<Category> {
        return categoryRepository.searchByName(searchTerm)
    }

    /**
     * Get the full category hierarchy starting from root categories
     */
    fun getCategoryHierarchy(): Flux<Category> {
        return categoryRepository.findByParentCategoryIdIsNull()
    }

    /**
     * Deactivate a category (soft delete)
     */
    fun deactivateCategory(categoryId: UUID): Mono<Category> {
        return categoryRepository.findById(categoryId)
            .flatMap { category ->
                categoryRepository.save(category.copy(isActive = false))
            }
    }

    /**
     * Activate a category
     */
    fun activateCategory(categoryId: UUID): Mono<Category> {
        return categoryRepository.findById(categoryId)
            .flatMap { category ->
                categoryRepository.save(category.copy(isActive = true))
            }
    }

    /**
     * Delete a category permanently
     * Note: This will fail if there are products associated with this category
     * due to foreign key constraint (ON DELETE RESTRICT)
     */
    fun deleteCategory(categoryId: UUID): Mono<Void> {
        return categoryRepository.deleteById(categoryId)
    }

    /**
     * Count child categories of a parent
     */
    fun countChildCategories(parentCategoryId: UUID): Mono<Long> {
        return categoryRepository.countByParentCategoryId(parentCategoryId)
    }

    /**
     * Count active categories
     */
    fun countActiveCategories(): Mono<Long> {
        return categoryRepository.countByIsActive(true)
    }

    /**
     * Check if a category has child categories
     */
    fun hasChildCategories(categoryId: UUID): Mono<Boolean> {
        return categoryRepository.countByParentCategoryId(categoryId)
            .map { count -> count > 0 }
    }
}
