package com.pintailconsultingllc.cdcdebezium.consumer

import com.pintailconsultingllc.cdcdebezium.dto.CustomerCdcEvent
import com.pintailconsultingllc.cdcdebezium.metrics.CdcMetricsService
import com.pintailconsultingllc.cdcdebezium.service.CustomerService
import com.pintailconsultingllc.cdcdebezium.tracing.CdcTracingService
import io.opentelemetry.api.trace.Span
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.time.Instant

@Component
class CustomerCdcConsumer(
    private val objectMapper: ObjectMapper,
    private val customerService: CustomerService,
    private val tracingService: CdcTracingService,
    private val metricsService: CdcMetricsService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = ["cdc.public.customer"],
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
                // Add CDC-specific fields to MDC for structured logging
                MDC.put("kafka_topic", record.topic())
                MDC.put("kafka_partition", record.partition().toString())
                MDC.put("kafka_offset", record.offset().toString())
                MDC.put("message_key", record.key()?.take(36) ?: "null")

                try {
                    val value = record.value()

                    logger.info("Received CDC event")

                    val (operation, outcome) = when {
                        value == null -> {
                            logger.info("Processing tombstone message")
                            tracingService.setDbOperation(span, CdcTracingService.DbOperation.IGNORE)
                            "ignore" to "success"
                        }
                        else -> {
                            val event = objectMapper.readValue(value, CustomerCdcEvent::class.java)
                            MDC.put("customer_id", event.id.toString())
                            processEvent(event, span)
                        }
                    }

                    MDC.put("processing_outcome", outcome)
                    MDC.put("db_operation", operation)

                    metricsService.recordMessageProcessed(record.topic(), record.partition(), operation)
                    metricsService.recordProcessingLatency(startTime, record.topic(), record.partition())

                    logger.info("CDC event processed successfully")

                    acknowledgment.acknowledge()
                    tracingService.endSpanSuccess(span)
                } finally {
                    // Clean up MDC
                    MDC.remove("kafka_topic")
                    MDC.remove("kafka_partition")
                    MDC.remove("kafka_offset")
                    MDC.remove("message_key")
                    MDC.remove("customer_id")
                    MDC.remove("processing_outcome")
                    MDC.remove("db_operation")
                }
            }
        } catch (e: Exception) {
            MDC.put("processing_outcome", "error")
            MDC.put("error_type", e.javaClass.simpleName)

            logger.error("Error processing CDC event: {}", e.message, e)

            metricsService.recordMessageError(record.topic(), record.partition())
            metricsService.recordProcessingLatency(startTime, record.topic(), record.partition())
            tracingService.endSpanError(span, e)
            acknowledgment.acknowledge()

            MDC.remove("processing_outcome")
            MDC.remove("error_type")
        }
    }

    private fun processEvent(event: CustomerCdcEvent, span: Span): Pair<String, String> {
        return if (event.isDelete()) {
            tracingService.setDbOperation(span, CdcTracingService.DbOperation.DELETE)
            logger.info("Processing DELETE operation")
            customerService.delete(event.id).block()
            metricsService.recordDbDelete()
            "delete" to "success"
        } else {
            tracingService.setDbOperation(span, CdcTracingService.DbOperation.UPSERT)
            logger.info("Processing UPSERT operation for email={}, status={}", event.email, event.status)
            customerService.upsert(event).block()
            metricsService.recordDbUpsert()
            "upsert" to "success"
        }
    }
}
