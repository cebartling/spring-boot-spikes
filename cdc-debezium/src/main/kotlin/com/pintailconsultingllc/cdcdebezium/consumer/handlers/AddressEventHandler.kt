package com.pintailconsultingllc.cdcdebezium.consumer.handlers

import com.pintailconsultingllc.cdcdebezium.consumer.CdcEventHandler
import com.pintailconsultingllc.cdcdebezium.dto.AddressCdcEvent
import com.pintailconsultingllc.cdcdebezium.metrics.CdcMetricsService
import com.pintailconsultingllc.cdcdebezium.service.AddressMongoService
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import tools.jackson.databind.ObjectMapper

@Component
class AddressEventHandler(
    private val objectMapper: ObjectMapper,
    private val addressService: AddressMongoService,
    private val metricsService: CdcMetricsService
) : CdcEventHandler {

    private val logger = LoggerFactory.getLogger(javaClass)

    override val topic = "cdc.public.address"
    override val entityType = "address"

    override fun handle(record: ConsumerRecord<String, String>): Mono<Void> {
        val event = objectMapper.readValue(record.value(), AddressCdcEvent::class.java)
        MDC.put("address_id", event.id.toString())
        MDC.put("customer_id", event.customerId.toString())

        try {
            return if (event.isDelete()) {
                logger.info("Processing address DELETE operation")
                addressService.delete(
                    id = event.id.toString(),
                    sourceTimestamp = event.sourceTimestamp ?: System.currentTimeMillis()
                ).doOnSuccess { metricsService.recordDbDelete() }
            } else {
                logger.info(
                    "Processing address UPSERT operation for type={}, city={}",
                    event.type, event.city
                )
                addressService.upsert(event, record.offset(), record.partition())
                    .doOnSuccess { metricsService.recordDbUpsert() }
                    .then()
            }
        } finally {
            MDC.remove("address_id")
            MDC.remove("customer_id")
        }
    }
}
