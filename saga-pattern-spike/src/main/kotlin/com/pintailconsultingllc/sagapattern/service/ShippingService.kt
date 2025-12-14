package com.pintailconsultingllc.sagapattern.service

import com.pintailconsultingllc.sagapattern.domain.ShippingAddress
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
 * Service for interacting with the external shipping API.
 */
@Service
class ShippingService(
    @Qualifier("shippingWebClient") private val webClient: WebClient,
    private val errorResponseParser: ErrorResponseParser
) {
    private val logger = LoggerFactory.getLogger(ShippingService::class.java)

    /**
     * Create a shipment for an order.
     *
     * @param orderId The order ID for tracking
     * @param shippingAddress The delivery address
     * @return Shipment response with shipment ID and tracking information
     * @throws ShippingException if shipment creation fails
     */
    @Observed(name = "shipping.create", contextualName = "create-shipment")
    suspend fun createShipment(
        orderId: UUID,
        shippingAddress: ShippingAddress
    ): ShipmentResponse {
        logger.info("Creating shipment for order $orderId")

        val request = ShipmentRequest(
            orderId = orderId.toString(),
            shippingAddress = ShipmentAddress(
                street = shippingAddress.street,
                city = shippingAddress.city,
                state = shippingAddress.state,
                postalCode = shippingAddress.postalCode,
                country = shippingAddress.country
            )
        )

        return try {
            webClient.post()
                .uri("")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(ShipmentResponse::class.java)
                .awaitSingle()
                .also { logger.info("Successfully created shipment: ${it.shipmentId}") }
        } catch (e: WebClientResponseException) {
            logger.error("Shipment creation failed: ${e.statusCode} - ${e.responseBodyAsString}")
            throw ShippingException(
                message = "Failed to create shipment: ${e.responseBodyAsString}",
                errorCode = errorResponseParser.extractErrorCode(e.responseBodyAsString),
                retryable = errorResponseParser.isRetryable(e.responseBodyAsString),
                cause = e
            )
        }
    }

    /**
     * Cancel a previously created shipment.
     *
     * @param shipmentId The shipment ID to cancel
     */
    @Observed(name = "shipping.cancel", contextualName = "cancel-shipment")
    suspend fun cancelShipment(shipmentId: String) {
        logger.info("Cancelling shipment: $shipmentId")

        try {
            webClient.post()
                .uri("/$shipmentId/cancel")
                .retrieve()
                .toBodilessEntity()
                .awaitSingle()
            logger.info("Successfully cancelled shipment: $shipmentId")
        } catch (e: WebClientResponseException) {
            logger.error("Failed to cancel shipment: ${e.statusCode}")
            throw ShippingException(
                message = "Failed to cancel shipment: ${e.responseBodyAsString}",
                cause = e
            )
        }
    }
}

data class ShipmentRequest(
    val orderId: String,
    val shippingAddress: ShipmentAddress
)

data class ShipmentAddress(
    val street: String,
    val city: String,
    val state: String,
    val postalCode: String,
    val country: String
)

data class ShipmentResponse(
    val shipmentId: String,
    val status: String,
    val carrier: String? = null,
    val trackingNumber: String? = null,
    val estimatedDelivery: String? = null,
    val createdAt: String? = null
)

class ShippingException(
    message: String,
    val errorCode: String? = null,
    val retryable: Boolean = false,
    cause: Throwable? = null
) : RuntimeException(message, cause)
