package com.pintailconsultingllc.cdcdebezium.schema

import com.pintailconsultingllc.cdcdebezium.metrics.SchemaMetricsService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.util.concurrent.ConcurrentHashMap

@Component
class SchemaChangeDetector(
    private val objectMapper: ObjectMapper,
    private val schemaMetrics: SchemaMetricsService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    private val knownFields = ConcurrentHashMap<String, MutableSet<String>>()

    private val expectedFields = mapOf(
        "customer" to setOf(
            "id", "email", "status", "updated_at",
            "__deleted", "__op", "__source_ts_ms"
        ),
        "address" to setOf(
            "id", "customer_id", "type", "street", "city", "state",
            "postal_code", "country", "is_default", "updated_at",
            "__deleted", "__op", "__source_ts_ms"
        ),
        "orders" to setOf(
            "id", "customer_id", "status", "total_amount",
            "created_at", "updated_at",
            "__deleted", "__op", "__source_ts_ms"
        ),
        "order_item" to setOf(
            "id", "order_id", "product_sku", "product_name",
            "quantity", "unit_price", "line_total",
            "__deleted", "__op", "__source_ts_ms"
        )
    )

    fun detectChanges(
        entityType: String,
        rawJson: String,
        kafkaOffset: Long,
        kafkaPartition: Int
    ): List<SchemaChangeEvent> {
        val changes = mutableListOf<SchemaChangeEvent>()

        try {
            val jsonNode = objectMapper.readTree(rawJson)
            val currentFields = extractFields(jsonNode)

            val known = knownFields.computeIfAbsent(entityType) {
                expectedFields[entityType]?.toMutableSet() ?: mutableSetOf()
            }

            val newFields = currentFields - known
            newFields.forEach { field ->
                logger.info(
                    "Schema change detected: new field '{}' in entity '{}'",
                    field, entityType
                )
                changes.add(
                    SchemaChangeEvent(
                        entityType = entityType,
                        changeType = SchemaChangeType.NEW_FIELD,
                        fieldName = field,
                        oldValue = null,
                        newValue = "detected",
                        kafkaOffset = kafkaOffset,
                        kafkaPartition = kafkaPartition
                    )
                )

                known.add(field)
                schemaMetrics.recordSchemaChange(entityType, SchemaChangeType.NEW_FIELD, field)
            }

            val expected = expectedFields[entityType] ?: emptySet()
            val optionalMetaFields = setOf("__deleted", "__op", "__source_ts_ms")
            val missingExpected = expected - currentFields - optionalMetaFields

            missingExpected.forEach { field ->
                if (jsonNode.has(field) && jsonNode.get(field).isNull) {
                    // Field is present but null - OK
                } else if (!jsonNode.has(field)) {
                    logger.debug(
                        "Expected field '{}' not present in {} event",
                        field, entityType
                    )
                }
            }
        } catch (e: Exception) {
            logger.error("Error detecting schema changes for {}", entityType, e)
            schemaMetrics.recordSchemaValidationError(entityType, e.message ?: "Unknown error")
            changes.add(
                SchemaChangeEvent(
                    entityType = entityType,
                    changeType = SchemaChangeType.UNKNOWN,
                    fieldName = null,
                    oldValue = null,
                    newValue = "Error: ${e.message}",
                    kafkaOffset = kafkaOffset,
                    kafkaPartition = kafkaPartition
                )
            )
        }

        return changes
    }

    private fun extractFields(node: JsonNode): Set<String> {
        val fields = mutableSetOf<String>()
        node.properties().forEach { entry ->
            fields.add(entry.key)
        }
        return fields
    }

    fun getCurrentSchema(entityType: String): SchemaVersion {
        val fields = knownFields[entityType] ?: expectedFields[entityType] ?: emptySet()
        return SchemaVersion(
            entityType = entityType,
            version = fields.size,
            fields = fields.toSet()
        )
    }

    fun resetTracking() {
        knownFields.clear()
    }

    fun getKnownFields(entityType: String): Set<String> {
        return knownFields[entityType]?.toSet() ?: expectedFields[entityType] ?: emptySet()
    }
}
