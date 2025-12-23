package com.pintailconsultingllc.cdcdebezium.consumer

import org.apache.kafka.clients.consumer.ConsumerRecord
import reactor.core.publisher.Mono

/**
 * Generic interface for CDC event handlers.
 * Each entity type implements this interface to provide
 * specialized handling for its CDC events.
 */
interface CdcEventHandler {
    /**
     * The Kafka topic this handler processes.
     */
    val topic: String

    /**
     * The entity type name for metrics and logging.
     */
    val entityType: String

    /**
     * Process a single CDC event.
     * @param record The Kafka consumer record containing the CDC event
     * @return Mono that completes when processing is done
     */
    fun handle(record: ConsumerRecord<String, String>): Mono<Void>

    /**
     * Check if this handler can process the given topic.
     * @param topic The topic name to check
     * @return true if this handler can process events from the topic
     */
    fun canHandle(topic: String): Boolean = this.topic == topic
}
