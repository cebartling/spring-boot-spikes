package com.pintailconsultingllc.cdcdebezium.document

import com.pintailconsultingllc.cdcdebezium.dto.OrderCdcEvent
import com.pintailconsultingllc.cdcdebezium.dto.OrderItemCdcEvent
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.math.BigDecimal
import java.time.Instant

@Document(collection = "orders")
@CompoundIndex(name = "idx_customer_status", def = "{'customerId': 1, 'status': 1}")
data class OrderDocument(
    @Id
    val id: String,

    @Indexed
    val customerId: String,

    @Indexed
    val status: OrderStatus,

    val totalAmount: BigDecimal,

    val items: List<OrderItemEmbedded> = emptyList(),

    val createdAt: Instant,
    val updatedAt: Instant,
    val cdcMetadata: CdcMetadata
) {
    fun isNewerThan(other: OrderDocument): Boolean =
        this.cdcMetadata.sourceTimestamp > other.cdcMetadata.sourceTimestamp

    fun withItem(item: OrderItemEmbedded): OrderDocument {
        val existingIndex = items.indexOfFirst { it.id == item.id }
        val newItems = if (existingIndex >= 0) {
            items.toMutableList().apply { this[existingIndex] = item }
        } else {
            items + item
        }
        return copy(items = newItems)
    }

    fun withoutItem(itemId: String): OrderDocument =
        copy(items = items.filter { it.id != itemId })

    companion object {
        fun fromCdcEvent(
            event: OrderCdcEvent,
            operation: CdcOperation,
            kafkaOffset: Long,
            kafkaPartition: Int,
            existingItems: List<OrderItemEmbedded> = emptyList()
        ): OrderDocument = OrderDocument(
            id = event.id.toString(),
            customerId = event.customerId.toString(),
            status = OrderStatus.fromString(event.status),
            totalAmount = event.totalAmount ?: BigDecimal.ZERO,
            items = existingItems,
            createdAt = event.createdAt ?: Instant.now(),
            updatedAt = event.updatedAt ?: Instant.now(),
            cdcMetadata = CdcMetadata(
                sourceTimestamp = event.sourceTimestamp ?: System.currentTimeMillis(),
                operation = operation,
                kafkaOffset = kafkaOffset,
                kafkaPartition = kafkaPartition
            )
        )
    }
}

data class OrderItemEmbedded(
    val id: String,
    val productSku: String,
    val productName: String,
    val quantity: Int,
    val unitPrice: BigDecimal,
    val lineTotal: BigDecimal,
    val cdcMetadata: CdcMetadata
) {
    companion object {
        fun fromCdcEvent(
            event: OrderItemCdcEvent,
            operation: CdcOperation,
            kafkaOffset: Long,
            kafkaPartition: Int
        ): OrderItemEmbedded = OrderItemEmbedded(
            id = event.id.toString(),
            productSku = event.productSku ?: "",
            productName = event.productName ?: "",
            quantity = event.quantity ?: 0,
            unitPrice = event.unitPrice ?: BigDecimal.ZERO,
            lineTotal = event.lineTotal ?: BigDecimal.ZERO,
            cdcMetadata = CdcMetadata(
                sourceTimestamp = event.sourceTimestamp ?: System.currentTimeMillis(),
                operation = operation,
                kafkaOffset = kafkaOffset,
                kafkaPartition = kafkaPartition
            )
        )
    }
}

enum class OrderStatus {
    PENDING,
    CONFIRMED,
    PROCESSING,
    SHIPPED,
    DELIVERED,
    CANCELLED;

    companion object {
        fun fromString(value: String?): OrderStatus =
            when (value?.lowercase()) {
                "pending" -> PENDING
                "confirmed" -> CONFIRMED
                "processing" -> PROCESSING
                "shipped" -> SHIPPED
                "delivered" -> DELIVERED
                "cancelled" -> CANCELLED
                else -> PENDING
            }
    }
}
