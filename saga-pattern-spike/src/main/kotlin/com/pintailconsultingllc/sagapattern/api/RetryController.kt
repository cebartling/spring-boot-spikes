package com.pintailconsultingllc.sagapattern.api

import com.pintailconsultingllc.sagapattern.api.dto.InitiateRetryRequest
import com.pintailconsultingllc.sagapattern.api.dto.RetryAttemptResponse
import com.pintailconsultingllc.sagapattern.api.dto.RetryEligibilityResponse
import com.pintailconsultingllc.sagapattern.api.dto.RetryHistoryResponse
import com.pintailconsultingllc.sagapattern.api.dto.RetryResultResponse
import com.pintailconsultingllc.sagapattern.retry.OrderRetryService
import com.pintailconsultingllc.sagapattern.retry.RetryOrchestrator
import com.pintailconsultingllc.sagapattern.retry.RetryRequest
import com.pintailconsultingllc.sagapattern.retry.ShippingAddress
import io.micrometer.observation.annotation.Observed
import kotlinx.coroutines.reactor.mono
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * REST controller for order retry operations.
 *
 * Provides endpoints for checking retry eligibility, initiating retries,
 * and viewing retry history.
 */
@RestController
@RequestMapping("/api/orders")
class RetryController(
    private val orderRetryService: OrderRetryService,
    private val retryOrchestrator: RetryOrchestrator
) {
    private val logger = LoggerFactory.getLogger(RetryController::class.java)

    /**
     * Check if an order is eligible for retry.
     *
     * Returns eligibility status, any blockers, and required actions.
     *
     * @param orderId The order ID to check
     * @return Retry eligibility information
     */
    @GetMapping("/{orderId}/retry/eligibility")
    @Observed(name = "http.retry.eligibility", contextualName = "get-retry-eligibility")
    fun checkRetryEligibility(@PathVariable orderId: UUID): Mono<ResponseEntity<RetryEligibilityResponse>> {
        logger.info("Checking retry eligibility for order: {}", orderId)

        return mono {
            val eligibility = orderRetryService.checkRetryEligibility(orderId)
            ResponseEntity.ok(RetryEligibilityResponse.fromRetryEligibility(eligibility))
        }
    }

    /**
     * Initiate a retry for a failed order.
     *
     * The order must be eligible for retry. Updated payment/shipping
     * information can be provided.
     *
     * @param orderId The order ID to retry
     * @param request Optional updates for the retry
     * @return Result of the retry operation
     */
    @PostMapping("/{orderId}/retry")
    @Observed(name = "http.retry.initiate", contextualName = "post-retry")
    fun initiateRetry(
        @PathVariable orderId: UUID,
        @RequestBody(required = false) request: InitiateRetryRequest?
    ): Mono<ResponseEntity<RetryResultResponse>> {
        logger.info("Initiating retry for order: {}", orderId)

        return mono {
            try {
                val retryRequest = RetryRequest(
                    orderId = orderId,
                    updatedPaymentMethodId = request?.updatedPaymentMethodId,
                    updatedShippingAddress = request?.updatedShippingAddress?.let {
                        ShippingAddress(
                            street = it.street,
                            city = it.city,
                            state = it.state,
                            postalCode = it.postalCode,
                            country = it.country
                        )
                    },
                    acknowledgedChanges = request?.acknowledgedPriceChanges ?: emptyList()
                )

                val result = retryOrchestrator.executeRetry(orderId, retryRequest)
                val response = RetryResultResponse.fromSagaRetryResult(result)

                if (response.success) {
                    logger.info("Retry successful for order: {}", orderId)
                    ResponseEntity.ok(response)
                } else {
                    logger.warn("Retry failed for order {}: {}", orderId, response.failureReason)
                    ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response)
                }
            } catch (e: Exception) {
                logger.error("Error during retry for order {}: {} - {}", orderId, e.javaClass.simpleName, e.message, e)
                throw e
            }
        }
    }

    /**
     * Get the retry history for an order.
     *
     * Returns all previous retry attempts with their outcomes.
     *
     * @param orderId The order ID to get history for
     * @return List of retry attempts
     */
    @GetMapping("/{orderId}/retry/history")
    @Observed(name = "http.retry.history", contextualName = "get-retry-history")
    fun getRetryHistory(@PathVariable orderId: UUID): Mono<ResponseEntity<RetryHistoryResponse>> {
        logger.info("Retrieving retry history for order: {}", orderId)

        return mono {
            val history = orderRetryService.getRetryHistory(orderId)
            val response = RetryHistoryResponse(
                orderId = orderId,
                attempts = history.map { RetryAttemptResponse.fromRetryAttempt(it) }
            )
            ResponseEntity.ok(response)
        }
    }
}
