package com.pintailconsultingllc.sagapattern.retry

import com.pintailconsultingllc.sagapattern.config.SagaDefaults
import com.pintailconsultingllc.sagapattern.domain.Order
import com.pintailconsultingllc.sagapattern.domain.SagaStepResult
import com.pintailconsultingllc.sagapattern.domain.ShippingAddress
import com.pintailconsultingllc.sagapattern.domain.StepStatus
import com.pintailconsultingllc.sagapattern.repository.SagaStepResultRepository
import com.pintailconsultingllc.sagapattern.saga.SagaContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue
import java.util.UUID

/**
 * Builds SagaContext for retry operations by reconstructing context data
 * from previous step results and validating required data.
 *
 * This builder addresses the following issues with retry context:
 * 1. Reconstructs context data (reservationId, authorizationId, etc.) from previous step results
 * 2. Removes empty default values that previously caused validation issues
 * 3. Validates that required context data is available before proceeding
 */
@Service
class RetryContextBuilder(
    private val sagaStepResultRepository: SagaStepResultRepository,
    private val sagaDefaults: SagaDefaults
) {
    private val logger = LoggerFactory.getLogger(RetryContextBuilder::class.java)
    private val objectMapper = jacksonObjectMapper()

    /**
     * Build a SagaContext for a retry operation.
     *
     * @param order The order being retried
     * @param sagaExecutionId The new saga execution ID for this retry
     * @param originalExecutionId The original saga execution ID to reconstruct context from
     * @param request The retry request with any updated information
     * @return A validated SagaContext ready for retry execution
     * @throws RetryContextValidationException if required context data is missing
     */
    suspend fun buildContext(
        order: Order,
        sagaExecutionId: UUID,
        originalExecutionId: UUID,
        request: RetryRequest
    ): SagaContext {
        logger.debug("Building retry context for order {} from execution {}", order.id, originalExecutionId)

        // Validate and resolve shipping address
        val shippingAddress = resolveShippingAddress(request)

        // Validate and resolve payment method
        val paymentMethodId = resolvePaymentMethodId(request)

        // Create the base context
        val context = SagaContext(
            order = order,
            sagaExecutionId = sagaExecutionId,
            customerId = order.customerId,
            paymentMethodId = paymentMethodId,
            shippingAddress = shippingAddress
        )

        // Reconstruct context data from previous step results
        reconstructContextData(context, originalExecutionId)

        logger.debug("Successfully built retry context for order {}", order.id)
        return context
    }

    /**
     * Resolve the shipping address for the retry context.
     * Requires the address to be provided in the retry request - no empty defaults.
     *
     * @throws RetryContextValidationException if shipping address is not provided
     */
    private fun resolveShippingAddress(request: RetryRequest): ShippingAddress {
        val updatedAddress = request.updatedShippingAddress
            ?: throw RetryContextValidationException(
                field = "shippingAddress",
                reason = "Shipping address is required for retry. Please provide the shipping address in the retry request."
            )

        // Validate address fields are not empty
        validateShippingAddress(updatedAddress)

        return ShippingAddress(
            street = updatedAddress.street,
            city = updatedAddress.city,
            state = updatedAddress.state,
            postalCode = updatedAddress.postalCode,
            country = updatedAddress.country
        )
    }

    /**
     * Validate that shipping address fields are not empty.
     *
     * @throws RetryContextValidationException if any required field is empty
     */
    private fun validateShippingAddress(address: com.pintailconsultingllc.sagapattern.retry.ShippingAddress) {
        val emptyFields = mutableListOf<String>()

        if (address.street.isBlank()) emptyFields.add("street")
        if (address.city.isBlank()) emptyFields.add("city")
        if (address.state.isBlank()) emptyFields.add("state")
        if (address.postalCode.isBlank()) emptyFields.add("postalCode")
        if (address.country.isBlank()) emptyFields.add("country")

        if (emptyFields.isNotEmpty()) {
            throw RetryContextValidationException(
                field = "shippingAddress",
                reason = "Shipping address has empty required fields: ${emptyFields.joinToString(", ")}"
            )
        }
    }

    /**
     * Resolve the payment method ID for the retry context.
     * Uses the updated payment method from the request, falls back to default if configured.
     *
     * @throws RetryContextValidationException if payment method is not available
     */
    private fun resolvePaymentMethodId(request: RetryRequest): String {
        return request.updatedPaymentMethodId
            ?: sagaDefaults.defaultPaymentMethodId
            ?: throw RetryContextValidationException(
                field = "paymentMethodId",
                reason = "Payment method is required for retry. Please provide a payment method in the retry request."
            )
    }

    /**
     * Reconstruct context data from previous step results.
     *
     * Loads completed step results from the original execution and extracts
     * any stored data (reservationId, authorizationId, etc.) into the new context.
     */
    private suspend fun reconstructContextData(
        context: SagaContext,
        originalExecutionId: UUID
    ) {
        val stepResults = sagaStepResultRepository
            .findBySagaExecutionIdOrderByStepOrder(originalExecutionId)

        logger.debug("Found {} step results from original execution {}", stepResults.size, originalExecutionId)

        for (stepResult in stepResults) {
            if (stepResult.status == StepStatus.COMPLETED && stepResult.stepData != null) {
                extractAndStoreData(context, stepResult)
            }
        }
    }

    /**
     * Extract data from a completed step result and store it in the context.
     *
     * Parses the JSON step data and stores known context keys using type-safe keys.
     */
    @Suppress("DEPRECATION")
    private fun extractAndStoreData(context: SagaContext, stepResult: SagaStepResult) {
        val stepData = stepResult.stepData ?: return

        try {
            val dataMap: Map<String, Any> = objectMapper.readValue<Map<String, Any>>(stepData)

            // Map known keys from step data to type-safe context keys
            dataMap["reservationId"]?.toString()?.let {
                context.putData(SagaContext.RESERVATION_ID, it)
                logger.debug("Restored reservationId from step {}", stepResult.stepName)
            }

            dataMap["authorizationId"]?.toString()?.let {
                context.putData(SagaContext.AUTHORIZATION_ID, it)
                logger.debug("Restored authorizationId from step {}", stepResult.stepName)
            }

            dataMap["shipmentId"]?.toString()?.let {
                context.putData(SagaContext.SHIPMENT_ID, it)
                logger.debug("Restored shipmentId from step {}", stepResult.stepName)
            }

            dataMap["trackingNumber"]?.toString()?.let {
                context.putData(SagaContext.TRACKING_NUMBER, it)
                logger.debug("Restored trackingNumber from step {}", stepResult.stepName)
            }

            dataMap["estimatedDelivery"]?.toString()?.let {
                context.putData(SagaContext.ESTIMATED_DELIVERY, it)
                logger.debug("Restored estimatedDelivery from step {}", stepResult.stepName)
            }
        } catch (e: Exception) {
            logger.warn(
                "Failed to parse step data for step {}: {}",
                stepResult.stepName,
                e.message
            )
        }
    }

    /**
     * Validate that a context has all required data for resuming from a specific step.
     *
     * @param context The context to validate
     * @param resumeStepName The name of the step to resume from
     * @return A validation result indicating success or missing data
     */
    fun validateContextForResume(
        context: SagaContext,
        resumeStepName: String
    ): ContextValidationResult {
        val missingData = mutableListOf<String>()

        // Validate based on which step we're resuming from
        when (resumeStepName) {
            "PaymentProcessingStep" -> {
                // Need reservation ID from inventory step
                if (!context.hasData(SagaContext.RESERVATION_ID)) {
                    missingData.add("reservationId (from InventoryReservationStep)")
                }
            }
            "ShippingArrangementStep" -> {
                // Need both reservation ID and authorization ID
                if (!context.hasData(SagaContext.RESERVATION_ID)) {
                    missingData.add("reservationId (from InventoryReservationStep)")
                }
                if (!context.hasData(SagaContext.AUTHORIZATION_ID)) {
                    missingData.add("authorizationId (from PaymentProcessingStep)")
                }
            }
        }

        return if (missingData.isEmpty()) {
            ContextValidationResult.Valid
        } else {
            ContextValidationResult.Invalid(missingData)
        }
    }
}

/**
 * Exception thrown when retry context validation fails.
 */
class RetryContextValidationException(
    val field: String,
    val reason: String
) : RuntimeException("Invalid retry context: $field - $reason")

/**
 * Result of context validation for retry.
 */
sealed class ContextValidationResult {
    /**
     * Context is valid and has all required data.
     */
    data object Valid : ContextValidationResult()

    /**
     * Context is missing required data.
     */
    data class Invalid(val missingData: List<String>) : ContextValidationResult()
}
