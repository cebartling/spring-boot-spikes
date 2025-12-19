package com.pintailconsultingllc.cdcdebezium.consumer

import com.pintailconsultingllc.cdcdebezium.dto.CustomerCdcEvent
import com.pintailconsultingllc.cdcdebezium.service.CustomerService
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import tools.jackson.databind.ObjectMapper

@Component
class CustomerCdcConsumer(
    private val objectMapper: ObjectMapper,
    private val customerService: CustomerService
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

        if (value == null) {
            logger.info("Received tombstone for key={}", key)
            acknowledgment.acknowledge()
            return
        }

        try {
            val event = objectMapper.readValue(value, CustomerCdcEvent::class.java)
            processEvent(event)
                .doOnSuccess { acknowledgment.acknowledge() }
                .doOnError { e ->
                    logger.error("Error processing CDC event: key={}", key, e)
                    acknowledgment.acknowledge()
                }
                .block()
        } catch (e: Exception) {
            logger.error("Error deserializing CDC event: key={}", key, e)
            acknowledgment.acknowledge()
        }
    }

    private fun processEvent(event: CustomerCdcEvent): Mono<Void> {
        return if (event.isDelete()) {
            logger.info("Processing DELETE for customer: id={}", event.id)
            customerService.delete(event.id)
        } else {
            logger.info(
                "Processing UPSERT for customer: id={}, email={}, status={}",
                event.id, event.email, event.status
            )
            customerService.upsert(event).then()
        }
    }
}
