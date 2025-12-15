package com.pintailconsultingllc.sagapattern.saga

import com.pintailconsultingllc.sagapattern.domain.Order
import com.pintailconsultingllc.sagapattern.domain.ShippingAddress
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Type-safe key for storing and retrieving data in the SagaContext.
 *
 * Using typed keys eliminates runtime ClassCastException risks by
 * ensuring compile-time type safety for context data access.
 *
 * @param T The type of value associated with this key
 * @property name The string identifier for this key
 */
data class ContextKey<T>(val name: String)

/**
 * Context object that flows through saga step execution.
 * Contains order information and step-shared data.
 *
 * This is a regular class (not a data class) because it contains mutable state.
 * Data classes should represent immutable value objects, but SagaContext
 * needs to accumulate data from step executions during saga processing.
 *
 * Thread-safe for concurrent access during saga execution.
 */
class SagaContext(
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
    val shippingAddress: ShippingAddress
) {
    /**
     * Thread-safe map for storing data passed between steps.
     */
    private val stepData: ConcurrentHashMap<String, Any> = ConcurrentHashMap()

    /**
     * List of completed step names (in order of completion).
     * Note: The orchestrator maintains authoritative step completion state in the database.
     * This in-memory tracking is for convenience during saga execution.
     */
    private val completedSteps: MutableList<String> = mutableListOf()

    /**
     * Store data using a type-safe key.
     *
     * @param key The type-safe context key
     * @param value The value to store
     */
    fun <T : Any> putData(key: ContextKey<T>, value: T) {
        stepData[key.name] = value
    }

    /**
     * Store data using a string key.
     *
     * @deprecated Use putData(ContextKey<T>, T) for type safety
     * @param key The string key
     * @param value The value to store
     */
    @Deprecated(
        message = "Use type-safe putData(ContextKey<T>, T) instead",
        replaceWith = ReplaceWith("putData(contextKey, value)")
    )
    fun putData(key: String, value: Any) {
        stepData[key] = value
    }

    /**
     * Retrieve data using a type-safe key.
     *
     * @param key The type-safe context key
     * @return The value, or null if not found
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> getData(key: ContextKey<T>): T? = stepData[key.name] as? T

    /**
     * Retrieve data using a string key.
     *
     * @deprecated Use getData(ContextKey<T>) for type safety
     * @param key The string key
     * @return The value, or null if not found
     */
    @Deprecated(
        message = "Use type-safe getData(ContextKey<T>) instead",
        replaceWith = ReplaceWith("getData(contextKey)")
    )
    @Suppress("UNCHECKED_CAST")
    fun <T> getData(key: String): T? = stepData[key] as? T

    /**
     * Check if data exists for a type-safe key.
     *
     * @param key The type-safe context key
     * @return true if data exists for the key
     */
    fun <T> hasData(key: ContextKey<T>): Boolean = stepData.containsKey(key.name)

    /**
     * Check if data exists for a string key.
     *
     * @deprecated Use hasData(ContextKey<T>) for type safety
     * @param key The string key
     * @return true if data exists for the key
     */
    @Deprecated(
        message = "Use type-safe hasData(ContextKey<T>) instead",
        replaceWith = ReplaceWith("hasData(contextKey)")
    )
    fun hasData(key: String): Boolean = stepData.containsKey(key)

    /**
     * Get all stored data as an immutable map.
     */
    fun getAllData(): Map<String, Any> = stepData.toMap()

    /**
     * Mark a step as completed.
     *
     * Note: This is primarily for internal tracking during saga execution.
     * The authoritative step completion state is maintained in the database
     * by the orchestrator.
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
        // Type-safe context keys for step data
        val RESERVATION_ID = ContextKey<String>("reservationId")
        val AUTHORIZATION_ID = ContextKey<String>("authorizationId")
        val SHIPMENT_ID = ContextKey<String>("shipmentId")
        val TRACKING_NUMBER = ContextKey<String>("trackingNumber")
        val ESTIMATED_DELIVERY = ContextKey<String>("estimatedDelivery")

        // Legacy string constants for backward compatibility during migration
        @Deprecated("Use RESERVATION_ID context key instead")
        const val KEY_RESERVATION_ID = "reservationId"
        @Deprecated("Use AUTHORIZATION_ID context key instead")
        const val KEY_AUTHORIZATION_ID = "authorizationId"
        @Deprecated("Use SHIPMENT_ID context key instead")
        const val KEY_SHIPMENT_ID = "shipmentId"
        @Deprecated("Use TRACKING_NUMBER context key instead")
        const val KEY_TRACKING_NUMBER = "trackingNumber"
        @Deprecated("Use ESTIMATED_DELIVERY context key instead")
        const val KEY_ESTIMATED_DELIVERY = "estimatedDelivery"
    }
}
