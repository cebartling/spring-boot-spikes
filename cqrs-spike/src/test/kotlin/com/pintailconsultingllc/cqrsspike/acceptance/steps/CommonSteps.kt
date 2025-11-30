package com.pintailconsultingllc.cqrsspike.acceptance.steps

import com.pintailconsultingllc.cqrsspike.acceptance.context.TestContext
import io.cucumber.java.Before
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import org.assertj.core.api.Assertions.assertThat
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.web.reactive.server.WebTestClient

/**
 * Common step definitions shared across all acceptance test scenarios.
 *
 * This class provides reusable steps for:
 * - System health verification
 * - Response status validation
 * - Error message assertions
 * - Test cleanup and setup
 */
class CommonSteps {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var testContext: TestContext


    // ========== Given Steps ==========

    @Given("the system is running")
    fun theSystemIsRunning() {
        val response = webTestClient.get()
            .uri("/actuator/health")
            .exchange()
            .returnResult(String::class.java)

        testContext.lastResponseStatus = response.status
        testContext.lastResponseBody = response.responseBody.blockFirst()
    }

    @Given("the product catalog is empty")
    fun theProductCatalogIsEmpty() {
        // The test database should be manually cleaned before running tests
        // This step is primarily for documentation purposes in feature files
        testContext.createdProductIds.clear()
    }

    @Given("the database is available")
    fun theDatabaseIsAvailable() {
        // Verify database connectivity by checking health endpoint
        val response = webTestClient.get()
            .uri("/actuator/health")
            .exchange()
            .returnResult(String::class.java)

        testContext.lastResponseStatus = response.status
        testContext.lastResponseBody = response.responseBody.blockFirst()
    }

    // ========== Then Steps ==========

    @Then("the response status should be {int}")
    fun theResponseStatusShouldBe(expectedStatus: Int) {
        assertThat(testContext.lastResponseStatus?.value())
            .describedAs("Response status")
            .isEqualTo(expectedStatus)
    }

    @Then("the response status should be OK")
    fun theResponseStatusShouldBeOk() {
        assertThat(testContext.lastResponseStatus)
            .describedAs("Response status")
            .isEqualTo(HttpStatus.OK)
    }

    @Then("the response status should be CREATED")
    fun theResponseStatusShouldBeCreated() {
        assertThat(testContext.lastResponseStatus)
            .describedAs("Response status")
            .isEqualTo(HttpStatus.CREATED)
    }

    @Then("the response status should be NO_CONTENT")
    fun theResponseStatusShouldBeNoContent() {
        assertThat(testContext.lastResponseStatus)
            .describedAs("Response status")
            .isEqualTo(HttpStatus.NO_CONTENT)
    }

    @Then("the response status should be BAD_REQUEST")
    fun theResponseStatusShouldBeBadRequest() {
        assertThat(testContext.lastResponseStatus)
            .describedAs("Response status")
            .isEqualTo(HttpStatus.BAD_REQUEST)
    }

    @Then("the response status should be NOT_FOUND")
    fun theResponseStatusShouldBeNotFound() {
        assertThat(testContext.lastResponseStatus)
            .describedAs("Response status")
            .isEqualTo(HttpStatus.NOT_FOUND)
    }

    @Then("the response status should be CONFLICT")
    fun theResponseStatusShouldBeConflict() {
        assertThat(testContext.lastResponseStatus)
            .describedAs("Response status")
            .isEqualTo(HttpStatus.CONFLICT)
    }

    @Then("the response status should be UNPROCESSABLE_ENTITY")
    fun theResponseStatusShouldBeUnprocessableEntity() {
        assertThat(testContext.lastResponseStatus)
            .describedAs("Response status")
            .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
    }

    @Then("the response should contain error message {string}")
    fun theResponseShouldContainErrorMessage(expectedMessage: String) {
        assertThat(testContext.lastErrorMessage)
            .describedAs("Error message")
            .contains(expectedMessage)
    }

    @Then("the response should contain error code {string}")
    fun theResponseShouldContainErrorCode(expectedCode: String) {
        assertThat(testContext.lastErrorCode)
            .describedAs("Error code")
            .isEqualTo(expectedCode)
    }

    @Then("the response should contain validation error for field {string}")
    fun theResponseShouldContainValidationErrorForField(fieldName: String) {
        assertThat(testContext.lastValidationErrors)
            .describedAs("Validation errors")
            .anyMatch { it.field == fieldName }
    }

    @Then("the response should contain validation error for field {string} with message {string}")
    fun theResponseShouldContainValidationErrorForFieldWithMessage(fieldName: String, expectedMessage: String) {
        assertThat(testContext.lastValidationErrors)
            .describedAs("Validation errors")
            .anyMatch { it.field == fieldName && it.message.contains(expectedMessage) }
    }

    @Then("no validation errors should be present")
    fun noValidationErrorsShouldBePresent() {
        assertThat(testContext.lastValidationErrors)
            .describedAs("Validation errors")
            .isEmpty()
    }

    @Then("the response body should not be empty")
    fun theResponseBodyShouldNotBeEmpty() {
        assertThat(testContext.lastResponseBody)
            .describedAs("Response body")
            .isNotNull
            .isNotEmpty
    }

    @Then("the response body should be empty")
    fun theResponseBodyShouldBeEmpty() {
        assertThat(testContext.lastResponseBody)
            .describedAs("Response body")
            .isNullOrEmpty()
    }
}
