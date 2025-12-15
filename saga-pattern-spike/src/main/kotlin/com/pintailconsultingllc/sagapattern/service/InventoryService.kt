package com.pintailconsultingllc.sagapattern.service

import com.pintailconsultingllc.sagapattern.domain.OrderItem
import io.micrometer.observation.annotation.Observed
import kotlinx.coroutines.reactor.awaitSingle
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * Service for interacting with the external inventory API.
 */
@Service
class InventoryService(
    @Qualifier("inventoryWebClient") private val webClient: WebClient,
    private val errorResponseParser: ErrorResponseParser
) {
    private val logger = LoggerFactory.getLogger(InventoryService::class.java)

    /**
     * Reserve inventory for order items.
     *
     * @param orderId The order ID for tracking
     * @param items The items to reserve
     * @return Reservation response with reservation ID
     * @throws InventoryException if reservation fails
     */
    @Observed(name = "inventory.reserve", contextualName = "reserve-inventory")
    suspend fun reserveInventory(orderId: UUID, items: List<OrderItem>): ReservationResponse {
        logger.info("Reserving inventory for order {} with {} items", orderId, items.size)

        val request = ReservationRequest(
            orderId = orderId.toString(),
            items = items.map { item ->
                ReservationItem(
                    productId = item.productId.toString(),
                    quantity = item.quantity
                )
            }
        )

        return try {
            webClient.post()
                .uri("/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(ReservationResponse::class.java)
                .awaitSingle()
                .also { logger.info("Successfully reserved inventory: {}", it.reservationId) }
        } catch (e: WebClientResponseException) {
            logger.error("Inventory reservation failed: {} - {}", e.statusCode, e.responseBodyAsString)
            throw InventoryException(
                message = "Failed to reserve inventory: ${e.responseBodyAsString}",
                errorCode = errorResponseParser.extractErrorCode(e.responseBodyAsString),
                cause = e
            )
        }
    }

    /**
     * Release a previously made inventory reservation.
     *
     * @param reservationId The reservation ID to release
     */
    @Observed(name = "inventory.release", contextualName = "release-inventory")
    suspend fun releaseReservation(reservationId: String) {
        logger.info("Releasing inventory reservation: {}", reservationId)

        try {
            webClient.delete()
                .uri("/reservations/{id}", reservationId)
                .retrieve()
                .toBodilessEntity()
                .awaitSingle()
            logger.info("Successfully released inventory reservation: {}", reservationId)
        } catch (e: WebClientResponseException) {
            logger.error("Failed to release inventory reservation: {}", e.statusCode)
            throw InventoryException(
                message = "Failed to release reservation: ${e.responseBodyAsString}",
                cause = e
            )
        }
    }
}

data class ReservationRequest(
    val orderId: String,
    val items: List<ReservationItem>
)

data class ReservationItem(
    val productId: String,
    val quantity: Int
)

data class ReservationResponse(
    val reservationId: String,
    val status: String,
    val expiresAt: String? = null
)

class InventoryException(
    message: String,
    override val errorCode: String? = null,
    override val retryable: Boolean = false,
    cause: Throwable? = null
) : SagaServiceException(message, errorCode, retryable, cause)
