package com.pintailconsultingllc.cqrsspike.acceptance.steps

import com.pintailconsultingllc.cqrsspike.acceptance.context.TestContext
import com.pintailconsultingllc.cqrsspike.acceptance.helpers.ResponseParsingHelper
import com.pintailconsultingllc.cqrsspike.product.api.dto.ActivateProductRequest
import com.pintailconsultingllc.cqrsspike.product.api.dto.ChangePriceRequest
import com.pintailconsultingllc.cqrsspike.product.api.dto.CreateProductRequest
import com.pintailconsultingllc.cqrsspike.product.api.dto.DiscontinueProductRequest
import com.pintailconsultingllc.cqrsspike.product.api.dto.UpdateProductRequest
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.assertj.core.api.Assertions.assertThat
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient

/**
 * Step definitions for product lifecycle scenarios.
 *
 * Covers CRUD operations on products:
 * - Creating products
 * - Updating product details
 * - Changing product prices
 * - Activating products
 * - Discontinuing products
 * - Deleting products
 */
class ProductLifecycleSteps {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var testContext: TestContext

    @Autowired
    private lateinit var responseParsingHelper: ResponseParsingHelper

    // Temporary storage for building request data
    private var pendingProductSku: String = ""
    private var pendingProductName: String = ""
    private var pendingProductDescription: String? = null
    private var pendingProductPriceCents: Int = 0
    private var pendingExpectedVersion: Long = 0

    // ========== Given Steps ==========

    @Given("a product with SKU {string} exists")
    fun aProductWithSkuExists(sku: String) {
        createProduct(sku, "Test Product $sku", null, 1999)
    }

    @Given("a product with SKU {string} and name {string} exists")
    fun aProductWithSkuAndNameExists(sku: String, name: String) {
        createProduct(sku, name, null, 1999)
    }

    @Given("a product with SKU {string} and price {int} cents exists")
    fun aProductWithSkuAndPriceExists(sku: String, priceCents: Int) {
        createProduct(sku, "Test Product $sku", null, priceCents)
    }

    @Given("a product with SKU {string}, name {string}, and price {int} cents exists")
    fun aProductWithSkuNameAndPriceExists(sku: String, name: String, priceCents: Int) {
        createProduct(sku, name, null, priceCents)
    }

    @Given("an active product with SKU {string} exists")
    fun anActiveProductWithSkuExists(sku: String) {
        createProduct(sku, "Active Product $sku", null, 1999)
        activateCurrentProduct()
    }

    @Given("a discontinued product with SKU {string} exists")
    fun aDiscontinuedProductWithSkuExists(sku: String) {
        createProduct(sku, "Discontinued Product $sku", null, 1999)
        activateCurrentProduct()
        discontinueCurrentProduct()
    }

    @Given("I have product data with SKU {string}")
    fun iHaveProductDataWithSku(sku: String) {
        pendingProductSku = sku
        pendingProductName = "Default Product Name"
        pendingProductDescription = null
        pendingProductPriceCents = 1999
    }

    @Given("the product has name {string}")
    fun theProductHasName(name: String) {
        pendingProductName = name
    }

    @Given("the product has description {string}")
    fun theProductHasDescription(description: String) {
        pendingProductDescription = description
    }

    @Given("the product has price {int} cents")
    fun theProductHasPriceCents(priceCents: Int) {
        pendingProductPriceCents = priceCents
    }

    // ========== When Steps ==========

    @When("I create a product with SKU {string}, name {string}, and price {int} cents")
    fun iCreateProductWithSkuNameAndPrice(sku: String, name: String, priceCents: Int) {
        createProductAndStoreResponse(sku, name, null, priceCents)
    }

    @When("I create a product with SKU {string}, name {string}, description {string}, and price {int} cents")
    fun iCreateProductWithSkuNameDescriptionAndPrice(sku: String, name: String, description: String, priceCents: Int) {
        createProductAndStoreResponse(sku, name, description, priceCents)
    }

    @When("I create the product")
    fun iCreateTheProduct() {
        createProductAndStoreResponse(pendingProductSku, pendingProductName, pendingProductDescription, pendingProductPriceCents)
    }

    @When("I update the product name to {string}")
    fun iUpdateTheProductNameTo(newName: String) {
        val productId = testContext.currentProductId
            ?: throw IllegalStateException("No current product ID in context")

        val request = UpdateProductRequest(
            name = newName,
            description = null,
            expectedVersion = pendingExpectedVersion
        )

        val response = webTestClient.put()
            .uri("/api/products/{id}", productId)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .returnResult(String::class.java)

        testContext.lastResponseStatus = response.status
        testContext.lastResponseBody = response.responseBody.blockFirst()
        responseParsingHelper.parseErrorResponse()
    }

    @When("I update the product description to {string}")
    fun iUpdateTheProductDescriptionTo(newDescription: String) {
        val productId = testContext.currentProductId
            ?: throw IllegalStateException("No current product ID in context")

        val request = UpdateProductRequest(
            name = pendingProductName,
            description = newDescription,
            expectedVersion = pendingExpectedVersion
        )

        val response = webTestClient.put()
            .uri("/api/products/{id}", productId)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .returnResult(String::class.java)

        testContext.lastResponseStatus = response.status
        testContext.lastResponseBody = response.responseBody.blockFirst()
        responseParsingHelper.parseErrorResponse()
    }

    @When("I change the product price to {int} cents")
    fun iChangeTheProductPriceTo(newPriceCents: Int) {
        changeProductPrice(newPriceCents, false)
    }

    @When("I change the product price to {int} cents with confirmation")
    fun iChangeTheProductPriceToWithConfirmation(newPriceCents: Int) {
        changeProductPrice(newPriceCents, true)
    }

    @When("I activate the product")
    fun iActivateTheProduct() {
        val productId = testContext.currentProductId
            ?: throw IllegalStateException("No current product ID in context")

        val request = ActivateProductRequest(expectedVersion = pendingExpectedVersion)

        val response = webTestClient.post()
            .uri("/api/products/{id}/activate", productId)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .returnResult(String::class.java)

        testContext.lastResponseStatus = response.status
        testContext.lastResponseBody = response.responseBody.blockFirst()
        responseParsingHelper.parseErrorResponse()
        updateVersionFromResponse()
    }

    @When("I discontinue the product")
    fun iDiscontinueTheProduct() {
        val productId = testContext.currentProductId
            ?: throw IllegalStateException("No current product ID in context")

        val request = DiscontinueProductRequest(
            reason = null,
            expectedVersion = pendingExpectedVersion
        )

        val response = webTestClient.post()
            .uri("/api/products/{id}/discontinue", productId)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .returnResult(String::class.java)

        testContext.lastResponseStatus = response.status
        testContext.lastResponseBody = response.responseBody.blockFirst()
        responseParsingHelper.parseErrorResponse()
        updateVersionFromResponse()
    }

    @When("I discontinue the product with reason {string}")
    fun iDiscontinueTheProductWithReason(reason: String) {
        val productId = testContext.currentProductId
            ?: throw IllegalStateException("No current product ID in context")

        val request = DiscontinueProductRequest(
            reason = reason,
            expectedVersion = pendingExpectedVersion
        )

        val response = webTestClient.post()
            .uri("/api/products/{id}/discontinue", productId)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .returnResult(String::class.java)

        testContext.lastResponseStatus = response.status
        testContext.lastResponseBody = response.responseBody.blockFirst()
        responseParsingHelper.parseErrorResponse()
        updateVersionFromResponse()
    }

    @When("I delete the product")
    fun iDeleteTheProduct() {
        val productId = testContext.currentProductId
            ?: throw IllegalStateException("No current product ID in context")

        // Delete endpoint uses query parameters, not request body
        val response = webTestClient.delete()
            .uri { builder ->
                builder.path("/api/products/{id}")
                    .queryParam("expectedVersion", pendingExpectedVersion)
                    .queryParam("deletedBy", "acceptance-test")
                    .build(productId)
            }
            .exchange()
            .returnResult(String::class.java)

        testContext.lastResponseStatus = response.status
        testContext.lastResponseBody = response.responseBody.blockFirst()
        responseParsingHelper.parseErrorResponse()
    }

    @When("I try to create a product with duplicate SKU {string}")
    fun iTryToCreateProductWithDuplicateSku(sku: String) {
        createProductAndStoreResponse(sku, "Duplicate Product", null, 1999)
    }

    @When("I use expected version {long}")
    fun iUseExpectedVersion(version: Long) {
        pendingExpectedVersion = version
    }

    // ========== Then Steps ==========

    @Then("the product should be created successfully")
    fun theProductShouldBeCreatedSuccessfully() {
        assertThat(testContext.lastResponseStatus?.is2xxSuccessful)
            .describedAs("Response should be successful")
            .isTrue
        assertThat(testContext.currentProductId)
            .describedAs("Product ID should be set")
            .isNotNull
    }

    @Then("the product should have SKU {string}")
    fun theProductShouldHaveSku(expectedSku: String) {
        val body = testContext.lastResponseBody
        assertThat(body)
            .describedAs("Response body")
            .isNotNull
            .contains("\"sku\":\"$expectedSku\"")
    }

    @Then("the product should have name {string}")
    fun theProductShouldHaveName(expectedName: String) {
        // CommandSuccess response doesn't include name, so we need to fetch from read model
        val productId = testContext.currentProductId
            ?: throw IllegalStateException("No current product ID in context")

        // Retry with exponential backoff for eventual consistency
        val body = fetchProductWithRetry(productId)
        assertThat(body)
            .describedAs("Product response body")
            .isNotNull
            .contains("\"name\":\"$expectedName\"")
    }

    @Then("the product should have price {int} cents")
    fun theProductShouldHavePriceCents(expectedPriceCents: Int) {
        // CommandSuccess response doesn't include priceCents, so we need to fetch from read model
        val productId = testContext.currentProductId
            ?: throw IllegalStateException("No current product ID in context")

        // Retry with exponential backoff for eventual consistency
        val body = fetchProductWithRetry(productId)
        assertThat(body)
            .describedAs("Product response body")
            .isNotNull
            .contains("\"priceCents\":$expectedPriceCents")
    }

    /**
     * Fetches a product from the read model with retry logic to handle eventual consistency.
     */
    private fun fetchProductWithRetry(productId: java.util.UUID): String? {
        val maxAttempts = 5
        val initialDelayMs = 100L

        for (attempt in 1..maxAttempts) {
            Thread.sleep(initialDelayMs * attempt)

            val response = webTestClient.get()
                .uri("/api/products/{id}", productId)
                .exchange()
                .returnResult(String::class.java)

            val body = response.responseBody.blockFirst()
            if (response.status.is2xxSuccessful && body != null && body.contains("\"id\":")) {
                return body
            }
        }
        return null
    }

    @Then("the product status should be {string}")
    fun theProductStatusShouldBe(expectedStatus: String) {
        val body = testContext.lastResponseBody
        assertThat(body)
            .describedAs("Response body")
            .isNotNull
            .contains("\"status\":\"$expectedStatus\"")
    }

    @Then("the product version should be {long}")
    fun theProductVersionShouldBe(expectedVersion: Long) {
        val body = testContext.lastResponseBody
        assertThat(body)
            .describedAs("Response body")
            .isNotNull
            .contains("\"version\":$expectedVersion")
    }

    @Then("the product should be updated successfully")
    fun theProductShouldBeUpdatedSuccessfully() {
        assertThat(testContext.lastResponseStatus?.is2xxSuccessful)
            .describedAs("Response should be successful")
            .isTrue
    }

    @Then("the product should be activated")
    fun theProductShouldBeActivated() {
        assertThat(testContext.lastResponseStatus?.is2xxSuccessful)
            .describedAs("Response should be successful")
            .isTrue
        theProductStatusShouldBe("ACTIVE")
    }

    @Then("the product should be discontinued")
    fun theProductShouldBeDiscontinued() {
        assertThat(testContext.lastResponseStatus?.is2xxSuccessful)
            .describedAs("Response should be successful")
            .isTrue
        theProductStatusShouldBe("DISCONTINUED")
    }

    @Then("the product should be deleted")
    fun theProductShouldBeDeleted() {
        assertThat(testContext.lastResponseStatus?.is2xxSuccessful)
            .describedAs("Response should be successful")
            .isTrue
    }

    // ========== Helper Methods ==========

    private fun createProduct(sku: String, name: String, description: String?, priceCents: Int) {
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

        responseParsingHelper.extractProductIdFromResponse()
        updateVersionFromResponse()
        pendingProductName = name

        // Verify product was actually created - fail fast if not
        if (testContext.currentProductId == null) {
            throw IllegalStateException(
                "Failed to create product with SKU '$sku'. " +
                "Status: ${testContext.lastResponseStatus}, " +
                "Body: ${testContext.lastResponseBody}"
            )
        }
    }

    private fun createProductAndStoreResponse(sku: String, name: String, description: String?, priceCents: Int) {
        createProduct(sku, name, description, priceCents)
        responseParsingHelper.parseErrorResponse()
    }

    private fun activateCurrentProduct() {
        val productId = testContext.currentProductId
            ?: throw IllegalStateException("No current product ID in context")

        val request = ActivateProductRequest(expectedVersion = pendingExpectedVersion)

        val response = webTestClient.post()
            .uri("/api/products/{id}/activate", productId)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .returnResult(String::class.java)

        testContext.lastResponseStatus = response.status
        testContext.lastResponseBody = response.responseBody.blockFirst()
        updateVersionFromResponse()
    }

    private fun discontinueCurrentProduct() {
        val productId = testContext.currentProductId
            ?: throw IllegalStateException("No current product ID in context")

        val request = DiscontinueProductRequest(
            reason = "Test discontinuation",
            expectedVersion = pendingExpectedVersion
        )

        val response = webTestClient.post()
            .uri("/api/products/{id}/discontinue", productId)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .returnResult(String::class.java)

        testContext.lastResponseStatus = response.status
        testContext.lastResponseBody = response.responseBody.blockFirst()
        updateVersionFromResponse()
    }

    private fun changeProductPrice(newPriceCents: Int, confirmLargeChange: Boolean) {
        val productId = testContext.currentProductId
            ?: throw IllegalStateException("No current product ID in context")

        val request = ChangePriceRequest(
            newPriceCents = newPriceCents,
            confirmLargeChange = confirmLargeChange,
            expectedVersion = pendingExpectedVersion
        )

        val response = webTestClient.patch()
            .uri("/api/products/{id}/price", productId)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .returnResult(String::class.java)

        testContext.lastResponseStatus = response.status
        testContext.lastResponseBody = response.responseBody.blockFirst()
        responseParsingHelper.parseErrorResponse()
        updateVersionFromResponse()
    }

    private fun updateVersionFromResponse() {
        val version = responseParsingHelper.extractVersionFromResponse()
        if (version != null) {
            pendingExpectedVersion = version
        }
    }
}
