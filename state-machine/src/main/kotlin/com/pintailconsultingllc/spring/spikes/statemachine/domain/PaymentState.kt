package com.pintailconsultingllc.spring.spikes.statemachine.domain

enum class PaymentState {
    NEW,
    PRE_AUTH,
    PRE_AUTH_ERROR,
    AUTH,
    AUTH_ERROR
}