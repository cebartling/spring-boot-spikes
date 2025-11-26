package com.pintailconsultingllc.cqrsspike.product.command.model

/**
 * Represents the lifecycle status of a Product.
 *
 * State transitions:
 * - DRAFT → ACTIVE (via activate)
 * - DRAFT → DISCONTINUED (via discontinue)
 * - ACTIVE → DISCONTINUED (via discontinue)
 * - DISCONTINUED is terminal (no transitions out)
 */
enum class ProductStatus {
    /** Product is being prepared, not yet visible to customers */
    DRAFT,

    /** Product is live and available for purchase */
    ACTIVE,

    /** Product is no longer available (terminal state) */
    DISCONTINUED;

    /**
     * Checks if transition to the target status is valid.
     * @param target The desired new status
     * @return true if the transition is allowed
     */
    fun canTransitionTo(target: ProductStatus): Boolean = when (this) {
        DRAFT -> target in setOf(ACTIVE, DISCONTINUED)
        ACTIVE -> target == DISCONTINUED
        DISCONTINUED -> false
    }

    /**
     * Returns all valid target statuses from current status.
     */
    fun validTransitions(): Set<ProductStatus> = when (this) {
        DRAFT -> setOf(ACTIVE, DISCONTINUED)
        ACTIVE -> setOf(DISCONTINUED)
        DISCONTINUED -> emptySet()
    }
}
