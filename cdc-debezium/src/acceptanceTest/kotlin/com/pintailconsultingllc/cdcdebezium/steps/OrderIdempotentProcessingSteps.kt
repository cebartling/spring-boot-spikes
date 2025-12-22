package com.pintailconsultingllc.cdcdebezium.steps

import com.pintailconsultingllc.cdcdebezium.document.CdcMetadata
import com.pintailconsultingllc.cdcdebezium.document.CdcOperation
import com.pintailconsultingllc.cdcdebezium.document.OrderDocument
import com.pintailconsultingllc.cdcdebezium.document.OrderItemEmbedded
import com.pintailconsultingllc.cdcdebezium.document.OrderStatus
import com.pintailconsultingllc.cdcdebezium.dto.OrderCdcEvent
import com.pintailconsultingllc.cdcdebezium.dto.OrderItemCdcEvent
import com.pintailconsultingllc.cdcdebezium.repository.OrderMongoRepository
import com.pintailconsultingllc.cdcdebezium.service.OrderMongoService
import io.cucumber.datatable.DataTable
import io.cucumber.java.Before
import io.cucumber.java.en.And
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OrderIdempotentProcessingSteps {

    @Autowired
    private lateinit var orderMongoService: OrderMongoService

    @Autowired
    private lateinit var orderMongoRepository: OrderMongoRepository

    private var currentOrderId: UUID? = null
    private var lastError: Throwable? = null

    @Before
    fun setUp() {
        lastError = null
        currentOrderId = null
    }

    @Given("the order materialized table is empty")
    fun theOrderMaterializedTableIsEmpty() {
        orderMongoRepository.deleteAll().block()
    }

    @Given("an order does not exist with id {string}")
    fun anOrderDoesNotExistWithId(idString: String) {
        val id = UUID.fromString(idString)
        orderMongoRepository.deleteById(id.toString()).block()
        currentOrderId = id
    }

    @Given("an order exists in the materialized table:")
    fun anOrderExistsInTheMaterializedTable(dataTable: DataTable) {
        val row = dataTable.asMaps()[0]
        val id = UUID.fromString(row["id"])
        val document = OrderDocument(
            id = id.toString(),
            customerId = row["customerId"] ?: "",
            status = OrderStatus.fromString(row["status"]),
            totalAmount = row["totalAmount"]?.let { BigDecimal(it) } ?: BigDecimal.ZERO,
            items = emptyList(),
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            cdcMetadata = CdcMetadata(
                sourceTimestamp = row["sourceTimestamp"]?.toLongOrNull() ?: System.currentTimeMillis(),
                operation = CdcOperation.INSERT,
                kafkaOffset = 0L,
                kafkaPartition = 0
            )
        )
        orderMongoRepository.save(document).block()
        currentOrderId = id
    }

    @And("an order item exists embedded in the order:")
    fun anOrderItemExistsEmbeddedInTheOrder(dataTable: DataTable) {
        val row = dataTable.asMaps()[0]
        val orderId = row["orderId"] ?: currentOrderId.toString()
        val itemId = row["id"] ?: UUID.randomUUID().toString()

        val itemEmbedded = OrderItemEmbedded(
            id = itemId,
            productSku = row["productSku"] ?: "",
            productName = row["productName"] ?: "",
            quantity = row["quantity"]?.toIntOrNull() ?: 1,
            unitPrice = row["unitPrice"]?.let { BigDecimal(it) } ?: BigDecimal.ZERO,
            lineTotal = row["lineTotal"]?.let { BigDecimal(it) } ?: BigDecimal.ZERO,
            cdcMetadata = CdcMetadata(
                sourceTimestamp = row["sourceTimestamp"]?.toLongOrNull() ?: System.currentTimeMillis(),
                operation = CdcOperation.INSERT,
                kafkaOffset = 0L,
                kafkaPartition = 0
            )
        )

        val order = orderMongoRepository.findById(orderId).block()
        assertNotNull(order, "Order $orderId must exist before adding item")
        orderMongoRepository.save(order.withItem(itemEmbedded)).block()
    }

    @When("a CDC insert event is processed for order:")
    fun aCdcInsertEventIsProcessedForOrder(dataTable: DataTable) {
        processOrderEventFromDataTable(dataTable, "c")
    }

    @When("a CDC update event is processed for order:")
    fun aCdcUpdateEventIsProcessedForOrder(dataTable: DataTable) {
        processOrderEventFromDataTable(dataTable, "u")
    }

    @When("a CDC delete event is processed for order id {string}")
    fun aCdcDeleteEventIsProcessedForOrderId(idString: String) {
        val id = UUID.fromString(idString)
        currentOrderId = id
        try {
            orderMongoService.deleteOrder(
                id = id.toString(),
                sourceTimestamp = System.currentTimeMillis()
            ).block()
        } catch (e: Throwable) {
            lastError = e
        }
    }

    @When("a CDC insert event is processed for order item:")
    fun aCdcInsertEventIsProcessedForOrderItem(dataTable: DataTable) {
        processOrderItemEventFromDataTable(dataTable, "c")
    }

    @And("a CDC insert event is processed for order item:")
    fun andACdcInsertEventIsProcessedForOrderItem(dataTable: DataTable) {
        processOrderItemEventFromDataTable(dataTable, "c")
    }

    @When("a CDC update event is processed for order item:")
    fun aCdcUpdateEventIsProcessedForOrderItem(dataTable: DataTable) {
        processOrderItemEventFromDataTable(dataTable, "u")
    }

    @When("a CDC delete event is processed for order item {string} in order {string}")
    fun aCdcDeleteEventIsProcessedForOrderItemInOrder(itemIdString: String, orderIdString: String) {
        currentOrderId = UUID.fromString(orderIdString)
        try {
            orderMongoService.deleteOrderItem(
                orderId = orderIdString,
                itemId = itemIdString,
                sourceTimestamp = System.currentTimeMillis()
            ).block()
        } catch (e: Throwable) {
            lastError = e
        }
    }

    @Then("an order should exist in the materialized table with id {string}")
    fun anOrderShouldExistInTheMaterializedTableWithId(idString: String) {
        val id = UUID.fromString(idString)
        val order = orderMongoRepository.findById(id.toString()).block()
        assertTrue(order != null, "Order with id $id should exist")
        currentOrderId = id
    }

    @Then("an order should not exist in the materialized table with id {string}")
    fun anOrderShouldNotExistInTheMaterializedTableWithId(idString: String) {
        val id = UUID.fromString(idString)
        val order = orderMongoRepository.findById(id.toString()).block()
        assertNull(order, "Order with id $id should not exist")
    }

    @Then("the order should have status {string}")
    fun theOrderShouldHaveStatus(expectedStatus: String) {
        val order = orderMongoRepository.findById(currentOrderId!!.toString()).block()
        assertEquals(OrderStatus.valueOf(expectedStatus), order?.status)
    }

    @Then("the order should have total amount {string}")
    fun theOrderShouldHaveTotalAmount(expectedAmount: String) {
        val order = orderMongoRepository.findById(currentOrderId!!.toString()).block()
        assertEquals(0, BigDecimal(expectedAmount).compareTo(order?.totalAmount))
    }

    @Then("the order should have {int} items")
    fun theOrderShouldHaveItems(expectedCount: Int) {
        val order = orderMongoRepository.findById(currentOrderId!!.toString()).block()
        assertEquals(expectedCount, order?.items?.size ?: 0)
    }

    @Then("the order should contain item with sku {string}")
    fun theOrderShouldContainItemWithSku(expectedSku: String) {
        val order = orderMongoRepository.findById(currentOrderId!!.toString()).block()
        assertTrue(
            order?.items?.any { it.productSku == expectedSku } == true,
            "Order should contain item with SKU $expectedSku"
        )
    }

    @Then("the order item {string} should have quantity {int}")
    fun theOrderItemShouldHaveQuantity(itemId: String, expectedQuantity: Int) {
        val order = orderMongoRepository.findById(currentOrderId!!.toString()).block()
        val item = order?.items?.find { it.id == itemId }
        assertNotNull(item, "Item $itemId should exist in order")
        assertEquals(expectedQuantity, item.quantity)
    }

    private fun processOrderEventFromDataTable(dataTable: DataTable, operation: String) {
        val row = dataTable.asMaps()[0]
        val id = UUID.fromString(row["id"])
        currentOrderId = id

        val event = OrderCdcEvent(
            id = id,
            customerId = UUID.fromString(row["customerId"]),
            status = row["status"],
            totalAmount = row["totalAmount"]?.let { BigDecimal(it) },
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            operation = operation,
            sourceTimestamp = row["sourceTimestamp"]?.toLongOrNull()
        )

        try {
            orderMongoService.upsertOrder(event, 0L, 0).block()
        } catch (e: Throwable) {
            lastError = e
        }
    }

    private fun processOrderItemEventFromDataTable(dataTable: DataTable, operation: String) {
        val row = dataTable.asMaps()[0]
        val id = UUID.fromString(row["id"])
        val orderId = UUID.fromString(row["orderId"])
        currentOrderId = orderId

        val event = OrderItemCdcEvent(
            id = id,
            orderId = orderId,
            productSku = row["productSku"],
            productName = row["productName"],
            quantity = row["quantity"]?.toIntOrNull(),
            unitPrice = row["unitPrice"]?.let { BigDecimal(it) },
            lineTotal = row["lineTotal"]?.let { BigDecimal(it) },
            operation = operation,
            sourceTimestamp = row["sourceTimestamp"]?.toLongOrNull()
        )

        try {
            orderMongoService.upsertOrderItem(event, 0L, 0).block()
        } catch (e: Throwable) {
            lastError = e
        }
    }
}
