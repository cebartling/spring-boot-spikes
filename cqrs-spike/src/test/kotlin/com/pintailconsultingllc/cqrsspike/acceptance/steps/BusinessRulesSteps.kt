package com.pintailconsultingllc.cqrsspike.acceptance.steps

import com.fasterxml.jackson.databind.ObjectMapper
import com.pintailconsultingllc.cqrsspike.acceptance.context.TestContext
import com.pintailconsultingllc.cqrsspike.acceptance.context.ValidationError
import com.pintailconsultingllc.cqrsspike.product.api.dto.ActivateProductRequest
import com.pintailconsultingllc.cqrsspike.product.api.dto.ChangePriceRequest
import com.pintailconsultingllc.cqrsspike.product.api.dto.CreateProductRequest
import com.pintailconsultingllc.cqrsspike.product.api.dto.DiscontinueProductRequest
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.assertj.core.api.Assertions.assertThat
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import java.util.UUID

/**
 * Step definitions for business rules validation scenarios.
 *
 * Tests AC9 requirements:
 * - Product name validation (1-255 characters)
 * - SKU validation (alphanumeric, 3-50 chars)
 * - Price validation (positive integer)
 * - Description length limit (5000 characters)
 * - Status transitions (DRAFT -> ACTIVE -> DISCONTINUED)
 * - Price change confirmation for >20% changes
 */
class BusinessRulesSteps {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var testContext: TestContext

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private var currentExpectedVersion: Long = 0

    // ========== Given Steps ==========

    @Given("I have an active product with price {int} cents")
    fun iHaveAnActiveProductWithPrice(priceCents: Int) {
        createProduct("ACTIVE-TEST-SKU", "Active Test Product", priceCents)
        activateCurrentProduct()
    }

    @Given("I have a draft product")
    fun iHaveADraftProduct() {
        createProduct("DRAFT-TEST-SKU", "Draft Test Product", 1999)
    }

    @Given("I have a discontinued product")
    fun iHaveADiscontinuedProduct() {
        createProduct("DISCONTINUED-SKU", "Discontinued Product", 1999)
        activateCurrentProduct()
        discontinueCurrentProduct()
    }

    // ========== When Steps - Validation ==========

    @When("I try to create a product with empty name")
    fun iTryToCreateProductWithEmptyName() {
        createProductAndStoreResponse("TEST-SKU", "", null, 1999)
    }

    @When("I try to create a product with name longer than 255 characters")
    fun iTryToCreateProductWithNameLongerThan255Characters() {
        val longName = "A".repeat(256)
        createProductAndStoreResponse("TEST-SKU", longName, null, 1999)
    }

    @When("I try to create a product with empty SKU")
    fun iTryToCreateProductWithEmptySku() {
        createProductAndStoreResponse("", "Test Product", null, 1999)
    }

    @When("I try to create a product with SKU shorter than 3 characters")
    fun iTryToCreateProductWithSkuShorterThan3Characters() {
        createProductAndStoreResponse("AB", "Test Product", null, 1999)
    }

    @When("I try to create a product with SKU longer than 50 characters")
    fun iTryToCreateProductWithSkuLongerThan50Characters() {
        val longSku = "A".repeat(51)
        createProductAndStoreResponse(longSku, "Test Product", null, 1999)
    }

    @When("I try to create a product with SKU containing special characters")
    fun iTryToCreateProductWithSkuContainingSpecialCharacters() {
        createProductAndStoreResponse("TEST@SKU#123", "Test Product", null, 1999)
    }

    @When("I try to create a product with negative price")
    fun iTryToCreateProductWithNegativePrice() {
        createProductAndStoreResponse("TEST-SKU", "Test Product", null, -100)
    }

    @When("I try to create a product with zero price")
    fun iTryToCreateProductWithZeroPrice() {
        createProductAndStoreResponse("TEST-SKU", "Test Product", null, 0)
    }

    @When("I try to create a product with description longer than 5000 characters")
    fun iTryToCreateProductWithDescriptionLongerThan5000Characters() {
        val longDescription = "A".repeat(5001)
        createProductAndStoreResponse("TEST-SKU", "Test Product", longDescription, 1999)
    }

    // ========== When Steps - Status Transitions ==========

    @When("I try to activate a draft product")
    fun iTryToActivateADraftProduct() {
        activateCurrentProductAndStoreResponse()
    }

    @When("I try to activate an already active product")
    fun iTryToActivateAnAlreadyActiveProduct() {
        activateCurrentProductAndStoreResponse()
    }

    @When("I try to discontinue an active product")
    fun iTryToDiscontinueAnActiveProduct() {
        discontinueCurrentProductAndStoreResponse()
    }

    @When("I try to discontinue a draft product")
    fun iTryToDiscontinueADraftProduct() {
        discontinueCurrentProductAndStoreResponse()
    }

    @When("I try to reactivate a discontinued product")
    fun iTryToReactivateADiscontinuedProduct() {
        activateCurrentProductAndStoreResponse()
    }

    // ========== When Steps - Price Changes ==========

    @When("I try to change the price by more than 20% without confirmation")
    fun iTryToChangePriceByMoreThan20PercentWithoutConfirmation() {
        val productId = testContext.currentProductId
            ?: throw IllegalStateException("No current product ID in context")

        // Get current price from a previous step or assume 1000 cents
        // New price of 500 is a 50% decrease
        val request = ChangePriceRequest(
            newPriceCents = 500,
            confirmLargeChange = false,
            expectedVersion = currentExpectedVersion
        )

        val response = webTestClient.patch()
            .uri("/api/products/{id}/price", productId)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .returnResult(String::class.java)

        testContext.lastResponseStatus = response.status
        testContext.lastResponseBody = response.responseBody.blockFirst()
        parseErrorResponse()
    }

    @When("I try to change the price by more than 20% with confirmation")
    fun iTryToChangePriceByMoreThan20PercentWithConfirmation() {
        val productId = testContext.currentProductId
            ?: throw IllegalStateException("No current product ID in context")

        val request = ChangePriceRequest(
            newPriceCents = 500,
            confirmLargeChange = true,
            expectedVersion = currentExpectedVersion
        )

        val response = webTestClient.patch()
            .uri("/api/products/{id}/price", productId)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .returnResult(String::class.java)

        testContext.lastResponseStatus = response.status
        testContext.lastResponseBody = response.responseBody.blockFirst()
        parseErrorResponse()
        updateVersionFromResponse()
    }

    @When("I try to change the price by less than 20%")
    fun iTryToChangePriceByLessThan20Percent() {
        val productId = testContext.currentProductId
            ?: throw IllegalStateException("No current product ID in context")

        // Assuming current price is 1000, change to 900 (10% decrease)
        val request = ChangePriceRequest(
            newPriceCents = 900,
            confirmLargeChange = false,
            expectedVersion = currentExpectedVersion
        )

        val response = webTestClient.patch()
            .uri("/api/products/{id}/price", productId)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .returnResult(String::class.java)

        testContext.lastResponseStatus = response.status
        testContext.lastResponseBody = response.responseBody.blockFirst()
        parseErrorResponse()
        updateVersionFromResponse()
    }

    // ========== When Steps - Concurrent Modification ==========

    @When("I try to update with an outdated version")
    fun iTryToUpdateWithAnOutdatedVersion() {
        val productId = testContext.currentProductId
            ?: throw IllegalStateException("No current product ID in context")

        val request = mapOf(
            "name" to "Updated Name",
            "expectedVersion" to 0L // Outdated version
        )

        val response = webTestClient.put()
            .uri("/api/products/{id}", productId)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .returnResult(String::class.java)

        testContext.lastResponseStatus = response.status
        testContext.lastResponseBody = response.responseBody.blockFirst()
        parseErrorResponse()
    }

    // ========== Then Steps ==========

    @Then("the product should be rejected with validation error")
    fun theProductShouldBeRejectedWithValidationError() {
        assertThat(testContext.lastResponseStatus)
            .describedAs("Response status should be BAD_REQUEST")
            .isEqualTo(HttpStatus.BAD_REQUEST)
    }

    @Then("the validation error should mention {string}")
    fun theValidationErrorShouldMention(expectedMessage: String) {
        val hasMatchingError = testContext.lastValidationErrors.any { error ->
            error.message.contains(expectedMessage, ignoreCase = true)
        } || testContext.lastErrorMessage?.contains(expectedMessage, ignoreCase = true) == true

        assertThat(hasMatchingError)
            .describedAs("Validation error should mention '$expectedMessage'")
            .isTrue
    }

    @Then("the status transition should be rejected")
    fun theStatusTransitionShouldBeRejected() {
        assertThat(testContext.lastResponseStatus?.is4xxClientError)
            .describedAs("Response should be client error")
            .isTrue
    }

    @Then("the error should indicate invalid status transition")
    fun theErrorShouldIndicateInvalidStatusTransition() {
        val errorMessage = testContext.lastErrorMessage ?: ""
        assertThat(errorMessage.lowercase())
            .describedAs("Error message should mention status transition")
            .containsAnyOf("status", "transition", "cannot", "invalid")
    }

    @Then("the price change should require confirmation")
    fun thePriceChangeShouldRequireConfirmation() {
        assertThat(testContext.lastResponseStatus)
            .describedAs("Response status should be UNPROCESSABLE_ENTITY")
            .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)

        assertThat(testContext.lastErrorCode)
            .describedAs("Error code")
            .isEqualTo("PRICE_CHANGE_THRESHOLD_EXCEEDED")
    }

    @Then("the price change should be accepted")
    fun thePriceChangeShouldBeAccepted() {
        assertThat(testContext.lastResponseStatus?.is2xxSuccessful)
            .describedAs("Response should be successful")
            .isTrue
    }

    @Then("the response should indicate concurrent modification conflict")
    fun theResponseShouldIndicateConcurrentModificationConflict() {
        assertThat(testContext.lastResponseStatus)
            .describedAs("Response status should be CONFLICT")
            .isEqualTo(HttpStatus.CONFLICT)

        assertThat(testContext.lastErrorCode)
            .describedAs("Error code")
            .isEqualTo("CONCURRENT_MODIFICATION")
    }

    @Then("the response should include retry guidance")
    fun theResponseShouldIncludeRetryGuidance() {
        val body = testContext.lastResponseBody ?: ""
        assertThat(body)
            .describedAs("Response body should include retry guidance")
            .containsIgnoringCase("retry")
    }

    // ========== Helper Methods ==========

    private fun createProduct(sku: String, name: String, priceCents: Int) {
        val request = CreateProductRequest(
            sku = sku,
            name = name,
            description = null,
            priceCents = priceCents
        )

        val response = webTestClient.post()
            .uri("/api/products")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .returnResult(String::class.java)

        testContext.lastResponseStatus = response.status
        testContext.lastResponseBody = response.responseBody.blockFirst()
        extractProductIdFromResponse()
        updateVersionFromResponse()
    }

    private fun createProductAndStoreResponse(sku: String, name: String, description: String?, priceCents: Int) {
        val request = CreateProductRequest(
            sku = sku,
            name = name,
            description = description,
            priceCents = priceCents
        )

        val response = webTestClient.post()
            .uri("/api/products")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .returnResult(String::class.java)

        testContext.lastResponseStatus = response.status
        testContext.lastResponseBody = response.responseBody.blockFirst()
        parseErrorResponse()
    }

    private fun activateCurrentProduct() {
        val productId = testContext.currentProductId
            ?: throw IllegalStateException("No current product ID in context")

        val request = ActivateProductRequest(expectedVersion = currentExpectedVersion)

        val response = webTestClient.post()
            .uri("/api/products/{id}/activate", productId)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .returnResult(String::class.java)

        testContext.lastResponseBody = response.responseBody.blockFirst()
        updateVersionFromResponse()
    }

    private fun activateCurrentProductAndStoreResponse() {
        val productId = testContext.currentProductId
            ?: throw IllegalStateException("No current product ID in context")

        val request = ActivateProductRequest(expectedVersion = currentExpectedVersion)

        val response = webTestClient.post()
            .uri("/api/products/{id}/activate", productId)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .returnResult(String::class.java)

        testContext.lastResponseStatus = response.status
        testContext.lastResponseBody = response.responseBody.blockFirst()
        parseErrorResponse()
        updateVersionFromResponse()
    }

    private fun discontinueCurrentProduct() {
        val productId = testContext.currentProductId
            ?: throw IllegalStateException("No current product ID in context")

        val request = DiscontinueProductRequest(
            reason = "Test discontinuation",
            expectedVersion = currentExpectedVersion
        )

        val response = webTestClient.post()
            .uri("/api/products/{id}/discontinue", productId)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .returnResult(String::class.java)

        testContext.lastResponseBody = response.responseBody.blockFirst()
        updateVersionFromResponse()
    }

    private fun discontinueCurrentProductAndStoreResponse() {
        val productId = testContext.currentProductId
            ?: throw IllegalStateException("No current product ID in context")

        val request = DiscontinueProductRequest(
            reason = "Test discontinuation",
            expectedVersion = currentExpectedVersion
        )

        val response = webTestClient.post()
            .uri("/api/products/{id}/discontinue", productId)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .returnResult(String::class.java)

        testContext.lastResponseStatus = response.status
        testContext.lastResponseBody = response.responseBody.blockFirst()
        parseErrorResponse()
        updateVersionFromResponse()
    }

    private fun extractProductIdFromResponse() {
        val body = testContext.lastResponseBody ?: return
        try {
            val jsonNode = objectMapper.readTree(body)
            val productIdStr = jsonNode.get("productId")?.asText()
            if (productIdStr != null) {
                val productId = UUID.fromString(productIdStr)
                testContext.currentProductId = productId
                testContext.createdProductIds.add(productId)
            }
        } catch (e: Exception) {
            // Response may not contain productId
        }
    }

    private fun updateVersionFromResponse() {
        val body = testContext.lastResponseBody ?: return
        try {
            val jsonNode = objectMapper.readTree(body)
            val version = jsonNode.get("version")?.asLong()
            if (version != null) {
                currentExpectedVersion = version
            }
        } catch (e: Exception) {
            // Response may not contain version
        }
    }

    private fun parseErrorResponse() {
        val body = testContext.lastResponseBody ?: return
        try {
            val jsonNode = objectMapper.readTree(body)
            testContext.lastErrorMessage = jsonNode.get("message")?.asText()
            testContext.lastErrorCode = jsonNode.get("code")?.asText()

            // Parse validation errors if present
            val errors = jsonNode.get("errors")
            if (errors != null && errors.isArray) {
                testContext.lastValidationErrors.clear()
                errors.forEach { error ->
                    val field = error.get("field")?.asText() ?: ""
                    val message = error.get("message")?.asText() ?: ""
                    testContext.lastValidationErrors.add(ValidationError(field, message))
                }
            }
        } catch (e: Exception) {
            // Response may not be JSON
        }
    }
}
