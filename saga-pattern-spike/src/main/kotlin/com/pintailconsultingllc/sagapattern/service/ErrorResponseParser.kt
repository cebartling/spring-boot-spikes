package com.pintailconsultingllc.sagapattern.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import tools.jackson.module.kotlin.jacksonObjectMapper

/**
 * Utility for parsing error responses from external services.
 *
 * Provides robust JSON-based parsing instead of fragile regex patterns.
 * Falls back gracefully when the response body is not valid JSON or
 * doesn't contain expected fields.
 */
@Component
class ErrorResponseParser {

    private val objectMapper = jacksonObjectMapper()
    private val logger = LoggerFactory.getLogger(ErrorResponseParser::class.java)

    /**
     * Extract the error code from a JSON error response.
     *
     * Looks for an "error" field in the response body. Falls back to
     * "errorCode" and "code" fields if "error" is not found.
     *
     * @param responseBody The response body string
     * @return The error code if found, null otherwise
     */
    fun extractErrorCode(responseBody: String): String? {
        return parseJsonField(responseBody, "error")
            ?: parseJsonField(responseBody, "errorCode")
            ?: parseJsonField(responseBody, "code")
    }

    /**
     * Determine if an error is retryable based on the response.
     *
     * Looks for a "retryable" boolean field in the response body.
     * Defaults to false if the field is missing or the body is invalid.
     *
     * @param responseBody The response body string
     * @return true if the error is marked as retryable, false otherwise
     */
    fun isRetryable(responseBody: String): Boolean {
        return try {
            val json = objectMapper.readTree(responseBody)
            json?.get("retryable")?.asBoolean() ?: false
        } catch (e: Exception) {
            logger.debug("Could not parse retryable flag from response: {}", e.message)
            false
        }
    }

    /**
     * Extract the error message from a JSON error response.
     *
     * Looks for a "message" field in the response body. Falls back to
     * "error" and "detail" fields if "message" is not found.
     *
     * @param responseBody The response body string
     * @return The error message if found, null otherwise
     */
    fun extractErrorMessage(responseBody: String): String? {
        return parseJsonField(responseBody, "message")
            ?: parseJsonField(responseBody, "error")
            ?: parseJsonField(responseBody, "detail")
    }

    /**
     * Parse a complete error response into a structured result.
     *
     * @param responseBody The response body string
     * @return Parsed error information
     */
    fun parseErrorResponse(responseBody: String): ParsedErrorResponse {
        return ParsedErrorResponse(
            errorCode = extractErrorCode(responseBody),
            message = extractErrorMessage(responseBody),
            isRetryable = isRetryable(responseBody),
            rawBody = responseBody
        )
    }

    private fun parseJsonField(responseBody: String, fieldName: String): String? {
        return try {
            val json = objectMapper.readTree(responseBody)
            json?.get(fieldName)?.asText()?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            logger.debug("Could not parse '{}' from response: {}", fieldName, e.message)
            null
        }
    }
}

/**
 * Structured representation of a parsed error response.
 */
data class ParsedErrorResponse(
    val errorCode: String?,
    val message: String?,
    val isRetryable: Boolean,
    val rawBody: String
)
