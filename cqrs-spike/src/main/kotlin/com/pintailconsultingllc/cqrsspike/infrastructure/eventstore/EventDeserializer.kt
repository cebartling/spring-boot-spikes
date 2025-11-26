package com.pintailconsultingllc.cqrsspike.infrastructure.eventstore

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.pintailconsultingllc.cqrsspike.product.event.ProductActivated
import com.pintailconsultingllc.cqrsspike.product.event.ProductCreated
import com.pintailconsultingllc.cqrsspike.product.event.ProductDeleted
import com.pintailconsultingllc.cqrsspike.product.event.ProductDiscontinued
import com.pintailconsultingllc.cqrsspike.product.event.ProductEvent
import com.pintailconsultingllc.cqrsspike.product.event.ProductPriceChanged
import com.pintailconsultingllc.cqrsspike.product.event.ProductUpdated
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import kotlin.reflect.KClass

/**
 * Deserializes domain events from JSON with versioning support.
 */
@Component
class EventDeserializer {

    private val logger = LoggerFactory.getLogger(EventDeserializer::class.java)

    private val objectMapper: ObjectMapper = jacksonObjectMapper()
        .registerModule(JavaTimeModule())

    /**
     * Deserialize event from JSON string.
     *
     * @param eventType The event type name
     * @param eventVersion The schema version of the event
     * @param json The JSON string
     * @return The deserialized ProductEvent
     * @throws EventDeserializationException if deserialization fails
     */
    fun deserialize(eventType: String, eventVersion: Int, json: String): ProductEvent {
        val eventClass = getEventClass(eventType)
            ?: throw EventDeserializationException("Unknown event type: $eventType")

        return try {
            // Handle version migrations if needed
            val migratedJson = migrateEventIfNeeded(eventType, eventVersion, json)
            objectMapper.readValue(migratedJson, eventClass.java)
        } catch (e: Exception) {
            logger.error("Failed to deserialize event: type=$eventType, version=$eventVersion", e)
            throw EventDeserializationException("Failed to deserialize event: $eventType", e)
        }
    }

    /**
     * Get the Kotlin class for an event type.
     */
    private fun getEventClass(eventType: String): KClass<out ProductEvent>? {
        return EVENT_TYPE_MAP[eventType]
    }

    /**
     * Migrate event JSON from older versions to current version.
     * Override this method to handle schema evolution.
     */
    private fun migrateEventIfNeeded(eventType: String, fromVersion: Int, json: String): String {
        val currentVersion = CURRENT_VERSIONS[eventType] ?: 1

        if (fromVersion >= currentVersion) {
            return json
        }

        // Apply migrations sequentially
        var migratedJson = json
        for (version in fromVersion until currentVersion) {
            migratedJson = applyMigration(eventType, version, migratedJson)
        }

        logger.debug("Migrated event $eventType from v$fromVersion to v$currentVersion")
        return migratedJson
    }

    /**
     * Apply a single version migration.
     * Add specific migration logic here as events evolve.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun applyMigration(eventType: String, fromVersion: Int, json: String): String {
        // Example migration structure (add real migrations as needed):
        // when {
        //     eventType == "ProductCreated" && fromVersion == 1 -> migrateProductCreatedV1ToV2(json)
        //     else -> json
        // }
        return json
    }

    companion object {
        /**
         * Map of event type names to their Kotlin classes.
         */
        private val EVENT_TYPE_MAP: Map<String, KClass<out ProductEvent>> = mapOf(
            "ProductCreated" to ProductCreated::class,
            "ProductUpdated" to ProductUpdated::class,
            "ProductPriceChanged" to ProductPriceChanged::class,
            "ProductActivated" to ProductActivated::class,
            "ProductDiscontinued" to ProductDiscontinued::class,
            "ProductDeleted" to ProductDeleted::class
        )

        /**
         * Current schema versions for each event type.
         */
        private val CURRENT_VERSIONS: Map<String, Int> = mapOf(
            "ProductCreated" to 1,
            "ProductUpdated" to 1,
            "ProductPriceChanged" to 1,
            "ProductActivated" to 1,
            "ProductDiscontinued" to 1,
            "ProductDeleted" to 1
        )
    }
}

/**
 * Exception thrown when event deserialization fails.
 */
class EventDeserializationException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
