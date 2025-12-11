package com.pintailconsultingllc.sagapattern.history

import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue
import org.springframework.stereotype.Component
import java.text.NumberFormat
import java.util.Locale

/**
 * Generates human-readable titles and descriptions for timeline entries.
 */
interface TimelineDescriptionGenerator {
    /**
     * Generate a human-readable title for an event.
     */
    fun generateTitle(event: OrderEvent): String

    /**
     * Generate a human-readable description for an event.
     */
    fun generateDescription(event: OrderEvent): String

    /**
     * Convert an event to a timeline entry.
     */
    fun toTimelineEntry(event: OrderEvent): TimelineEntry
}

/**
 * Default implementation of TimelineDescriptionGenerator.
 */
@Component
class DefaultTimelineDescriptionGenerator : TimelineDescriptionGenerator {

    private val objectMapper = jacksonObjectMapper()

    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale.US)

    override fun generateTitle(event: OrderEvent): String = when (event.eventType) {
        OrderEventType.ORDER_CREATED -> "Order Placed"
        OrderEventType.SAGA_STARTED -> "Processing Started"
        OrderEventType.STEP_STARTED -> getStepStartedTitle(event.stepName)
        OrderEventType.STEP_COMPLETED -> getStepCompletedTitle(event.stepName)
        OrderEventType.STEP_FAILED -> getStepFailedTitle(event.stepName)
        OrderEventType.COMPENSATION_STARTED -> "Rollback Started"
        OrderEventType.STEP_COMPENSATED -> getStepCompensatedTitle(event.stepName)
        OrderEventType.COMPENSATION_FAILED -> "Rollback Failed"
        OrderEventType.SAGA_COMPLETED -> "Processing Complete"
        OrderEventType.SAGA_FAILED -> "Processing Failed"
        OrderEventType.RETRY_INITIATED -> "Retry Started"
        OrderEventType.ORDER_COMPLETED -> "Order Complete"
        OrderEventType.ORDER_CANCELLED -> "Order Cancelled"
    }

    override fun generateDescription(event: OrderEvent): String {
        val details = parseDetails(event.detailsJson)
        val errorInfo = parseErrorInfo(event.errorInfoJson)

        return when (event.eventType) {
            OrderEventType.ORDER_CREATED ->
                "Your order was received and is being processed."

            OrderEventType.SAGA_STARTED ->
                "Order processing has begun."

            OrderEventType.STEP_STARTED ->
                getStepStartedDescription(event.stepName)

            OrderEventType.STEP_COMPLETED ->
                getStepCompletedDescription(event.stepName, details)

            OrderEventType.STEP_FAILED ->
                getStepFailedDescription(event.stepName, errorInfo)

            OrderEventType.COMPENSATION_STARTED ->
                "A problem occurred. Reversing completed steps."

            OrderEventType.STEP_COMPENSATED ->
                getStepCompensatedDescription(event.stepName)

            OrderEventType.COMPENSATION_FAILED ->
                errorInfo?.message ?: "Failed to reverse ${getStepDisplayName(event.stepName)}."

            OrderEventType.SAGA_COMPLETED ->
                details?.let { getCompletionDescription(it) }
                    ?: "Your order has been successfully processed."

            OrderEventType.SAGA_FAILED ->
                errorInfo?.message ?: "Order processing failed."

            OrderEventType.RETRY_INITIATED ->
                details?.get("attemptNumber")?.let { "Retry attempt #$it started." }
                    ?: "A retry has been initiated."

            OrderEventType.ORDER_COMPLETED ->
                "Your order has been successfully completed and is ready for shipment."

            OrderEventType.ORDER_CANCELLED ->
                "Your order has been cancelled."
        }
    }

    override fun toTimelineEntry(event: OrderEvent): TimelineEntry {
        val title = generateTitle(event)
        val description = generateDescription(event)
        val errorInfo = parseErrorInfo(event.errorInfoJson)
        val details = parseDetails(event.detailsJson)

        val status = when (event.outcome) {
            EventOutcome.SUCCESS -> TimelineStatus.SUCCESS
            EventOutcome.FAILED -> TimelineStatus.FAILED
            EventOutcome.COMPENSATED -> TimelineStatus.COMPENSATED
            EventOutcome.NEUTRAL, null -> TimelineStatus.NEUTRAL
        }

        return TimelineEntry(
            timestamp = event.timestamp,
            title = title,
            description = description,
            status = status,
            stepName = event.stepName,
            details = details,
            error = errorInfo
        )
    }

    private fun getStepDisplayName(stepName: String?): String = when (stepName) {
        "Inventory Reservation" -> "inventory reservation"
        "Payment Processing" -> "payment"
        "Shipping Arrangement" -> "shipping"
        else -> stepName?.lowercase() ?: "step"
    }

    private fun getStepStartedTitle(stepName: String?): String = when (stepName) {
        "Inventory Reservation" -> "Checking Inventory"
        "Payment Processing" -> "Processing Payment"
        "Shipping Arrangement" -> "Arranging Shipping"
        else -> "Processing ${stepName ?: "Step"}"
    }

    private fun getStepCompletedTitle(stepName: String?): String = when (stepName) {
        "Inventory Reservation" -> "Inventory Reserved"
        "Payment Processing" -> "Payment Processed"
        "Shipping Arrangement" -> "Shipping Arranged"
        else -> "${stepName ?: "Step"} Complete"
    }

    private fun getStepFailedTitle(stepName: String?): String = when (stepName) {
        "Inventory Reservation" -> "Inventory Check Failed"
        "Payment Processing" -> "Payment Failed"
        "Shipping Arrangement" -> "Shipping Failed"
        else -> "${stepName ?: "Step"} Failed"
    }

    private fun getStepCompensatedTitle(stepName: String?): String = when (stepName) {
        "Inventory Reservation" -> "Inventory Released"
        "Payment Processing" -> "Payment Reversed"
        "Shipping Arrangement" -> "Shipping Cancelled"
        else -> "${stepName ?: "Step"} Reversed"
    }

    private fun getStepStartedDescription(stepName: String?): String = when (stepName) {
        "Inventory Reservation" -> "Checking if items are available in the warehouse."
        "Payment Processing" -> "Processing your payment."
        "Shipping Arrangement" -> "Setting up delivery for your order."
        else -> "Processing ${getStepDisplayName(stepName)}."
    }

    private fun getStepCompletedDescription(stepName: String?, details: Map<String, Any>?): String =
        when (stepName) {
            "Inventory Reservation" -> "Items have been reserved from our warehouse."
            "Payment Processing" -> {
                val amount = details?.get("totalChargedInCents")?.toString()?.toLongOrNull()
                if (amount != null) {
                    "Payment of ${formatCurrency(amount)} was successfully charged."
                } else {
                    "Your payment was successfully processed."
                }
            }
            "Shipping Arrangement" -> {
                val carrier = details?.get("carrier")?.toString()
                if (carrier != null) {
                    "Your order will be shipped via $carrier."
                } else {
                    "Shipping has been arranged for your order."
                }
            }
            else -> "${stepName ?: "Step"} completed successfully."
        }

    private fun getStepFailedDescription(stepName: String?, errorInfo: ErrorInfo?): String {
        // Prefer the error message if available
        if (errorInfo != null) {
            val suggestion = errorInfo.suggestedAction?.let { " $it" } ?: ""
            return "${errorInfo.message}$suggestion"
        }

        return when (stepName) {
            "Inventory Reservation" -> "One or more items could not be reserved."
            "Payment Processing" -> "Your payment could not be processed."
            "Shipping Arrangement" -> "Unable to arrange shipping for your order."
            else -> "${stepName ?: "Step"} failed."
        }
    }

    private fun getStepCompensatedDescription(stepName: String?): String = when (stepName) {
        "Inventory Reservation" -> "Reserved items have been released back to inventory."
        "Payment Processing" -> "Your payment authorization has been voided."
        "Shipping Arrangement" -> "The shipping arrangement has been cancelled."
        else -> "The ${getStepDisplayName(stepName)} has been successfully reversed."
    }

    private fun getCompletionDescription(details: Map<String, Any>): String {
        val confirmationNumber = details["confirmationNumber"]?.toString()
        val trackingNumber = details["trackingNumber"]?.toString()

        return when {
            confirmationNumber != null && trackingNumber != null ->
                "Order $confirmationNumber is complete. Tracking: $trackingNumber"
            confirmationNumber != null ->
                "Order $confirmationNumber has been successfully processed."
            else ->
                "Your order has been successfully processed."
        }
    }

    private fun formatCurrency(cents: Long): String {
        return currencyFormat.format(cents / 100.0)
    }

    private fun parseDetails(json: String?): Map<String, Any>? {
        if (json.isNullOrBlank()) return null
        return try {
            objectMapper.readValue<Map<String, Any>>(json)
        } catch (e: Exception) {
            null
        }
    }

    private fun parseErrorInfo(json: String?): ErrorInfo? {
        if (json.isNullOrBlank()) return null
        return try {
            objectMapper.readValue<ErrorInfo>(json)
        } catch (e: Exception) {
            null
        }
    }
}
