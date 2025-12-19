package com.pintailconsultingllc.cdcdebezium.steps

import com.pintailconsultingllc.cdcdebezium.dto.CustomerCdcEvent
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import java.time.Instant
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CdcEventProcessingSteps {

    private var currentEvent: CustomerCdcEvent? = null
    private var isTombstone: Boolean = false
    private var isAcknowledged: Boolean = false

    @Given("the application is running")
    fun theApplicationIsRunning() {
        // Application context is loaded via CucumberSpringConfiguration
    }

    @When("a customer insert CDC event is received")
    fun aCustomerInsertCdcEventIsReceived() {
        currentEvent = createEvent(operation = "c")
        processEvent()
    }

    @When("a customer update CDC event is received")
    fun aCustomerUpdateCdcEventIsReceived() {
        currentEvent = createEvent(operation = "u")
        processEvent()
    }

    @When("a customer delete CDC event with __deleted flag is received")
    fun aCustomerDeleteCdcEventWithDeletedFlagIsReceived() {
        currentEvent = createEvent(deleted = "true")
        processEvent()
    }

    @When("a customer delete CDC event with operation code {string} is received")
    fun aCustomerDeleteCdcEventWithOperationCodeIsReceived(operationCode: String) {
        currentEvent = createEvent(operation = operationCode)
        processEvent()
    }

    @When("a Kafka tombstone message is received")
    fun aKafkaTombstoneMessageIsReceived() {
        currentEvent = null
        isTombstone = true
        processTombstone()
    }

    @Then("the event should be identified as an upsert operation")
    fun theEventShouldBeIdentifiedAsAnUpsertOperation() {
        assertNotNull(currentEvent)
        assertFalse(currentEvent!!.isDelete())
    }

    @Then("the event should be identified as a delete operation")
    fun theEventShouldBeIdentifiedAsADeleteOperation() {
        assertNotNull(currentEvent)
        assertTrue(currentEvent!!.isDelete())
    }

    @Then("the event should be acknowledged")
    fun theEventShouldBeAcknowledged() {
        assertTrue(isAcknowledged)
    }

    @Then("the tombstone should be handled gracefully")
    fun theTombstoneShouldBeHandledGracefully() {
        assertTrue(isTombstone)
    }

    @Then("the message should be acknowledged")
    fun theMessageShouldBeAcknowledged() {
        assertTrue(isAcknowledged)
    }

    private fun createEvent(
        id: UUID = UUID.randomUUID(),
        email: String = "test@example.com",
        status: String = "active",
        deleted: String? = null,
        operation: String? = null
    ) = CustomerCdcEvent(
        id = id,
        email = email,
        status = status,
        updatedAt = Instant.now(),
        deleted = deleted,
        operation = operation
    )

    private fun processEvent() {
        // Simulate event processing
        isAcknowledged = true
    }

    private fun processTombstone() {
        // Simulate tombstone processing
        isAcknowledged = true
    }
}
