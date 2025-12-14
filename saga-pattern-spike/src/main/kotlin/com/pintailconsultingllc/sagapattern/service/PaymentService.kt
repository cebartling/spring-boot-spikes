package com.pintailconsultingllc.sagapattern.service

import io.micrometer.observation.annotation.Observed
import kotlinx.coroutines.reactor.awaitSingle
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.util.UUID

/**
 * Service for interacting with the external payment API.
 */
@Service
class PaymentService(
    @Qualifier("paymentWebClient") private val webClient: WebClient,
    private val errorResponseParser: ErrorResponseParser
) {
    private val logger = LoggerFactory.getLogger(PaymentService::class.java)

    /**
     * Authorize a payment for an order.
     *
     * @param orderId The order ID for tracking
     * @param customerId The customer ID
     * @param paymentMethodId The payment method to use
     * @param amountInCents The amount to authorize in cents
     * @return Authorization response with authorization ID
     * @throws PaymentException if authorization fails
     */
    @Observed(name = "payment.authorize", contextualName = "authorize-payment")
    suspend fun authorizePayment(
        orderId: UUID,
        customerId: UUID,
        paymentMethodId: String,
        amountInCents: Long
    ): AuthorizationResponse {
        logger.info("Authorizing payment for order $orderId, amount: $amountInCents cents")

        val request = AuthorizationRequest(
            orderId = orderId.toString(),
            customerId = customerId.toString(),
            paymentMethodId = paymentMethodId,
            amountInCents = amountInCents,
            currency = "USD"
        )

        return try {
            webClient.post()
                .uri("/authorize")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(AuthorizationResponse::class.java)
                .awaitSingle()
                .also { logger.info("Successfully authorized payment: ${it.authorizationId}") }
        } catch (e: WebClientResponseException) {
            logger.error("Payment authorization failed: ${e.statusCode} - ${e.responseBodyAsString}")
            throw PaymentException(
                message = "Failed to authorize payment: ${e.responseBodyAsString}",
                errorCode = errorResponseParser.extractErrorCode(e.responseBodyAsString),
                retryable = errorResponseParser.isRetryable(e.responseBodyAsString),
                cause = e
            )
        }
    }

    /**
     * Void a previously made payment authorization.
     *
     * @param authorizationId The authorization ID to void
     */
    @Observed(name = "payment.void", contextualName = "void-payment")
    suspend fun voidAuthorization(authorizationId: String) {
        logger.info("Voiding payment authorization: $authorizationId")

        try {
            webClient.post()
                .uri("/authorizations/$authorizationId/void")
                .retrieve()
                .toBodilessEntity()
                .awaitSingle()
            logger.info("Successfully voided payment authorization: $authorizationId")
        } catch (e: WebClientResponseException) {
            logger.error("Failed to void payment authorization: ${e.statusCode}")
            throw PaymentException(
                message = "Failed to void authorization: ${e.responseBodyAsString}",
                cause = e
            )
        }
    }
}

data class AuthorizationRequest(
    val orderId: String,
    val customerId: String,
    val paymentMethodId: String,
    val amountInCents: Long,
    val currency: String
)

data class AuthorizationResponse(
    val authorizationId: String,
    val status: String,
    val amount: String? = null,
    val currency: String? = null,
    val expiresAt: String? = null
)

class PaymentException(
    message: String,
    override val errorCode: String? = null,
    override val retryable: Boolean = false,
    cause: Throwable? = null
) : SagaServiceException(message, errorCode, retryable, cause)
