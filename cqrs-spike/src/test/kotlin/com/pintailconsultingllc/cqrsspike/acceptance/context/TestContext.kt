package com.pintailconsultingllc.cqrsspike.acceptance.context

import io.cucumber.spring.ScenarioScope
import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Scenario-scoped context for sharing state between Cucumber steps.
 *
 * This class maintains state that needs to be passed between different
 * step definitions within a single scenario. A fresh instance is created
 * for each new scenario via the @ScenarioScope annotation.
 *
 * Usage:
 * - Store product IDs after creation for use in subsequent steps
 * - Track HTTP response status and body for validation
 * - Maintain lists of created resources for cleanup
 * - Store error messages for assertion in error scenarios
 */
@Component
@ScenarioScope
class TestContext {

    // Product-related state
    var currentProductId: UUID? = null
    var createdProductIds: MutableList<UUID> = mutableListOf()

    // HTTP response state
    var lastResponseStatus: HttpStatusCode? = null
    var lastResponseBody: String? = null
    var lastResponseHeaders: MutableMap<String, String> = mutableMapOf()

    // Error handling state
    var lastErrorMessage: String? = null
    var lastErrorCode: String? = null
    var lastValidationErrors: MutableList<ValidationError> = mutableListOf()

    // Event sourcing state
    var lastEventId: UUID? = null
    var capturedEvents: MutableList<CapturedEvent> = mutableListOf()

    // Query results state
    var lastQueryResults: MutableList<ProductResult> = mutableListOf()
    var totalResultCount: Long = 0
    var currentPage: Int = 0
    var totalPages: Int = 0

}

/**
 * Represents a validation error returned from the API.
 */
data class ValidationError(
    val field: String,
    val message: String
)

/**
 * Represents a captured domain event for verification.
 */
data class CapturedEvent(
    val eventId: UUID,
    val eventType: String,
    val aggregateId: UUID,
    val version: Long,
    val occurredAt: String,
    val eventData: Map<String, Any?>
)

/**
 * Represents a product result from query operations.
 */
data class ProductResult(
    val id: UUID,
    val sku: String,
    val name: String,
    val description: String?,
    val priceCents: Int,
    val status: String,
    val version: Long
)
