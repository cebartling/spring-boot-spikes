package com.pintailconsultingllc.spring.spikes.statemachine.jpa.entities

import com.pintailconsultingllc.spring.spikes.statemachine.domain.PaymentState
import javax.persistence.*

@Entity
@Table(name = "payments")
data class Payment(
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        var id: Long? = null,

        @Enumerated(EnumType.STRING)
        var state: PaymentState? = null,

        var amountInCents: Long
)