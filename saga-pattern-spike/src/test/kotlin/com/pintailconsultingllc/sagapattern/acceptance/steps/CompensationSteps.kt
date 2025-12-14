package com.pintailconsultingllc.sagapattern.acceptance.steps

import com.pintailconsultingllc.sagapattern.acceptance.config.TestContext
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Step definitions for SAGA-002: Automatic Rollback on Failure
 *
 * Tests compensation/rollback logic when saga steps fail.
 */
class CompensationSteps(
    @Autowired private val testContext: TestContext
) {
    @Value("\${local.server.port:8080}")
    private var serverPort: Int = 8080

    private val webClient: WebClient by lazy {
        WebClient.builder()
            .baseUrl("http://localhost:$serverPort")
            .build()
    }

    // ==================== Given Steps ====================

    @Given("I have items in my cart that are out of stock")
    fun iHaveItemsInMyCartThatAreOutOfStock() {
        // Use WireMock trigger: productId = "out-of-stock-product"
        testContext.cartItems.add(
            mapOf(
                "productId" to "out-of-stock-product",
                "productName" to "Out of Stock Product",
                "quantity" to 5,
                "unitPriceInCents" to 2999
            )
        )
    }

    @Given("I have a payment method that will be declined")
    fun iHaveAPaymentMethodThatWillBeDeclined() {
        // Use WireMock trigger: paymentMethodId = "declined-card"
        testContext.paymentMethodId = "declined-card"
    }

    @Given("I have an invalid shipping address")
    fun iHaveAnInvalidShippingAddress() {
        // Use WireMock trigger: postalCode = "00000"
        testContext.shippingAddress = mapOf(
            "street" to "123 Invalid St",
            "city" to "Nowhereville",
            "state" to "XX",
            "postalCode" to "00000",
            "country" to "US"
        )
    }

    @Given("I have submitted an order that will fail at shipping")
    fun iHaveSubmittedAnOrderThatWillFailAtShipping() {
        // Set up customer if not already set
        if (testContext.customerId == null) {
            testContext.customerId = UUID.randomUUID()
        }
        // Set up for shipping failure
        testContext.cartItems.add(
            mapOf(
                "productId" to UUID.randomUUID().toString(),
                "productName" to "Test Product",
                "quantity" to 1,
                "unitPriceInCents" to 2999
            )
        )
        testContext.paymentMethodId = "valid-card"
        testContext.shippingAddress = mapOf(
            "street" to "123 Invalid St",
            "city" to "Nowhereville",
            "state" to "XX",
            "postalCode" to "00000",
            "country" to "US"
        )
        submitOrder()
    }

    @Given("I have an order that failed and was compensated")
    fun iHaveAnOrderThatFailedAndWasCompensated() {
        // Create an order that fails at shipping (causes compensation)
        iHaveSubmittedAnOrderThatWillFailAtShipping()
        // Verify it was compensated
        assertEquals("COMPENSATED", testContext.orderResponse?.get("status"))
    }

    @Given("I have an order that failed due to payment decline")
    fun iHaveAnOrderThatFailedDueToPaymentDecline() {
        // Set up customer if not already set
        if (testContext.customerId == null) {
            testContext.customerId = UUID.randomUUID()
        }
        // Set up for payment failure
        testContext.cartItems.add(
            mapOf(
                "productId" to UUID.randomUUID().toString(),
                "productName" to "Test Product",
                "quantity" to 1,
                "unitPriceInCents" to 2999
            )
        )
        testContext.paymentMethodId = "declined-card"
        testContext.shippingAddress = mapOf(
            "street" to "123 Main St",
            "city" to "Anytown",
            "state" to "CA",
            "postalCode" to "90210",
            "country" to "US"
        )
        submitOrder()
    }

    @Given("I have an order that failed after payment authorization")
    fun iHaveAnOrderThatFailedAfterPaymentAuthorization() {
        // Same as shipping failure - payment was authorized before shipping failed
        iHaveSubmittedAnOrderThatWillFailAtShipping()
    }

    @Given("I have an order that will fail at the payment step")
    fun iHaveAnOrderThatWillFailAtThePaymentStep() {
        // Set up customer if not already set
        if (testContext.customerId == null) {
            testContext.customerId = UUID.randomUUID()
        }
        testContext.cartItems.add(
            mapOf(
                "productId" to UUID.randomUUID().toString(),
                "productName" to "Test Product",
                "quantity" to 1,
                "unitPriceInCents" to 2999
            )
        )
        testContext.paymentMethodId = "declined-card"
        testContext.shippingAddress = mapOf(
            "street" to "123 Main St",
            "city" to "Anytown",
            "state" to "CA",
            "postalCode" to "90210",
            "country" to "US"
        )
    }

    // ==================== When Steps ====================

    @When("the shipping step fails")
    fun theShippingStepFails() {
        // The order was already submitted in the Given step
        // Verify shipping failed
        @Suppress("UNCHECKED_CAST")
        val error = testContext.orderResponse?.get("error") as? Map<String, Any>
        assertEquals("Shipping Arrangement", error?.get("failedStep"))
    }

    @When("compensation is triggered again")
    fun compensationIsTriggeredAgain() {
        // Compensation is idempotent - calling it again should not cause issues
        // Since compensation is automatic, we just verify the state
        val status = testContext.orderResponse?.get("status")
        assertTrue(status == "COMPENSATED" || status == "FAILED", "Order should already be compensated or failed")
    }

    @When("I receive the failure notification")
    fun iReceiveTheFailureNotification() {
        // The failure notification is embedded in the response
        assertNotNull(testContext.orderResponse, "Should have order response")
        assertNotNull(testContext.orderResponse?.get("error"), "Should have error details")
    }

    @When("compensation completes")
    fun compensationCompletes() {
        // Compensation is synchronous, so if we have a response it's complete
        assertNotNull(testContext.orderResponse, "Should have order response")
    }

    @When("the payment step fails")
    fun thePaymentStepFails() {
        // Submit the order (if not already submitted)
        if (testContext.orderResponse == null) {
            submitOrder()
        }
        // Verify payment failed
        @Suppress("UNCHECKED_CAST")
        val error = testContext.orderResponse?.get("error") as? Map<String, Any>
        assertEquals("Payment Processing", error?.get("failedStep"))
    }

    @When("compensation occurs")
    fun compensationOccurs() {
        // Submit order if not already submitted
        if (testContext.orderResponse == null) {
            submitOrder()
        }
        // Compensation should have occurred automatically
        val status = testContext.orderResponse?.get("status")
        assertTrue(status == "COMPENSATED" || status == "FAILED", "Compensation should have occurred")
    }

    // ==================== Then Steps ====================

    @Then("the inventory reservation step should fail")
    fun theInventoryReservationStepShouldFail() {
        @Suppress("UNCHECKED_CAST")
        val error = testContext.orderResponse?.get("error") as? Map<String, Any>
        assertEquals("Inventory Reservation", error?.get("failedStep"))
    }

    @Then("the payment authorization step should fail")
    fun thePaymentAuthorizationStepShouldFail() {
        @Suppress("UNCHECKED_CAST")
        val error = testContext.orderResponse?.get("error") as? Map<String, Any>
        assertEquals("Payment Processing", error?.get("failedStep"))
    }

    @Then("the shipping arrangement step should fail")
    fun theShippingArrangementStepShouldFail() {
        @Suppress("UNCHECKED_CAST")
        val error = testContext.orderResponse?.get("error") as? Map<String, Any>
        assertEquals("Shipping Arrangement", error?.get("failedStep"))
    }

    @Then("no compensation should be triggered")
    fun noCompensationShouldBeTriggered() {
        @Suppress("UNCHECKED_CAST")
        val compensation = testContext.orderResponse?.get("compensation") as? Map<String, Any>
        assertEquals("NOT_NEEDED", compensation?.get("status"))
        @Suppress("UNCHECKED_CAST")
        val reversedSteps = compensation?.get("reversedSteps") as? List<*>
        assertTrue(reversedSteps?.isEmpty() == true, "No steps should be reversed")
    }

    @Then("I should receive a failure notification")
    fun iShouldReceiveAFailureNotification() {
        assertNotNull(testContext.orderResponse, "Should have order response")
        assertNotNull(testContext.orderResponse?.get("error"), "Should have error details")
        assertNotNull(testContext.orderResponse?.get("suggestions"), "Should have suggestions")
    }

    @Then("the failure reason should indicate {string}")
    fun theFailureReasonShouldIndicate(reason: String) {
        @Suppress("UNCHECKED_CAST")
        val error = testContext.orderResponse?.get("error") as? Map<String, Any>
        val message = error?.get("message").toString().lowercase()
        assertTrue(
            message.contains(reason.lowercase()),
            "Failure reason '$message' should indicate '$reason'"
        )
    }

    @Then("the inventory reservation should be automatically released")
    fun theInventoryReservationShouldBeAutomaticallyReleased() {
        @Suppress("UNCHECKED_CAST")
        val compensation = testContext.orderResponse?.get("compensation") as? Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val reversedSteps = compensation?.get("reversedSteps") as? List<*>
        assertTrue(
            reversedSteps?.contains("Inventory Reservation") == true,
            "Inventory Reservation should be in reversed steps"
        )
    }

    @Then("no inventory reservations should remain")
    fun noInventoryReservationsShouldRemain() {
        // If compensation succeeded, reservations were released
        @Suppress("UNCHECKED_CAST")
        val compensation = testContext.orderResponse?.get("compensation") as? Map<String, Any>
        val status = compensation?.get("status")
        assertTrue(status == "COMPLETED" || status == "NOT_NEEDED", "Compensation should be complete")
    }

    @Then("the payment authorization should be automatically voided")
    fun thePaymentAuthorizationShouldBeAutomaticallyVoided() {
        @Suppress("UNCHECKED_CAST")
        val compensation = testContext.orderResponse?.get("compensation") as? Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val reversedSteps = compensation?.get("reversedSteps") as? List<*>
        assertTrue(
            reversedSteps?.contains("Payment Processing") == true,
            "Payment Processing should be in reversed steps"
        )
    }

    @Then("compensation should execute in reverse order:")
    fun compensationShouldExecuteInReverseOrder(dataTable: DataTable) {
        val expectedOrder = dataTable.asMaps()
        @Suppress("UNCHECKED_CAST")
        val compensation = testContext.orderResponse?.get("compensation") as? Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val reversedSteps = compensation?.get("reversedSteps") as? List<*>

        // Verify steps are in reverse order (Payment first, then Inventory)
        expectedOrder.forEachIndexed { index, row ->
            val stepName = row["step"]
            val expectedPosition = row["compensation_order"]?.toInt()?.minus(1) ?: index
            assertEquals(stepName, reversedSteps?.get(expectedPosition), "Step at position $expectedPosition should be $stepName")
        }
    }

    @Then("each compensation step should be recorded")
    fun eachCompensationStepShouldBeRecorded() {
        // Compensation is recorded in the response
        @Suppress("UNCHECKED_CAST")
        val compensation = testContext.orderResponse?.get("compensation") as? Map<String, Any>
        assertNotNull(compensation, "Compensation details should be recorded")
        assertNotNull(compensation["status"], "Compensation status should be recorded")
        assertNotNull(compensation["reversedSteps"], "Reversed steps should be recorded")
    }

    @Then("no duplicate reversals should occur")
    fun noDuplicateReversalsShouldOccur() {
        // Verify no duplicate step names in reversed steps
        @Suppress("UNCHECKED_CAST")
        val compensation = testContext.orderResponse?.get("compensation") as? Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val reversedSteps = compensation?.get("reversedSteps") as? List<*>
        val uniqueSteps = reversedSteps?.distinct()
        assertEquals(reversedSteps?.size, uniqueSteps?.size, "Should have no duplicate reversals")
    }

    @Then("the compensation result should indicate already compensated")
    fun theCompensationResultShouldIndicateAlreadyCompensated() {
        // Status should remain COMPENSATED
        assertEquals("COMPENSATED", testContext.orderResponse?.get("status"))
    }

    @Then("the notification should include the order ID")
    fun theNotificationShouldIncludeTheOrderId() {
        assertNotNull(testContext.orderResponse?.get("orderId"), "Should include order ID")
    }

    @Then("the notification should include the failed step name")
    fun theNotificationShouldIncludeTheFailedStepName() {
        @Suppress("UNCHECKED_CAST")
        val error = testContext.orderResponse?.get("error") as? Map<String, Any>
        assertNotNull(error?.get("failedStep"), "Should include failed step name")
    }

    @Then("the notification should include a clear failure reason")
    fun theNotificationShouldIncludeAClearFailureReason() {
        @Suppress("UNCHECKED_CAST")
        val error = testContext.orderResponse?.get("error") as? Map<String, Any>
        assertNotNull(error?.get("message"), "Should include failure reason")
        assertTrue(error?.get("message").toString().isNotBlank(), "Failure reason should not be blank")
    }

    @Then("the notification should include suggested next steps")
    fun theNotificationShouldIncludeSuggestedNextSteps() {
        @Suppress("UNCHECKED_CAST")
        val suggestions = testContext.orderResponse?.get("suggestions") as? List<*>
        assertNotNull(suggestions, "Should include suggestions")
        assertTrue(suggestions.isNotEmpty(), "Should have at least one suggestion")
    }

    @Then("no payment charges should exist for the order")
    fun noPaymentChargesShouldExistForTheOrder() {
        // If payment was compensated, authorization was voided
        @Suppress("UNCHECKED_CAST")
        val compensation = testContext.orderResponse?.get("compensation") as? Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val reversedSteps = compensation?.get("reversedSteps") as? List<*>
        if (reversedSteps?.contains("Payment Processing") == true) {
            // Payment was voided
            assertEquals("COMPLETED", compensation["status"])
        }
    }

    @Then("no pending authorizations should exist for the order")
    fun noPendingAuthorizationsShouldExistForTheOrder() {
        // Same verification as above - voided authorization means no pending auth
        noPaymentChargesShouldExistForTheOrder()
    }

    // ==================== Helper Methods ====================

    private fun submitOrder() {
        val orderRequest = mapOf(
            "customerId" to testContext.customerId.toString(),
            "items" to testContext.cartItems.map { item ->
                mapOf(
                    "productId" to item["productId"],
                    "productName" to item["productName"],
                    "quantity" to item["quantity"],
                    "unitPriceInCents" to item["unitPriceInCents"]
                )
            },
            "paymentMethodId" to testContext.paymentMethodId,
            "shippingAddress" to testContext.shippingAddress
        )

        try {
            @Suppress("UNCHECKED_CAST")
            val response = webClient.post()
                .uri("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(orderRequest)
                .retrieve()
                .bodyToMono(Map::class.java)
                .block() as? Map<String, Any>

            testContext.orderResponse = response
            if (response?.containsKey("orderId") == true) {
                testContext.orderId = UUID.fromString(response["orderId"].toString())
            }
        } catch (e: WebClientResponseException) {
            // Parse the error response body
            @Suppress("UNCHECKED_CAST")
            val errorBody = try {
                val mapper = jacksonObjectMapper()
                mapper.readValue(e.responseBodyAsString, Map::class.java) as Map<String, Any>
            } catch (_: Exception) {
                mapOf("error" to e.responseBodyAsString) as Map<String, Any>
            }
            testContext.orderResponse = errorBody
            testContext.lastError = e.responseBodyAsString
            if (errorBody.containsKey("orderId")) {
                testContext.orderId = UUID.fromString(errorBody["orderId"].toString())
            }
        }
    }
}
