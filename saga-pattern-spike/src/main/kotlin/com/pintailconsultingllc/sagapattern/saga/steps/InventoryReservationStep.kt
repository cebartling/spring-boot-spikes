package com.pintailconsultingllc.sagapattern.saga.steps

import com.pintailconsultingllc.sagapattern.saga.CompensationResult
import com.pintailconsultingllc.sagapattern.saga.SagaContext
import com.pintailconsultingllc.sagapattern.saga.SagaStep
import com.pintailconsultingllc.sagapattern.saga.StepResult
import com.pintailconsultingllc.sagapattern.service.InventoryException
import com.pintailconsultingllc.sagapattern.service.InventoryService
import io.micrometer.observation.annotation.Observed
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Saga step for reserving inventory for order items.
 *
 * Execute: Creates inventory reservations for all order items
 * Compensate: Releases any reservations made during execution
 */
@Component
class InventoryReservationStep(
    private val inventoryService: InventoryService
) : SagaStep {

    private val logger = LoggerFactory.getLogger(InventoryReservationStep::class.java)

    override fun getStepName(): String = STEP_NAME

    override fun getStepOrder(): Int = STEP_ORDER

    @Observed(name = "saga.step.execute", contextualName = "inventory-reservation-execute")
    override suspend fun execute(context: SagaContext): StepResult {
        logger.info("Executing inventory reservation for order ${context.order.id}")

        return try {
            val items = context.order.items
            if (items.isEmpty()) {
                return StepResult.failure(
                    errorMessage = "No items in order to reserve",
                    errorCode = "NO_ITEMS"
                )
            }

            val response = inventoryService.reserveInventory(context.order.id, items)

            // Store reservation ID for potential compensation
            context.putData(SagaContext.RESERVATION_ID, response.reservationId)

            logger.info("Inventory reserved successfully: ${response.reservationId}")

            StepResult.success(
                mapOf(
                    "reservationId" to response.reservationId,
                    "status" to response.status,
                    "expiresAt" to (response.expiresAt ?: "")
                )
            )
        } catch (e: InventoryException) {
            logger.error("Inventory reservation failed: ${e.message}")
            StepResult.failure(
                errorMessage = e.message ?: "Inventory reservation failed",
                errorCode = e.errorCode
            )
        } catch (e: Exception) {
            logger.error("Unexpected error during inventory reservation", e)
            StepResult.failure(
                errorMessage = "Unexpected error: ${e.message}",
                errorCode = "UNEXPECTED_ERROR"
            )
        }
    }

    @Observed(name = "saga.step.compensate", contextualName = "inventory-reservation-compensate")
    override suspend fun compensate(context: SagaContext): CompensationResult {
        val reservationId = context.getData(SagaContext.RESERVATION_ID)

        if (reservationId == null) {
            logger.warn("No reservation ID found for compensation")
            return CompensationResult.success("No reservation to release")
        }

        logger.info("Compensating inventory reservation: $reservationId")

        return try {
            inventoryService.releaseReservation(reservationId)
            logger.info("Successfully released inventory reservation: $reservationId")
            CompensationResult.success("Released reservation $reservationId")
        } catch (e: Exception) {
            logger.error("Failed to release inventory reservation: $reservationId", e)
            CompensationResult.failure("Failed to release reservation: ${e.message}")
        }
    }

    companion object {
        const val STEP_NAME = "Inventory Reservation"
        const val STEP_ORDER = 1
    }
}
