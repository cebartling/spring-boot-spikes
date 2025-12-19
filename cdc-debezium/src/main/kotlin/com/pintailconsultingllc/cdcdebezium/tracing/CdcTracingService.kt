package com.pintailconsultingllc.cdcdebezium.tracing

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import io.opentelemetry.context.propagation.TextMapGetter
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.header.Headers
import org.springframework.stereotype.Component

@Component
class CdcTracingService {

    private val tracer: Tracer by lazy {
        GlobalOpenTelemetry.getTracer("cdc-consumer", "1.0.0")
    }

    private val kafkaHeaderGetter = object : TextMapGetter<Headers> {
        override fun keys(carrier: Headers): Iterable<String> =
            carrier.map { it.key() }

        override fun get(carrier: Headers?, key: String): String? =
            carrier?.lastHeader(key)?.value()?.let { String(it) }
    }

    fun startSpan(
        record: ConsumerRecord<String, String?>,
        consumerGroup: String
    ): Span {
        val extractedContext = GlobalOpenTelemetry.getPropagators()
            .textMapPropagator
            .extract(Context.current(), record.headers(), kafkaHeaderGetter)

        val span = tracer.spanBuilder("${record.topic()} process")
            .setParent(extractedContext)
            .setSpanKind(SpanKind.CONSUMER)
            .setAllAttributes(
                Attributes.builder()
                    .put(MESSAGING_SYSTEM, "kafka")
                    .put(MESSAGING_DESTINATION_NAME, record.topic())
                    .put(MESSAGING_KAFKA_CONSUMER_GROUP, consumerGroup)
                    .put(MESSAGING_KAFKA_PARTITION, record.partition().toLong())
                    .put(MESSAGING_KAFKA_MESSAGE_OFFSET, record.offset())
                    .put(MESSAGING_OPERATION, "process")
                    .build()
            )
            .startSpan()

        record.key()?.let { key ->
            span.setAttribute(CUSTOMER_ID, key)
        }

        return span
    }

    fun setDbOperation(span: Span, operation: DbOperation) {
        span.setAttribute(DB_OPERATION, operation.value)
    }

    fun endSpanSuccess(span: Span) {
        span.setStatus(StatusCode.OK)
        span.end()
    }

    fun endSpanError(span: Span, error: Throwable) {
        span.setStatus(StatusCode.ERROR, error.message ?: "Unknown error")
        span.recordException(error)
        span.end()
    }

    enum class DbOperation(val value: String) {
        UPSERT("upsert"),
        DELETE("delete"),
        IGNORE("ignore")
    }

    companion object {
        private val MESSAGING_SYSTEM = AttributeKey.stringKey("messaging.system")
        private val MESSAGING_DESTINATION_NAME = AttributeKey.stringKey("messaging.destination.name")
        private val MESSAGING_KAFKA_CONSUMER_GROUP = AttributeKey.stringKey("messaging.kafka.consumer.group")
        private val MESSAGING_KAFKA_PARTITION = AttributeKey.longKey("messaging.kafka.partition")
        private val MESSAGING_KAFKA_MESSAGE_OFFSET = AttributeKey.longKey("messaging.kafka.message.offset")
        private val MESSAGING_OPERATION = AttributeKey.stringKey("messaging.operation")
        private val DB_OPERATION = AttributeKey.stringKey("db.operation")
        private val CUSTOMER_ID = AttributeKey.stringKey("customer.id")
    }
}
