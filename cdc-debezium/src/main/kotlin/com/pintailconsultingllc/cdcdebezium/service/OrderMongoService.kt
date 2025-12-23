package com.pintailconsultingllc.cdcdebezium.service

import com.pintailconsultingllc.cdcdebezium.document.CdcOperation
import com.pintailconsultingllc.cdcdebezium.document.OrderDocument
import com.pintailconsultingllc.cdcdebezium.document.OrderItemEmbedded
import com.pintailconsultingllc.cdcdebezium.dto.OrderCdcEvent
import com.pintailconsultingllc.cdcdebezium.dto.OrderItemCdcEvent
import com.pintailconsultingllc.cdcdebezium.repository.OrderMongoRepository
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.Tracer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

@Service
class OrderMongoService(
    private val orderRepository: OrderMongoRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    private val tracer: Tracer by lazy {
        GlobalOpenTelemetry.getTracer("cdc-mongodb-service", "1.0.0")
    }

    fun upsertOrder(
        event: OrderCdcEvent,
        kafkaOffset: Long,
        kafkaPartition: Int
    ): Mono<OrderDocument> {
        val span = tracer.spanBuilder("mongodb.order.upsert")
            .setAttribute("db.operation", "upsert")
            .setAttribute("order.id", event.id.toString())
            .setAttribute("customer.id", event.customerId.toString())
            .startSpan()

        val document = OrderDocument.fromCdcEvent(
            event = event,
            operation = CdcOperation.UPDATE,
            kafkaOffset = kafkaOffset,
            kafkaPartition = kafkaPartition
        )

        return orderRepository.findById(event.id.toString())
            .flatMap { existing ->
                if (document.isNewerThan(existing)) {
                    logger.debug(
                        "Updating order: id={}, customerId={}",
                        event.id,
                        event.customerId
                    )
                    span.setAttribute("db.operation.result", "updated")
                    // Preserve existing items when updating order
                    orderRepository.save(document.copy(items = existing.items))
                } else {
                    logger.debug(
                        "Skipping stale update for order: id={}",
                        event.id
                    )
                    span.setAttribute("db.operation.result", "skipped_stale")
                    Mono.just(existing)
                }
            }
            .switchIfEmpty(
                Mono.defer {
                    logger.debug("Inserting new order: id={}, customerId={}", event.id, event.customerId)
                    span.setAttribute("db.operation.result", "inserted")
                    orderRepository.save(
                        document.copy(
                            cdcMetadata = document.cdcMetadata.copy(operation = CdcOperation.INSERT)
                        )
                    )
                }
            )
            .doFinally { span.end() }
    }

    fun deleteOrder(
        id: String,
        sourceTimestamp: Long
    ): Mono<Void> {
        val span = tracer.spanBuilder("mongodb.order.delete")
            .setAttribute("db.operation", "delete")
            .setAttribute("order.id", id)
            .startSpan()

        return orderRepository.findById(id)
            .flatMap { existing ->
                if (sourceTimestamp >= existing.cdcMetadata.sourceTimestamp) {
                    logger.debug("Deleting order: id={}", id)
                    span.setAttribute("db.operation.result", "deleted")
                    orderRepository.deleteById(id)
                } else {
                    logger.debug(
                        "Skipping stale delete for order: id={}, sourceTs={} < existingTs={}",
                        id, sourceTimestamp, existing.cdcMetadata.sourceTimestamp
                    )
                    span.setAttribute("db.operation.result", "skipped_stale")
                    Mono.empty()
                }
            }
            .switchIfEmpty(
                Mono.defer {
                    logger.debug("Order already deleted or never existed: id={}", id)
                    span.setAttribute("db.operation.result", "not_found")
                    Mono.empty()
                }
            )
            .doFinally { span.end() }
    }

    /**
     * Handle order item changes by embedding in parent order document.
     * This denormalization optimizes read performance.
     */
    fun upsertOrderItem(
        event: OrderItemCdcEvent,
        kafkaOffset: Long,
        kafkaPartition: Int
    ): Mono<OrderDocument> {
        val span = tracer.spanBuilder("mongodb.order.item.upsert")
            .setAttribute("db.operation", "upsert_item")
            .setAttribute("order.id", event.orderId.toString())
            .setAttribute("item.id", event.id.toString())
            .startSpan()

        val itemEmbedded = OrderItemEmbedded.fromCdcEvent(
            event = event,
            operation = CdcOperation.UPDATE,
            kafkaOffset = kafkaOffset,
            kafkaPartition = kafkaPartition
        )

        return orderRepository.findById(event.orderId.toString())
            .flatMap { order ->
                val existingItem = order.items.find { it.id == event.id.toString() }

                // Only update if this event is newer
                val shouldUpdate = existingItem == null ||
                    itemEmbedded.cdcMetadata.sourceTimestamp > existingItem.cdcMetadata.sourceTimestamp

                if (shouldUpdate) {
                    logger.debug("Updating order item: orderId={}, itemId={}", event.orderId, event.id)
                    span.setAttribute("db.operation.result", if (existingItem == null) "inserted" else "updated")
                    orderRepository.save(order.withItem(itemEmbedded))
                } else {
                    logger.debug("Skipping stale item update: itemId={}", event.id)
                    span.setAttribute("db.operation.result", "skipped_stale")
                    Mono.just(order)
                }
            }
            .switchIfEmpty(
                Mono.defer {
                    // Order doesn't exist yet - this can happen with out-of-order events
                    logger.warn("Order not found for item: orderId={}, itemId={}", event.orderId, event.id)
                    span.setAttribute("db.operation.result", "order_not_found")
                    Mono.empty()
                }
            )
            .doFinally { span.end() }
    }

    fun deleteOrderItem(
        orderId: String,
        itemId: String,
        sourceTimestamp: Long
    ): Mono<OrderDocument> {
        val span = tracer.spanBuilder("mongodb.order.item.delete")
            .setAttribute("db.operation", "delete_item")
            .setAttribute("order.id", orderId)
            .setAttribute("item.id", itemId)
            .startSpan()

        return orderRepository.findById(orderId)
            .flatMap { order ->
                val existingItem = order.items.find { it.id == itemId }

                if (existingItem != null && sourceTimestamp >= existingItem.cdcMetadata.sourceTimestamp) {
                    logger.debug("Removing order item: orderId={}, itemId={}", orderId, itemId)
                    span.setAttribute("db.operation.result", "deleted")
                    orderRepository.save(order.withoutItem(itemId))
                } else {
                    logger.debug(
                        "Skipping stale item delete: orderId={}, itemId={}",
                        orderId, itemId
                    )
                    span.setAttribute("db.operation.result", "skipped_stale")
                    Mono.just(order)
                }
            }
            .switchIfEmpty(
                Mono.defer {
                    logger.debug("Order not found for item delete: orderId={}", orderId)
                    span.setAttribute("db.operation.result", "order_not_found")
                    Mono.empty()
                }
            )
            .doFinally { span.end() }
    }
}
