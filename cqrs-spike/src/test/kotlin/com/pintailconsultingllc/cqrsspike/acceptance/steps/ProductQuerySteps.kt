package com.pintailconsultingllc.cqrsspike.acceptance.steps

import com.fasterxml.jackson.databind.ObjectMapper
import com.pintailconsultingllc.cqrsspike.acceptance.context.ProductResult
import com.pintailconsultingllc.cqrsspike.acceptance.context.TestContext
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.assertj.core.api.Assertions.assertThat
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.reactive.server.WebTestClient
import java.util.UUID

/**
 * Step definitions for product query scenarios.
 *
 * Covers:
 * - Single product retrieval by ID
 * - Product listing with pagination
 * - Filtering by status
 * - Text search functionality
 * - Sorting options
 */
class ProductQuerySteps {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var testContext: TestContext

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    // ========== Given Steps ==========

    @Given("there are {int} products in the catalog")
    fun thereAreProductsInTheCatalog(count: Int) {
        repeat(count) { index ->
            createTestProduct("TEST-SKU-$index", "Test Product $index", 1999 + index * 100)
        }
    }

    @Given("there are {int} active products in the catalog")
    fun thereAreActiveProductsInTheCatalog(count: Int) {
        repeat(count) { index ->
            createTestProduct("ACTIVE-SKU-$index", "Active Product $index", 1999 + index * 100)
            activateProduct(testContext.currentProductId!!)
        }
    }

    @Given("there are {int} draft products in the catalog")
    fun thereAreDraftProductsInTheCatalog(count: Int) {
        repeat(count) { index ->
            createTestProduct("DRAFT-SKU-$index", "Draft Product $index", 1999 + index * 100)
        }
    }

    @Given("products with names containing {string} exist")
    fun productsWithNamesContainingExist(searchTerm: String) {
        createTestProduct("SEARCH-1", "Product with $searchTerm in name", 1999)
        createTestProduct("SEARCH-2", "Another $searchTerm product", 2999)
        createTestProduct("SEARCH-3", "$searchTerm at start", 3999)
    }

    // ========== When Steps ==========

    @When("I retrieve the product by ID")
    fun iRetrieveTheProductById() {
        val productId = testContext.currentProductId
            ?: throw IllegalStateException("No current product ID in context")

        val response = webTestClient.get()
            .uri("/api/products/{id}", productId)
            .exchange()
            .returnResult(String::class.java)

        testContext.lastResponseStatus = response.status
        testContext.lastResponseBody = response.responseBody.blockFirst()
        parseProductResponse()
    }

    @When("I retrieve a product with ID {string}")
    fun iRetrieveProductWithId(productId: String) {
        val response = webTestClient.get()
            .uri("/api/products/{id}", productId)
            .exchange()
            .returnResult(String::class.java)

        testContext.lastResponseStatus = response.status
        testContext.lastResponseBody = response.responseBody.blockFirst()
        parseProductResponse()
    }

    @When("I retrieve a product with non-existent ID")
    fun iRetrieveProductWithNonExistentId() {
        val nonExistentId = UUID.randomUUID()

        val response = webTestClient.get()
            .uri("/api/products/{id}", nonExistentId)
            .exchange()
            .returnResult(String::class.java)

        testContext.lastResponseStatus = response.status
        testContext.lastResponseBody = response.responseBody.blockFirst()
        parseErrorResponse()
    }

    @When("I list all products")
    fun iListAllProducts() {
        val response = webTestClient.get()
            .uri("/api/products")
            .exchange()
            .returnResult(String::class.java)

        testContext.lastResponseStatus = response.status
        testContext.lastResponseBody = response.responseBody.blockFirst()
        parsePageResponse()
    }

    @When("I list products with page {int} and size {int}")
    fun iListProductsWithPageAndSize(page: Int, size: Int) {
        val response = webTestClient.get()
            .uri { uriBuilder ->
                uriBuilder
                    .path("/api/products")
                    .queryParam("page", page)
                    .queryParam("size", size)
                    .build()
            }
            .exchange()
            .returnResult(String::class.java)

        testContext.lastResponseStatus = response.status
        testContext.lastResponseBody = response.responseBody.blockFirst()
        parsePageResponse()
    }

    @When("I list products filtered by status {string}")
    fun iListProductsFilteredByStatus(status: String) {
        val response = webTestClient.get()
            .uri("/api/products/by-status/{status}", status)
            .exchange()
            .returnResult(String::class.java)

        testContext.lastResponseStatus = response.status
        testContext.lastResponseBody = response.responseBody.blockFirst()
        parsePageResponse()
    }

    @When("I search for products with query {string}")
    fun iSearchForProductsWithQuery(query: String) {
        val response = webTestClient.get()
            .uri { uriBuilder ->
                uriBuilder
                    .path("/api/products/search")
                    .queryParam("q", query)
                    .build()
            }
            .exchange()
            .returnResult(String::class.java)

        testContext.lastResponseStatus = response.status
        testContext.lastResponseBody = response.responseBody.blockFirst()
        parseSearchResponse()
    }

    @When("I list products sorted by {string} in {string} order")
    fun iListProductsSortedByInOrder(sortField: String, direction: String) {
        val response = webTestClient.get()
            .uri { uriBuilder ->
                uriBuilder
                    .path("/api/products")
                    .queryParam("sort", sortField)
                    .queryParam("direction", direction)
                    .build()
            }
            .exchange()
            .returnResult(String::class.java)

        testContext.lastResponseStatus = response.status
        testContext.lastResponseBody = response.responseBody.blockFirst()
        parsePageResponse()
    }

    // ========== Then Steps ==========

    @Then("I should receive the product details")
    fun iShouldReceiveTheProductDetails() {
        assertThat(testContext.lastResponseStatus?.is2xxSuccessful)
            .describedAs("Response should be successful")
            .isTrue
        assertThat(testContext.lastQueryResults)
            .describedAs("Should have at least one result")
            .isNotEmpty
    }

    @Then("the returned product should have ID matching the created product")
    fun theReturnedProductShouldHaveIdMatchingTheCreatedProduct() {
        val expectedId = testContext.currentProductId
        assertThat(testContext.lastQueryResults)
            .describedAs("Query results")
            .anyMatch { it.id == expectedId }
    }

    @Then("I should receive {int} products")
    fun iShouldReceiveProducts(expectedCount: Int) {
        assertThat(testContext.lastQueryResults)
            .describedAs("Number of products")
            .hasSize(expectedCount)
    }

    @Then("the total count should be {long}")
    fun theTotalCountShouldBe(expectedTotal: Long) {
        assertThat(testContext.totalResultCount)
            .describedAs("Total result count")
            .isEqualTo(expectedTotal)
    }

    @Then("I should be on page {int}")
    fun iShouldBeOnPage(expectedPage: Int) {
        assertThat(testContext.currentPage)
            .describedAs("Current page")
            .isEqualTo(expectedPage)
    }

    @Then("there should be {int} total pages")
    fun thereShouldBeTotalPages(expectedPages: Int) {
        assertThat(testContext.totalPages)
            .describedAs("Total pages")
            .isEqualTo(expectedPages)
    }

    @Then("all returned products should have status {string}")
    fun allReturnedProductsShouldHaveStatus(expectedStatus: String) {
        assertThat(testContext.lastQueryResults)
            .describedAs("All products status")
            .allMatch { it.status == expectedStatus }
    }

    @Then("all returned products should contain {string} in their name or description")
    fun allReturnedProductsShouldContainInTheirNameOrDescription(searchTerm: String) {
        assertThat(testContext.lastQueryResults)
            .describedAs("All products should match search term")
            .allMatch {
                it.name.contains(searchTerm, ignoreCase = true) ||
                    (it.description?.contains(searchTerm, ignoreCase = true) == true)
            }
    }

    @Then("the products should be sorted by {string} in {string} order")
    fun theProductsShouldBeSortedByInOrder(field: String, direction: String) {
        val products = testContext.lastQueryResults
        if (products.size < 2) return // Nothing to verify with 0 or 1 elements

        val isAscending = direction.equals("ASC", ignoreCase = true)

        when (field.uppercase()) {
            "NAME" -> {
                val names = products.map { it.name }
                if (isAscending) {
                    assertThat(names).isSorted
                } else {
                    assertThat(names).isSortedAccordingTo(Comparator.reverseOrder())
                }
            }
            "PRICE" -> {
                val prices = products.map { it.priceCents }
                if (isAscending) {
                    assertThat(prices).isSorted
                } else {
                    assertThat(prices).isSortedAccordingTo(Comparator.reverseOrder())
                }
            }
        }
    }

    @Then("the first product should have SKU {string}")
    fun theFirstProductShouldHaveSku(expectedSku: String) {
        assertThat(testContext.lastQueryResults)
            .describedAs("Query results")
            .isNotEmpty
        assertThat(testContext.lastQueryResults.first().sku)
            .describedAs("First product SKU")
            .isEqualTo(expectedSku)
    }

    @Then("no products should be returned")
    fun noProductsShouldBeReturned() {
        assertThat(testContext.lastQueryResults)
            .describedAs("Query results")
            .isEmpty()
    }

    // ========== Helper Methods ==========

    private fun createTestProduct(sku: String, name: String, priceCents: Int) {
        val request = mapOf(
            "sku" to sku,
            "name" to name,
            "priceCents" to priceCents
        )

        val response = webTestClient.post()
            .uri("/api/products")
            .bodyValue(request)
            .exchange()
            .returnResult(String::class.java)

        val body = response.responseBody.blockFirst()
        extractProductIdFromBody(body)
    }

    private fun activateProduct(productId: UUID) {
        val request = mapOf("expectedVersion" to 1L)

        webTestClient.post()
            .uri("/api/products/{id}/activate", productId)
            .bodyValue(request)
            .exchange()
            .returnResult(String::class.java)
    }

    private fun extractProductIdFromBody(body: String?) {
        body ?: return
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

    private fun parseProductResponse() {
        val body = testContext.lastResponseBody ?: return
        try {
            val jsonNode = objectMapper.readTree(body)
            val product = ProductResult(
                id = UUID.fromString(jsonNode.get("id").asText()),
                sku = jsonNode.get("sku").asText(),
                name = jsonNode.get("name").asText(),
                description = jsonNode.get("description")?.asText(),
                priceCents = jsonNode.get("priceCents").asInt(),
                status = jsonNode.get("status").asText(),
                version = jsonNode.get("version").asLong()
            )
            testContext.lastQueryResults.clear()
            testContext.lastQueryResults.add(product)
        } catch (e: Exception) {
            // Response may not be a valid product response
        }
    }

    private fun parsePageResponse() {
        val body = testContext.lastResponseBody ?: return
        try {
            val jsonNode = objectMapper.readTree(body)
            testContext.lastQueryResults.clear()

            val content = jsonNode.get("content")
            if (content != null && content.isArray) {
                content.forEach { node ->
                    testContext.lastQueryResults.add(
                        ProductResult(
                            id = UUID.fromString(node.get("id").asText()),
                            sku = node.get("sku").asText(),
                            name = node.get("name").asText(),
                            description = node.get("description")?.asText(),
                            priceCents = node.get("priceCents").asInt(),
                            status = node.get("status").asText(),
                            version = node.get("version").asLong()
                        )
                    )
                }
            }

            testContext.totalResultCount = jsonNode.get("totalElements")?.asLong() ?: 0L
            testContext.currentPage = jsonNode.get("page")?.asInt() ?: 0
            testContext.totalPages = jsonNode.get("totalPages")?.asInt() ?: 0
        } catch (e: Exception) {
            // Response may not be a valid page response
        }
    }

    private fun parseSearchResponse() {
        val body = testContext.lastResponseBody ?: return
        try {
            val jsonNode = objectMapper.readTree(body)
            testContext.lastQueryResults.clear()

            val content = jsonNode.get("content")
            if (content != null && content.isArray) {
                content.forEach { node ->
                    testContext.lastQueryResults.add(
                        ProductResult(
                            id = UUID.fromString(node.get("id").asText()),
                            sku = node.get("sku").asText(),
                            name = node.get("name").asText(),
                            description = node.get("description")?.asText(),
                            priceCents = node.get("priceCents").asInt(),
                            status = node.get("status").asText(),
                            version = node.get("version").asLong()
                        )
                    )
                }
            }

            testContext.totalResultCount = jsonNode.get("totalMatches")?.asLong() ?: 0L
        } catch (e: Exception) {
            // Response may not be a valid search response
        }
    }

    private fun parseErrorResponse() {
        val body = testContext.lastResponseBody ?: return
        try {
            val jsonNode = objectMapper.readTree(body)
            testContext.lastErrorMessage = jsonNode.get("message")?.asText()
            testContext.lastErrorCode = jsonNode.get("code")?.asText()
        } catch (e: Exception) {
            // Response may not be JSON
        }
    }
}
