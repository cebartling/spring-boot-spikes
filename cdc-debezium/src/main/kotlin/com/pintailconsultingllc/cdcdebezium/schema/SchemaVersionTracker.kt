package com.pintailconsultingllc.cdcdebezium.schema

import org.slf4j.LoggerFactory
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.time.Instant

@Component
class SchemaVersionTracker(
    private val mongoTemplate: ReactiveMongoTemplate
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    data class SchemaHistoryDocument(
        val entityType: String,
        val fieldName: String,
        val changeType: String,
        val detectedAt: Instant,
        val kafkaOffset: Long,
        val kafkaPartition: Int
    )

    fun recordSchemaChange(event: SchemaChangeEvent): Mono<Void> {
        val document = SchemaHistoryDocument(
            entityType = event.entityType,
            fieldName = event.fieldName ?: "unknown",
            changeType = event.changeType.name,
            detectedAt = event.detectedAt,
            kafkaOffset = event.kafkaOffset,
            kafkaPartition = event.kafkaPartition
        )

        return mongoTemplate.save(document, COLLECTION_NAME)
            .doOnSuccess {
                logger.info(
                    "Recorded schema change: entity={}, field={}, type={}",
                    event.entityType, event.fieldName, event.changeType
                )
            }
            .then()
    }

    fun getSchemaHistory(entityType: String): Mono<List<SchemaHistoryDocument>> {
        val query = Query(Criteria.where("entityType").`is`(entityType))
            .with(Sort.by(Sort.Direction.DESC, "detectedAt"))

        return mongoTemplate.find(query, SchemaHistoryDocument::class.java, COLLECTION_NAME)
            .collectList()
    }

    fun getAllSchemaHistory(): Mono<List<SchemaHistoryDocument>> {
        val query = Query().with(Sort.by(Sort.Direction.DESC, "detectedAt"))

        return mongoTemplate.find(query, SchemaHistoryDocument::class.java, COLLECTION_NAME)
            .collectList()
    }

    companion object {
        private const val COLLECTION_NAME = "schema_history"
    }
}
