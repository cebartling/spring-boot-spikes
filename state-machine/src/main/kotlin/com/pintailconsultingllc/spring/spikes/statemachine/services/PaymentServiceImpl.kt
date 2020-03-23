package com.pintailconsultingllc.spring.spikes.statemachine.services

import com.pintailconsultingllc.spring.spikes.statemachine.domain.PaymentEvent
import com.pintailconsultingllc.spring.spikes.statemachine.domain.PaymentState
import com.pintailconsultingllc.spring.spikes.statemachine.jpa.entities.Payment
import com.pintailconsultingllc.spring.spikes.statemachine.jpa.repositories.PaymentRepository
import org.springframework.messaging.Message
import org.springframework.messaging.support.MessageBuilder
import org.springframework.statemachine.StateMachine
import org.springframework.statemachine.config.StateMachineFactory
import org.springframework.statemachine.support.DefaultStateMachineContext
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

const val PAYMENT_ID_HEADER_KEY = "payment_id"

@Service
class PaymentServiceImpl(
        val paymentRepository: PaymentRepository,
        val stateMachineFactory: StateMachineFactory<PaymentState, PaymentEvent>,
        val paymentStateChangeInterceptor: PaymentStateChangeInterceptor
) : PaymentService {

    @Transactional
    override fun newPayment(payment: Payment): Payment {
        payment.state = PaymentState.NEW
        return paymentRepository.save(payment)
    }

    @Transactional
    override fun preAuthorizePayment(paymentId: Long): StateMachine<PaymentState, PaymentEvent> {
        val stateMachine = build(paymentId)
        sendEvent(paymentId, stateMachine, PaymentEvent.PRE_AUTHORIZE)
        return stateMachine
    }

    @Transactional
    override fun authorizePayment(paymentId: Long): StateMachine<PaymentState, PaymentEvent> {
        val stateMachine = build(paymentId)
        sendEvent(paymentId, stateMachine, PaymentEvent.AUTHORIZE)
        return stateMachine
    }

//    @Transactional
//    override fun declineAuth(paymentId: Long): StateMachine<PaymentState, PaymentEvent> {
//        val stateMachine = build(paymentId)
//        sendEvent(paymentId, stateMachine, PaymentEvent.AUTH_DECLINED)
//        return stateMachine
//    }
//
//    @Transactional
//    override fun approveAuth(paymentId: Long): StateMachine<PaymentState, PaymentEvent> {
//        val stateMachine = build(paymentId)
//        sendEvent(paymentId, stateMachine, PaymentEvent.AUTH_APPROVED)
//        return stateMachine
//    }

    private fun sendEvent(paymentId: Long, stateMachine: StateMachine<PaymentState, PaymentEvent>, event: PaymentEvent) {
        val message: Message<PaymentEvent> = MessageBuilder.withPayload(event)
                .setHeader(PAYMENT_ID_HEADER_KEY, paymentId)
                .build()
        stateMachine.sendEvent(message)
    }

    private fun build(paymentId: Long): StateMachine<PaymentState, PaymentEvent> {
        val payment = paymentRepository.getOne(paymentId)
        val stateMachine = stateMachineFactory.getStateMachine(paymentId.toString())
        stateMachine.stop()
        stateMachine.stateMachineAccessor.doWithAllRegions { stateMachineAccess ->
            stateMachineAccess.addStateMachineInterceptor(paymentStateChangeInterceptor)
            val context = DefaultStateMachineContext<PaymentState, PaymentEvent>(payment.state, null, null, null)
            stateMachineAccess.resetStateMachine(context)
        }
        stateMachine.start()
        return stateMachine
    }
}
