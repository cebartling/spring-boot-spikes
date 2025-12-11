package com.pintailconsultingllc.sagapattern.history

import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Transient
import org.springframework.data.domain.Persistable
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.util.UUID

/**
 * Represents an immutable event in the order processing timeline.
 * Each significant action is recorded as an event for auditing and history.
 *
 * Implements [Persistable] to correctly handle INSERT vs UPDATE with pre-generated UUIDs.
 */
@Table("order_events")
data class OrderEvent(
    @Id
    @get:JvmName("id")
    val id: UUID = UUID.randomUUID(),

    @Column("order_id")
    val orderId: UUID,

    @Column("saga_execution_id")
    val sagaExecutionId: UUID? = null,

    @Column("event_type")
    val eventType: OrderEventType,

    val timestamp: Instant = Instant.now(),

    @Column("step_name")
    val stepName: String? = null,

    val outcome: EventOutcome? = null,

    /** JSON-serialized additional details. */
    val details: String? = null,

    /** JSON-serialized error info. */
    @Column("error_info")
    val errorInfo: String? = null,

    @Transient
    private val isNewEntity: Boolean = true
) : Persistable<UUID> {

    override fun getId(): UUID = id

    override fun isNew(): Boolean = isNewEntity

    /**
     * Transient parsed details map (not persisted directly).
     */
    @Transient
    var parsedDetails: Map<String, Any>? = null

    /**
     * Transient parsed error info (not persisted directly).
     */
    @Transient
    var parsedErrorInfo: ErrorInfo? = null

    /**
     * Mark this entity as persisted.
     */
    fun asPersisted(): OrderEvent = copy(isNewEntity = false)

    companion object {
        /**
         * Create an ORDER_CREATED event.
         */
        fun orderCreated(orderId: UUID): OrderEvent = OrderEvent(
            orderId = orderId,
            eventType = OrderEventType.ORDER_CREATED,
            outcome = EventOutcome.SUCCESS
        )

        /**
         * Create a SAGA_STARTED event.
         */
        fun sagaStarted(orderId: UUID, sagaExecutionId: UUID): OrderEvent = OrderEvent(
            orderId = orderId,
            sagaExecutionId = sagaExecutionId,
            eventType = OrderEventType.SAGA_STARTED,
            outcome = EventOutcome.NEUTRAL
        )

        /**
         * Create a STEP_STARTED event.
         */
        fun stepStarted(
            orderId: UUID,
            sagaExecutionId: UUID,
            stepName: String
        ): OrderEvent = OrderEvent(
            orderId = orderId,
            sagaExecutionId = sagaExecutionId,
            eventType = OrderEventType.STEP_STARTED,
            stepName = stepName,
            outcome = EventOutcome.NEUTRAL
        )

        /**
         * Create a STEP_COMPLETED event.
         */
        fun stepCompleted(
            orderId: UUID,
            sagaExecutionId: UUID,
            stepName: String,
            details: String? = null
        ): OrderEvent = OrderEvent(
            orderId = orderId,
            sagaExecutionId = sagaExecutionId,
            eventType = OrderEventType.STEP_COMPLETED,
            stepName = stepName,
            outcome = EventOutcome.SUCCESS,
            details = details
        )

        /**
         * Create a STEP_FAILED event.
         */
        fun stepFailed(
            orderId: UUID,
            sagaExecutionId: UUID,
            stepName: String,
            errorInfo: String? = null
        ): OrderEvent = OrderEvent(
            orderId = orderId,
            sagaExecutionId = sagaExecutionId,
            eventType = OrderEventType.STEP_FAILED,
            stepName = stepName,
            outcome = EventOutcome.FAILED,
            errorInfo = errorInfo
        )

        /**
         * Create a COMPENSATION_STARTED event.
         */
        fun compensationStarted(
            orderId: UUID,
            sagaExecutionId: UUID,
            failedStep: String
        ): OrderEvent = OrderEvent(
            orderId = orderId,
            sagaExecutionId = sagaExecutionId,
            eventType = OrderEventType.COMPENSATION_STARTED,
            stepName = failedStep,
            outcome = EventOutcome.NEUTRAL
        )

        /**
         * Create a STEP_COMPENSATED event.
         */
        fun stepCompensated(
            orderId: UUID,
            sagaExecutionId: UUID,
            stepName: String
        ): OrderEvent = OrderEvent(
            orderId = orderId,
            sagaExecutionId = sagaExecutionId,
            eventType = OrderEventType.STEP_COMPENSATED,
            stepName = stepName,
            outcome = EventOutcome.COMPENSATED
        )

        /**
         * Create a COMPENSATION_FAILED event.
         */
        fun compensationFailed(
            orderId: UUID,
            sagaExecutionId: UUID,
            stepName: String,
            errorInfo: String? = null
        ): OrderEvent = OrderEvent(
            orderId = orderId,
            sagaExecutionId = sagaExecutionId,
            eventType = OrderEventType.COMPENSATION_FAILED,
            stepName = stepName,
            outcome = EventOutcome.FAILED,
            errorInfo = errorInfo
        )

        /**
         * Create a SAGA_COMPLETED event.
         */
        fun sagaCompleted(
            orderId: UUID,
            sagaExecutionId: UUID,
            details: String? = null
        ): OrderEvent = OrderEvent(
            orderId = orderId,
            sagaExecutionId = sagaExecutionId,
            eventType = OrderEventType.SAGA_COMPLETED,
            outcome = EventOutcome.SUCCESS,
            details = details
        )

        /**
         * Create a SAGA_FAILED event.
         */
        fun sagaFailed(
            orderId: UUID,
            sagaExecutionId: UUID,
            failedStep: String,
            errorInfo: String? = null
        ): OrderEvent = OrderEvent(
            orderId = orderId,
            sagaExecutionId = sagaExecutionId,
            eventType = OrderEventType.SAGA_FAILED,
            stepName = failedStep,
            outcome = EventOutcome.FAILED,
            errorInfo = errorInfo
        )

        /**
         * Create a RETRY_INITIATED event.
         */
        fun retryInitiated(
            orderId: UUID,
            sagaExecutionId: UUID,
            details: String? = null
        ): OrderEvent = OrderEvent(
            orderId = orderId,
            sagaExecutionId = sagaExecutionId,
            eventType = OrderEventType.RETRY_INITIATED,
            outcome = EventOutcome.NEUTRAL,
            details = details
        )

        /**
         * Create an ORDER_COMPLETED event.
         */
        fun orderCompleted(orderId: UUID, sagaExecutionId: UUID): OrderEvent = OrderEvent(
            orderId = orderId,
            sagaExecutionId = sagaExecutionId,
            eventType = OrderEventType.ORDER_COMPLETED,
            outcome = EventOutcome.SUCCESS
        )

        /**
         * Create an ORDER_CANCELLED event.
         */
        fun orderCancelled(orderId: UUID): OrderEvent = OrderEvent(
            orderId = orderId,
            eventType = OrderEventType.ORDER_CANCELLED,
            outcome = EventOutcome.NEUTRAL
        )
    }
}
