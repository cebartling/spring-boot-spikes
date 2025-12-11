package com.pintailconsultingllc.sagapattern.retry

import com.pintailconsultingllc.sagapattern.domain.SagaStepResult
import com.pintailconsultingllc.sagapattern.domain.StepStatus
import com.pintailconsultingllc.sagapattern.saga.SagaContext
import io.micrometer.observation.annotation.Observed
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodyOrNull
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue
import java.time.Duration
import java.time.Instant

/**
 * Interface for checking the validity of previous step results.
 *
 * Used during retry to determine which steps can be skipped
 * and which need to be re-executed.
 */
interface StepValidityChecker {
    /**
     * Check if a previous step result is still valid.
     *
     * @param stepResult The previous step result to check
     * @param context The current saga context
     * @return Result indicating whether the step is still valid
     */
    suspend fun isStepResultStillValid(
        stepResult: SagaStepResult,
        context: SagaContext
    ): StepValidityResult
}

/**
 * Default implementation of StepValidityChecker.
 *
 * Checks validity based on step type and configured TTLs.
 * Can optionally call external services to verify validity.
 */
@Component
class DefaultStepValidityChecker(
    private val inventoryWebClient: WebClient,
    private val paymentWebClient: WebClient,
    private val shippingWebClient: WebClient,
    @Value("\${saga.retry.inventory-reservation-ttl:PT1H}")
    private val inventoryReservationTtl: String,
    @Value("\${saga.retry.payment-authorization-ttl:PT24H}")
    private val paymentAuthorizationTtl: String,
    @Value("\${saga.retry.shipping-quote-ttl:PT4H}")
    private val shippingQuoteTtl: String
) : StepValidityChecker {
    private val logger = LoggerFactory.getLogger(DefaultStepValidityChecker::class.java)
    private val objectMapper = jacksonObjectMapper()
    @Observed(name = "retry.validity.check", contextualName = "check-step-validity")
    override suspend fun isStepResultStillValid(
        stepResult: SagaStepResult,
        context: SagaContext
    ): StepValidityResult {
        // Only completed steps can be valid
        if (stepResult.status != StepStatus.COMPLETED) {
            return StepValidityResult.invalidRequiresReExecution(
                "Step status is ${stepResult.status}, not COMPLETED"
            )
        }

        return when (stepResult.stepName) {
            "Inventory Reservation" -> checkInventoryReservationValidity(stepResult)
            "Payment Processing" -> checkPaymentAuthorizationValidity(stepResult)
            "Shipping Arrangement" -> checkShippingQuoteValidity(stepResult)
            else -> {
                logger.warn("Unknown step name: ${stepResult.stepName}, assuming valid")
                StepValidityResult.valid()
            }
        }
    }

    /**
     * Check if an inventory reservation is still valid.
     */
    private suspend fun checkInventoryReservationValidity(stepResult: SagaStepResult): StepValidityResult {
        // Check TTL-based expiration first
        val ttl = Duration.parse(inventoryReservationTtl)
        if (isExpiredByTtl(stepResult.completedAt, ttl)) {
            return StepValidityResult.expiredButRefreshable(
                "Inventory reservation expired (TTL: $ttl)"
            )
        }

        // If step data contains reservation ID, verify with service
        val reservationId = extractReservationId(stepResult.stepData)
        if (reservationId != null) {
            return try {
                val valid = verifyInventoryReservation(reservationId)
                if (valid) {
                    StepValidityResult.valid()
                } else {
                    StepValidityResult.invalidRequiresReExecution(
                        "Inventory reservation $reservationId no longer valid"
                    )
                }
            } catch (e: Exception) {
                logger.warn("Failed to verify inventory reservation: ${e.message}")
                // If we can't verify, check TTL only
                StepValidityResult.valid()
            }
        }

        return StepValidityResult.valid()
    }

    /**
     * Check if a payment authorization is still valid.
     */
    private suspend fun checkPaymentAuthorizationValidity(stepResult: SagaStepResult): StepValidityResult {
        val ttl = Duration.parse(paymentAuthorizationTtl)
        if (isExpiredByTtl(stepResult.completedAt, ttl)) {
            return StepValidityResult.invalidRequiresReExecution(
                "Payment authorization expired (TTL: $ttl)"
            )
        }

        // If step data contains transaction ID, verify with service
        val transactionId = extractTransactionId(stepResult.stepData)
        if (transactionId != null) {
            return try {
                val valid = verifyPaymentAuthorization(transactionId)
                if (valid) {
                    StepValidityResult.valid()
                } else {
                    StepValidityResult.invalidRequiresReExecution(
                        "Payment authorization $transactionId no longer valid"
                    )
                }
            } catch (e: Exception) {
                logger.warn("Failed to verify payment authorization: ${e.message}")
                StepValidityResult.valid()
            }
        }

        return StepValidityResult.valid()
    }

    /**
     * Check if a shipping quote is still valid.
     */
    private suspend fun checkShippingQuoteValidity(stepResult: SagaStepResult): StepValidityResult {
        val ttl = Duration.parse(shippingQuoteTtl)
        if (isExpiredByTtl(stepResult.completedAt, ttl)) {
            return StepValidityResult.expiredButRefreshable(
                "Shipping quote expired (TTL: $ttl)"
            )
        }

        // If step data contains shipment ID, verify with service
        val shipmentId = extractShipmentId(stepResult.stepData)
        if (shipmentId != null) {
            return try {
                val valid = verifyShippingQuote(shipmentId)
                if (valid) {
                    StepValidityResult.valid()
                } else {
                    StepValidityResult.invalidRequiresReExecution(
                        "Shipping quote $shipmentId no longer valid"
                    )
                }
            } catch (e: Exception) {
                logger.warn("Failed to verify shipping quote: ${e.message}")
                StepValidityResult.valid()
            }
        }

        return StepValidityResult.valid()
    }

    private fun isExpiredByTtl(completedAt: Instant?, ttl: Duration): Boolean {
        if (completedAt == null) return true
        return Instant.now().isAfter(completedAt.plus(ttl))
    }

    private fun extractReservationId(stepData: String?): String? {
        if (stepData == null) return null
        return try {
            val data = objectMapper.readValue<Map<String, Any>>(stepData)
            data["reservationId"]?.toString()
        } catch (e: Exception) {
            null
        }
    }

    private fun extractTransactionId(stepData: String?): String? {
        if (stepData == null) return null
        return try {
            val data = objectMapper.readValue<Map<String, Any>>(stepData)
            data["transactionId"]?.toString()
        } catch (e: Exception) {
            null
        }
    }

    private fun extractShipmentId(stepData: String?): String? {
        if (stepData == null) return null
        return try {
            val data = objectMapper.readValue<Map<String, Any>>(stepData)
            data["shipmentId"]?.toString()
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun verifyInventoryReservation(reservationId: String): Boolean {
        return try {
            val response = inventoryWebClient.get()
                .uri("/api/inventory/reservations/$reservationId")
                .retrieve()
                .awaitBodyOrNull<Map<String, Any>>()

            response?.get("status") == "ACTIVE"
        } catch (e: Exception) {
            logger.warn("Error verifying inventory reservation: ${e.message}")
            true // Assume valid if we can't verify
        }
    }

    private suspend fun verifyPaymentAuthorization(transactionId: String): Boolean {
        return try {
            val response = paymentWebClient.get()
                .uri("/api/payments/authorizations/$transactionId")
                .retrieve()
                .awaitBodyOrNull<Map<String, Any>>()

            response?.get("status") == "AUTHORIZED"
        } catch (e: Exception) {
            logger.warn("Error verifying payment authorization: ${e.message}")
            true
        }
    }

    private suspend fun verifyShippingQuote(shipmentId: String): Boolean {
        return try {
            val response = shippingWebClient.get()
                .uri("/api/shipments/$shipmentId")
                .retrieve()
                .awaitBodyOrNull<Map<String, Any>>()

            response?.get("status") in listOf("PENDING", "CONFIRMED")
        } catch (e: Exception) {
            logger.warn("Error verifying shipping quote: ${e.message}")
            true
        }
    }
}
