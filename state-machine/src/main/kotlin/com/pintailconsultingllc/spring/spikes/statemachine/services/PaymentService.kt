package com.pintailconsultingllc.spring.spikes.statemachine.services

import com.pintailconsultingllc.spring.spikes.statemachine.domain.PaymentEvent
import com.pintailconsultingllc.spring.spikes.statemachine.domain.PaymentState
import com.pintailconsultingllc.spring.spikes.statemachine.jpa.entities.Payment
import org.springframework.statemachine.StateMachine

interface PaymentService {
    fun newPayment(payment: Payment): Payment
    fun preAuthorizePayment(paymentId: Long): StateMachine<PaymentState, PaymentEvent>
    fun authorizePayment(paymentId: Long): StateMachine<PaymentState, PaymentEvent>
    fun declineAuth(paymentId: Long): StateMachine<PaymentState, PaymentEvent>
    fun approveAuth(paymentId: Long): StateMachine<PaymentState, PaymentEvent>
}
