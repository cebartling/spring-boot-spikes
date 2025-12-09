package com.pintailconsultingllc.sagapattern.saga

import com.pintailconsultingllc.sagapattern.domain.Order
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Context object that flows through saga step execution.
 * Contains order information and step-shared data.
 *
 * Thread-safe for concurrent access during saga execution.
 */
data class SagaContext(
    /**
     * The order being processed.
     */
    val order: Order,

    /**
     * Unique identifier for this saga execution.
     */
    val sagaExecutionId: UUID,

    /**
     * Customer ID for the order.
     */
    val customerId: UUID,

    /**
     * Payment method ID to use for payment processing.
     */
    val paymentMethodId: String,

    /**
     * Shipping address details.
     */
    val shippingAddress: ShippingAddress,

    /**
     * Thread-safe map for storing data passed between steps.
     */
    private val stepData: ConcurrentHashMap<String, Any> = ConcurrentHashMap(),

    /**
     * List of completed step names (in order of completion).
     */
    private val completedSteps: MutableList<String> = mutableListOf()
) {
    /**
     * Store data for use by subsequent steps.
     */
    fun putData(key: String, value: Any) {
        stepData[key] = value
    }

    /**
     * Retrieve data stored by a previous step.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> getData(key: String): T? = stepData[key] as? T

    /**
     * Check if a specific data key exists.
     */
    fun hasData(key: String): Boolean = stepData.containsKey(key)

    /**
     * Get all stored data as an immutable map.
     */
    fun getAllData(): Map<String, Any> = stepData.toMap()

    /**
     * Mark a step as completed.
     */
    fun markStepCompleted(stepName: String) {
        synchronized(completedSteps) {
            if (stepName !in completedSteps) {
                completedSteps.add(stepName)
            }
        }
    }

    /**
     * Get the list of completed steps in order.
     */
    fun getCompletedSteps(): List<String> = synchronized(completedSteps) {
        completedSteps.toList()
    }

    /**
     * Check if a specific step has been completed.
     */
    fun isStepCompleted(stepName: String): Boolean = synchronized(completedSteps) {
        stepName in completedSteps
    }

    companion object {
        // Common data keys used across steps
        const val KEY_RESERVATION_ID = "reservationId"
        const val KEY_AUTHORIZATION_ID = "authorizationId"
        const val KEY_SHIPMENT_ID = "shipmentId"
        const val KEY_TRACKING_NUMBER = "trackingNumber"
        const val KEY_ESTIMATED_DELIVERY = "estimatedDelivery"
    }
}

/**
 * Shipping address details for order fulfillment.
 */
data class ShippingAddress(
    val street: String,
    val city: String,
    val state: String,
    val postalCode: String,
    val country: String
)
