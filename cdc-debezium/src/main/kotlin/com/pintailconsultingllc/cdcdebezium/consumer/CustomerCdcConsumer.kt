package com.pintailconsultingllc.cdcdebezium.consumer

import com.pintailconsultingllc.cdcdebezium.dto.CustomerCdcEvent
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

@Component
class CustomerCdcConsumer(
    private val objectMapper: ObjectMapper
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
        val key = record.key()
        val value = record.value()

        logger.info(
            "Received CDC event: topic={}, partition={}, offset={}, key={}",
            record.topic(), record.partition(), record.offset(), key
        )

        try {
            when {
                value == null -> {
                    logger.info("Received tombstone for key={}", key)
                }
                else -> {
                    val event = objectMapper.readValue(value, CustomerCdcEvent::class.java)
                    processEvent(event)
                }
            }
            acknowledgment.acknowledge()
        } catch (e: Exception) {
            logger.error("Error processing CDC event: key={}", key, e)
            acknowledgment.acknowledge()
        }
    }

    private fun processEvent(event: CustomerCdcEvent) {
        if (event.isDelete()) {
            logger.info("Processing DELETE for customer: id={}", event.id)
            // TODO: Implement delete logic in PLAN-005
        } else {
            logger.info(
                "Processing UPSERT for customer: id={}, email={}, status={}",
                event.id, event.email, event.status
            )
            // TODO: Implement upsert logic in PLAN-005
        }
    }
}
