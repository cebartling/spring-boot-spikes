package com.pintailconsultingllc.cdcdebezium.repository

import com.pintailconsultingllc.cdcdebezium.document.AddressDocument
import com.pintailconsultingllc.cdcdebezium.document.AddressType
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Repository
interface AddressMongoRepository : ReactiveMongoRepository<AddressDocument, String> {

    fun findByCustomerId(customerId: String): Flux<AddressDocument>

    fun findByCustomerIdAndType(customerId: String, type: AddressType): Mono<AddressDocument>

    fun findByCustomerIdAndIsDefaultTrue(customerId: String): Flux<AddressDocument>

    fun deleteByCustomerId(customerId: String): Mono<Long>

    fun findByPostalCode(postalCode: String): Flux<AddressDocument>
}
