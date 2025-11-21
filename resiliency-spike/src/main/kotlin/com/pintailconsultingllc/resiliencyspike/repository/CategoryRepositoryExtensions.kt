package com.pintailconsultingllc.resiliencyspike.repository

import com.pintailconsultingllc.resiliencyspike.domain.Category
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

/**
 * Extension methods for CategoryRepository to load relationships
 */
interface CategoryRepositoryExtensions {

    /**
     * Find a category by ID with its parent category loaded
     */
    fun findByIdWithParent(categoryId: UUID): Mono<Category>

    /**
     * Find a category by ID with its child categories loaded
     */
    fun findByIdWithChildren(categoryId: UUID): Mono<Category>

    /**
     * Find a category by ID with both parent and child categories loaded
     */
    fun findByIdWithHierarchy(categoryId: UUID): Mono<Category>

    /**
     * Find all root categories (no parent) with their children loaded
     */
    fun findRootCategoriesWithChildren(): Flux<Category>
}
