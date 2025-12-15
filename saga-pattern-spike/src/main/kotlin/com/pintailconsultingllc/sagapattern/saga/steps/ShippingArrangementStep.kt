package com.pintailconsultingllc.sagapattern.saga.steps

import com.pintailconsultingllc.sagapattern.saga.CompensationResult
import com.pintailconsultingllc.sagapattern.saga.SagaContext
import com.pintailconsultingllc.sagapattern.saga.StepResult
import com.pintailconsultingllc.sagapattern.service.ShippingService
import io.micrometer.observation.annotation.Observed
import org.springframework.stereotype.Component

/**
 * Saga step for arranging shipping.
 *
 * Execute: Creates a shipment record and reserves carrier capacity
 * Compensate: Cancels the shipment arrangement
 */
@Component
class ShippingArrangementStep(
    private val shippingService: ShippingService
) : AbstractSagaStep(STEP_NAME, STEP_ORDER) {

    @Observed(name = "saga.step.execute", contextualName = "shipping-arrangement-execute")
    override suspend fun doExecute(context: SagaContext): StepResult {
        val response = shippingService.createShipment(
            orderId = context.order.id,
            shippingAddress = context.shippingAddress
        )

        // Store shipment data for potential compensation and order completion
        context.putData(SagaContext.SHIPMENT_ID, response.shipmentId)
        response.trackingNumber?.let { context.putData(SagaContext.TRACKING_NUMBER, it) }
        response.estimatedDelivery?.let { context.putData(SagaContext.ESTIMATED_DELIVERY, it) }

        logger.info("Shipment created successfully: ${response.shipmentId}")

        return StepResult.success(
            mapOf(
                "shipmentId" to response.shipmentId,
                "status" to response.status,
                "trackingNumber" to (response.trackingNumber ?: ""),
                "estimatedDelivery" to (response.estimatedDelivery ?: ""),
                "carrier" to (response.carrier ?: "")
            )
        )
    }

    override fun hasDataToCompensate(context: SagaContext): Boolean =
        context.getData(SagaContext.SHIPMENT_ID) != null

    override fun getNoCompensationMessage(): String = "No shipment to cancel"

    @Observed(name = "saga.step.compensate", contextualName = "shipping-arrangement-compensate")
    override suspend fun doCompensate(context: SagaContext): CompensationResult {
        val shipmentId = context.getData(SagaContext.SHIPMENT_ID)!!

        shippingService.cancelShipment(shipmentId)
        logger.info("Successfully cancelled shipment: $shipmentId")

        return CompensationResult.success("Cancelled shipment $shipmentId")
    }

    companion object {
        const val STEP_NAME = "Shipping Arrangement"
        const val STEP_ORDER = 3
    }
}
