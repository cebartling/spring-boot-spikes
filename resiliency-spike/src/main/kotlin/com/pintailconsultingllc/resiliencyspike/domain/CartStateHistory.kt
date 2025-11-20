package com.pintailconsultingllc.resiliencyspike.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.OffsetDateTime

/**
 * Entity representing a historical record of shopping cart state changes.
 *
 * Tracks important events in the lifecycle of a cart, including item additions/removals,
 * quantity changes, and status transitions (abandoned, converted, etc.).
 */
@Table("cart_state_history")
data class CartStateHistory(
    @Id
    val id: Long? = null,

    @Column("cart_id")
    val cartId: Long,

    @Column("event_type")
    val eventType: CartEventType,

    @Column("previous_status")
    val previousStatus: String? = null,

    @Column("new_status")
    val newStatus: String? = null,

    @Column("event_data")
    val eventData: String? = null,  // JSON stored as String for additional context

    @Column("created_at")
    val createdAt: OffsetDateTime = OffsetDateTime.now()
)

/**
 * Shopping cart event type enumeration.
 */
enum class CartEventType {
    CREATED,          // Cart was created
    ITEM_ADDED,       // Item was added to cart
    ITEM_REMOVED,     // Item was removed from cart
    ITEM_UPDATED,     // Item properties were updated
    QUANTITY_CHANGED, // Item quantity was changed
    ABANDONED,        // Cart was marked as abandoned
    CONVERTED,        // Cart was converted to an order
    EXPIRED,          // Cart expired
    RESTORED          // Cart was restored from abandoned/expired state
}
