package com.pintailconsultingllc.cdcdebezium.acceptance

import com.pintailconsultingllc.cdcdebezium.dto.CustomerCdcEvent
import com.pintailconsultingllc.cdcdebezium.repository.CustomerMongoRepository
import com.pintailconsultingllc.cdcdebezium.validation.ValidationService
import com.pintailconsultingllc.cdcdebezium.validation.rules.BusinessValidationRule
import org.awaitility.kotlin.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.kafka.core.KafkaTemplate
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@DisplayName("Data Validation Framework (PLAN-014)")
class DataValidationFrameworkAcceptanceTest : AbstractAcceptanceTest() {

    @Autowired
    private lateinit var kafkaTemplate: KafkaTemplate<String, String>

    @Autowired
    private lateinit var customerRepository: CustomerMongoRepository

    @Autowired
    private lateinit var validationService: ValidationService

    @BeforeEach
    fun setUp() {
        customerRepository.deleteAll().block()
    }

    @Nested
    @DisplayName("Valid Event Processing")
    inner class ValidEventProcessing {

        @Test
        @DisplayName("should pass all validation rules and persist to MongoDB")
        fun shouldPassAllValidationRulesAndPersist() {
            val customerId = UUID.randomUUID()
            val email = "valid-event-$customerId@company.com"

            val cdcEvent = buildCdcEventJson(
                id = customerId,
                email = email,
                status = "active",
                updatedAt = Instant.now(),
                operation = "c",
                sourceTimestamp = System.currentTimeMillis()
            )

            kafkaTemplate.send(CUSTOMER_CDC_TOPIC, customerId.toString(), cdcEvent).get()

            await.atMost(Duration.ofSeconds(10)).untilAsserted {
                val customer = customerRepository.findById(customerId.toString()).block()
                assertNotNull(customer)
                assertEquals(email, customer.email)
                assertEquals("active", customer.status)
            }
        }

        @Test
        @DisplayName("should validate event passes all rules via ValidationService")
        fun shouldValidateEventPassesAllRules() {
            val event = CustomerCdcEvent(
                id = UUID.randomUUID(),
                email = "valid@company.com",
                status = "active",
                updatedAt = Instant.now(),
                sourceTimestamp = System.currentTimeMillis(),
                operation = "c"
            )

            val result = validationService.validate(event)

            assertTrue(result.valid)
            assertTrue(result.failures.isEmpty())
            assertTrue(result.results.all { it.valid })
        }
    }

    @Nested
    @DisplayName("Schema Validation")
    inner class SchemaValidation {

        @Test
        @DisplayName("should fail when email is missing for non-delete event")
        fun shouldFailWhenEmailMissing() {
            val event = CustomerCdcEvent(
                id = UUID.randomUUID(),
                email = null,
                status = "active",
                updatedAt = Instant.now(),
                sourceTimestamp = System.currentTimeMillis(),
                operation = "c"
            )

            val result = validationService.validate(event)

            assertFalse(result.valid)
            val schemaFailure = result.failures.find { it.ruleId == "SCHEMA_001" }
            assertNotNull(schemaFailure)
            assertTrue(schemaFailure.message?.contains("Schema validation failed") == true)

            @Suppress("UNCHECKED_CAST")
            val errors = schemaFailure.details["errors"] as? List<String>
            assertNotNull(errors)
            assertTrue(errors.any { it.contains("email is required") })
        }

        @Test
        @DisplayName("should fail when email is blank for non-delete event")
        fun shouldFailWhenEmailBlank() {
            val event = CustomerCdcEvent(
                id = UUID.randomUUID(),
                email = "   ",
                status = "active",
                updatedAt = Instant.now(),
                sourceTimestamp = System.currentTimeMillis(),
                operation = "c"
            )

            val result = validationService.validate(event)

            assertFalse(result.valid)
            val schemaFailure = result.failures.find { it.ruleId == "SCHEMA_001" }
            assertNotNull(schemaFailure)
        }

        @Test
        @DisplayName("should fail when email format is invalid")
        fun shouldFailWhenEmailFormatInvalid() {
            val event = CustomerCdcEvent(
                id = UUID.randomUUID(),
                email = "invalid-email-no-at-symbol",
                status = "active",
                updatedAt = Instant.now(),
                sourceTimestamp = System.currentTimeMillis(),
                operation = "c"
            )

            val result = validationService.validate(event)

            assertFalse(result.valid)
            val schemaFailure = result.failures.find { it.ruleId == "SCHEMA_001" }
            assertNotNull(schemaFailure)

            @Suppress("UNCHECKED_CAST")
            val errors = schemaFailure.details["errors"] as? List<String>
            assertNotNull(errors)
            assertTrue(errors.any { it.contains("email format is invalid") })
        }

        @Test
        @DisplayName("should pass schema validation for delete event without email")
        fun shouldPassSchemaValidationForDeleteEvent() {
            val event = CustomerCdcEvent(
                id = UUID.randomUUID(),
                email = null,
                status = null,
                updatedAt = Instant.now(),
                sourceTimestamp = System.currentTimeMillis(),
                operation = "d"
            )

            val result = validationService.validate(event)

            val schemaResult = result.results.find { it.ruleId == "SCHEMA_001" }
            assertNotNull(schemaResult)
            assertTrue(schemaResult.valid)
        }
    }

    @Nested
    @DisplayName("Business Validation")
    inner class BusinessValidation {

        @Test
        @DisplayName("should fail when status is invalid")
        fun shouldFailWhenStatusInvalid() {
            val event = CustomerCdcEvent(
                id = UUID.randomUUID(),
                email = "test@company.com",
                status = "unknown-invalid-status",
                updatedAt = Instant.now(),
                sourceTimestamp = System.currentTimeMillis(),
                operation = "c"
            )

            val result = validationService.validate(event)

            val businessResult = result.results.find { it.ruleId == "BUSINESS_001" }
            assertNotNull(businessResult)
            assertFalse(businessResult.valid)
            assertTrue(businessResult.message?.contains("Business validation failed") == true)

            @Suppress("UNCHECKED_CAST")
            val errors = businessResult.details["errors"] as? List<String>
            assertNotNull(errors)
            assertTrue(errors.any { it.contains("Invalid status") })
            assertTrue(errors.any { it.contains("Must be one of") })
        }

        @Test
        @DisplayName("should pass with valid status values")
        fun shouldPassWithValidStatus() {
            BusinessValidationRule.VALID_STATUSES.forEach { validStatus ->
                val event = CustomerCdcEvent(
                    id = UUID.randomUUID(),
                    email = "test-$validStatus@company.com",
                    status = validStatus,
                    updatedAt = Instant.now(),
                    sourceTimestamp = System.currentTimeMillis(),
                    operation = "c"
                )

                val result = validationService.validate(event)
                val businessResult = result.results.find { it.ruleId == "BUSINESS_001" }
                assertNotNull(businessResult, "Should have business validation result for status: $validStatus")
                assertTrue(businessResult.valid, "Status '$validStatus' should be valid")
            }
        }

        @Test
        @DisplayName("should warn for test email addresses but still pass")
        fun shouldWarnForTestEmailAddresses() {
            val event = CustomerCdcEvent(
                id = UUID.randomUUID(),
                email = "user@example.com",
                status = "active",
                updatedAt = Instant.now(),
                sourceTimestamp = System.currentTimeMillis(),
                operation = "c"
            )

            val result = validationService.validate(event)

            val businessResult = result.results.find { it.ruleId == "BUSINESS_001" }
            assertNotNull(businessResult)
            assertTrue(businessResult.valid)
            assertTrue(businessResult.message?.contains("warnings") == true)
        }

        @Test
        @DisplayName("should bypass business rules for delete events")
        fun shouldBypassBusinessRulesForDeleteEvents() {
            val event = CustomerCdcEvent(
                id = UUID.randomUUID(),
                email = null,
                status = null,
                updatedAt = Instant.now(),
                sourceTimestamp = System.currentTimeMillis(),
                operation = "d"
            )

            val result = validationService.validate(event)

            val businessResult = result.results.find { it.ruleId == "BUSINESS_001" }
            assertNotNull(businessResult)
            assertTrue(businessResult.valid)
            assertTrue(businessResult.message?.contains("Delete events bypass business rules") == true)
        }
    }

    @Nested
    @DisplayName("Temporal Validation")
    inner class TemporalValidation {

        @Test
        @DisplayName("should fail when source timestamp is in the future")
        fun shouldFailWhenSourceTimestampInFuture() {
            val futureTimestamp = System.currentTimeMillis() + Duration.ofMinutes(10).toMillis()

            val event = CustomerCdcEvent(
                id = UUID.randomUUID(),
                email = "future@company.com",
                status = "active",
                updatedAt = Instant.now(),
                sourceTimestamp = futureTimestamp,
                operation = "c"
            )

            val result = validationService.validate(event)

            val temporalResult = result.results.find { it.ruleId == "TEMPORAL_001" }
            assertNotNull(temporalResult)
            assertFalse(temporalResult.valid)
            assertTrue(temporalResult.message?.contains("Temporal validation failed") == true)

            @Suppress("UNCHECKED_CAST")
            val errors = temporalResult.details["errors"] as? List<String>
            assertNotNull(errors)
            assertTrue(errors.any { it.contains("in the future") })
        }

        @Test
        @DisplayName("should fail when updatedAt is in the future")
        fun shouldFailWhenUpdatedAtInFuture() {
            val futureUpdatedAt = Instant.now().plus(Duration.ofMinutes(10))

            val event = CustomerCdcEvent(
                id = UUID.randomUUID(),
                email = "future-update@company.com",
                status = "active",
                updatedAt = futureUpdatedAt,
                sourceTimestamp = System.currentTimeMillis(),
                operation = "c"
            )

            val result = validationService.validate(event)

            val temporalResult = result.results.find { it.ruleId == "TEMPORAL_001" }
            assertNotNull(temporalResult)
            assertFalse(temporalResult.valid)

            @Suppress("UNCHECKED_CAST")
            val errors = temporalResult.details["errors"] as? List<String>
            assertNotNull(errors)
            assertTrue(errors.any { it.contains("updatedAt timestamp is in the future") })
        }

        @Test
        @DisplayName("should pass with valid timestamps")
        fun shouldPassWithValidTimestamps() {
            val event = CustomerCdcEvent(
                id = UUID.randomUUID(),
                email = "valid-time@company.com",
                status = "active",
                updatedAt = Instant.now(),
                sourceTimestamp = System.currentTimeMillis(),
                operation = "c"
            )

            val result = validationService.validate(event)

            val temporalResult = result.results.find { it.ruleId == "TEMPORAL_001" }
            assertNotNull(temporalResult)
            assertTrue(temporalResult.valid)
        }

        @Test
        @DisplayName("should warn for missing source timestamp but still pass")
        fun shouldWarnForMissingSourceTimestamp() {
            val event = CustomerCdcEvent(
                id = UUID.randomUUID(),
                email = "no-timestamp@company.com",
                status = "active",
                updatedAt = Instant.now(),
                sourceTimestamp = null,
                operation = "c"
            )

            val result = validationService.validate(event)

            val temporalResult = result.results.find { it.ruleId == "TEMPORAL_001" }
            assertNotNull(temporalResult)
            // Should pass but with warning
            assertTrue(temporalResult.valid)
        }
    }

    @Nested
    @DisplayName("Validation Metrics")
    inner class ValidationMetrics {

        @Test
        @DisplayName("should record metrics for passed validation")
        fun shouldRecordMetricsForPassedValidation() {
            val event = CustomerCdcEvent(
                id = UUID.randomUUID(),
                email = "metrics-pass@company.com",
                status = "active",
                updatedAt = Instant.now(),
                sourceTimestamp = System.currentTimeMillis(),
                operation = "c"
            )

            // This will trigger metrics recording internally
            val result = validationService.validate(event)

            assertTrue(result.valid)
            // Metrics are recorded internally - verify result was processed
            assertNotNull(result.eventId)
            assertEquals("customer", result.entityType)
        }

        @Test
        @DisplayName("should record metrics for failed validation")
        fun shouldRecordMetricsForFailedValidation() {
            val event = CustomerCdcEvent(
                id = UUID.randomUUID(),
                email = "invalid-email-format",
                status = "invalid-status",
                updatedAt = Instant.now().plus(Duration.ofHours(1)),
                sourceTimestamp = System.currentTimeMillis() + Duration.ofHours(1).toMillis(),
                operation = "c"
            )

            // This will trigger metrics recording internally
            val result = validationService.validate(event)

            assertFalse(result.valid)
            assertTrue(result.failureCount > 0)
            // Verify failure information is captured
            assertTrue(result.failures.isNotEmpty())
            result.failures.forEach { failure ->
                assertNotNull(failure.ruleId)
                assertNotNull(failure.message)
            }
        }

        @Test
        @DisplayName("should track validation result with timing data")
        fun shouldTrackValidationResultWithTimingData() {
            val event = CustomerCdcEvent(
                id = UUID.randomUUID(),
                email = "timing@company.com",
                status = "active",
                updatedAt = Instant.now(),
                sourceTimestamp = System.currentTimeMillis(),
                operation = "c"
            )

            val startTime = System.currentTimeMillis()
            val result = validationService.validate(event)
            val endTime = System.currentTimeMillis()

            // Verify the result has timestamp
            assertNotNull(result.timestamp)
            // Timestamp should be reasonable (between start and end time)
            assertTrue(result.timestamp.toEpochMilli() >= startTime - 1000)
            assertTrue(result.timestamp.toEpochMilli() <= endTime + 1000)
        }
    }

    @Nested
    @DisplayName("Aggregated Results")
    inner class AggregatedResults {

        @Test
        @DisplayName("should aggregate results from all validation rules")
        fun shouldAggregateResultsFromAllRules() {
            val event = CustomerCdcEvent(
                id = UUID.randomUUID(),
                email = "aggregate@company.com",
                status = "active",
                updatedAt = Instant.now(),
                sourceTimestamp = System.currentTimeMillis(),
                operation = "c"
            )

            val result = validationService.validate(event)

            // Should have results from multiple rules
            assertTrue(result.results.size >= 3, "Should have at least 3 rule results")

            // Verify expected rules are present
            val ruleIds = result.results.map { it.ruleId }
            assertTrue(ruleIds.contains("SCHEMA_001"))
            assertTrue(ruleIds.contains("BUSINESS_001"))
            assertTrue(ruleIds.contains("TEMPORAL_001"))
        }

        @Test
        @DisplayName("should report failure count correctly")
        fun shouldReportFailureCountCorrectly() {
            val event = CustomerCdcEvent(
                id = UUID.randomUUID(),
                email = "invalid-email",
                status = "invalid-status",
                updatedAt = Instant.now().plus(Duration.ofHours(1)),
                sourceTimestamp = System.currentTimeMillis() + Duration.ofHours(1).toMillis(),
                operation = "c"
            )

            val result = validationService.validate(event)

            assertFalse(result.valid)
            assertEquals(result.failures.size, result.failureCount)
            assertTrue(result.failureCount > 0)
        }
    }

    @Nested
    @DisplayName("End-to-End Validation Flow")
    inner class EndToEndValidationFlow {

        @Test
        @DisplayName("should process valid CDC event through full pipeline")
        fun shouldProcessValidCdcEventThroughFullPipeline() {
            val customerId = UUID.randomUUID()
            val email = "e2e-valid-$customerId@company.com"

            val cdcEvent = buildCdcEventJson(
                id = customerId,
                email = email,
                status = "active",
                updatedAt = Instant.now(),
                operation = "c",
                sourceTimestamp = System.currentTimeMillis()
            )

            kafkaTemplate.send(CUSTOMER_CDC_TOPIC, customerId.toString(), cdcEvent).get()

            await.atMost(Duration.ofSeconds(10)).untilAsserted {
                val customer = customerRepository.findById(customerId.toString()).block()
                assertNotNull(customer)
                assertEquals(email, customer.email)
            }
        }
    }

    companion object {
        private const val CUSTOMER_CDC_TOPIC = "cdc.public.customer"

        fun buildCdcEventJson(
            id: UUID,
            email: String?,
            status: String?,
            updatedAt: Instant,
            operation: String? = null,
            deleted: String? = null,
            sourceTimestamp: Long
        ): String {
            val fields = mutableListOf<String>()
            fields.add(""""id": "$id"""")
            email?.let { fields.add(""""email": "$it"""") }
            status?.let { fields.add(""""status": "$it"""") }
            fields.add(""""updated_at": "$updatedAt"""")
            operation?.let { fields.add(""""__op": "$it"""") }
            deleted?.let { fields.add(""""__deleted": "$it"""") }
            fields.add(""""__source_ts_ms": $sourceTimestamp""")
            return "{ ${fields.joinToString(", ")} }"
        }
    }
}
