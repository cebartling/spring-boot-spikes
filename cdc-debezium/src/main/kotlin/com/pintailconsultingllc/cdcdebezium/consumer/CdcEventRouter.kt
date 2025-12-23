package com.pintailconsultingllc.cdcdebezium.consumer

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

/**
 * Routes CDC events to the appropriate handler based on topic.
 * Discovers handlers via Spring dependency injection and maintains
 * a mapping of topics to their respective handlers.
 */
@Component
class CdcEventRouter(
    private val handlers: List<CdcEventHandler>
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val handlerMap: Map<String, CdcEventHandler> by lazy {
        handlers.associateBy { it.topic }
    }

    init {
        logger.info(
            "Initialized CdcEventRouter with handlers for topics: {}",
            handlers.map { it.topic }
        )
    }

    /**
     * Route an event to the appropriate handler.
     * @param record The Kafka consumer record to route
     * @return Mono that completes when the handler finishes processing
     */
    fun route(record: ConsumerRecord<String, String>): Mono<Void> {
        val handler = handlerMap[record.topic()]

        return if (handler != null) {
            logger.debug(
                "Routing event to {}: topic={}, key={}",
                handler.entityType, record.topic(), record.key()
            )
            handler.handle(record)
        } else {
            logger.warn("No handler found for topic: {}", record.topic())
            Mono.empty()
        }
    }

    /**
     * Get all registered topics.
     * @return Set of topic names that have registered handlers
     */
    fun getRegisteredTopics(): Set<String> = handlerMap.keys

    /**
     * Get handler count for monitoring/testing.
     * @return Number of registered handlers
     */
    fun getHandlerCount(): Int = handlers.size
}
