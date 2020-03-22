package com.pintailconsultingllc.spring.spikes.statemachine.jpa.entities

import com.pintailconsultingllc.spring.spikes.statemachine.domain.PaymentState
import java.math.BigDecimal
import javax.persistence.*

@Entity
data class Payment(
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        var id: Long,

        @Enumerated(EnumType.STRING)
        var state: PaymentState,

        var amountInCents: BigDecimal
)