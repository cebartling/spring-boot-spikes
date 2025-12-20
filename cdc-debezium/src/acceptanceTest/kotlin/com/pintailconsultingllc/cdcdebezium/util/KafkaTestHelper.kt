package com.pintailconsultingllc.cdcdebezium.util

import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.OffsetSpec
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.Properties
import java.util.concurrent.TimeUnit

class KafkaTestHelper(
    private val bootstrapServers: String
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun getConsumerLag(groupId: String, topic: String): Long {
        val props = Properties().apply {
            put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
            put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, "10000")
            put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, "10000")
        }

        return AdminClient.create(props).use { adminClient ->
            try {
                // Get consumer group offsets
                val consumerOffsets = adminClient.listConsumerGroupOffsets(groupId)
                    .partitionsToOffsetAndMetadata()
                    .get(10, TimeUnit.SECONDS)
                    .filter { it.key.topic() == topic }

                if (consumerOffsets.isEmpty()) {
                    logger.warn("No consumer offsets found for group {} on topic {}", groupId, topic)
                    return@use 0L
                }

                // Get end offsets for the partitions
                val partitions = consumerOffsets.keys.toList()
                val endOffsets = adminClient.listOffsets(
                    partitions.associateWith { OffsetSpec.latest() }
                ).all().get(10, TimeUnit.SECONDS)

                // Calculate total lag
                var totalLag = 0L
                for ((partition, offsetAndMetadata) in consumerOffsets) {
                    val endOffset = endOffsets[partition]?.offset() ?: 0L
                    val consumerOffset = offsetAndMetadata.offset()
                    val partitionLag = maxOf(0L, endOffset - consumerOffset)
                    logger.debug(
                        "Partition {} lag: {} (end: {}, consumer: {})",
                        partition.partition(), partitionLag, endOffset, consumerOffset
                    )
                    totalLag += partitionLag
                }

                logger.info("Total consumer lag for group {} on topic {}: {}", groupId, topic, totalLag)
                totalLag
            } catch (e: Exception) {
                logger.error("Error getting consumer lag: {}", e.message, e)
                -1L
            }
        }
    }

    fun waitForZeroLag(
        groupId: String,
        topic: String,
        timeout: Duration = Duration.ofSeconds(60)
    ): Boolean {
        val deadline = Instant.now().plus(timeout)
        while (Instant.now().isBefore(deadline)) {
            val lag = getConsumerLag(groupId, topic)
            if (lag == 0L) {
                logger.info("Consumer lag is now zero for group {} on topic {}", groupId, topic)
                return true
            }
            logger.debug("Current lag: {}, waiting...", lag)
            Thread.sleep(2000)
        }
        logger.warn("Timeout waiting for zero consumer lag")
        return false
    }

    fun sendInvalidMessage(topic: String, key: String, value: String) {
        val props = Properties().apply {
            put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
            put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
            put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
        }

        KafkaProducer<String, String>(props).use { producer ->
            val record = ProducerRecord(topic, key, value)
            producer.send(record).get(10, TimeUnit.SECONDS)
            logger.info("Sent invalid message to topic {}", topic)
        }
    }

    fun getTopicEndOffset(topic: String): Long {
        val props = Properties().apply {
            put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
            put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, "10000")
        }

        return AdminClient.create(props).use { adminClient ->
            try {
                val topicDescription = adminClient.describeTopics(listOf(topic))
                    .allTopicNames()
                    .get(10, TimeUnit.SECONDS)[topic]

                val partitions = topicDescription?.partitions()?.map {
                    TopicPartition(topic, it.partition())
                } ?: emptyList()

                if (partitions.isEmpty()) return@use 0L

                val endOffsets = adminClient.listOffsets(
                    partitions.associateWith { OffsetSpec.latest() }
                ).all().get(10, TimeUnit.SECONDS)

                endOffsets.values.sumOf { it.offset() }
            } catch (e: Exception) {
                logger.error("Error getting topic end offset: {}", e.message, e)
                0L
            }
        }
    }
}
