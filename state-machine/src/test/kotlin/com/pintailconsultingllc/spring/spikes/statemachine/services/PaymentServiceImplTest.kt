package com.pintailconsultingllc.spring.spikes.statemachine.services

import com.pintailconsultingllc.spring.spikes.statemachine.domain.PaymentState
import com.pintailconsultingllc.spring.spikes.statemachine.jpa.entities.Payment
import com.pintailconsultingllc.spring.spikes.statemachine.jpa.repositories.PaymentRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.junit.jupiter.SpringExtension

@SpringBootTest
@ExtendWith(SpringExtension::class)
class PaymentServiceImplTest @Autowired constructor(
        val paymentService: PaymentService,
        val paymentRepository: PaymentRepository
) {
    lateinit var payment: Payment

    @BeforeEach
    fun setUp() {
        payment = Payment(amountInCents = 10000L)
    }

    @Test
    fun newPayment() {
        val savedPayment = paymentService.newPayment(payment)
        assertNotNull(savedPayment.id)
    }

    @Test
    fun preAuthorizePayment() {
        val savedPayment = paymentService.newPayment(payment)

        paymentService.preAuthorizePayment(savedPayment.id!!)

        val verifyPayment = paymentRepository.getOne(savedPayment.id!!)
        assertEquals(PaymentState.PRE_AUTH, verifyPayment.state)
    }

//    @Test
//    fun authorizePayment() {
//    }
//
//    @Test
//    fun declineAuth() {
//    }
//
//    @Test
//    fun approveAuth() {
//    }
}