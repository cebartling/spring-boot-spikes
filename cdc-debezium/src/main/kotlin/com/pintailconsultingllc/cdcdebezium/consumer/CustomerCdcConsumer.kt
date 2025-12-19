package com.pintailconsultingllc.cdcdebezium.consumer

import com.pintailconsultingllc.cdcdebezium.dto.CustomerCdcEvent
import com.pintailconsultingllc.cdcdebezium.service.CustomerService
import com.pintailconsultingllc.cdcdebezium.tracing.CdcTracingService
import io.opentelemetry.api.trace.Span
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

@Component
class CustomerCdcConsumer(
    private val objectMapper: ObjectMapper,
    private val customerService: CustomerService,
    private val tracingService: CdcTracingService
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

        try {
            span.makeCurrent().use {
                val key = record.key()
                val value = record.value()

                logger.info(
                    "Received CDC event: topic={}, partition={}, offset={}, key={}",
                    record.topic(), record.partition(), record.offset(), key
                )

                when {
                    value == null -> {
                        logger.info("Received tombstone for key={}", key)
                        tracingService.setDbOperation(span, CdcTracingService.DbOperation.IGNORE)
                    }
                    else -> {
                        val event = objectMapper.readValue(value, CustomerCdcEvent::class.java)
                        processEvent(event, span)
                    }
                }

                acknowledgment.acknowledge()
                tracingService.endSpanSuccess(span)
            }
        } catch (e: Exception) {
            logger.error("Error processing CDC event: key={}", record.key(), e)
            tracingService.endSpanError(span, e)
            acknowledgment.acknowledge()
        }
    }

    private fun processEvent(event: CustomerCdcEvent, span: Span) {
        if (event.isDelete()) {
            tracingService.setDbOperation(span, CdcTracingService.DbOperation.DELETE)
            logger.info("Processing DELETE for customer: id={}", event.id)
            customerService.delete(event.id).block()
        } else {
            tracingService.setDbOperation(span, CdcTracingService.DbOperation.UPSERT)
            logger.info(
                "Processing UPSERT for customer: id={}, email={}, status={}",
                event.id, event.email, event.status
            )
            customerService.upsert(event).block()
        }
    }
}
