package com.pintailconsultingllc.spring.spikes.statemachine.configurations

import com.pintailconsultingllc.spring.spikes.statemachine.domain.PaymentEvent
import com.pintailconsultingllc.spring.spikes.statemachine.domain.PaymentState
import mu.KotlinLogging
import org.springframework.context.annotation.Configuration
import org.springframework.statemachine.config.EnableStateMachineFactory
import org.springframework.statemachine.config.StateMachineConfigurerAdapter
import org.springframework.statemachine.config.builders.StateMachineConfigurationConfigurer
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer
import org.springframework.statemachine.listener.StateMachineListenerAdapter
import org.springframework.statemachine.state.State
import java.util.*

private val logger = KotlinLogging.logger {}

/**
 * State machine configuration. Enables the Spring Statemachine system.
 */
@EnableStateMachineFactory
@Configuration
class StateMachineConfiguration : StateMachineConfigurerAdapter<PaymentState, PaymentEvent>() {

    override fun configure(states: StateMachineStateConfigurer<PaymentState, PaymentEvent>) {
        states.withStates()
                .initial(PaymentState.NEW)
                .states(EnumSet.allOf(PaymentState::class.java))
                .end(PaymentState.AUTH)
                .end(PaymentState.AUTH_ERROR)
                .end(PaymentState.PRE_AUTH_ERROR)
    }

    override fun configure(transitions: StateMachineTransitionConfigurer<PaymentState, PaymentEvent>) {
        transitions
                .withExternal()
                .source(PaymentState.NEW).target(PaymentState.NEW).event(PaymentEvent.PRE_AUTHORIZE)
                .and()
                .withExternal()
                .source(PaymentState.NEW).target(PaymentState.PRE_AUTH).event(PaymentEvent.PRE_AUTH_APPROVED)
                .and()
                .withExternal()
                .source(PaymentState.NEW).target(PaymentState.PRE_AUTH_ERROR).event(PaymentEvent.PRE_AUTH_DECLINED)
    }

    override fun configure(config: StateMachineConfigurationConfigurer<PaymentState, PaymentEvent>) {
        val stateMachineListener = object : StateMachineListenerAdapter<PaymentState, PaymentEvent>() {
            override fun stateChanged(from: State<PaymentState, PaymentEvent>?, to: State<PaymentState, PaymentEvent>?) {
                logger.info { "stateChanged(from: ${from?.id}, to: ${to?.id})" }
            }
        }
        config.withConfiguration()
                .listener(stateMachineListener)
    }
}