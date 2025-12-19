package com.pintailconsultingllc.cdcdebezium.metrics

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.metrics.LongCounter
import io.opentelemetry.api.metrics.LongHistogram
import io.opentelemetry.api.metrics.Meter
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

@Component
class CdcMetricsService {

    private val meter: Meter by lazy {
        GlobalOpenTelemetry.getMeter("cdc-consumer")
    }

    private val messagesProcessed: LongCounter by lazy {
        meter.counterBuilder("cdc.messages.processed")
            .setDescription("Total number of CDC messages processed")
            .setUnit("{messages}")
            .build()
    }

    private val messagesErrored: LongCounter by lazy {
        meter.counterBuilder("cdc.messages.errors")
            .setDescription("Total number of CDC message processing errors")
            .setUnit("{messages}")
            .build()
    }

    private val processingLatency: LongHistogram by lazy {
        meter.histogramBuilder("cdc.processing.latency")
            .setDescription("CDC message processing latency")
            .setUnit("ms")
            .ofLongs()
            .build()
    }

    private val dbUpserts: LongCounter by lazy {
        meter.counterBuilder("cdc.db.upserts")
            .setDescription("Total number of database upsert operations")
            .setUnit("{operations}")
            .build()
    }

    private val dbDeletes: LongCounter by lazy {
        meter.counterBuilder("cdc.db.deletes")
            .setDescription("Total number of database delete operations")
            .setUnit("{operations}")
            .build()
    }

    fun recordMessageProcessed(topic: String, partition: Int, operation: String) {
        messagesProcessed.add(
            1,
            Attributes.of(
                TOPIC, topic,
                PARTITION, partition.toLong(),
                OPERATION, operation
            )
        )
    }

    fun recordMessageError(topic: String, partition: Int) {
        messagesErrored.add(
            1,
            Attributes.of(
                TOPIC, topic,
                PARTITION, partition.toLong()
            )
        )
    }

    fun recordProcessingLatency(startTime: Instant, topic: String, partition: Int) {
        val latencyMs = Duration.between(startTime, Instant.now()).toMillis()
        processingLatency.record(
            latencyMs,
            Attributes.of(
                TOPIC, topic,
                PARTITION, partition.toLong()
            )
        )
    }

    fun recordDbUpsert() {
        dbUpserts.add(1)
    }

    fun recordDbDelete() {
        dbDeletes.add(1)
    }

    fun <T> timed(
        topic: String,
        partition: Int,
        block: () -> T
    ): T {
        val start = Instant.now()
        try {
            return block()
        } finally {
            recordProcessingLatency(start, topic, partition)
        }
    }

    companion object {
        private val TOPIC = AttributeKey.stringKey("topic")
        private val PARTITION = AttributeKey.longKey("partition")
        private val OPERATION = AttributeKey.stringKey("operation")
    }
}
