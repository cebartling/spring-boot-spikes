package com.pintailconsultingllc.cdcdebezium.consumer

import com.pintailconsultingllc.cdcdebezium.dto.CustomerCdcEvent
import com.pintailconsultingllc.cdcdebezium.metrics.CdcMetricsService
import com.pintailconsultingllc.cdcdebezium.service.CustomerService
import com.pintailconsultingllc.cdcdebezium.tracing.CdcTracingService
import io.opentelemetry.api.trace.Span
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
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
                val key = record.key()
                val value = record.value()

                logger.info(
                    "Received CDC event: topic={}, partition={}, offset={}, key={}",
                    record.topic(), record.partition(), record.offset(), key
                )

                val operation = when {
                    value == null -> {
                        logger.info("Received tombstone for key={}", key)
                        tracingService.setDbOperation(span, CdcTracingService.DbOperation.IGNORE)
                        "ignore"
                    }
                    else -> {
                        val event = objectMapper.readValue(value, CustomerCdcEvent::class.java)
                        processEvent(event, span)
                    }
                }

                metricsService.recordMessageProcessed(record.topic(), record.partition(), operation)
                metricsService.recordProcessingLatency(startTime, record.topic(), record.partition())

                acknowledgment.acknowledge()
                tracingService.endSpanSuccess(span)
            }
        } catch (e: Exception) {
            logger.error("Error processing CDC event: key={}", record.key(), e)
            metricsService.recordMessageError(record.topic(), record.partition())
            metricsService.recordProcessingLatency(startTime, record.topic(), record.partition())
            tracingService.endSpanError(span, e)
            acknowledgment.acknowledge()
        }
    }

    private fun processEvent(event: CustomerCdcEvent, span: Span): String {
        return if (event.isDelete()) {
            tracingService.setDbOperation(span, CdcTracingService.DbOperation.DELETE)
            logger.info("Processing DELETE for customer: id={}", event.id)
            customerService.delete(event.id).block()
            metricsService.recordDbDelete()
            "delete"
        } else {
            tracingService.setDbOperation(span, CdcTracingService.DbOperation.UPSERT)
            logger.info(
                "Processing UPSERT for customer: id={}, email={}, status={}",
                event.id, event.email, event.status
            )
            customerService.upsert(event).block()
            metricsService.recordDbUpsert()
            "upsert"
        }
    }
}
