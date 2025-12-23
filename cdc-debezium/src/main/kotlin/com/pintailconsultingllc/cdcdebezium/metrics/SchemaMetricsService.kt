package com.pintailconsultingllc.cdcdebezium.metrics

import com.pintailconsultingllc.cdcdebezium.schema.SchemaChangeType
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.metrics.LongCounter
import io.opentelemetry.api.metrics.Meter
import org.springframework.stereotype.Service

@Service
class SchemaMetricsService {

    private val meter: Meter by lazy {
        GlobalOpenTelemetry.getMeter("cdc-schema")
    }

    private val schemaChanges: LongCounter by lazy {
        meter.counterBuilder("cdc.schema.changes")
            .setDescription("Schema changes detected in CDC events")
            .setUnit("{changes}")
            .build()
    }

    private val schemaValidationErrors: LongCounter by lazy {
        meter.counterBuilder("cdc.schema.validation.errors")
            .setDescription("Schema validation errors encountered")
            .setUnit("{errors}")
            .build()
    }

    fun recordSchemaChange(
        entityType: String,
        changeType: SchemaChangeType,
        fieldName: String?
    ) {
        schemaChanges.add(
            1,
            Attributes.of(
                ENTITY_TYPE, entityType,
                CHANGE_TYPE, changeType.name,
                FIELD_NAME, fieldName ?: "unknown"
            )
        )
    }

    fun recordSchemaValidationError(entityType: String, error: String) {
        schemaValidationErrors.add(
            1,
            Attributes.of(
                ENTITY_TYPE, entityType,
                ERROR, error.take(50)
            )
        )
    }

    companion object {
        private val ENTITY_TYPE = AttributeKey.stringKey("entity_type")
        private val CHANGE_TYPE = AttributeKey.stringKey("change_type")
        private val FIELD_NAME = AttributeKey.stringKey("field_name")
        private val ERROR = AttributeKey.stringKey("error")
    }
}
