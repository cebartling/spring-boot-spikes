package com.pintailconsultingllc.sagapattern.saga.steps

import com.pintailconsultingllc.sagapattern.saga.CompensationResult
import com.pintailconsultingllc.sagapattern.saga.SagaContext
import com.pintailconsultingllc.sagapattern.saga.SagaStep
import com.pintailconsultingllc.sagapattern.saga.StepResult
import com.pintailconsultingllc.sagapattern.service.PaymentException
import com.pintailconsultingllc.sagapattern.service.PaymentService
import io.micrometer.observation.annotation.Observed
import org.slf4j.LoggerFactory
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
) : SagaStep {

    private val logger = LoggerFactory.getLogger(PaymentProcessingStep::class.java)

    override fun getStepName(): String = STEP_NAME

    override fun getStepOrder(): Int = STEP_ORDER

    @Observed(name = "saga.step.execute", contextualName = "payment-processing-execute")
    override suspend fun execute(context: SagaContext): StepResult {
        logger.info("Executing payment processing for order ${context.order.id}")

        return try {
            val response = paymentService.authorizePayment(
                orderId = context.order.id,
                customerId = context.customerId,
                paymentMethodId = context.paymentMethodId,
                amountInCents = context.order.totalAmountInCents
            )

            // Store authorization ID for potential compensation
            context.putData(SagaContext.AUTHORIZATION_ID, response.authorizationId)

            logger.info("Payment authorized successfully: ${response.authorizationId}")

            StepResult.success(
                mapOf(
                    "authorizationId" to response.authorizationId,
                    "status" to response.status,
                    "amountInCents" to (response.amount ?: context.order.totalAmountInCents.toString())
                )
            )
        } catch (e: PaymentException) {
            logger.error("Payment authorization failed: ${e.message}")
            StepResult.failure(
                errorMessage = e.message ?: "Payment authorization failed",
                errorCode = e.errorCode
            )
        } catch (e: Exception) {
            logger.error("Unexpected error during payment processing", e)
            StepResult.failure(
                errorMessage = "Unexpected error: ${e.message}",
                errorCode = "UNEXPECTED_ERROR"
            )
        }
    }

    @Observed(name = "saga.step.compensate", contextualName = "payment-processing-compensate")
    override suspend fun compensate(context: SagaContext): CompensationResult {
        val authorizationId = context.getData(SagaContext.AUTHORIZATION_ID)

        if (authorizationId == null) {
            logger.warn("No authorization ID found for compensation")
            return CompensationResult.success("No authorization to void")
        }

        logger.info("Compensating payment authorization: $authorizationId")

        return try {
            paymentService.voidAuthorization(authorizationId)
            logger.info("Successfully voided payment authorization: $authorizationId")
            CompensationResult.success("Voided authorization $authorizationId")
        } catch (e: Exception) {
            logger.error("Failed to void payment authorization: $authorizationId", e)
            CompensationResult.failure("Failed to void authorization: ${e.message}")
        }
    }

    companion object {
        const val STEP_NAME = "Payment Processing"
        const val STEP_ORDER = 2
    }
}
