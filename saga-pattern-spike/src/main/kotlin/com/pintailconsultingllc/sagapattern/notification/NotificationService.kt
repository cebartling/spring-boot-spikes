package com.pintailconsultingllc.sagapattern.notification

import com.pintailconsultingllc.sagapattern.event.DomainEventPublisher
import com.pintailconsultingllc.sagapattern.event.OrderSagaFailed
import com.pintailconsultingllc.sagapattern.saga.SagaResult
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Service for sending notifications related to saga execution.
 *
 * Handles both failure notifications (to customers) and domain events
 * (for internal consumption and auditing).
 */
@Service
class NotificationService(
    private val domainEventPublisher: DomainEventPublisher
) {
    private val logger = LoggerFactory.getLogger(NotificationService::class.java)

    /**
     * Send a failure notification for a failed saga.
     *
     * This method:
     * 1. Creates a customer-facing notification with suggestions
     * 2. Publishes a domain event for internal consumers
     * 3. Logs the notification for auditing
     *
     * @param orderId The order that failed
     * @param customerId The customer to notify
     * @param result The saga failure result
     * @return The notification that was sent
     */
    fun sendFailureNotification(
        orderId: UUID,
        customerId: UUID,
        result: SagaResult.Failed
    ): FailureNotification {
        val suggestions = FailureNotification.suggestionsForError(result.errorCode)

        val notification = FailureNotification.forFirstStepFailure(
            orderId = orderId,
            customerId = customerId,
            failedStep = result.failedStep,
            failureReason = result.failureReason,
            suggestions = suggestions
        )

        logger.info(
            "Sending failure notification for order {} (step: {}, reason: {})",
            orderId,
            result.failedStep,
            result.failureReason
        )

        // Publish domain event
        domainEventPublisher.publishSagaFailed(
            OrderSagaFailed(
                orderId = orderId,
                failedStep = result.failedStep,
                failureReason = result.failureReason,
                errorCode = result.errorCode,
                compensationRequired = false,
                compensatedSteps = emptyList()
            )
        )

        // In a real implementation, this would send an email, push notification, etc.
        logNotification(notification)

        return notification
    }

    /**
     * Send a failure notification for a compensated saga.
     *
     * This method:
     * 1. Creates a customer-facing notification explaining what was rolled back
     * 2. Publishes a domain event for internal consumers
     * 3. Logs the notification for auditing
     *
     * @param orderId The order that failed
     * @param customerId The customer to notify
     * @param result The compensated saga result
     * @return The notification that was sent
     */
    fun sendCompensatedNotification(
        orderId: UUID,
        customerId: UUID,
        result: SagaResult.Compensated
    ): FailureNotification {
        val suggestions = FailureNotification.suggestionsForError(null)

        val notification = FailureNotification.forCompensatedFailure(
            orderId = orderId,
            customerId = customerId,
            failedStep = result.failedStep,
            failureReason = result.failureReason,
            compensatedSteps = result.compensatedSteps,
            suggestions = suggestions
        )

        logger.info(
            "Sending compensated notification for order {} (step: {}, compensated: {})",
            orderId,
            result.failedStep,
            result.compensatedSteps
        )

        // Publish domain event
        domainEventPublisher.publishSagaFailed(
            OrderSagaFailed(
                orderId = orderId,
                failedStep = result.failedStep,
                failureReason = result.failureReason,
                errorCode = null,
                compensationRequired = true,
                compensatedSteps = result.compensatedSteps
            )
        )

        // In a real implementation, this would send an email, push notification, etc.
        logNotification(notification)

        return notification
    }

    private fun logNotification(notification: FailureNotification) {
        logger.info(
            """
            |=== Failure Notification ===
            |Order ID: ${notification.orderId}
            |Customer ID: ${notification.customerId}
            |Failed Step: ${notification.failedStep}
            |Reason: ${notification.failureReason}
            |Compensation Status: ${notification.compensationStatus}
            |Compensated Steps: ${notification.compensatedSteps.joinToString(", ").ifEmpty { "None" }}
            |Suggestions:
            |${notification.suggestions.joinToString("\n") { "  - $it" }}
            |============================
            """.trimMargin()
        )
    }
}
