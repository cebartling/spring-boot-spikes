package com.pintailconsultingllc.resiliencyspike.domain

import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Transient
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.OffsetDateTime
import java.util.*

/**
 * Entity representing a product category.
 *
 * Categories can have a hierarchical structure with parent-child relationships.
 *
 * Note: The `parentCategory` and `childCategories` fields are transient and not persisted.
 * They can be loaded separately via custom repository methods when needed.
 */
@Table("categories")
data class Category(
    @Id
    val id: UUID? = null,

    @Column("name")
    val name: String,

    @Column("description")
    val description: String? = null,

    @Column("parent_category_id")
    val parentCategoryId: UUID? = null,

    @Column("is_active")
    val isActive: Boolean = true,

    @Column("created_at")
    val createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column("updated_at")
    val updatedAt: OffsetDateTime = OffsetDateTime.now(),

    // Transient relationships - not stored in database, loaded separately
    @Transient
    val parentCategory: Category? = null,

    @Transient
    val childCategories: List<Category> = emptyList()
)
