package com.pintailconsultingllc.cqrsspike.infrastructure.eventstore

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.pintailconsultingllc.cqrsspike.product.event.ProductEvent
import org.springframework.stereotype.Component

/**
 * Serializes domain events to JSON for storage in event store.
 */
@Component
class EventSerializer {

    private val objectMapper: ObjectMapper = jacksonObjectMapper()
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    /**
     * Serialize event to JSON string.
     */
    fun serialize(event: ProductEvent): String {
        return objectMapper.writeValueAsString(event)
    }

    /**
     * Serialize metadata to JSON string.
     */
    fun serializeMetadata(metadata: EventMetadata?): String? {
        return metadata?.let { objectMapper.writeValueAsString(it) }
    }

    /**
     * Get the event type name for storage.
     */
    fun getEventTypeName(event: ProductEvent): String {
        return event::class.simpleName ?: "UnknownEvent"
    }

    /**
     * Get the event schema version.
     * Override for specific event types that have evolved.
     */
    fun getEventVersion(event: ProductEvent): Int {
        return EVENT_VERSIONS[event::class.simpleName] ?: 1
    }

    companion object {
        /**
         * Event schema versions. Increment when event structure changes.
         */
        private val EVENT_VERSIONS = mapOf(
            "ProductCreated" to 1,
            "ProductUpdated" to 1,
            "ProductPriceChanged" to 1,
            "ProductActivated" to 1,
            "ProductDiscontinued" to 1,
            "ProductDeleted" to 1
        )
    }
}
