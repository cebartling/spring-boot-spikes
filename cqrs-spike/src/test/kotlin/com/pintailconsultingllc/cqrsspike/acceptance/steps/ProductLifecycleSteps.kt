package com.pintailconsultingllc.cqrsspike.acceptance.steps

import com.fasterxml.jackson.databind.ObjectMapper
import com.pintailconsultingllc.cqrsspike.acceptance.context.TestContext
import com.pintailconsultingllc.cqrsspike.acceptance.context.ValidationError
import com.pintailconsultingllc.cqrsspike.product.api.dto.ActivateProductRequest
import com.pintailconsultingllc.cqrsspike.product.api.dto.ChangePriceRequest
import com.pintailconsultingllc.cqrsspike.product.api.dto.CreateProductRequest
import com.pintailconsultingllc.cqrsspike.product.api.dto.DeleteProductRequest
import com.pintailconsultingllc.cqrsspike.product.api.dto.DiscontinueProductRequest
import com.pintailconsultingllc.cqrsspike.product.api.dto.UpdateProductRequest
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.assertj.core.api.Assertions.assertThat
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import java.util.UUID

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
    private lateinit var objectMapper: ObjectMapper

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
        parseErrorResponse()
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
        parseErrorResponse()
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
        parseErrorResponse()
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
        parseErrorResponse()
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
        parseErrorResponse()
        updateVersionFromResponse()
    }

    @When("I delete the product")
    fun iDeleteTheProduct() {
        val productId = testContext.currentProductId
            ?: throw IllegalStateException("No current product ID in context")

        val request = DeleteProductRequest(
            deletedBy = "acceptance-test",
            expectedVersion = pendingExpectedVersion
        )

        val response = webTestClient.method(org.springframework.http.HttpMethod.DELETE)
            .uri("/api/products/{id}", productId)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .returnResult(String::class.java)

        testContext.lastResponseStatus = response.status
        testContext.lastResponseBody = response.responseBody.blockFirst()
        parseErrorResponse()
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
        val body = testContext.lastResponseBody
        assertThat(body)
            .describedAs("Response body")
            .isNotNull
            .contains("\"name\":\"$expectedName\"")
    }

    @Then("the product should have price {int} cents")
    fun theProductShouldHavePriceCents(expectedPriceCents: Int) {
        val body = testContext.lastResponseBody
        assertThat(body)
            .describedAs("Response body")
            .isNotNull
            .contains("\"priceCents\":$expectedPriceCents")
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

        extractProductIdFromResponse()
        updateVersionFromResponse()
        pendingProductName = name
    }

    private fun createProductAndStoreResponse(sku: String, name: String, description: String?, priceCents: Int) {
        createProduct(sku, name, description, priceCents)
        parseErrorResponse()
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
            // Response may not contain productId (e.g., error responses)
        }
    }

    private fun updateVersionFromResponse() {
        val body = testContext.lastResponseBody ?: return
        try {
            val jsonNode = objectMapper.readTree(body)
            val version = jsonNode.get("version")?.asLong()
            if (version != null) {
                pendingExpectedVersion = version
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
            // Response may not be JSON or may not have expected structure
        }
    }
}
