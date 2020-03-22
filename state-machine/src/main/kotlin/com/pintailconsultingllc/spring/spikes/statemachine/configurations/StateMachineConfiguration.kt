package com.pintailconsultingllc.spring.spikes.statemachine.configurations

import com.pintailconsultingllc.spring.spikes.statemachine.domain.PaymentEvent
import com.pintailconsultingllc.spring.spikes.statemachine.domain.PaymentState
import mu.KotlinLogging
import org.springframework.context.annotation.Configuration
import org.springframework.statemachine.config.EnableStateMachineFactory
import org.springframework.statemachine.config.StateMachineConfigurerAdapter
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer
import java.util.EnumSet

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
}