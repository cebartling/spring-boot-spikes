package com.pintailconsultingllc.cdcdebezium.consumer.handlers

import com.pintailconsultingllc.cdcdebezium.consumer.CdcEventHandler
import com.pintailconsultingllc.cdcdebezium.dto.CustomerCdcEvent
import com.pintailconsultingllc.cdcdebezium.metrics.CdcMetricsService
import com.pintailconsultingllc.cdcdebezium.service.CustomerMongoService
import com.pintailconsultingllc.cdcdebezium.validation.ValidationService
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import tools.jackson.databind.ObjectMapper

@Component
class CustomerEventHandler(
    private val objectMapper: ObjectMapper,
    private val customerService: CustomerMongoService,
    private val validationService: ValidationService,
    private val metricsService: CdcMetricsService
) : CdcEventHandler {

    private val logger = LoggerFactory.getLogger(javaClass)

    override val topic = "cdc.public.customer"
    override val entityType = "customer"

    override fun handle(record: ConsumerRecord<String, String>): Mono<Void> {
        val event = objectMapper.readValue(record.value(), CustomerCdcEvent::class.java)
        MDC.put("customer_id", event.id.toString())

        try {
            val validationResult = validationService.validate(event)

            if (!validationResult.valid) {
                logger.warn(
                    "Validation failed for customer {}: {}",
                    event.id,
                    validationResult.failures.map { "${it.ruleId}: ${it.message}" }
                )
                return Mono.empty()
            }

            return if (event.isDelete()) {
                logger.info("Processing customer DELETE operation")
                customerService.delete(
                    id = event.id.toString(),
                    sourceTimestamp = event.sourceTimestamp ?: System.currentTimeMillis(),
                    kafkaOffset = record.offset(),
                    kafkaPartition = record.partition()
                ).doOnSuccess { metricsService.recordDbDelete() }
            } else {
                logger.info(
                    "Processing customer UPSERT operation for email={}, status={}",
                    event.email, event.status
                )
                customerService.upsert(event, record.offset(), record.partition())
                    .doOnSuccess { metricsService.recordDbUpsert() }
                    .then()
            }
        } finally {
            MDC.remove("customer_id")
        }
    }
}
