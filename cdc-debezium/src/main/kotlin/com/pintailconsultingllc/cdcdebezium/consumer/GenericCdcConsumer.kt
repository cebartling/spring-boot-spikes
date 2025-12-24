package com.pintailconsultingllc.cdcdebezium.consumer

import com.pintailconsultingllc.cdcdebezium.metrics.CdcMetricsService
import com.pintailconsultingllc.cdcdebezium.schema.SchemaChangeDetector
import com.pintailconsultingllc.cdcdebezium.schema.SchemaVersionTracker
import com.pintailconsultingllc.cdcdebezium.tracing.CdcTracingService
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import java.time.Instant

/**
 * Unified CDC consumer that handles all entity types via the router.
 * This consumer listens to all CDC topics and delegates processing
 * to the appropriate handler based on the event topic.
 */
@Component
class GenericCdcConsumer(
    private val router: CdcEventRouter,
    private val metricsService: CdcMetricsService,
    private val tracingService: CdcTracingService,
    private val schemaDetector: SchemaChangeDetector,
    private val schemaTracker: SchemaVersionTracker
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        id = "cdc-consumer",
        topics = [
            "cdc.public.customer",
            "cdc.public.address",
            "cdc.public.orders",
            "cdc.public.order_item"
        ],
        groupId = "\${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    fun consume(
        record: ConsumerRecord<String, String?>,
        acknowledgment: Acknowledgment
    ) {
        val topic = record.topic()
        val key = record.key()
        val value = record.value()
        val startTime = Instant.now()
        val span = tracingService.startSpan(record, "cdc-consumer-group")

        try {
            span.makeCurrent().use {
                MDC.put("kafka_topic", topic)
                MDC.put("kafka_partition", record.partition().toString())
                MDC.put("kafka_offset", record.offset().toString())
                MDC.put("message_key", key?.take(36) ?: "null")

                try {
                    logger.info("Received CDC event on topic: {}", topic)

                    val (operation, outcome) = when {
                        value == null -> {
                            logger.debug("Received tombstone: topic={}, key={}", topic, key)
                            metricsService.recordTombstone(topic)
                            tracingService.setDbOperation(span, CdcTracingService.DbOperation.IGNORE)
                            "ignore" to "success"
                        }
                        else -> {
                            val entityType = extractEntityType(topic)

                            val schemaChanges = schemaDetector.detectChanges(
                                entityType = entityType,
                                rawJson = value,
                                kafkaOffset = record.offset(),
                                kafkaPartition = record.partition()
                            )

                            if (schemaChanges.isNotEmpty()) {
                                Flux.fromIterable(schemaChanges)
                                    .flatMap { schemaTracker.recordSchemaChange(it) }
                                    .then()
                                    .block()
                            }

                            @Suppress("UNCHECKED_CAST")
                            val typedRecord = record as ConsumerRecord<String, String>
                            router.route(typedRecord)
                                .doOnSuccess {
                                    metricsService.recordDbUpsert()
                                }
                                .block()
                            "upsert" to "success"
                        }
                    }

                    MDC.put("processing_outcome", outcome)
                    MDC.put("db_operation", operation)

                    metricsService.recordMessageProcessed(topic, record.partition(), operation)
                    metricsService.recordProcessingLatency(startTime, topic, record.partition())

                    logger.info(
                        "Processed CDC event: topic={}, key={}, operation={}",
                        topic, key, operation
                    )

                    acknowledgment.acknowledge()
                    tracingService.endSpanSuccess(span)
                } finally {
                    MDC.remove("kafka_topic")
                    MDC.remove("kafka_partition")
                    MDC.remove("kafka_offset")
                    MDC.remove("message_key")
                    MDC.remove("processing_outcome")
                    MDC.remove("db_operation")
                }
            }
        } catch (e: Exception) {
            MDC.put("processing_outcome", "error")
            MDC.put("error_type", e.javaClass.simpleName)

            logger.error("Error processing CDC event: topic={}, key={}", topic, key, e)

            metricsService.recordMessageError(topic, record.partition())
            metricsService.recordProcessingLatency(startTime, topic, record.partition())
            tracingService.endSpanError(span, e)
            acknowledgment.acknowledge()

            MDC.remove("processing_outcome")
            MDC.remove("error_type")
        }
    }

    private fun extractEntityType(topic: String): String {
        return topic.substringAfterLast(".")
    }
}
