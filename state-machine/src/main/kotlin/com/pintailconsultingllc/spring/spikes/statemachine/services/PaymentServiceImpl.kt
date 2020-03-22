package com.pintailconsultingllc.spring.spikes.statemachine.services

import com.pintailconsultingllc.spring.spikes.statemachine.domain.PaymentEvent
import com.pintailconsultingllc.spring.spikes.statemachine.domain.PaymentState
import com.pintailconsultingllc.spring.spikes.statemachine.jpa.entities.Payment
import com.pintailconsultingllc.spring.spikes.statemachine.jpa.repositories.PaymentRepository
import org.springframework.statemachine.StateMachine
import org.springframework.statemachine.config.StateMachineFactory
import org.springframework.statemachine.support.DefaultStateMachineContext
import org.springframework.stereotype.Service

@Service
class PaymentServiceImpl(
        val paymentRepository: PaymentRepository,
        val stateMachineFactory: StateMachineFactory<PaymentState, PaymentEvent>
) : PaymentService {

    override fun newPayment(payment: Payment): Payment {
        payment.state = PaymentState.NEW
        return paymentRepository.save(payment)
    }

    override fun preAuthorizePayment(paymentId: Long): StateMachine<PaymentState, PaymentEvent> {
        val payment = paymentRepository.getOne(paymentId)
        TODO("Not yet implemented")
    }

    override fun authorizePayment(paymentId: Long): StateMachine<PaymentState, PaymentEvent> {
        val payment = paymentRepository.getOne(paymentId)
        TODO("Not yet implemented")
    }

    override fun declineAuth(paymentId: Long): StateMachine<PaymentState, PaymentEvent> {
        val payment = paymentRepository.getOne(paymentId)
        TODO("Not yet implemented")
    }

    override fun approveAuth(paymentId: Long): StateMachine<PaymentState, PaymentEvent> {
        val payment = paymentRepository.getOne(paymentId)
        TODO("Not yet implemented")
    }

    private fun build(paymentId: Long): StateMachine<PaymentState, PaymentEvent> {
        val payment = paymentRepository.getOne(paymentId)
        val stateMachine = stateMachineFactory.getStateMachine(paymentId.toString())
        stateMachine.stop()
        stateMachine.stateMachineAccessor.doWithAllRegions { stateMachineAccess ->
            val context = DefaultStateMachineContext<PaymentState, PaymentEvent>(payment.state, null, null, null)
            stateMachineAccess.resetStateMachine(context)
        }
        stateMachine.start()
        return stateMachine
    }
}