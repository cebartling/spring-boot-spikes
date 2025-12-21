package com.pintailconsultingllc.cdcdebezium.service

import com.pintailconsultingllc.cdcdebezium.document.AddressDocument
import com.pintailconsultingllc.cdcdebezium.document.CdcOperation
import com.pintailconsultingllc.cdcdebezium.dto.AddressCdcEvent
import com.pintailconsultingllc.cdcdebezium.repository.AddressMongoRepository
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.Tracer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

@Service
class AddressMongoService(
    private val addressRepository: AddressMongoRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    private val tracer: Tracer by lazy {
        GlobalOpenTelemetry.getTracer("cdc-mongodb-service", "1.0.0")
    }

    fun upsert(
        event: AddressCdcEvent,
        kafkaOffset: Long,
        kafkaPartition: Int
    ): Mono<AddressDocument> {
        val span = tracer.spanBuilder("mongodb.address.upsert")
            .setAttribute("db.operation", "upsert")
            .setAttribute("address.id", event.id.toString())
            .setAttribute("customer.id", event.customerId.toString())
            .startSpan()

        val document = AddressDocument.fromCdcEvent(
            event = event,
            operation = CdcOperation.UPDATE,
            kafkaOffset = kafkaOffset,
            kafkaPartition = kafkaPartition
        )

        return addressRepository.findById(event.id.toString())
            .flatMap { existing ->
                if (document.isNewerThan(existing)) {
                    logger.debug(
                        "Updating address: id={}, customerId={}",
                        event.id,
                        event.customerId
                    )
                    span.setAttribute("db.operation.result", "updated")
                    addressRepository.save(document)
                } else {
                    logger.debug(
                        "Skipping stale update for address: id={}",
                        event.id
                    )
                    span.setAttribute("db.operation.result", "skipped_stale")
                    Mono.just(existing)
                }
            }
            .switchIfEmpty(
                Mono.defer {
                    logger.debug("Inserting new address: id={}, customerId={}", event.id, event.customerId)
                    span.setAttribute("db.operation.result", "inserted")
                    addressRepository.save(
                        document.copy(
                            cdcMetadata = document.cdcMetadata.copy(operation = CdcOperation.INSERT)
                        )
                    )
                }
            )
            .doFinally { span.end() }
    }

    fun delete(
        id: String,
        sourceTimestamp: Long
    ): Mono<Void> {
        val span = tracer.spanBuilder("mongodb.address.delete")
            .setAttribute("db.operation", "delete")
            .setAttribute("address.id", id)
            .startSpan()

        return addressRepository.findById(id)
            .flatMap { existing ->
                if (sourceTimestamp >= existing.cdcMetadata.sourceTimestamp) {
                    logger.debug("Deleting address: id={}", id)
                    span.setAttribute("db.operation.result", "deleted")
                    addressRepository.deleteById(id)
                } else {
                    logger.debug(
                        "Skipping stale delete for address: id={}, sourceTs={} < existingTs={}",
                        id, sourceTimestamp, existing.cdcMetadata.sourceTimestamp
                    )
                    span.setAttribute("db.operation.result", "skipped_stale")
                    Mono.empty()
                }
            }
            .switchIfEmpty(
                Mono.defer {
                    logger.debug("Address already deleted or never existed: id={}", id)
                    span.setAttribute("db.operation.result", "not_found")
                    Mono.empty()
                }
            )
            .doFinally { span.end() }
    }
}
