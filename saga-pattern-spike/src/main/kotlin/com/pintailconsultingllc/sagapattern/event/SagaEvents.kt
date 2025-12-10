package com.pintailconsultingllc.sagapattern.event

import java.time.Instant
import java.util.UUID

/**
 * Base class for saga-related domain events.
 */
sealed class SagaEvent {
    abstract val orderId: UUID
    abstract val timestamp: Instant
}

/**
 * Event published when a saga completes successfully.
 */
data class OrderSagaCompleted(
    override val orderId: UUID,
    override val timestamp: Instant = Instant.now(),
    val confirmationNumber: String,
    val totalChargedInCents: Long
) : SagaEvent()

/**
 * Event published when a saga fails and requires compensation.
 */
data class OrderSagaFailed(
    override val orderId: UUID,
    override val timestamp: Instant = Instant.now(),
    val failedStep: String,
    val failureReason: String,
    val errorCode: String?,
    val compensationRequired: Boolean,
    val compensatedSteps: List<String>
) : SagaEvent()

/**
 * Event published when compensation is started.
 */
data class SagaCompensationStarted(
    override val orderId: UUID,
    override val timestamp: Instant = Instant.now(),
    val failedStep: String,
    val stepsToCompensate: List<String>
) : SagaEvent()

/**
 * Event published when compensation completes.
 */
data class SagaCompensationCompleted(
    override val orderId: UUID,
    override val timestamp: Instant = Instant.now(),
    val compensatedSteps: List<String>,
    val failedCompensations: List<String>,
    val allSuccessful: Boolean
) : SagaEvent()
