package com.pintailconsultingllc.cdcdebezium.repository

import com.pintailconsultingllc.cdcdebezium.document.OrderDocument
import com.pintailconsultingllc.cdcdebezium.document.OrderStatus
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux

@Repository
interface OrderMongoRepository : ReactiveMongoRepository<OrderDocument, String> {

    fun findByCustomerId(customerId: String): Flux<OrderDocument>

    fun findByStatus(status: OrderStatus): Flux<OrderDocument>

    fun findByCustomerIdAndStatus(customerId: String, status: OrderStatus): Flux<OrderDocument>

    fun findByCustomerIdOrderByCreatedAtDesc(customerId: String): Flux<OrderDocument>
}
