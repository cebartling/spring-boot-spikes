package com.pintailconsultingllc.resiliencyspike.service

import com.pintailconsultingllc.resiliencyspike.domain.Category
import com.pintailconsultingllc.resiliencyspike.repository.CategoryRepositoryExtensionsImpl
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

/**
 * Service extensions for working with categories and their hierarchical relationships
 */
@Service
class CategoryServiceExtensions(
    private val categoryRepositoryExtensions: CategoryRepositoryExtensionsImpl
) {

    /**
     * Find a category with its parent category loaded
     */
    fun findCategoryWithParent(categoryId: UUID): Mono<Category> {
        return categoryRepositoryExtensions.findByIdWithParent(categoryId)
    }

    /**
     * Find a category with its child categories loaded
     */
    fun findCategoryWithChildren(categoryId: UUID): Mono<Category> {
        return categoryRepositoryExtensions.findByIdWithChildren(categoryId)
    }

    /**
     * Find a category with both parent and children loaded
     */
    fun findCategoryWithHierarchy(categoryId: UUID): Mono<Category> {
        return categoryRepositoryExtensions.findByIdWithHierarchy(categoryId)
    }

    /**
     * Find all root categories (no parent) with their direct children loaded
     */
    fun findRootCategoriesWithChildren(): Flux<Category> {
        return categoryRepositoryExtensions.findRootCategoriesWithChildren()
    }

    /**
     * Build full category tree starting from root categories
     */
    fun buildCategoryTree(): Flux<Category> {
        return findRootCategoriesWithChildren()
    }
}
