package com.pintailconsultingllc.cdcdebezium.service

import com.pintailconsultingllc.cdcdebezium.document.CdcMetadata
import com.pintailconsultingllc.cdcdebezium.document.CdcOperation
import com.pintailconsultingllc.cdcdebezium.document.CustomerDocument
import com.pintailconsultingllc.cdcdebezium.dto.CustomerCdcEvent
import com.pintailconsultingllc.cdcdebezium.repository.CustomerMongoRepository
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.Tracer
import org.slf4j.LoggerFactory
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.time.Instant

@Service
class CustomerMongoService(
    private val customerRepository: CustomerMongoRepository,
    private val mongoTemplate: ReactiveMongoTemplate
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    private val tracer: Tracer by lazy {
        GlobalOpenTelemetry.getTracer("cdc-mongodb-service", "1.0.0")
    }

    /**
     * Idempotent upsert: insert or update based on primary key.
     * Uses source timestamp for optimistic concurrency - only applies
     * updates if the incoming event is newer than existing data.
     */
    fun upsert(
        event: CustomerCdcEvent,
        kafkaOffset: Long,
        kafkaPartition: Int
    ): Mono<CustomerDocument> {
        val span = tracer.spanBuilder("mongodb.upsert")
            .setAttribute("db.operation", "upsert")
            .setAttribute("customer.id", event.id.toString())
            .startSpan()

        val document = CustomerDocument.fromCdcEvent(
            id = event.id.toString(),
            email = event.email ?: "",
            status = event.status ?: "",
            updatedAt = event.updatedAt ?: Instant.now(),
            sourceTimestamp = event.sourceTimestamp ?: System.currentTimeMillis(),
            operation = if (event.isDelete()) CdcOperation.DELETE else CdcOperation.UPDATE,
            kafkaOffset = kafkaOffset,
            kafkaPartition = kafkaPartition
        )

        return customerRepository.findById(event.id.toString())
            .flatMap { existing ->
                if (document.isNewerThan(existing)) {
                    logger.debug(
                        "Updating customer: id={}, sourceTs={} > existingTs={}",
                        event.id,
                        document.cdcMetadata.sourceTimestamp,
                        existing.cdcMetadata.sourceTimestamp
                    )
                    span.setAttribute("db.operation.result", "updated")
                    customerRepository.save(document)
                } else {
                    logger.debug(
                        "Skipping stale update for customer: id={}, sourceTs={} <= existingTs={}",
                        event.id,
                        document.cdcMetadata.sourceTimestamp,
                        existing.cdcMetadata.sourceTimestamp
                    )
                    span.setAttribute("db.operation.result", "skipped_stale")
                    Mono.just(existing)
                }
            }
            .switchIfEmpty(
                Mono.defer {
                    logger.debug("Inserting new customer: id={}", event.id)
                    span.setAttribute("db.operation.result", "inserted")
                    customerRepository.save(
                        document.copy(
                            cdcMetadata = document.cdcMetadata.copy(operation = CdcOperation.INSERT)
                        )
                    )
                }
            )
            .doFinally { span.end() }
    }

    /**
     * Idempotent delete: succeeds even if record doesn't exist.
     * Optionally soft-deletes by updating status instead of removing.
     */
    fun delete(
        id: String,
        sourceTimestamp: Long,
        kafkaOffset: Long,
        kafkaPartition: Int,
        softDelete: Boolean = false
    ): Mono<Void> {
        val span = tracer.spanBuilder("mongodb.delete")
            .setAttribute("db.operation", "delete")
            .setAttribute("customer.id", id)
            .setAttribute("soft_delete", softDelete)
            .startSpan()

        return customerRepository.findById(id)
            .flatMap { existing ->
                // Only delete if this event is newer
                if (sourceTimestamp >= existing.cdcMetadata.sourceTimestamp) {
                    if (softDelete) {
                        logger.debug("Soft-deleting customer: id={}", id)
                        span.setAttribute("db.operation.result", "soft_deleted")
                        val updated = existing.copy(
                            status = "DELETED",
                            cdcMetadata = CdcMetadata(
                                sourceTimestamp = sourceTimestamp,
                                operation = CdcOperation.DELETE,
                                kafkaOffset = kafkaOffset,
                                kafkaPartition = kafkaPartition
                            )
                        )
                        customerRepository.save(updated).then()
                    } else {
                        logger.debug("Hard-deleting customer: id={}", id)
                        span.setAttribute("db.operation.result", "hard_deleted")
                        customerRepository.deleteById(id)
                    }
                } else {
                    logger.debug(
                        "Skipping stale delete for customer: id={}, sourceTs={} < existingTs={}",
                        id, sourceTimestamp, existing.cdcMetadata.sourceTimestamp
                    )
                    span.setAttribute("db.operation.result", "skipped_stale")
                    Mono.empty()
                }
            }
            .switchIfEmpty(
                Mono.defer {
                    logger.debug("Customer already deleted or never existed: id={}", id)
                    span.setAttribute("db.operation.result", "not_found")
                    Mono.empty()
                }
            )
            .doFinally { span.end() }
    }

    /**
     * Atomic upsert using MongoDB's native upsert capability.
     * More efficient than find-then-save for high-throughput scenarios.
     */
    fun atomicUpsert(
        event: CustomerCdcEvent,
        kafkaOffset: Long,
        kafkaPartition: Int
    ): Mono<CustomerDocument> {
        val query = Query(
            Criteria.where("_id").`is`(event.id.toString())
                .and("cdcMetadata.sourceTimestamp").lt(event.sourceTimestamp ?: 0)
        )

        val update = Update()
            .set("email", event.email ?: "")
            .set("status", event.status ?: "")
            .set("updatedAt", event.updatedAt ?: Instant.now())
            .set("cdcMetadata.sourceTimestamp", event.sourceTimestamp ?: System.currentTimeMillis())
            .set("cdcMetadata.operation", CdcOperation.UPDATE.name)
            .set("cdcMetadata.kafkaOffset", kafkaOffset)
            .set("cdcMetadata.kafkaPartition", kafkaPartition)
            .set("cdcMetadata.processedAt", Instant.now())

        return mongoTemplate.findAndModify(
            query,
            update,
            CustomerDocument::class.java
        ).switchIfEmpty(
            // Document doesn't exist or timestamp check failed, try insert
            customerRepository.findById(event.id.toString())
                .switchIfEmpty(
                    Mono.defer {
                        val doc = CustomerDocument.fromCdcEvent(
                            id = event.id.toString(),
                            email = event.email ?: "",
                            status = event.status ?: "",
                            updatedAt = event.updatedAt ?: Instant.now(),
                            sourceTimestamp = event.sourceTimestamp ?: System.currentTimeMillis(),
                            operation = CdcOperation.INSERT,
                            kafkaOffset = kafkaOffset,
                            kafkaPartition = kafkaPartition
                        )
                        customerRepository.save(doc)
                    }
                )
        )
    }
}
