package com.pintailconsultingllc.resiliencyspike.repository

import com.pintailconsultingllc.resiliencyspike.domain.Category
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

/**
 * Reactive repository for Category entities
 */
@Repository
interface CategoryRepository : ReactiveCrudRepository<Category, UUID> {

    /**
     * Find a category by its name
     */
    fun findByName(name: String): Mono<Category>

    /**
     * Find all active categories
     */
    fun findByIsActive(isActive: Boolean): Flux<Category>

    /**
     * Find all child categories of a parent category
     */
    fun findByParentCategoryId(parentCategoryId: UUID): Flux<Category>

    /**
     * Find all root categories (categories with no parent)
     */
    fun findByParentCategoryIdIsNull(): Flux<Category>

    /**
     * Find all active root categories
     */
    @Query("SELECT * FROM categories WHERE parent_category_id IS NULL AND is_active = true ORDER BY name ASC")
    fun findActiveRootCategories(): Flux<Category>

    /**
     * Find all active child categories of a parent
     */
    @Query("SELECT * FROM categories WHERE parent_category_id = :parentCategoryId AND is_active = true ORDER BY name ASC")
    fun findActiveChildCategories(parentCategoryId: UUID): Flux<Category>

    /**
     * Search categories by name (case-insensitive partial match)
     */
    @Query("SELECT * FROM categories WHERE LOWER(name) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    fun searchByName(searchTerm: String): Flux<Category>

    /**
     * Count child categories of a parent
     */
    fun countByParentCategoryId(parentCategoryId: UUID): Mono<Long>

    /**
     * Count active categories
     */
    fun countByIsActive(isActive: Boolean): Mono<Long>
}
