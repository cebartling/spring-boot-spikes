package com.pintailconsultingllc.cdcdebezium.steps

import com.pintailconsultingllc.cdcdebezium.document.AddressDocument
import com.pintailconsultingllc.cdcdebezium.document.AddressType
import com.pintailconsultingllc.cdcdebezium.document.CdcMetadata
import com.pintailconsultingllc.cdcdebezium.document.CdcOperation
import com.pintailconsultingllc.cdcdebezium.dto.AddressCdcEvent
import com.pintailconsultingllc.cdcdebezium.repository.AddressMongoRepository
import com.pintailconsultingllc.cdcdebezium.service.AddressMongoService
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

class AddressIdempotentProcessingSteps {

    @Autowired
    private lateinit var addressMongoService: AddressMongoService

    @Autowired
    private lateinit var addressMongoRepository: AddressMongoRepository

    private var currentAddressId: UUID? = null
    private var lastError: Throwable? = null

    @Before
    fun setUp() {
        lastError = null
        currentAddressId = null
    }

    @Given("the address materialized table is empty")
    fun theAddressMaterializedTableIsEmpty() {
        addressMongoRepository.deleteAll().block()
    }

    @Given("an address does not exist with id {string}")
    fun anAddressDoesNotExistWithId(idString: String) {
        val id = UUID.fromString(idString)
        addressMongoRepository.deleteById(id.toString()).block()
        currentAddressId = id
    }

    @Given("an address exists in the materialized table:")
    fun anAddressExistsInTheMaterializedTable(dataTable: DataTable) {
        val row = dataTable.asMaps()[0]
        val id = UUID.fromString(row["id"])
        val document = AddressDocument(
            id = id.toString(),
            customerId = row["customerId"] ?: "",
            type = AddressType.fromString(row["type"]),
            street = row["street"] ?: "",
            city = row["city"] ?: "",
            state = row["state"],
            postalCode = row["postalCode"] ?: "",
            country = row["country"] ?: "USA",
            isDefault = row["isDefault"]?.toBoolean() ?: false,
            updatedAt = Instant.now(),
            cdcMetadata = CdcMetadata(
                sourceTimestamp = row["sourceTimestamp"]?.toLongOrNull() ?: System.currentTimeMillis(),
                operation = CdcOperation.INSERT,
                kafkaOffset = 0L,
                kafkaPartition = 0
            )
        )
        addressMongoRepository.save(document).block()
        currentAddressId = id
    }

    @When("a CDC insert event is processed for address:")
    fun aCdcInsertEventIsProcessedForAddress(dataTable: DataTable) {
        processEventFromDataTable(dataTable, "c")
    }

    @When("a CDC update event is processed for address:")
    fun aCdcUpdateEventIsProcessedForAddress(dataTable: DataTable) {
        processEventFromDataTable(dataTable, "u")
    }

    @When("a CDC delete event is processed for address id {string}")
    fun aCdcDeleteEventIsProcessedForAddressId(idString: String) {
        val id = UUID.fromString(idString)
        currentAddressId = id
        try {
            addressMongoService.delete(
                id = id.toString(),
                sourceTimestamp = System.currentTimeMillis()
            ).block()
        } catch (e: Throwable) {
            lastError = e
        }
    }

    @Then("an address should exist in the materialized table with id {string}")
    fun anAddressShouldExistInTheMaterializedTableWithId(idString: String) {
        val id = UUID.fromString(idString)
        val address = addressMongoRepository.findById(id.toString()).block()
        assertTrue(address != null, "Address with id $id should exist")
        currentAddressId = id
    }

    @Then("an address should not exist in the materialized table with id {string}")
    fun anAddressShouldNotExistInTheMaterializedTableWithId(idString: String) {
        val id = UUID.fromString(idString)
        val address = addressMongoRepository.findById(id.toString()).block()
        assertNull(address, "Address with id $id should not exist")
    }

    @Then("the address should have street {string}")
    fun theAddressShouldHaveStreet(expectedStreet: String) {
        val address = addressMongoRepository.findById(currentAddressId!!.toString()).block()
        assertEquals(expectedStreet, address?.street)
    }

    @Then("the address should have city {string}")
    fun theAddressShouldHaveCity(expectedCity: String) {
        val address = addressMongoRepository.findById(currentAddressId!!.toString()).block()
        assertEquals(expectedCity, address?.city)
    }

    @Then("the address should have type {string}")
    fun theAddressShouldHaveType(expectedType: String) {
        val address = addressMongoRepository.findById(currentAddressId!!.toString()).block()
        assertEquals(AddressType.valueOf(expectedType), address?.type)
    }

    @Then("the address count in the materialized table should be {int}")
    fun theAddressCountInTheMaterializedTableShouldBe(expectedCount: Int) {
        val count = addressMongoRepository.count().block()
        assertEquals(expectedCount.toLong(), count)
    }

    @Then("the address should belong to customer {string}")
    fun theAddressShouldBelongToCustomer(expectedCustomerId: String) {
        val address = addressMongoRepository.findById(currentAddressId!!.toString()).block()
        assertEquals(expectedCustomerId, address?.customerId)
    }

    private fun processEventFromDataTable(dataTable: DataTable, operation: String) {
        val row = dataTable.asMaps()[0]
        val id = UUID.fromString(row["id"])
        currentAddressId = id

        val event = AddressCdcEvent(
            id = id,
            customerId = UUID.fromString(row["customerId"]),
            type = row["type"],
            street = row["street"],
            city = row["city"],
            state = row["state"],
            postalCode = row["postalCode"],
            country = row["country"],
            isDefault = row["isDefault"]?.toBoolean(),
            updatedAt = Instant.now(),
            operation = operation,
            sourceTimestamp = row["sourceTimestamp"]?.toLongOrNull()
        )

        try {
            addressMongoService.upsert(event, 0L, 0).block()
        } catch (e: Throwable) {
            lastError = e
        }
    }
}
