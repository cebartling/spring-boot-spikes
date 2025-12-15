package com.pintailconsultingllc.sagapattern.saga.steps

import com.pintailconsultingllc.sagapattern.saga.CompensationResult
import com.pintailconsultingllc.sagapattern.saga.SagaContext
import com.pintailconsultingllc.sagapattern.saga.SagaErrorMessages
import com.pintailconsultingllc.sagapattern.saga.StepResult
import com.pintailconsultingllc.sagapattern.service.PaymentService
import io.micrometer.observation.annotation.Observed
import org.springframework.stereotype.Component

/**
 * Saga step for processing payment authorization.
 *
 * Execute: Authorizes the payment (holds funds)
 * Compensate: Voids the authorization to release held funds
 */
@Component
class PaymentProcessingStep(
    private val paymentService: PaymentService
) : AbstractSagaStep(STEP_NAME, STEP_ORDER) {

    @Observed(name = "saga.step.execute", contextualName = "payment-processing-execute")
    override suspend fun doExecute(context: SagaContext): StepResult {
        val response = paymentService.authorizePayment(
            orderId = context.order.id,
            customerId = context.customerId,
            paymentMethodId = context.paymentMethodId,
            amountInCents = context.order.totalAmountInCents
        )

        // Store authorization ID for potential compensation
        context.putData(SagaContext.AUTHORIZATION_ID, response.authorizationId)

        logger.info("Payment authorized successfully: ${response.authorizationId}")

        return StepResult.success(
            mapOf(
                "authorizationId" to response.authorizationId,
                "status" to response.status,
                "amountInCents" to (response.amount ?: context.order.totalAmountInCents.toString())
            )
        )
    }

    override fun hasDataToCompensate(context: SagaContext): Boolean =
        context.getData(SagaContext.AUTHORIZATION_ID) != null

    override fun getNoCompensationMessage(): String = "No authorization to void"

    @Observed(name = "saga.step.compensate", contextualName = "payment-processing-compensate")
    override suspend fun doCompensate(context: SagaContext): CompensationResult {
        val authorizationId = context.getData(SagaContext.AUTHORIZATION_ID)!!

        paymentService.voidAuthorization(authorizationId)
        logger.info("Successfully voided payment authorization: $authorizationId")

        return CompensationResult.success(SagaErrorMessages.paymentVoided(authorizationId))
    }

    companion object {
        const val STEP_NAME = "Payment Processing"
        const val STEP_ORDER = 2
    }
}
