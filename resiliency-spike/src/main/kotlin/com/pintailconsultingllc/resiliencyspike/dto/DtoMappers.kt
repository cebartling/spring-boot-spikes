package com.pintailconsultingllc.resiliencyspike.dto

import com.pintailconsultingllc.resiliencyspike.domain.CartItem
import com.pintailconsultingllc.resiliencyspike.domain.CartStateHistory
import com.pintailconsultingllc.resiliencyspike.domain.Product
import com.pintailconsultingllc.resiliencyspike.domain.ShoppingCart

/**
 * Extension functions to map domain entities to DTOs
 */

fun ShoppingCart.toResponse(includeItems: Boolean = false): ShoppingCartResponse {
    return ShoppingCartResponse(
        id = this.id!!,
        cartUuid = this.cartUuid,
        userId = this.userId,
        sessionId = this.sessionId,
        status = this.status,
        currencyCode = this.currencyCode,
        subtotalCents = this.subtotalCents,
        taxAmountCents = this.taxAmountCents,
        discountAmountCents = this.discountAmountCents,
        totalAmountCents = this.totalAmountCents,
        itemCount = this.itemCount,
        metadata = this.metadata,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
        expiresAt = this.expiresAt,
        convertedAt = this.convertedAt,
        items = if (includeItems) this.items.map { it.toResponse() } else null
    )
}

fun CartItem.toResponse(): CartItemResponse {
    return CartItemResponse(
        id = this.id!!,
        cartId = this.cartId,
        productId = this.productId,
        sku = this.sku,
        productName = this.productName,
        quantity = this.quantity,
        unitPriceCents = this.unitPriceCents,
        lineTotalCents = this.lineTotalCents,
        discountAmountCents = this.discountAmountCents,
        metadata = this.metadata,
        addedAt = this.addedAt,
        updatedAt = this.updatedAt
    )
}

fun CartStateHistory.toResponse(): CartStateHistoryResponse {
    return CartStateHistoryResponse(
        id = this.id!!,
        cartId = this.cartId,
        eventType = this.eventType,
        previousStatus = this.previousStatus,
        newStatus = this.newStatus,
        eventData = this.eventData,
        createdAt = this.createdAt
    )
}

fun Product.toResponse(): ProductResponse {
    return ProductResponse(
        id = this.id!!,
        sku = this.sku,
        name = this.name,
        description = this.description,
        categoryId = this.categoryId,
        price = this.price,
        stockQuantity = this.stockQuantity,
        isActive = this.isActive,
        metadata = this.metadata,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt
    )
}
