package com.pintailconsultingllc.spring.spikes.statemachine.jpa.repositories

import com.pintailconsultingllc.spring.spikes.statemachine.jpa.entities.Payment
import org.springframework.data.jpa.repository.JpaRepository

interface PaymentRepository : JpaRepository<Payment, Long>