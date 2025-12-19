package com.pintailconsultingllc.cdcdebezium.steps

import com.pintailconsultingllc.cdcdebezium.dto.CustomerCdcEvent
import com.pintailconsultingllc.cdcdebezium.entity.CustomerEntity
import com.pintailconsultingllc.cdcdebezium.repository.CustomerRepository
import com.pintailconsultingllc.cdcdebezium.service.CustomerService
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
    private lateinit var customerService: CustomerService

    @Autowired
    private lateinit var customerRepository: CustomerRepository

    private var currentCustomerId: UUID? = null
    private var lastError: Throwable? = null

    @Before
    fun setUp() {
        lastError = null
        currentCustomerId = null
    }

    @Given("the customer materialized table is empty")
    fun theCustomerMaterializedTableIsEmpty() {
        customerRepository.deleteAll().block()
    }

    @Given("a customer does not exist with id {string}")
    fun aCustomerDoesNotExistWithId(idString: String) {
        val id = UUID.fromString(idString)
        customerRepository.deleteById(id).block()
        currentCustomerId = id
    }

    @Given("a customer exists in the materialized table:")
    fun aCustomerExistsInTheMaterializedTable(dataTable: DataTable) {
        val row = dataTable.asMaps()[0]
        val id = UUID.fromString(row["id"])
        val entity = CustomerEntity.create(
            id = id,
            email = row["email"] ?: "",
            status = row["status"] ?: "",
            updatedAt = Instant.now(),
            sourceTimestamp = row["sourceTimestamp"]?.toLongOrNull(),
            isNewEntity = true
        )
        customerRepository.save(entity).block()
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
            customerService.delete(id).block()
        } catch (e: Throwable) {
            lastError = e
        }
    }

    @Then("a customer should exist in the materialized table with id {string}")
    fun aCustomerShouldExistInTheMaterializedTableWithId(idString: String) {
        val id = UUID.fromString(idString)
        val customer = customerRepository.findById(id).block()
        assertTrue(customer != null, "Customer with id $id should exist")
        currentCustomerId = id
    }

    @Then("a customer should not exist in the materialized table with id {string}")
    fun aCustomerShouldNotExistInTheMaterializedTableWithId(idString: String) {
        val id = UUID.fromString(idString)
        val customer = customerRepository.findById(id).block()
        assertNull(customer, "Customer with id $id should not exist")
    }

    @Then("the customer should have email {string}")
    fun theCustomerShouldHaveEmail(expectedEmail: String) {
        val customer = customerRepository.findById(currentCustomerId!!).block()
        assertEquals(expectedEmail, customer?.email)
    }

    @Then("the customer should have status {string}")
    fun theCustomerShouldHaveStatus(expectedStatus: String) {
        val customer = customerRepository.findById(currentCustomerId!!).block()
        assertEquals(expectedStatus, customer?.status)
    }

    @Then("the customer count in the materialized table should be {int}")
    fun theCustomerCountInTheMaterializedTableShouldBe(expectedCount: Int) {
        val count = customerRepository.count().block()
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
            customerService.upsert(event).block()
        } catch (e: Throwable) {
            lastError = e
        }
    }
}
