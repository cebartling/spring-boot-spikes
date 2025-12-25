package com.pintailconsultingllc.cdcdebezium.acceptance

import org.awaitility.kotlin.await
import org.slf4j.LoggerFactory
import org.springframework.kafka.config.KafkaListenerEndpointRegistry
import org.springframework.kafka.listener.MessageListenerContainer
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * Utility to ensure Kafka consumers are ready before tests send messages.
 *
 * When using auto-offset-reset=latest, we need to ensure the consumer has
 * been assigned partitions BEFORE sending test messages. Otherwise, messages
 * sent before partition assignment will be missed.
 */
@Component
class KafkaConsumerReadinessChecker(
    private val registry: KafkaListenerEndpointRegistry
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Wait for the CDC consumer to be ready (running and assigned to partitions for all topics).
     *
     * @param timeout Maximum time to wait for consumer readiness
     * @throws org.awaitility.core.ConditionTimeoutException if consumer is not ready within timeout
     */
    fun waitForConsumerReady(timeout: Duration = Duration.ofSeconds(30)) {
        logger.info("Waiting for Kafka consumer to be ready...")

        await.atMost(timeout).pollInterval(Duration.ofMillis(100)).until {
            val container = registry.getListenerContainer(CDC_CONSUMER_ID)
            container != null && isContainerReady(container)
        }

        // Add stabilization delay to ensure consumer has fully initialized
        // and is ready to receive messages on all partitions
        Thread.sleep(STABILIZATION_DELAY_MS)

        logger.info("Kafka consumer is ready and assigned to partitions")
    }

    private fun isContainerReady(container: MessageListenerContainer): Boolean {
        if (!container.isRunning) {
            logger.debug("Container is not running yet")
            return false
        }

        val assignedPartitions = container.assignedPartitions
        if (assignedPartitions.isNullOrEmpty()) {
            logger.debug("Container has no assigned partitions yet")
            return false
        }

        // Verify we have partitions from all expected topics
        val topicsWithPartitions = assignedPartitions.map { it.topic() }.toSet()
        val hasAllTopics = EXPECTED_TOPICS.all { it in topicsWithPartitions }

        if (!hasAllTopics) {
            val missingTopics = EXPECTED_TOPICS - topicsWithPartitions
            logger.debug("Missing partitions for topics: {}", missingTopics)
            return false
        }

        logger.debug(
            "Container assigned to {} partitions across {} topics",
            assignedPartitions.size,
            topicsWithPartitions.size
        )
        return true
    }

    companion object {
        const val CDC_CONSUMER_ID = "cdc-consumer"
        const val STABILIZATION_DELAY_MS = 500L

        val EXPECTED_TOPICS = setOf(
            "cdc.public.customer",
            "cdc.public.address",
            "cdc.public.orders",
            "cdc.public.order_item"
        )
    }
}
