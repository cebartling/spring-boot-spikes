package com.pintailconsultingllc.sagapattern.saga.steps

import com.pintailconsultingllc.sagapattern.saga.CompensationResult
import com.pintailconsultingllc.sagapattern.saga.SagaContext
import com.pintailconsultingllc.sagapattern.saga.SagaErrorMessages
import com.pintailconsultingllc.sagapattern.saga.StepResult
import com.pintailconsultingllc.sagapattern.service.InventoryService
import io.micrometer.observation.annotation.Observed
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
) : AbstractSagaStep(STEP_NAME, STEP_ORDER) {

    /**
     * Validates that the order has items to reserve.
     */
    override fun validatePreConditions(context: SagaContext): StepResult? {
        if (context.order.items.isEmpty()) {
            return StepResult.failure(
                errorMessage = SagaErrorMessages.noItemsToReserve(),
                errorCode = SagaErrorMessages.Codes.NO_ITEMS
            )
        }
        return null
    }

    @Observed(name = "saga.step.execute", contextualName = "inventory-reservation-execute")
    override suspend fun doExecute(context: SagaContext): StepResult {
        val items = context.order.items
        val response = inventoryService.reserveInventory(context.order.id, items)

        // Store reservation ID for potential compensation
        context.putData(SagaContext.RESERVATION_ID, response.reservationId)

        logger.info("Inventory reserved successfully: ${response.reservationId}")

        return StepResult.success(
            mapOf(
                "reservationId" to response.reservationId,
                "status" to response.status,
                "expiresAt" to (response.expiresAt ?: "")
            )
        )
    }

    override fun hasDataToCompensate(context: SagaContext): Boolean =
        context.getData(SagaContext.RESERVATION_ID) != null

    override fun getNoCompensationMessage(): String = "No reservation to release"

    @Observed(name = "saga.step.compensate", contextualName = "inventory-reservation-compensate")
    override suspend fun doCompensate(context: SagaContext): CompensationResult {
        val reservationId = context.getData(SagaContext.RESERVATION_ID)!!

        inventoryService.releaseReservation(reservationId)
        logger.info("Successfully released inventory reservation: $reservationId")

        return CompensationResult.success(SagaErrorMessages.inventoryReleased(reservationId))
    }

    companion object {
        const val STEP_NAME = "Inventory Reservation"
        const val STEP_ORDER = 1
    }
}
