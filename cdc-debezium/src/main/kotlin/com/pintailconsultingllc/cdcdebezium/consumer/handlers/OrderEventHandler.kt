package com.pintailconsultingllc.cdcdebezium.consumer.handlers

import com.pintailconsultingllc.cdcdebezium.consumer.CdcEventHandler
import com.pintailconsultingllc.cdcdebezium.dto.OrderCdcEvent
import com.pintailconsultingllc.cdcdebezium.metrics.CdcMetricsService
import com.pintailconsultingllc.cdcdebezium.service.OrderMongoService
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import tools.jackson.databind.ObjectMapper

@Component
class OrderEventHandler(
    private val objectMapper: ObjectMapper,
    private val orderService: OrderMongoService,
    private val metricsService: CdcMetricsService
) : CdcEventHandler {

    private val logger = LoggerFactory.getLogger(javaClass)

    override val topic = "cdc.public.orders"
    override val entityType = "order"

    override fun handle(record: ConsumerRecord<String, String>): Mono<Void> {
        val event = objectMapper.readValue(record.value(), OrderCdcEvent::class.java)
        MDC.put("order_id", event.id.toString())
        MDC.put("customer_id", event.customerId.toString())

        try {
            return if (event.isDelete()) {
                logger.info("Processing order DELETE operation")
                orderService.deleteOrder(
                    id = event.id.toString(),
                    sourceTimestamp = event.sourceTimestamp ?: System.currentTimeMillis()
                ).doOnSuccess { metricsService.recordDbDelete() }
            } else {
                logger.info(
                    "Processing order UPSERT operation for status={}, totalAmount={}",
                    event.status, event.totalAmount
                )
                orderService.upsertOrder(event, record.offset(), record.partition())
                    .doOnSuccess { metricsService.recordDbUpsert() }
                    .then()
            }
        } finally {
            MDC.remove("order_id")
            MDC.remove("customer_id")
        }
    }
}
