package com.pintailconsultingllc.sagapattern.saga.steps

import com.pintailconsultingllc.sagapattern.saga.CompensationResult
import com.pintailconsultingllc.sagapattern.saga.SagaContext
import com.pintailconsultingllc.sagapattern.saga.SagaStep
import com.pintailconsultingllc.sagapattern.saga.StepResult
import com.pintailconsultingllc.sagapattern.service.ShippingException
import com.pintailconsultingllc.sagapattern.service.ShippingService
import io.micrometer.observation.annotation.Observed
import org.slf4j.LoggerFactory
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
) : SagaStep {

    private val logger = LoggerFactory.getLogger(ShippingArrangementStep::class.java)

    override fun getStepName(): String = STEP_NAME

    override fun getStepOrder(): Int = STEP_ORDER

    @Observed(name = "saga.step.execute", contextualName = "shipping-arrangement-execute")
    override suspend fun execute(context: SagaContext): StepResult {
        logger.info("Executing shipping arrangement for order ${context.order.id}")

        return try {
            val response = shippingService.createShipment(
                orderId = context.order.id,
                shippingAddress = context.shippingAddress
            )

            // Store shipment data for potential compensation and order completion
            context.putData(SagaContext.SHIPMENT_ID, response.shipmentId)
            response.trackingNumber?.let { context.putData(SagaContext.TRACKING_NUMBER, it) }
            response.estimatedDelivery?.let { context.putData(SagaContext.ESTIMATED_DELIVERY, it) }

            logger.info("Shipment created successfully: ${response.shipmentId}")

            StepResult.success(
                mapOf(
                    "shipmentId" to response.shipmentId,
                    "status" to response.status,
                    "trackingNumber" to (response.trackingNumber ?: ""),
                    "estimatedDelivery" to (response.estimatedDelivery ?: ""),
                    "carrier" to (response.carrier ?: "")
                )
            )
        } catch (e: ShippingException) {
            logger.error("Shipping arrangement failed: ${e.message}")
            StepResult.failure(
                errorMessage = e.message ?: "Shipping arrangement failed",
                errorCode = e.errorCode
            )
        } catch (e: Exception) {
            logger.error("Unexpected error during shipping arrangement", e)
            StepResult.failure(
                errorMessage = "Unexpected error: ${e.message}",
                errorCode = "UNEXPECTED_ERROR"
            )
        }
    }

    @Observed(name = "saga.step.compensate", contextualName = "shipping-arrangement-compensate")
    override suspend fun compensate(context: SagaContext): CompensationResult {
        val shipmentId = context.getData(SagaContext.SHIPMENT_ID)

        if (shipmentId == null) {
            logger.warn("No shipment ID found for compensation")
            return CompensationResult.success("No shipment to cancel")
        }

        logger.info("Compensating shipping arrangement: $shipmentId")

        return try {
            shippingService.cancelShipment(shipmentId)
            logger.info("Successfully cancelled shipment: $shipmentId")
            CompensationResult.success("Cancelled shipment $shipmentId")
        } catch (e: Exception) {
            logger.error("Failed to cancel shipment: $shipmentId", e)
            CompensationResult.failure("Failed to cancel shipment: ${e.message}")
        }
    }

    companion object {
        const val STEP_NAME = "Shipping Arrangement"
        const val STEP_ORDER = 3
    }
}
