package com.pintailconsultingllc.spring.spikes.statemachine.configurations

import com.pintailconsultingllc.spring.spikes.statemachine.domain.PaymentEvent
import com.pintailconsultingllc.spring.spikes.statemachine.domain.PaymentState
import com.pintailconsultingllc.spring.spikes.statemachine.services.PAYMENT_ID_HEADER_KEY
import mu.KotlinLogging
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.support.MessageBuilder
import org.springframework.statemachine.action.Action
import org.springframework.statemachine.config.EnableStateMachineFactory
import org.springframework.statemachine.config.StateMachineConfigurerAdapter
import org.springframework.statemachine.config.builders.StateMachineConfigurationConfigurer
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer
import org.springframework.statemachine.guard.Guard
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
                .withExternal().source(PaymentState.NEW).target(PaymentState.NEW).event(PaymentEvent.PRE_AUTHORIZE).action(preAuthAction()).guard(paymentIdGuard())
                .and()
                .withExternal().source(PaymentState.NEW).target(PaymentState.PRE_AUTH).event(PaymentEvent.PRE_AUTH_APPROVED)
                .and()
                .withExternal().source(PaymentState.NEW).target(PaymentState.PRE_AUTH_ERROR).event(PaymentEvent.PRE_AUTH_DECLINED)
                .and()
                .withExternal().source(PaymentState.PRE_AUTH).target(PaymentState.PRE_AUTH).event(PaymentEvent.AUTHORIZE).action(authAction()).guard(paymentIdGuard())
                .and()
                .withExternal().source(PaymentState.PRE_AUTH).target(PaymentState.AUTH).event(PaymentEvent.AUTH_APPROVED)
                .and()
                .withExternal().source(PaymentState.PRE_AUTH).target(PaymentState.AUTH_ERROR).event(PaymentEvent.AUTH_DECLINED)
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

    /**
     * Example of a guard.
     */
    fun paymentIdGuard(): Guard<PaymentState, PaymentEvent> {
        return Guard<PaymentState, PaymentEvent> { context -> context.getMessageHeader(PAYMENT_ID_HEADER_KEY) != null }
    }

    fun preAuthAction(): Action<PaymentState, PaymentEvent> {
        return Action<PaymentState, PaymentEvent> { context ->
            // Randomized to show different behaviors
            if (Random().nextInt(10) < 8) {
                logger.info { "Pre-auth APPROVED!" }
                val message = MessageBuilder
                        .withPayload(PaymentEvent.PRE_AUTH_APPROVED)
                        .setHeader(PAYMENT_ID_HEADER_KEY, context.getMessageHeader(PAYMENT_ID_HEADER_KEY))
                        .build()
                context.stateMachine.sendEvent(message)
            } else {
                logger.info { "Pre-auth DECLINED!" }
                val message = MessageBuilder
                        .withPayload(PaymentEvent.PRE_AUTH_DECLINED)
                        .setHeader(PAYMENT_ID_HEADER_KEY, context.getMessageHeader(PAYMENT_ID_HEADER_KEY))
                        .build()
                context.stateMachine.sendEvent(message)
            }
        }
    }

    fun authAction(): Action<PaymentState, PaymentEvent> {
        return Action<PaymentState, PaymentEvent> { context ->
            // Randomized to show different behaviors
            if (Random().nextInt(10) < 8) {
                logger.info { "Auth APPROVED!" }
                val message = MessageBuilder
                        .withPayload(PaymentEvent.AUTH_APPROVED)
                        .setHeader(PAYMENT_ID_HEADER_KEY, context.getMessageHeader(PAYMENT_ID_HEADER_KEY))
                        .build()
                context.stateMachine.sendEvent(message)
            } else {
                logger.info { "Auth DECLINED!" }
                val message = MessageBuilder
                        .withPayload(PaymentEvent.AUTH_DECLINED)
                        .setHeader(PAYMENT_ID_HEADER_KEY, context.getMessageHeader(PAYMENT_ID_HEADER_KEY))
                        .build()
                context.stateMachine.sendEvent(message)
            }
        }
    }
}