package com.pintailconsultingllc.cdcdebezium.repository

import com.pintailconsultingllc.cdcdebezium.document.CustomerDocument
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Repository
interface CustomerMongoRepository : ReactiveMongoRepository<CustomerDocument, String> {

    fun findByEmail(email: String): Mono<CustomerDocument>

    fun findByStatus(status: String): Flux<CustomerDocument>

    fun findByStatusOrderByUpdatedAtDesc(status: String): Flux<CustomerDocument>

    fun existsByEmail(email: String): Mono<Boolean>
}
