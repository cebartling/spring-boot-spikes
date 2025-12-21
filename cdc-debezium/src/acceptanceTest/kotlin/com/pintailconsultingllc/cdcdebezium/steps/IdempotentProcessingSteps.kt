package com.pintailconsultingllc.cdcdebezium.steps

import com.pintailconsultingllc.cdcdebezium.document.CdcMetadata
import com.pintailconsultingllc.cdcdebezium.document.CdcOperation
import com.pintailconsultingllc.cdcdebezium.document.CustomerDocument
import com.pintailconsultingllc.cdcdebezium.dto.CustomerCdcEvent
import com.pintailconsultingllc.cdcdebezium.repository.CustomerMongoRepository
import com.pintailconsultingllc.cdcdebezium.service.CustomerMongoService
import io.cucumber.datatable.DataTable
import io.cucumber.java.Before
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IdempotentProcessingSteps {

    @Autowired
    private lateinit var customerMongoService: CustomerMongoService

    @Autowired
    private lateinit var customerMongoRepository: CustomerMongoRepository

    private var currentCustomerId: UUID? = null
    private var lastError: Throwable? = null

    @Before
    fun setUp() {
        lastError = null
        currentCustomerId = null
    }

    @Given("the customer materialized table is empty")
    fun theCustomerMaterializedTableIsEmpty() {
        customerMongoRepository.deleteAll().block()
    }

    @Given("a customer does not exist with id {string}")
    fun aCustomerDoesNotExistWithId(idString: String) {
        val id = UUID.fromString(idString)
        customerMongoRepository.deleteById(id.toString()).block()
        currentCustomerId = id
    }

    @Given("a customer exists in the materialized table:")
    fun aCustomerExistsInTheMaterializedTable(dataTable: DataTable) {
        val row = dataTable.asMaps()[0]
        val id = UUID.fromString(row["id"])
        val document = CustomerDocument(
            id = id.toString(),
            email = row["email"] ?: "",
            status = row["status"] ?: "",
            updatedAt = Instant.now(),
            cdcMetadata = CdcMetadata(
                sourceTimestamp = row["sourceTimestamp"]?.toLongOrNull() ?: System.currentTimeMillis(),
                operation = CdcOperation.INSERT,
                kafkaOffset = 0L,
                kafkaPartition = 0
            )
        )
        customerMongoRepository.save(document).block()
        currentCustomerId = id
    }

    @When("a CDC insert event is processed for customer:")
    fun aCdcInsertEventIsProcessedForCustomer(dataTable: DataTable) {
        processEventFromDataTable(dataTable, "c")
    }

    @When("a CDC update event is processed for customer:")
    fun aCdcUpdateEventIsProcessedForCustomer(dataTable: DataTable) {
        processEventFromDataTable(dataTable, "u")
    }

    @When("a CDC delete event is processed for customer id {string}")
    fun aCdcDeleteEventIsProcessedForCustomerId(idString: String) {
        val id = UUID.fromString(idString)
        currentCustomerId = id
        try {
            customerMongoService.delete(
                id = id.toString(),
                sourceTimestamp = System.currentTimeMillis(),
                kafkaOffset = 0L,
                kafkaPartition = 0
            ).block()
        } catch (e: Throwable) {
            lastError = e
        }
    }

    @Then("a customer should exist in the materialized table with id {string}")
    fun aCustomerShouldExistInTheMaterializedTableWithId(idString: String) {
        val id = UUID.fromString(idString)
        val customer = customerMongoRepository.findById(id.toString()).block()
        assertTrue(customer != null, "Customer with id $id should exist")
        currentCustomerId = id
    }

    @Then("a customer should not exist in the materialized table with id {string}")
    fun aCustomerShouldNotExistInTheMaterializedTableWithId(idString: String) {
        val id = UUID.fromString(idString)
        val customer = customerMongoRepository.findById(id.toString()).block()
        assertNull(customer, "Customer with id $id should not exist")
    }

    @Then("the customer should have email {string}")
    fun theCustomerShouldHaveEmail(expectedEmail: String) {
        val customer = customerMongoRepository.findById(currentCustomerId!!.toString()).block()
        assertEquals(expectedEmail, customer?.email)
    }

    @Then("the customer should have status {string}")
    fun theCustomerShouldHaveStatus(expectedStatus: String) {
        val customer = customerMongoRepository.findById(currentCustomerId!!.toString()).block()
        assertEquals(expectedStatus, customer?.status)
    }

    @Then("the customer count in the materialized table should be {int}")
    fun theCustomerCountInTheMaterializedTableShouldBe(expectedCount: Int) {
        val count = customerMongoRepository.count().block()
        assertEquals(expectedCount.toLong(), count)
    }

    @Then("no error should occur")
    fun noErrorShouldOccur() {
        assertNull(lastError, "Expected no error but got: ${lastError?.message}")
    }

    private fun processEventFromDataTable(dataTable: DataTable, operation: String) {
        val row = dataTable.asMaps()[0]
        val id = UUID.fromString(row["id"])
        currentCustomerId = id

        val event = CustomerCdcEvent(
            id = id,
            email = row["email"],
            status = row["status"],
            updatedAt = Instant.now(),
            operation = operation,
            sourceTimestamp = row["sourceTimestamp"]?.toLongOrNull()
        )

        try {
            customerMongoService.upsert(event, 0L, 0).block()
        } catch (e: Throwable) {
            lastError = e
        }
    }
}
