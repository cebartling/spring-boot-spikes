package com.pintailconsultingllc.cdcdebezium.steps

import com.pintailconsultingllc.cdcdebezium.document.CdcMetadata
import com.pintailconsultingllc.cdcdebezium.document.CdcOperation
import com.pintailconsultingllc.cdcdebezium.document.CustomerDocument
import com.pintailconsultingllc.cdcdebezium.repository.CustomerMongoRepository
import io.cucumber.datatable.DataTable
import io.cucumber.java.After
import io.cucumber.java.Before
import io.cucumber.java.en.And
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

class MongoDbConfigurationSteps {

    @Autowired
    private lateinit var applicationContext: ApplicationContext

    @Autowired
    private lateinit var customerMongoRepository: CustomerMongoRepository

    @Autowired
    private lateinit var reactiveMongoTemplate: ReactiveMongoTemplate

    private var mongoHealthy = false
    private var repositoryBean: Any? = null
    private var savedDocument: CustomerDocument? = null
    private var retrievedDocument: CustomerDocument? = null
    private var queryResults: List<CustomerDocument> = emptyList()
    private val savedDocumentIds = mutableListOf<String>()

    @Before("@requires-mongodb")
    fun setup() {
        // Clean up all test data to ensure test isolation
        try {
            customerMongoRepository.deleteAll()
                .timeout(Duration.ofSeconds(5))
                .block()
        } catch (_: Exception) {
        }
        savedDocumentIds.clear()
        queryResults = emptyList()
    }

    @After("@requires-mongodb")
    fun cleanup() {
        savedDocumentIds.forEach { id ->
            try {
                customerMongoRepository.deleteById(id)
                    .timeout(Duration.ofSeconds(5))
                    .block()
            } catch (_: Exception) {
            }
        }
        savedDocumentIds.clear()
    }

    @Given("MongoDB is running and accessible")
    fun mongoDbIsRunningAndAccessible() {
        try {
            val count = customerMongoRepository.count()
                .timeout(Duration.ofSeconds(5))
                .block()
            mongoHealthy = count != null && count >= 0
            assertTrue(mongoHealthy, "MongoDB health check failed: unable to query database")
        } catch (e: Exception) {
            fail("MongoDB is not accessible: ${e.message}")
        }
    }

    @Then("MongoDB should be healthy")
    fun mongoDbShouldBeHealthy() {
        assertTrue(mongoHealthy, "MongoDB is not healthy")
    }

    @Given("the application context is loaded")
    fun theApplicationContextIsLoaded() {
        assertNotNull(applicationContext, "Application context should be loaded")
    }

    @Given("the application is started")
    fun theApplicationIsStarted() {
        assertNotNull(applicationContext, "Application should be started")
    }

    @When("I check for CustomerMongoRepository bean")
    fun iCheckForCustomerMongoRepositoryBean() {
        repositoryBean = applicationContext.getBean(CustomerMongoRepository::class.java)
    }

    @Then("the repository bean should be available")
    fun theRepositoryBeanShouldBeAvailable() {
        assertNotNull(repositoryBean, "CustomerMongoRepository bean should be available")
    }

    @And("it should be a reactive repository")
    fun itShouldBeAReactiveRepository() {
        assertTrue(
            repositoryBean is ReactiveMongoRepository<*, *>,
            "Repository should be a ReactiveMongoRepository"
        )
    }

    @When("I save a CustomerDocument with id {string}")
    fun iSaveACustomerDocumentWithId(id: String) {
        val document = CustomerDocument(
            id = id,
            email = "$id@test.com",
            status = "active",
            updatedAt = Instant.now(),
            cdcMetadata = CdcMetadata(
                sourceTimestamp = System.currentTimeMillis(),
                operation = CdcOperation.INSERT,
                kafkaOffset = 0L,
                kafkaPartition = 0
            )
        )

        savedDocument = customerMongoRepository.save(document)
            .timeout(Duration.ofSeconds(5))
            .block()
        savedDocumentIds.add(id)

        assertNotNull(savedDocument, "Document should be saved successfully")
    }

    @Then("the document should be persisted in MongoDB")
    fun theDocumentShouldBePersistedInMongoDB() {
        assertNotNull(savedDocument, "Saved document should not be null")
        val exists = customerMongoRepository.existsById(savedDocument!!.id)
            .timeout(Duration.ofSeconds(5))
            .block()
        assertTrue(exists == true, "Document should exist in MongoDB")
    }

    @And("I should be able to retrieve it by id {string}")
    fun iShouldBeAbleToRetrieveItById(id: String) {
        retrievedDocument = customerMongoRepository.findById(id)
            .timeout(Duration.ofSeconds(5))
            .block()
        assertNotNull(retrievedDocument, "Should be able to retrieve document by id")
        assertEquals(id, retrievedDocument!!.id, "Retrieved document should have correct id")
    }

    @When("I save a CustomerDocument with CDC metadata")
    fun iSaveACustomerDocumentWithCdcMetadata() {
        val id = "cdc-metadata-test-${System.currentTimeMillis()}"
        val document = CustomerDocument(
            id = id,
            email = "$id@test.com",
            status = "active",
            updatedAt = Instant.now(),
            cdcMetadata = CdcMetadata(
                sourceTimestamp = 1705315800000L,
                operation = CdcOperation.UPDATE,
                kafkaOffset = 42L,
                kafkaPartition = 3
            )
        )

        savedDocument = customerMongoRepository.save(document)
            .timeout(Duration.ofSeconds(5))
            .block()
        savedDocumentIds.add(id)

        retrievedDocument = customerMongoRepository.findById(id)
            .timeout(Duration.ofSeconds(5))
            .block()
    }

    @Then("the document should contain cdcMetadata field")
    fun theDocumentShouldContainCdcMetadataField() {
        assertNotNull(retrievedDocument, "Retrieved document should not be null")
        assertNotNull(retrievedDocument!!.cdcMetadata, "Document should contain cdcMetadata")
    }

    @And("cdcMetadata should have sourceTimestamp")
    fun cdcMetadataShouldHaveSourceTimestamp() {
        assertNotNull(
            retrievedDocument!!.cdcMetadata.sourceTimestamp,
            "cdcMetadata should have sourceTimestamp"
        )
        assertEquals(
            1705315800000L,
            retrievedDocument!!.cdcMetadata.sourceTimestamp,
            "sourceTimestamp should match"
        )
    }

    @And("cdcMetadata should have operation")
    fun cdcMetadataShouldHaveOperation() {
        assertNotNull(
            retrievedDocument!!.cdcMetadata.operation,
            "cdcMetadata should have operation"
        )
        assertEquals(
            CdcOperation.UPDATE,
            retrievedDocument!!.cdcMetadata.operation,
            "operation should match"
        )
    }

    @And("cdcMetadata should have processedAt")
    fun cdcMetadataShouldHaveProcessedAt() {
        assertNotNull(
            retrievedDocument!!.cdcMetadata.processedAt,
            "cdcMetadata should have processedAt"
        )
    }

    @Given("I have saved the following CustomerDocuments:")
    fun iHaveSavedTheFollowingCustomerDocuments(dataTable: DataTable) {
        val rows = dataTable.asMaps()
        rows.forEach { row ->
            val id = row["id"]!!
            val email = row["email"]!!
            val status = row["status"]!!

            val document = CustomerDocument(
                id = id,
                email = email,
                status = status,
                updatedAt = Instant.now(),
                cdcMetadata = CdcMetadata(
                    sourceTimestamp = System.currentTimeMillis(),
                    operation = CdcOperation.INSERT,
                    kafkaOffset = 0L,
                    kafkaPartition = 0
                )
            )

            customerMongoRepository.save(document)
                .timeout(Duration.ofSeconds(5))
                .block()
            savedDocumentIds.add(id)
        }
    }

    @When("I query for documents with status {string}")
    fun iQueryForDocumentsWithStatus(status: String) {
        queryResults = customerMongoRepository.findByStatus(status)
            .timeout(Duration.ofSeconds(5))
            .collectList()
            .block() ?: emptyList()
    }

    @Then("only documents with status {string} should be returned")
    fun onlyDocumentsWithStatusShouldBeReturned(status: String) {
        assertTrue(
            queryResults.all { it.status == status },
            "All returned documents should have status '$status'"
        )
    }

    @And("the result count should be {int}")
    fun theResultCountShouldBe(expectedCount: Int) {
        assertEquals(
            expectedCount,
            queryResults.size,
            "Result count should be $expectedCount"
        )
    }
}
