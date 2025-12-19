package com.pintailconsultingllc.cdcdebezium.service

import com.pintailconsultingllc.cdcdebezium.dto.CustomerCdcEvent
import com.pintailconsultingllc.cdcdebezium.entity.CustomerEntity
import com.pintailconsultingllc.cdcdebezium.repository.CustomerRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.UUID

@Service
class CustomerService(
    private val customerRepository: CustomerRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Idempotent upsert: insert or update based on primary key.
     * Uses source timestamp for optimistic concurrency - only applies
     * updates if the incoming event is newer than existing data.
     */
    fun upsert(event: CustomerCdcEvent): Mono<CustomerEntity> {
        return customerRepository.findById(event.id)
            .flatMap { existing ->
                val entity = createEntity(event, isNew = false)
                if (shouldUpdate(existing, entity)) {
                    logger.debug("Updating customer: id={}", event.id)
                    customerRepository.save(entity)
                } else {
                    logger.debug("Skipping stale update for customer: id={}", event.id)
                    Mono.just(existing)
                }
            }
            .switchIfEmpty(
                Mono.defer {
                    val entity = createEntity(event, isNew = true)
                    logger.debug("Inserting new customer: id={}", event.id)
                    customerRepository.save(entity)
                }
            )
    }

    private fun createEntity(event: CustomerCdcEvent, isNew: Boolean) = CustomerEntity.create(
        id = event.id,
        email = event.email ?: "",
        status = event.status ?: "",
        updatedAt = event.updatedAt ?: Instant.now(),
        sourceTimestamp = event.sourceTimestamp,
        isNewEntity = isNew
    )

    /**
     * Idempotent delete: succeeds even if record doesn't exist.
     */
    fun delete(id: UUID): Mono<Void> {
        return customerRepository.findById(id)
            .flatMap { existing ->
                logger.debug("Deleting customer: id={}", id)
                customerRepository.delete(existing)
            }
            .switchIfEmpty(
                Mono.defer {
                    logger.debug("Customer already deleted or never existed: id={}", id)
                    Mono.empty()
                }
            )
    }

    private fun shouldUpdate(existing: CustomerEntity, incoming: CustomerEntity): Boolean {
        val existingTs = existing.sourceTimestamp
        val incomingTs = incoming.sourceTimestamp

        return when {
            existingTs == null || incomingTs == null -> true
            incomingTs > existingTs -> true
            else -> false
        }
    }
}
