package com.pintailconsultingllc.cqrsspike.product.query.model

/**
 * Read-side representation of product status.
 * Mirrors the command model status for query operations.
 */
enum class ProductStatusView {
    DRAFT,
    ACTIVE,
    DISCONTINUED;

    companion object {
        fun fromString(value: String): ProductStatusView {
            return entries.find { it.name == value.uppercase() }
                ?: throw IllegalArgumentException("Unknown product status: $value")
        }
    }
}
