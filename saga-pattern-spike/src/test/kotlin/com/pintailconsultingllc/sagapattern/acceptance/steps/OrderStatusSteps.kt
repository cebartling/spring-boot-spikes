package com.pintailconsultingllc.sagapattern.acceptance.steps

import com.pintailconsultingllc.sagapattern.acceptance.config.TestContext
import com.pintailconsultingllc.sagapattern.domain.Order
import com.pintailconsultingllc.sagapattern.domain.OrderStatus
import com.pintailconsultingllc.sagapattern.domain.SagaExecution
import com.pintailconsultingllc.sagapattern.domain.SagaStatus
import com.pintailconsultingllc.sagapattern.domain.SagaStepResult
import com.pintailconsultingllc.sagapattern.domain.StepStatus
import com.pintailconsultingllc.sagapattern.repository.OrderRepository
import com.pintailconsultingllc.sagapattern.repository.SagaExecutionRepository
import com.pintailconsultingllc.sagapattern.repository.SagaStepResultRepository
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Step definitions for SAGA-003: View Order Status During Processing
 */
class OrderStatusSteps(
    @Autowired private val testContext: TestContext,
    @Autowired private val orderRepository: OrderRepository,
    @Autowired private val sagaExecutionRepository: SagaExecutionRepository,
    @Autowired private val sagaStepResultRepository: SagaStepResultRepository
) {
    @Value("\${local.server.port:8080}")
    private var serverPort: Int = 8080

    private val webClient: WebClient by lazy {
        WebClient.builder()
            .baseUrl("http://localhost:$serverPort")
            .build()
    }

    // ==================== Given Steps ====================

    @Given("I have placed an order that is currently processing")
    fun iHavePlacedAnOrderThatIsCurrentlyProcessing() = runBlocking {
        val order = createTestOrder(OrderStatus.PROCESSING)
        testContext.orderId = order.id
        testContext.customerId = order.customerId

        // Create saga execution in progress
        val sagaExecution = createSagaExecution(order.id, SagaStatus.IN_PROGRESS)

        // Create step results - first completed, second in progress, third pending
        createStepResult(sagaExecution.id, "Inventory Reservation", 1, StepStatus.COMPLETED)
        createStepResult(sagaExecution.id, "Payment Processing", 2, StepStatus.IN_PROGRESS)
        createStepResult(sagaExecution.id, "Shipping Arrangement", 3, StepStatus.PENDING)
    }

    @Given("I have an order where inventory and payment steps are complete")
    fun iHaveAnOrderWhereInventoryAndPaymentStepsAreComplete() = runBlocking {
        val order = createTestOrder(OrderStatus.PROCESSING)
        testContext.orderId = order.id
        testContext.customerId = order.customerId

        // Create saga execution in progress
        val sagaExecution = createSagaExecution(order.id, SagaStatus.IN_PROGRESS)

        // Create step results
        createStepResult(sagaExecution.id, "Inventory Reservation", 1, StepStatus.COMPLETED)
        createStepResult(sagaExecution.id, "Payment Processing", 2, StepStatus.COMPLETED)
    }

    @Given("the shipping step is in progress")
    fun theShippingStepIsInProgress() = runBlocking {
        val sagaExecution = sagaExecutionRepository.findByOrderId(testContext.orderId!!)
        assertNotNull(sagaExecution, "Saga execution should exist")

        createStepResult(sagaExecution.id, "Shipping Arrangement", 3, StepStatus.IN_PROGRESS)
    }

    @Given("I have an order where the payment step failed")
    fun iHaveAnOrderWhereThePaymentStepFailed() = runBlocking {
        val order = createTestOrder(OrderStatus.FAILED)
        testContext.orderId = order.id
        testContext.customerId = order.customerId

        // Create saga execution in failed state
        val sagaExecution = createSagaExecution(
            order.id,
            SagaStatus.FAILED,
            failedStep = 1,
            failureReason = "Card declined"
        )

        // Create step results
        createStepResult(sagaExecution.id, "Inventory Reservation", 1, StepStatus.COMPLETED)
        createStepResult(
            sagaExecution.id, "Payment Processing", 2, StepStatus.FAILED,
            errorMessage = "Card declined"
        )
        createStepResult(sagaExecution.id, "Shipping Arrangement", 3, StepStatus.PENDING)
    }

    @Given("I have an order that is currently being compensated")
    fun iHaveAnOrderThatIsCurrentlyBeingCompensated() = runBlocking {
        val order = createTestOrder(OrderStatus.PROCESSING)
        testContext.orderId = order.id
        testContext.customerId = order.customerId

        // Create saga execution in compensating state
        val sagaExecution = createSagaExecution(
            order.id,
            SagaStatus.COMPENSATING,
            compensationStartedAt = Instant.now()
        )

        // Create step results with compensation in progress
        createStepResult(sagaExecution.id, "Inventory Reservation", 1, StepStatus.COMPENSATING)
        createStepResult(
            sagaExecution.id, "Payment Processing", 2, StepStatus.FAILED,
            errorMessage = "Card declined"
        )
        createStepResult(sagaExecution.id, "Shipping Arrangement", 3, StepStatus.PENDING)
    }

    @Given("I have a successfully completed order")
    fun iHaveASuccessfullyCompletedOrder() = runBlocking {
        val order = createTestOrder(OrderStatus.COMPLETED)
        testContext.orderId = order.id
        testContext.customerId = order.customerId

        // Create completed saga execution
        val sagaExecution = createSagaExecution(
            order.id,
            SagaStatus.COMPLETED,
            completedAt = Instant.now()
        )

        // All steps completed
        createStepResult(sagaExecution.id, "Inventory Reservation", 1, StepStatus.COMPLETED)
        createStepResult(sagaExecution.id, "Payment Processing", 2, StepStatus.COMPLETED)
        createStepResult(sagaExecution.id, "Shipping Arrangement", 3, StepStatus.COMPLETED)
    }

    @Given("I have placed an order")
    fun iHavePlacedAnOrder() = runBlocking {
        val order = createTestOrder(OrderStatus.PROCESSING)
        testContext.orderId = order.id
        testContext.customerId = order.customerId

        // Create in-progress saga execution
        val sagaExecution = createSagaExecution(order.id, SagaStatus.IN_PROGRESS)

        // Create step results
        createStepResult(sagaExecution.id, "Inventory Reservation", 1, StepStatus.COMPLETED)
        createStepResult(sagaExecution.id, "Payment Processing", 2, StepStatus.IN_PROGRESS)
        createStepResult(sagaExecution.id, "Shipping Arrangement", 3, StepStatus.PENDING)
    }

    @Given("I have an order in progress")
    fun iHaveAnOrderInProgress() = runBlocking {
        iHavePlacedAnOrderThatIsCurrentlyProcessing()
    }

    // ==================== When Steps ====================

    @When("I check my order status")
    fun iCheckMyOrderStatus() {
        iRequestTheOrderStatusViaApi()
    }

    @When("I request the order status via API")
    fun iRequestTheOrderStatusViaApi() {
        assertNotNull(testContext.orderId, "Order ID should be set")

        val responseEntity = webClient.get()
            .uri("/api/orders/${testContext.orderId}/status")
            .accept(MediaType.APPLICATION_JSON)
            .exchangeToMono { response ->
                response.bodyToMono(String::class.java)
                    .defaultIfEmpty("{}")
                    .map { bodyString ->
                        val mapper = tools.jackson.module.kotlin.jacksonObjectMapper()
                        @Suppress("UNCHECKED_CAST")
                        val body = try {
                            mapper.readValue(bodyString, Map::class.java) as Map<String, Any>
                        } catch (_: Exception) {
                            emptyMap<String, Any>()
                        }
                        Pair(response.headers().asHttpHeaders(), body)
                    }
            }
            .block()

        testContext.responseHeaders = responseEntity?.first
        testContext.statusResponse = responseEntity?.second
    }

    // ==================== Then Steps ====================

    @Then("I should see the overall status as {string}")
    fun iShouldSeeTheOverallStatusAs(expectedStatus: String) {
        val response = testContext.statusResponse
        assertNotNull(response, "Status response should exist")
        assertEquals(expectedStatus, response["overallStatus"], "Overall status should match")
    }

    @Then("I should see which step is currently in progress")
    fun iShouldSeeWhichStepIsCurrentlyInProgress() {
        val response = testContext.statusResponse
        assertNotNull(response, "Status response should exist")
        assertNotNull(response["currentStep"], "Current step should be set when in progress")
    }

    @Then("I should see which steps have completed")
    fun iShouldSeeWhichStepsHaveCompleted() {
        val response = testContext.statusResponse
        assertNotNull(response, "Status response should exist")

        @Suppress("UNCHECKED_CAST")
        val steps = response["steps"] as? List<Map<String, Any>>
        assertNotNull(steps, "Steps should exist")

        val completedSteps = steps.filter { it["status"] == "COMPLETED" }
        assertTrue(completedSteps.isNotEmpty(), "Should have at least one completed step")
    }

    @Then("I should see which steps are pending")
    fun iShouldSeeWhichStepsArePending() {
        val response = testContext.statusResponse
        assertNotNull(response, "Status response should exist")

        @Suppress("UNCHECKED_CAST")
        val steps = response["steps"] as? List<Map<String, Any>>
        assertNotNull(steps, "Steps should exist")

        val pendingSteps = steps.filter { it["status"] == "PENDING" }
        assertTrue(pendingSteps.isNotEmpty(), "Should have at least one pending step")
    }

    @Then("I should see the following step statuses:")
    fun iShouldSeeTheFollowingStepStatuses(dataTable: DataTable) {
        val response = testContext.statusResponse
        assertNotNull(response, "Status response should exist")

        @Suppress("UNCHECKED_CAST")
        val steps = response["steps"] as? List<Map<String, Any>>
        assertNotNull(steps, "Steps should exist")

        val expectedStatuses = dataTable.asMaps()
        for (expected in expectedStatuses) {
            val stepName = expected["step"]
            val expectedStatus = expected["status"]

            val step = steps.find { it["name"] == stepName }
            assertNotNull(step, "Step '$stepName' should exist")
            assertEquals(expectedStatus, step["status"], "Status for '$stepName' should match")
        }
    }

    @Then("I should see the payment step marked as {string}")
    fun iShouldSeeThePaymentStepMarkedAs(expectedStatus: String) {
        val response = testContext.statusResponse
        assertNotNull(response, "Status response should exist")

        @Suppress("UNCHECKED_CAST")
        val steps = response["steps"] as? List<Map<String, Any>>
        assertNotNull(steps, "Steps should exist")

        val paymentStep = steps.find { it["name"] == "Payment Processing" }
        assertNotNull(paymentStep, "Payment step should exist")
        assertEquals(expectedStatus, paymentStep["status"], "Payment step status should match")
    }

    @Then("I should see the failure reason for the payment step")
    fun iShouldSeeTheFailureReasonForThePaymentStep() {
        val response = testContext.statusResponse
        assertNotNull(response, "Status response should exist")

        @Suppress("UNCHECKED_CAST")
        val steps = response["steps"] as? List<Map<String, Any>>
        assertNotNull(steps, "Steps should exist")

        val paymentStep = steps.find { it["name"] == "Payment Processing" }
        assertNotNull(paymentStep, "Payment step should exist")
        assertNotNull(paymentStep["errorMessage"], "Payment step should have error message")
    }

    @Then("I should see which steps are being compensated")
    fun iShouldSeeWhichStepsAreBeingCompensated() {
        val response = testContext.statusResponse
        assertNotNull(response, "Status response should exist")

        @Suppress("UNCHECKED_CAST")
        val steps = response["steps"] as? List<Map<String, Any>>
        assertNotNull(steps, "Steps should exist")

        val compensatingSteps = steps.filter { it["status"] == "COMPENSATING" }
        assertTrue(compensatingSteps.isNotEmpty(), "Should have at least one compensating step")
    }

    @Then("I should see which steps have been compensated")
    fun iShouldSeeWhichStepsHaveBeenCompensated() {
        val response = testContext.statusResponse
        assertNotNull(response, "Status response should exist")

        @Suppress("UNCHECKED_CAST")
        val steps = response["steps"] as? List<Map<String, Any>>
        assertNotNull(steps, "Steps should exist")

        // During compensation, steps might still be COMPENSATING or already COMPENSATED
        val compensatedSteps = steps.filter {
            it["status"] == "COMPENSATED" || it["status"] == "COMPENSATING"
        }
        assertTrue(compensatedSteps.isNotEmpty(), "Should have steps in compensation")
    }

    @Then("all steps should show status {string}")
    fun allStepsShouldShowStatus(expectedStatus: String) {
        val response = testContext.statusResponse
        assertNotNull(response, "Status response should exist")

        @Suppress("UNCHECKED_CAST")
        val steps = response["steps"] as? List<Map<String, Any>>
        assertNotNull(steps, "Steps should exist")
        assertTrue(steps.isNotEmpty(), "Should have steps")

        for (step in steps) {
            assertEquals(expectedStatus, step["status"], "Step '${step["name"]}' should have status $expectedStatus")
        }
    }

    @Then("I should see the completion timestamp")
    fun iShouldSeeTheCompletionTimestamp() {
        val response = testContext.statusResponse
        assertNotNull(response, "Status response should exist")
        assertNotNull(response["lastUpdated"], "Should have lastUpdated timestamp")
    }

    @Then("the response should include:")
    fun theResponseShouldInclude(dataTable: DataTable) {
        val response = testContext.statusResponse
        assertNotNull(response, "Status response should exist")

        val expectedFields = dataTable.asMaps()
        for (field in expectedFields) {
            val fieldName = field["field"]
            val fieldType = field["type"]

            assertTrue(response.containsKey(fieldName), "Response should include '$fieldName'")

            val value = response[fieldName]
            when (fieldType) {
                "UUID" -> assertNotNull(value, "$fieldName should have a value (UUID)")
                "String" -> assertTrue(value == null || value is String, "$fieldName should be String or null")
                "ISO8601" -> assertNotNull(value, "$fieldName should have a timestamp value")
                "Array" -> assertTrue(value is List<*>, "$fieldName should be an array")
            }
        }
    }

    @Then("each step in the response should include:")
    fun eachStepInTheResponseShouldInclude(dataTable: DataTable) {
        val response = testContext.statusResponse
        assertNotNull(response, "Status response should exist")

        @Suppress("UNCHECKED_CAST")
        val steps = response["steps"] as? List<Map<String, Any>>
        assertNotNull(steps, "Steps should exist")
        assertTrue(steps.isNotEmpty(), "Should have steps")

        val expectedFields = dataTable.asMaps()
        for (step in steps) {
            for (field in expectedFields) {
                val fieldName = field["field"]
                assertTrue(step.containsKey(fieldName), "Step should include '$fieldName'")
            }
        }
    }

    @Then("each completed step should show a startedAt timestamp")
    fun eachCompletedStepShouldShowAStartedAtTimestamp() {
        val response = testContext.statusResponse
        assertNotNull(response, "Status response should exist")

        @Suppress("UNCHECKED_CAST")
        val steps = response["steps"] as? List<Map<String, Any>>
        assertNotNull(steps, "Steps should exist")

        val completedSteps = steps.filter { it["status"] == "COMPLETED" }
        for (step in completedSteps) {
            assertNotNull(step["startedAt"], "Completed step '${step["name"]}' should have startedAt")
        }
    }

    @Then("each completed step should show a completedAt timestamp")
    fun eachCompletedStepShouldShowACompletedAtTimestamp() {
        val response = testContext.statusResponse
        assertNotNull(response, "Status response should exist")

        @Suppress("UNCHECKED_CAST")
        val steps = response["steps"] as? List<Map<String, Any>>
        assertNotNull(steps, "Steps should exist")

        val completedSteps = steps.filter { it["status"] == "COMPLETED" }
        for (step in completedSteps) {
            assertNotNull(step["completedAt"], "Completed step '${step["name"]}' should have completedAt")
        }
    }

    @Then("the in-progress step should show only a startedAt timestamp")
    fun theInProgressStepShouldShowOnlyAStartedAtTimestamp() {
        val response = testContext.statusResponse
        assertNotNull(response, "Status response should exist")

        @Suppress("UNCHECKED_CAST")
        val steps = response["steps"] as? List<Map<String, Any>>
        assertNotNull(steps, "Steps should exist")

        val inProgressSteps = steps.filter { it["status"] == "IN_PROGRESS" }
        for (step in inProgressSteps) {
            assertNotNull(step["startedAt"], "In-progress step '${step["name"]}' should have startedAt")
            assertNull(step["completedAt"], "In-progress step '${step["name"]}' should not have completedAt")
        }
    }

    // ==================== Helper Methods ====================

    private suspend fun createTestOrder(status: OrderStatus): Order {
        val order = Order.forTest(
            id = UUID.randomUUID(),
            customerId = UUID.randomUUID(),
            totalAmountInCents = 9999L,
            status = status
        )
        return orderRepository.save(order)
    }

    private suspend fun createSagaExecution(
        orderId: UUID,
        status: SagaStatus,
        failedStep: Int? = null,
        failureReason: String? = null,
        completedAt: Instant? = null,
        compensationStartedAt: Instant? = null
    ): SagaExecution {
        val execution = SagaExecution.createWithDetails(
            id = UUID.randomUUID(),
            orderId = orderId,
            status = status,
            failedStep = failedStep,
            failureReason = failureReason,
            startedAt = Instant.now(),
            completedAt = completedAt,
            compensationStartedAt = compensationStartedAt
        )
        return sagaExecutionRepository.save(execution)
    }

    private suspend fun createStepResult(
        sagaExecutionId: UUID,
        stepName: String,
        stepOrder: Int,
        status: StepStatus,
        errorMessage: String? = null
    ): SagaStepResult {
        val startedAt = if (status != StepStatus.PENDING) Instant.now().minusSeconds(5) else null
        val completedAt = if (status in listOf(StepStatus.COMPLETED, StepStatus.FAILED, StepStatus.COMPENSATED)) {
            Instant.now()
        } else null

        val result = SagaStepResult.createWithDetails(
            sagaExecutionId = sagaExecutionId,
            stepName = stepName,
            stepOrder = stepOrder,
            status = status,
            errorMessage = errorMessage,
            startedAt = startedAt,
            completedAt = completedAt
        )
        return sagaStepResultRepository.save(result)
    }
}
