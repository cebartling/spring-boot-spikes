package com.pintailconsultingllc.cqrsspike.product.query.model

/**
 * Utility functions for formatting product data in the read model.
 * These functions are shared between production code and tests to ensure consistent behavior.
 */
object ProductFormatUtils {

    /**
     * Builds a search text field from product name and description.
     * This enables full-text search capabilities on the read model.
     *
     * @param name The product name
     * @param description The optional product description
     * @return A space-separated string containing non-null values
     */
    fun buildSearchText(name: String, description: String?): String {
        return listOfNotNull(name, description).joinToString(" ")
    }

    /**
     * Formats price in cents to a dollar display string.
     *
     * @param cents The price in cents
     * @return A formatted string like "$19.99"
     */
    fun formatPrice(cents: Int): String {
        val dollars = cents / 100
        val remainingCents = cents % 100
        return "$${dollars}.${remainingCents.toString().padStart(2, '0')}"
    }
}
