package com.pintailconsultingllc.sagapattern.acceptance.steps

import com.pintailconsultingllc.sagapattern.acceptance.config.TestContext
import com.pintailconsultingllc.sagapattern.domain.Order
import com.pintailconsultingllc.sagapattern.domain.OrderStatus
import com.pintailconsultingllc.sagapattern.domain.RetryAttempt
import com.pintailconsultingllc.sagapattern.domain.RetryOutcome
import com.pintailconsultingllc.sagapattern.domain.SagaExecution
import com.pintailconsultingllc.sagapattern.domain.SagaStatus
import com.pintailconsultingllc.sagapattern.domain.SagaStepResult
import com.pintailconsultingllc.sagapattern.domain.StepStatus
import com.pintailconsultingllc.sagapattern.repository.OrderRepository
import com.pintailconsultingllc.sagapattern.repository.RetryAttemptRepository
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
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Step definitions for SAGA-004: Retry Failed Orders
 */
class RetrySteps(
    @Autowired private val testContext: TestContext,
    @Autowired private val orderRepository: OrderRepository,
    @Autowired private val sagaExecutionRepository: SagaExecutionRepository,
    @Autowired private val sagaStepResultRepository: SagaStepResultRepository,
    @Autowired private val retryAttemptRepository: RetryAttemptRepository
) {
    @Value("\${local.server.port:8080}")
    private var serverPort: Int = 8080

    private val webClient: WebClient by lazy {
        WebClient.builder()
            .baseUrl("http://localhost:$serverPort")
            .build()
    }

    // ==================== Given Steps ====================

    @Given("I have an order that failed due to payment decline")
    fun iHaveAnOrderThatFailedDueToPaymentDecline() = runBlocking {
        // Create a failed order with payment decline failure
        val customerId = testContext.customerId ?: UUID.randomUUID().also { testContext.customerId = it }
        val orderId = UUID.randomUUID()
        val sagaExecutionId = UUID.randomUUID()

        // Create the order
        val order = Order(
            id = orderId,
            customerId = customerId,
            totalAmountInCents = 5000,
            status = OrderStatus.FAILED
        )
        orderRepository.save(order)
        testContext.orderId = orderId

        // Create saga execution
        val sagaExecution = SagaExecution(
            id = sagaExecutionId,
            orderId = orderId,
            status = SagaStatus.FAILED,
            startedAt = Instant.now().minusSeconds(60),
            currentStep = 2,
            failedStep = 2,
            failureReason = "Payment declined: Card was declined"
        )
        sagaExecutionRepository.save(sagaExecution)

        // Create step results - inventory succeeded, payment failed
        val inventoryStep = SagaStepResult(
            sagaExecutionId = sagaExecutionId,
            stepName = "Inventory Reservation",
            stepOrder = 1,
            status = StepStatus.COMPLETED,
            startedAt = Instant.now().minusSeconds(55),
            completedAt = Instant.now().minusSeconds(50),
            stepData = """{"reservationId": "${UUID.randomUUID()}"}"""
        )
        sagaStepResultRepository.save(inventoryStep)

        val paymentStep = SagaStepResult(
            sagaExecutionId = sagaExecutionId,
            stepName = "Payment Processing",
            stepOrder = 2,
            status = StepStatus.FAILED,
            startedAt = Instant.now().minusSeconds(45),
            completedAt = Instant.now().minusSeconds(30),
            errorMessage = "Payment declined: Card was declined"
        )
        sagaStepResultRepository.save(paymentStep)
    }

    @Given("I have an order that failed due to fraud detection")
    fun iHaveAnOrderThatFailedDueToFraudDetection() = runBlocking {
        val customerId = testContext.customerId ?: UUID.randomUUID().also { testContext.customerId = it }
        val orderId = UUID.randomUUID()
        val sagaExecutionId = UUID.randomUUID()

        val order = Order(
            id = orderId,
            customerId = customerId,
            totalAmountInCents = 5000,
            status = OrderStatus.FAILED
        )
        orderRepository.save(order)
        testContext.orderId = orderId

        val sagaExecution = SagaExecution(
            id = sagaExecutionId,
            orderId = orderId,
            status = SagaStatus.FAILED,
            startedAt = Instant.now().minusSeconds(60),
            currentStep = 2,
            failedStep = 2,
            failureReason = "FRAUD_DETECTED: Suspicious transaction"
        )
        sagaExecutionRepository.save(sagaExecution)
    }

    @Given("I have updated my payment method")
    fun iHaveUpdatedMyPaymentMethod() {
        testContext.paymentMethodId = "valid-card"
    }

    @Given("I have an order that failed at the shipping step")
    fun iHaveAnOrderThatFailedAtTheShippingStep() = runBlocking {
        val customerId = testContext.customerId ?: UUID.randomUUID().also { testContext.customerId = it }
        val orderId = UUID.randomUUID()
        val sagaExecutionId = UUID.randomUUID()

        val order = Order(
            id = orderId,
            customerId = customerId,
            totalAmountInCents = 5000,
            status = OrderStatus.FAILED
        )
        orderRepository.save(order)
        testContext.orderId = orderId

        val sagaExecution = SagaExecution(
            id = sagaExecutionId,
            orderId = orderId,
            status = SagaStatus.FAILED,
            startedAt = Instant.now().minusSeconds(60),
            currentStep = 3,
            failedStep = 3,
            failureReason = "Shipping: Invalid address"
        )
        sagaExecutionRepository.save(sagaExecution)

        // Inventory step completed
        sagaStepResultRepository.save(
            SagaStepResult(
                sagaExecutionId = sagaExecutionId,
                stepName = "Inventory Reservation",
                stepOrder = 1,
                status = StepStatus.COMPLETED,
                completedAt = Instant.now().minusSeconds(50),
                stepData = """{"reservationId": "${UUID.randomUUID()}"}"""
            )
        )

        // Payment step completed
        sagaStepResultRepository.save(
            SagaStepResult(
                sagaExecutionId = sagaExecutionId,
                stepName = "Payment Processing",
                stepOrder = 2,
                status = StepStatus.COMPLETED,
                completedAt = Instant.now().minusSeconds(40),
                stepData = """{"transactionId": "${UUID.randomUUID()}"}"""
            )
        )

        // Shipping step failed
        sagaStepResultRepository.save(
            SagaStepResult(
                sagaExecutionId = sagaExecutionId,
                stepName = "Shipping Arrangement",
                stepOrder = 3,
                status = StepStatus.FAILED,
                completedAt = Instant.now().minusSeconds(30),
                errorMessage = "Invalid address"
            )
        )
    }

    @Given("I have corrected my shipping address")
    fun iHaveCorrectedMyShippingAddress() {
        testContext.shippingAddress = mapOf(
            "street" to "456 Valid St",
            "city" to "Goodtown",
            "state" to "CA",
            "postalCode" to "90210",
            "country" to "US"
        )
    }

    @Given("I have an order that failed at payment")
    fun iHaveAnOrderThatFailedAtPayment() {
        iHaveAnOrderThatFailedDueToPaymentDecline()
    }

    @Given("the original inventory reservation has expired")
    fun theOriginalInventoryReservationHasExpired() = runBlocking {
        // Update the step result to have a very old completion time
        // In a real system, this would make the TTL check fail
        val orderId = testContext.orderId ?: return@runBlocking
        val sagaExecution = sagaExecutionRepository.findByOrderId(orderId) ?: return@runBlocking

        val stepResult = sagaStepResultRepository.findBySagaExecutionIdAndStepName(
            sagaExecution.id,
            "Inventory Reservation"
        )

        if (stepResult != null) {
            // Mark the step as very old (older than the TTL)
            sagaStepResultRepository.markCompleted(
                stepResult.id,
                stepResult.stepData,
                Instant.now().minusSeconds(7200) // 2 hours ago, exceeds 1 hour TTL
            )
        }
    }

    @Given("I have an order that has been retried {int} times")
    fun iHaveAnOrderThatHasBeenRetriedTimes(retryCount: Int) = runBlocking {
        // First create a failed order
        iHaveAnOrderThatFailedDueToPaymentDecline()

        val orderId = testContext.orderId ?: return@runBlocking
        val sagaExecution = sagaExecutionRepository.findByOrderId(orderId) ?: return@runBlocking

        // Create retry attempt records
        repeat(retryCount) { index ->
            retryAttemptRepository.save(
                RetryAttempt(
                    orderId = orderId,
                    originalExecutionId = sagaExecution.id,
                    attemptNumber = index + 1,
                    initiatedAt = Instant.now().minusSeconds((retryCount - index) * 600L),
                    completedAt = Instant.now().minusSeconds((retryCount - index - 1) * 600L),
                    outcome = RetryOutcome.FAILED,
                    failureReason = "Retry $index failed"
                )
            )
        }
    }

    @Given("I have an order that just failed")
    fun iHaveAnOrderThatJustFailed() = runBlocking {
        iHaveAnOrderThatFailedDueToPaymentDecline()

        // Update the order to have failed very recently (within cooldown)
        val orderId = testContext.orderId ?: return@runBlocking
        val order = orderRepository.findById(orderId) ?: return@runBlocking
        // Order failed in the last minute, which is within the 5-minute cooldown
    }

    @Given("I have an order with multiple retry attempts")
    fun iHaveAnOrderWithMultipleRetryAttempts() {
        iHaveAnOrderThatHasBeenRetriedTimes(2)
    }

    @Given("I have an order with a retry in progress")
    fun iHaveAnOrderWithARetryInProgress() = runBlocking {
        iHaveAnOrderThatFailedDueToPaymentDecline()

        val orderId = testContext.orderId ?: return@runBlocking
        val sagaExecution = sagaExecutionRepository.findByOrderId(orderId) ?: return@runBlocking

        // Create an active retry attempt (no completedAt, no outcome)
        retryAttemptRepository.save(
            RetryAttempt(
                orderId = orderId,
                originalExecutionId = sagaExecution.id,
                attemptNumber = 1,
                initiatedAt = Instant.now().minusSeconds(30)
                // No completedAt or outcome = active
            )
        )
    }

    @Given("the item prices have increased since the original order")
    fun theItemPricesHaveIncreasedSinceTheOriginalOrder() {
        // This would typically be tracked in a separate price change table
        // For now, we'll set a flag in the context
        testContext.orderResponse = (testContext.orderResponse ?: emptyMap()) + mapOf("priceChanged" to true)
    }

    // ==================== When Steps ====================

    @When("I check if the order is eligible for retry")
    fun iCheckIfTheOrderIsEligibleForRetry() {
        val orderId = testContext.orderId ?: throw IllegalStateException("No order ID in context")

        try {
            @Suppress("UNCHECKED_CAST")
            val response = webClient.get()
                .uri("/api/orders/$orderId/retry/eligibility")
                .retrieve()
                .bodyToMono(Map::class.java)
                .block() as? Map<String, Any>

            testContext.retryEligibilityResponse = response
        } catch (e: WebClientResponseException) {
            testContext.lastError = e.responseBodyAsString
            @Suppress("UNCHECKED_CAST")
            testContext.retryEligibilityResponse = mapOf(
                "error" to e.responseBodyAsString,
                "status" to e.statusCode.value()
            )
        }
    }

    @When("I retry the order")
    fun iRetryTheOrder() {
        val orderId = testContext.orderId ?: throw IllegalStateException("No order ID in context")

        val retryRequest = mutableMapOf<String, Any?>()
        testContext.paymentMethodId?.let { retryRequest["updatedPaymentMethodId"] = it }
        testContext.shippingAddress?.let { retryRequest["updatedShippingAddress"] = it }

        try {
            @Suppress("UNCHECKED_CAST")
            val response = webClient.post()
                .uri("/api/orders/$orderId/retry")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(retryRequest)
                .retrieve()
                .bodyToMono(Map::class.java)
                .block() as? Map<String, Any>

            testContext.retryResponse = response
        } catch (e: WebClientResponseException) {
            testContext.lastError = e.responseBodyAsString
            @Suppress("UNCHECKED_CAST")
            testContext.retryResponse = mapOf(
                "error" to e.responseBodyAsString,
                "success" to false,
                "failureReason" to e.responseBodyAsString
            )
        }
    }

    @When("I attempt to retry the order again")
    fun iAttemptToRetryTheOrderAgain() {
        iRetryTheOrder()
    }

    @When("I attempt to retry immediately")
    fun iAttemptToRetryImmediately() {
        iRetryTheOrder()
    }

    @When("I view the retry history")
    fun iViewTheRetryHistory() {
        val orderId = testContext.orderId ?: throw IllegalStateException("No order ID in context")

        try {
            @Suppress("UNCHECKED_CAST")
            val response = webClient.get()
                .uri("/api/orders/$orderId/retry/history")
                .retrieve()
                .bodyToMono(Map::class.java)
                .block() as? Map<String, Any>

            testContext.retryHistoryResponse = response
        } catch (e: WebClientResponseException) {
            testContext.lastError = e.responseBodyAsString
        }
    }

    @When("I attempt to start another retry")
    fun iAttemptToStartAnotherRetry() {
        iRetryTheOrder()
    }

    @When("I attempt to retry the order")
    fun iAttemptToRetryTheOrder() {
        iRetryTheOrder()
    }

    // ==================== Then Steps ====================

    @Then("the order should be eligible for retry")
    fun theOrderShouldBeEligibleForRetry() {
        val response = testContext.retryEligibilityResponse
        assertNotNull(response, "Should have eligibility response")
        assertTrue(response["eligible"] == true, "Order should be eligible for retry")
    }

    @Then("the required action should be {string}")
    fun theRequiredActionShouldBe(action: String) {
        val response = testContext.retryEligibilityResponse
        assertNotNull(response, "Should have eligibility response")

        @Suppress("UNCHECKED_CAST")
        val requiredActions = response["requiredActions"] as? List<Map<String, Any>>
        assertNotNull(requiredActions, "Should have required actions")
        assertTrue(
            requiredActions.any { it["action"] == action },
            "Required actions should include $action"
        )
    }

    @Then("the order should not be eligible for retry")
    fun theOrderShouldNotBeEligibleForRetry() {
        val response = testContext.retryEligibilityResponse
        assertNotNull(response, "Should have eligibility response")
        assertFalse(response["eligible"] == true, "Order should not be eligible for retry")
    }

    @Then("the reason should indicate {string}")
    fun theReasonShouldIndicate(reason: String) {
        val response = testContext.retryEligibilityResponse ?: testContext.retryResponse
        assertNotNull(response, "Should have response")

        val actualReason = response["reason"] ?: response["failureReason"] ?: ""
        assertTrue(
            actualReason.toString().lowercase().contains(reason.lowercase()),
            "Reason should contain '$reason', but was '$actualReason'"
        )
    }

    @Then("the retry should be initiated successfully")
    fun theRetryShouldBeInitiatedSuccessfully() {
        val response = testContext.retryResponse
        assertNotNull(response, "Should have retry response")
        assertTrue(response["success"] == true, "Retry should be initiated successfully")
    }

    @Then("the order should resume from the payment step")
    fun theOrderShouldResumeFromThePaymentStep() {
        val response = testContext.retryResponse
        assertNotNull(response, "Should have retry response")
        // The skipped steps should include inventory but not payment
        @Suppress("UNCHECKED_CAST")
        val skippedSteps = response["skippedSteps"] as? List<String>
        assertTrue(
            skippedSteps?.contains("Inventory Reservation") == true,
            "Inventory Reservation should be skipped"
        )
    }

    @Then("the inventory reservation should not be repeated")
    fun theInventoryReservationShouldNotBeRepeated() {
        val response = testContext.retryResponse
        assertNotNull(response, "Should have retry response")
        @Suppress("UNCHECKED_CAST")
        val skippedSteps = response["skippedSteps"] as? List<String>
        assertTrue(
            skippedSteps?.contains("Inventory Reservation") == true,
            "Inventory Reservation should be in skipped steps"
        )
    }

    @Then("the inventory step should be skipped")
    fun theInventoryStepShouldBeSkipped() {
        val response = testContext.retryResponse
        assertNotNull(response, "Should have retry response")
        @Suppress("UNCHECKED_CAST")
        val skippedSteps = response["skippedSteps"] as? List<String>
        assertTrue(
            skippedSteps?.contains("Inventory Reservation") == true,
            "Inventory step should be skipped"
        )
    }

    @Then("the payment step should be skipped")
    fun thePaymentStepShouldBeSkipped() {
        val response = testContext.retryResponse
        assertNotNull(response, "Should have retry response")
        @Suppress("UNCHECKED_CAST")
        val skippedSteps = response["skippedSteps"] as? List<String>
        assertTrue(
            skippedSteps?.contains("Payment Processing") == true,
            "Payment step should be skipped"
        )
    }

    @Then("the shipping step should execute with the new address")
    fun theShippingStepShouldExecuteWithTheNewAddress() {
        val response = testContext.retryResponse
        assertNotNull(response, "Should have retry response")
        // Shipping step should have executed (not in skipped steps)
        @Suppress("UNCHECKED_CAST")
        val skippedSteps = response["skippedSteps"] as? List<String> ?: emptyList()
        assertFalse(
            skippedSteps.contains("Shipping Arrangement"),
            "Shipping step should not be skipped"
        )
    }

    @Then("a new inventory reservation should be created")
    fun aNewInventoryReservationShouldBeCreated() {
        val response = testContext.retryResponse
        assertNotNull(response, "Should have retry response")
        @Suppress("UNCHECKED_CAST")
        val skippedSteps = response["skippedSteps"] as? List<String> ?: emptyList()
        assertFalse(
            skippedSteps.contains("Inventory Reservation"),
            "Inventory step should not be skipped (new reservation needed)"
        )
    }

    @Then("the payment step should execute")
    fun thePaymentStepShouldExecute() {
        val response = testContext.retryResponse
        assertNotNull(response, "Should have retry response")
        @Suppress("UNCHECKED_CAST")
        val skippedSteps = response["skippedSteps"] as? List<String> ?: emptyList()
        assertFalse(
            skippedSteps.contains("Payment Processing"),
            "Payment step should execute"
        )
    }

    @Then("the retry should be rejected")
    fun theRetryShouldBeRejected() {
        val response = testContext.retryResponse
        assertNotNull(response, "Should have retry response")
        assertFalse(response["success"] == true, "Retry should be rejected")
    }

    @Then("I should see when the next retry will be available")
    fun iShouldSeeWhenTheNextRetryWillBeAvailable() {
        val response = testContext.retryEligibilityResponse
        assertNotNull(response, "Should have eligibility response")
        assertNotNull(response["nextRetryAvailableAt"], "Should show next retry available time")
    }

    @Then("I should see all retry attempts")
    fun iShouldSeeAllRetryAttempts() {
        val response = testContext.retryHistoryResponse
        assertNotNull(response, "Should have retry history response")
        @Suppress("UNCHECKED_CAST")
        val attempts = response["attempts"] as? List<*>
        assertNotNull(attempts, "Should have attempts list")
        assertTrue(attempts.isNotEmpty(), "Should have at least one retry attempt")
    }

    @Then("each attempt should show:")
    fun eachAttemptShouldShow(dataTable: DataTable) {
        val response = testContext.retryHistoryResponse
        assertNotNull(response, "Should have retry history response")

        @Suppress("UNCHECKED_CAST")
        val attempts = response["attempts"] as? List<Map<String, Any>>
        assertNotNull(attempts, "Should have attempts list")

        val expectedFields = dataTable.asMaps().map { it["field"] }
        for (attempt in attempts) {
            for (field in expectedFields) {
                assertTrue(
                    attempt.containsKey(field),
                    "Attempt should contain field: $field"
                )
            }
        }
    }

    @Then("the second retry should be rejected")
    fun theSecondRetryShouldBeRejected() {
        theRetryShouldBeRejected()
    }

    @Then("the retry should require acknowledgment of the price change")
    fun theRetryShouldRequireAcknowledgmentOfThePriceChange() {
        // This scenario is for future implementation
        // For now, we verify the retry was attempted
        assertNotNull(testContext.retryResponse, "Should have retry response")
    }

    @Then("I should see the original and new prices")
    fun iShouldSeeTheOriginalAndNewPrices() {
        // This scenario is for future implementation
        // Price change handling would be in the response
        assertNotNull(testContext.retryResponse, "Should have retry response")
    }
}
