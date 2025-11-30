package com.pintailconsultingllc.cqrsspike.acceptance.helpers

import com.fasterxml.jackson.databind.ObjectMapper
import com.pintailconsultingllc.cqrsspike.acceptance.context.TestContext
import com.pintailconsultingllc.cqrsspike.acceptance.context.ValidationError
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Shared helper class for parsing HTTP responses in step definitions.
 *
 * This class provides common response parsing utilities used across
 * multiple step definition classes to reduce code duplication.
 */
@Component
class ResponseParsingHelper(
    private val objectMapper: ObjectMapper,
    private val testContext: TestContext
) {

    /**
     * Extracts the product ID from the current response body and stores it in the test context.
     *
     * @return the extracted product ID, or null if not found
     */
    fun extractProductIdFromResponse(): UUID? {
        val body = testContext.lastResponseBody ?: return null
        return try {
            val jsonNode = objectMapper.readTree(body)
            val productIdStr = jsonNode.get("productId")?.asText()
            if (productIdStr != null) {
                val productId = UUID.fromString(productIdStr)
                testContext.currentProductId = productId
                testContext.createdProductIds.add(productId)
                productId
            } else {
                null
            }
        } catch (e: Exception) {
            // Response may not contain productId
            null
        }
    }

    /**
     * Extracts the version from the current response body.
     *
     * @return the extracted version, or null if not found
     */
    fun extractVersionFromResponse(): Long? {
        val body = testContext.lastResponseBody ?: return null
        return try {
            val jsonNode = objectMapper.readTree(body)
            jsonNode.get("version")?.asLong()
        } catch (e: Exception) {
            // Response may not contain version
            null
        }
    }

    /**
     * Parses error response and stores error details in the test context.
     * Extracts message, code, and validation errors if present.
     *
     * Handles multiple error response formats:
     * - ApiErrorResponse with "details" array containing ValidationError objects
     * - CommandErrorResponse with "code" and "details" map
     * - Generic ErrorResponse with "message" field
     */
    fun parseErrorResponse() {
        val body = testContext.lastResponseBody ?: return
        try {
            val jsonNode = objectMapper.readTree(body)
            testContext.lastErrorMessage = jsonNode.get("message")?.asText()
            testContext.lastErrorCode = jsonNode.get("code")?.asText()

            // Clear previous validation errors
            testContext.lastValidationErrors.clear()

            // Parse validation errors - handle "errors" array format
            val errors = jsonNode.get("errors")
            if (errors != null && errors.isArray) {
                errors.forEach { error ->
                    val field = error.get("field")?.asText() ?: ""
                    val message = error.get("message")?.asText() ?: ""
                    testContext.lastValidationErrors.add(ValidationError(field, message))
                }
            }

            // Parse validation errors - handle "details" array format (ApiErrorResponse)
            val details = jsonNode.get("details")
            if (details != null && details.isArray) {
                details.forEach { detail ->
                    val field = detail.get("field")?.asText() ?: ""
                    val message = detail.get("message")?.asText() ?: ""
                    if (field.isNotEmpty()) {
                        testContext.lastValidationErrors.add(ValidationError(field, message))
                    }
                }
            }
        } catch (e: Exception) {
            // Response may not be JSON or may not have expected structure
        }
    }
}
