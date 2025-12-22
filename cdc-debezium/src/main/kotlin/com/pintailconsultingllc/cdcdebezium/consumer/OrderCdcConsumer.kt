package com.pintailconsultingllc.cdcdebezium.consumer

import com.pintailconsultingllc.cdcdebezium.dto.OrderCdcEvent
import com.pintailconsultingllc.cdcdebezium.metrics.CdcMetricsService
import com.pintailconsultingllc.cdcdebezium.service.OrderMongoService
import com.pintailconsultingllc.cdcdebezium.tracing.CdcTracingService
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.time.Instant

@Component
class OrderCdcConsumer(
    private val objectMapper: ObjectMapper,
    private val orderMongoService: OrderMongoService,
    private val tracingService: CdcTracingService,
    private val metricsService: CdcMetricsService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = ["cdc.public.orders"],
        groupId = "cdc-consumer-group"
    )
    fun consume(
        record: ConsumerRecord<String, String?>,
        acknowledgment: Acknowledgment
    ) {
        val span = tracingService.startSpan(record, "cdc-consumer-group")
        val startTime = Instant.now()

        try {
            span.makeCurrent().use {
                MDC.put("kafka_topic", record.topic())
                MDC.put("kafka_partition", record.partition().toString())
                MDC.put("kafka_offset", record.offset().toString())
                MDC.put("message_key", record.key()?.take(36) ?: "null")

                try {
                    val value = record.value()

                    logger.info("Received order CDC event")

                    val (operation, outcome) = when {
                        value == null -> {
                            logger.info("Processing order tombstone message")
                            tracingService.setDbOperation(span, CdcTracingService.DbOperation.IGNORE)
                            "ignore" to "success"
                        }
                        else -> {
                            val event = objectMapper.readValue(value, OrderCdcEvent::class.java)
                            MDC.put("order_id", event.id.toString())
                            MDC.put("customer_id", event.customerId.toString())
                            processEvent(event, record.offset(), record.partition())
                        }
                    }

                    MDC.put("processing_outcome", outcome)
                    MDC.put("db_operation", operation)

                    metricsService.recordMessageProcessed(record.topic(), record.partition(), operation)
                    metricsService.recordProcessingLatency(startTime, record.topic(), record.partition())

                    logger.info("Order CDC event processed successfully")

                    acknowledgment.acknowledge()
                    tracingService.endSpanSuccess(span)
                } finally {
                    MDC.remove("kafka_topic")
                    MDC.remove("kafka_partition")
                    MDC.remove("kafka_offset")
                    MDC.remove("message_key")
                    MDC.remove("order_id")
                    MDC.remove("customer_id")
                    MDC.remove("processing_outcome")
                    MDC.remove("db_operation")
                }
            }
        } catch (e: Exception) {
            MDC.put("processing_outcome", "error")
            MDC.put("error_type", e.javaClass.simpleName)

            logger.error("Error processing order CDC event: {}", e.message, e)

            metricsService.recordMessageError(record.topic(), record.partition())
            metricsService.recordProcessingLatency(startTime, record.topic(), record.partition())
            tracingService.endSpanError(span, e)
            acknowledgment.acknowledge()

            MDC.remove("processing_outcome")
            MDC.remove("error_type")
        }
    }

    private fun processEvent(
        event: OrderCdcEvent,
        kafkaOffset: Long,
        kafkaPartition: Int
    ): Pair<String, String> {
        return if (event.isDelete()) {
            logger.info("Processing order DELETE operation")
            orderMongoService.deleteOrder(
                id = event.id.toString(),
                sourceTimestamp = event.sourceTimestamp ?: System.currentTimeMillis()
            ).block()
            metricsService.recordDbDelete()
            "delete" to "success"
        } else {
            logger.info(
                "Processing order UPSERT operation for status={}, totalAmount={}",
                event.status, event.totalAmount
            )
            orderMongoService.upsertOrder(event, kafkaOffset, kafkaPartition).block()
            metricsService.recordDbUpsert()
            "upsert" to "success"
        }
    }
}
