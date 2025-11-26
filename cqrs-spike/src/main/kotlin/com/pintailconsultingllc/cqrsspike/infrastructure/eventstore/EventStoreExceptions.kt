package com.pintailconsultingllc.cqrsspike.infrastructure.eventstore

import java.util.UUID

/**
 * Base exception for event store errors.
 */
sealed class EventStoreException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

/**
 * Thrown when an event stream is not found.
 */
class EventStreamNotFoundException(
    val aggregateType: String,
    val aggregateId: UUID
) : EventStoreException("Event stream not found: $aggregateType/$aggregateId")

/**
 * Thrown when there's a version conflict during event append.
 */
class EventStoreVersionConflictException(
    val aggregateType: String,
    val aggregateId: UUID,
    val expectedVersion: Int,
    val actualVersion: Int
) : EventStoreException(
    "Version conflict for $aggregateType/$aggregateId: expected $expectedVersion, actual $actualVersion"
)

/**
 * Thrown when event serialization fails.
 */
class EventSerializationException(
    val eventType: String,
    cause: Throwable
) : EventStoreException("Failed to serialize event: $eventType", cause)

/**
 * Thrown when events don't belong to the same aggregate.
 */
class InvalidEventBatchException(
    message: String
) : EventStoreException(message)
