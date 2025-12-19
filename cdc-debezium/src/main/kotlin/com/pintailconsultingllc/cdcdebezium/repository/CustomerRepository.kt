package com.pintailconsultingllc.cdcdebezium.repository

import com.pintailconsultingllc.cdcdebezium.entity.CustomerEntity
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import java.util.UUID

interface CustomerRepository : ReactiveCrudRepository<CustomerEntity, UUID>
