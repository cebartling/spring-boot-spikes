package com.pintailconsultingllc.spring.spikes.statemachine.services

import com.pintailconsultingllc.spring.spikes.statemachine.domain.PaymentEvent
import com.pintailconsultingllc.spring.spikes.statemachine.domain.PaymentState
import com.pintailconsultingllc.spring.spikes.statemachine.jpa.repositories.PaymentRepository
import org.springframework.messaging.Message
import org.springframework.statemachine.StateMachine
import org.springframework.statemachine.state.State
import org.springframework.statemachine.support.StateMachineInterceptorAdapter
import org.springframework.statemachine.transition.Transition
import org.springframework.stereotype.Component

@Component
class PaymentStateChangeInterceptor(
        val paymentRepository: PaymentRepository
) : StateMachineInterceptorAdapter<PaymentState, PaymentEvent>() {

    override fun preStateChange(
            state: State<PaymentState, PaymentEvent>,
            message: Message<PaymentEvent>?,
            transition: Transition<PaymentState, PaymentEvent>,
            stateMachine: StateMachine<PaymentState, PaymentEvent>
    ) {
        message?.let { message ->
            val headerValue = message.headers.getOrDefault(PAYMENT_ID_HEADER_KEY, -1L)
            val paymentId = headerValue as? Long
            paymentId?.let {
                val payment = paymentRepository.getOne(it)
                payment.state = state.id
                paymentRepository.save(payment)
            }
        }
    }
}
