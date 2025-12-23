package com.pintailconsultingllc.cdcdebezium.consumer.handlers

import com.pintailconsultingllc.cdcdebezium.consumer.CdcEventHandler
import com.pintailconsultingllc.cdcdebezium.dto.OrderItemCdcEvent
import com.pintailconsultingllc.cdcdebezium.metrics.CdcMetricsService
import com.pintailconsultingllc.cdcdebezium.service.OrderMongoService
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import tools.jackson.databind.ObjectMapper

@Component
class OrderItemEventHandler(
    private val objectMapper: ObjectMapper,
    private val orderService: OrderMongoService,
    private val metricsService: CdcMetricsService
) : CdcEventHandler {

    private val logger = LoggerFactory.getLogger(javaClass)

    override val topic = "cdc.public.order_item"
    override val entityType = "order_item"

    override fun handle(record: ConsumerRecord<String, String>): Mono<Void> {
        val event = objectMapper.readValue(record.value(), OrderItemCdcEvent::class.java)
        MDC.put("order_id", event.orderId.toString())
        MDC.put("item_id", event.id.toString())
        MDC.put("product_sku", event.productSku)

        try {
            return if (event.isDelete()) {
                logger.info("Processing order item DELETE operation")
                orderService.deleteOrderItem(
                    orderId = event.orderId.toString(),
                    itemId = event.id.toString(),
                    sourceTimestamp = event.sourceTimestamp ?: System.currentTimeMillis()
                ).doOnSuccess { metricsService.recordDbDelete() }
                    .then()
            } else {
                logger.info(
                    "Processing order item UPSERT operation for productSku={}, quantity={}",
                    event.productSku, event.quantity
                )
                orderService.upsertOrderItem(event, record.offset(), record.partition())
                    .doOnSuccess { metricsService.recordDbUpsert() }
                    .then()
            }
        } finally {
            MDC.remove("order_id")
            MDC.remove("item_id")
            MDC.remove("product_sku")
        }
    }
}
