package com.pintailconsultingllc.spring.spikes.statemachine.configurations

import com.pintailconsultingllc.spring.spikes.statemachine.domain.PaymentEvent
import com.pintailconsultingllc.spring.spikes.statemachine.domain.PaymentState
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.statemachine.StateMachine
import org.springframework.statemachine.config.StateMachineFactory
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.util.*


@SpringBootTest(classes = [StateMachineConfiguration::class])
@ExtendWith(SpringExtension::class)
class StateMachineConfigurationTest {

    @Autowired lateinit var factory: StateMachineFactory<PaymentState, PaymentEvent>

    @Test
    fun `State transitions for events PRE_AUTHORIZE and PRE_AUTH_APPROVED`() {
        val stateMachine: StateMachine<PaymentState, PaymentEvent> = factory.getStateMachine(UUID.randomUUID())
        stateMachine.start()
        assertThat(stateMachine.state.id, equalTo(PaymentState.NEW))
        stateMachine.sendEvent(PaymentEvent.PRE_AUTHORIZE)
        assertThat(stateMachine.state.id, equalTo(PaymentState.NEW))
        stateMachine.sendEvent(PaymentEvent.PRE_AUTH_APPROVED)
        assertThat(stateMachine.state.id, equalTo(PaymentState.PRE_AUTH))
    }

    @Test
    fun `State transitions for events PRE_AUTHORIZE and PRE_AUTH_DECLINED`() {
        val stateMachine: StateMachine<PaymentState, PaymentEvent> = factory.getStateMachine(UUID.randomUUID())
        stateMachine.start()
        assertThat(stateMachine.state.id, equalTo(PaymentState.NEW))
        stateMachine.sendEvent(PaymentEvent.PRE_AUTHORIZE)
        assertThat(stateMachine.state.id, equalTo(PaymentState.NEW))
        stateMachine.sendEvent(PaymentEvent.PRE_AUTH_DECLINED)
        assertThat(stateMachine.state.id, equalTo(PaymentState.PRE_AUTH_ERROR))
    }
}