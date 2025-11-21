package com.pintailconsultingllc.resiliencyspike.repository

import com.pintailconsultingllc.resiliencyspike.domain.Category
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

/**
 * Implementation of CategoryRepositoryExtensions
 * Provides methods to load categories with their hierarchical relationships
 */
@Repository
class CategoryRepositoryExtensionsImpl(
    private val categoryRepository: CategoryRepository
) : CategoryRepositoryExtensions {

    override fun findByIdWithParent(categoryId: UUID): Mono<Category> {
        return categoryRepository.findById(categoryId)
            .flatMap { category -> loadParent(category) }
    }

    override fun findByIdWithChildren(categoryId: UUID): Mono<Category> {
        return categoryRepository.findById(categoryId)
            .flatMap { category -> loadChildren(category) }
    }

    override fun findByIdWithHierarchy(categoryId: UUID): Mono<Category> {
        return categoryRepository.findById(categoryId)
            .flatMap { category ->
                loadParent(category).flatMap { categoryWithParent ->
                    loadChildren(categoryWithParent)
                }
            }
    }

    override fun findRootCategoriesWithChildren(): Flux<Category> {
        return categoryRepository.findByParentCategoryIdIsNull()
            .flatMap { category -> loadChildren(category) }
    }

    private fun loadParent(category: Category): Mono<Category> {
        return if (category.parentCategoryId == null) {
            Mono.just(category)
        } else {
            categoryRepository.findById(category.parentCategoryId)
                .map { parent -> category.copy(parentCategory = parent) }
                .defaultIfEmpty(category)
        }
    }

    private fun loadChildren(category: Category): Mono<Category> {
        return if (category.id == null) {
            Mono.just(category)
        } else {
            categoryRepository.findByParentCategoryId(category.id)
                .collectList()
                .map { children -> category.copy(childCategories = children) }
        }
    }
}
