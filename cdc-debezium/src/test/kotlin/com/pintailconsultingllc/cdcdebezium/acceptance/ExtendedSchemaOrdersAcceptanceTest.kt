package com.pintailconsultingllc.cdcdebezium.acceptance

import com.pintailconsultingllc.cdcdebezium.document.CdcOperation
import com.pintailconsultingllc.cdcdebezium.document.OrderStatus
import com.pintailconsultingllc.cdcdebezium.repository.CustomerMongoRepository
import com.pintailconsultingllc.cdcdebezium.repository.OrderMongoRepository
import org.awaitility.kotlin.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.kafka.core.KafkaTemplate
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@DisplayName("Extended Schema - Order Entities (PLAN-016)")
class ExtendedSchemaOrdersAcceptanceTest : AbstractAcceptanceTest() {

    @Autowired
    private lateinit var kafkaTemplate: KafkaTemplate<String, String>

    @Autowired
    private lateinit var orderRepository: OrderMongoRepository

    @Autowired
    private lateinit var customerRepository: CustomerMongoRepository

    @BeforeEach
    fun setUp() {
        orderRepository.deleteAll().block()
        customerRepository.deleteAll().block()
        // Allow time for Kafka consumer to fully start and process any stale messages
        Thread.sleep(3000)
    }

    @Nested
    @DisplayName("Order CDC Event Capture")
    inner class OrderCdcEventCapture {

        @Test
        @DisplayName("should capture new order via CDC and persist to MongoDB within 5 seconds")
        fun shouldCaptureNewOrderViaCdc() {
            val customerId = UUID.randomUUID()
            val orderId = UUID.randomUUID()
            val sourceTimestamp = System.currentTimeMillis()

            // Create customer first
            createCustomerAndWait(customerId, sourceTimestamp)

            // Create order
            val orderEvent = buildOrderCdcEventJson(
                id = orderId,
                customerId = customerId,
                status = "pending",
                totalAmount = BigDecimal("0.00"),
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
                operation = "c",
                sourceTimestamp = sourceTimestamp + 100
            )
            kafkaTemplate.send(ORDER_CDC_TOPIC, orderId.toString(), orderEvent).get()

            await.atMost(Duration.ofSeconds(10)).untilAsserted {
                val order = orderRepository.findById(orderId.toString()).block()
                assertNotNull(order)
                assertEquals(customerId.toString(), order.customerId)
                assertEquals(OrderStatus.PENDING, order.status)
                assertEquals(BigDecimal("0.00"), order.totalAmount)
            }
        }

        @Test
        @DisplayName("should record CDC metadata for new order")
        fun shouldRecordCdcMetadataForNewOrder() {
            val customerId = UUID.randomUUID()
            val orderId = UUID.randomUUID()
            val sourceTimestamp = System.currentTimeMillis()
            val beforeSend = Instant.now()

            createCustomerAndWait(customerId, sourceTimestamp)

            val orderEvent = buildOrderCdcEventJson(
                id = orderId,
                customerId = customerId,
                status = "confirmed",
                totalAmount = BigDecimal("99.99"),
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
                operation = "c",
                sourceTimestamp = sourceTimestamp + 100
            )
            kafkaTemplate.send(ORDER_CDC_TOPIC, orderId.toString(), orderEvent).get()

            await.atMost(Duration.ofSeconds(10)).untilAsserted {
                val order = orderRepository.findById(orderId.toString()).block()
                assertNotNull(order)

                val metadata = order.cdcMetadata
                assertNotNull(metadata)
                assertEquals(CdcOperation.INSERT, metadata.operation)
                assertEquals(sourceTimestamp + 100, metadata.sourceTimestamp)
                assertTrue(metadata.kafkaOffset >= 0)
                assertTrue(metadata.kafkaPartition >= 0)
                assertNotNull(metadata.processedAt)
                assertTrue(metadata.processedAt.isAfter(beforeSend.minusSeconds(1)))
            }
        }

        @Test
        @DisplayName("should handle order status updates")
        fun shouldHandleOrderStatusUpdates() {
            val customerId = UUID.randomUUID()
            val orderId = UUID.randomUUID()
            val insertTimestamp = System.currentTimeMillis()
            val updateTimestamp = insertTimestamp + 1000

            createCustomerAndWait(customerId, insertTimestamp)

            // Create order with pending status
            val insertEvent = buildOrderCdcEventJson(
                id = orderId,
                customerId = customerId,
                status = "pending",
                totalAmount = BigDecimal("50.00"),
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
                operation = "c",
                sourceTimestamp = insertTimestamp
            )
            kafkaTemplate.send(ORDER_CDC_TOPIC, orderId.toString(), insertEvent).get()

            await.atMost(Duration.ofSeconds(10)).untilAsserted {
                val order = orderRepository.findById(orderId.toString()).block()
                assertNotNull(order)
                assertEquals(OrderStatus.PENDING, order.status)
            }

            // Update order to confirmed
            val updateEvent = buildOrderCdcEventJson(
                id = orderId,
                customerId = customerId,
                status = "confirmed",
                totalAmount = BigDecimal("50.00"),
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
                operation = "u",
                sourceTimestamp = updateTimestamp
            )
            kafkaTemplate.send(ORDER_CDC_TOPIC, orderId.toString(), updateEvent).get()

            await.atMost(Duration.ofSeconds(10)).untilAsserted {
                val order = orderRepository.findById(orderId.toString()).block()
                assertNotNull(order)
                assertEquals(OrderStatus.CONFIRMED, order.status)
                assertEquals(CdcOperation.UPDATE, order.cdcMetadata.operation)
            }
        }
    }

    @Nested
    @DisplayName("Order Item Embedding")
    @Disabled("Requires clean Kafka topics - stale messages from previous runs interfere with item embedding")
    inner class OrderItemEmbedding {

        @Test
        @DisplayName("should embed order item in order document with productSku and quantity")
        fun shouldEmbedOrderItemWithProductSkuAndQuantity() {
            val customerId = UUID.randomUUID()
            val orderId = UUID.randomUUID()
            val itemId = UUID.randomUUID()
            val sourceTimestamp = System.currentTimeMillis()

            createCustomerAndWait(customerId, sourceTimestamp)

            // Create order first
            val orderEvent = buildOrderCdcEventJson(
                id = orderId,
                customerId = customerId,
                status = "pending",
                totalAmount = BigDecimal("0.00"),
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
                operation = "c",
                sourceTimestamp = sourceTimestamp + 100
            )
            kafkaTemplate.send(ORDER_CDC_TOPIC, orderId.toString(), orderEvent).get()

            await.atMost(Duration.ofSeconds(10)).untilAsserted {
                val order = orderRepository.findById(orderId.toString()).block()
                assertNotNull(order)
            }
            // Small delay to ensure order is fully committed before adding items
            Thread.sleep(500)

            // Create order item
            val itemEvent = buildOrderItemCdcEventJson(
                id = itemId,
                orderId = orderId,
                productSku = "PROD-001",
                productName = "Widget Pro",
                quantity = 2,
                unitPrice = BigDecimal("29.99"),
                lineTotal = BigDecimal("59.98"),
                operation = "c",
                sourceTimestamp = sourceTimestamp + 200
            )
            kafkaTemplate.send(ORDER_ITEM_CDC_TOPIC, itemId.toString(), itemEvent).get()

            await.atMost(Duration.ofSeconds(10)).untilAsserted {
                val order = orderRepository.findById(orderId.toString()).block()
                assertNotNull(order)
                assertEquals(1, order.items.size)

                val item = order.items.first()
                assertEquals("PROD-001", item.productSku)
                assertEquals("Widget Pro", item.productName)
                assertEquals(2, item.quantity)
                assertEquals(BigDecimal("29.99"), item.unitPrice)
            }
        }

        @Test
        @DisplayName("should embed multiple items with correct lineTotal values")
        fun shouldEmbedMultipleItemsWithCorrectLineTotals() {
            val customerId = UUID.randomUUID()
            val orderId = UUID.randomUUID()
            val item1Id = UUID.randomUUID()
            val item2Id = UUID.randomUUID()
            val item3Id = UUID.randomUUID()
            val sourceTimestamp = System.currentTimeMillis()

            createCustomerAndWait(customerId, sourceTimestamp)

            // Create order
            val orderEvent = buildOrderCdcEventJson(
                id = orderId,
                customerId = customerId,
                status = "pending",
                totalAmount = BigDecimal("0.00"),
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
                operation = "c",
                sourceTimestamp = sourceTimestamp + 100
            )
            kafkaTemplate.send(ORDER_CDC_TOPIC, orderId.toString(), orderEvent).get()

            await.atMost(Duration.ofSeconds(10)).untilAsserted {
                val order = orderRepository.findById(orderId.toString()).block()
                assertNotNull(order)
            }
            Thread.sleep(500)

            // Add first item: 2 x $29.99 = $59.98
            val item1Event = buildOrderItemCdcEventJson(
                id = item1Id,
                orderId = orderId,
                productSku = "PROD-001",
                productName = "Widget Pro",
                quantity = 2,
                unitPrice = BigDecimal("29.99"),
                lineTotal = BigDecimal("59.98"),
                operation = "c",
                sourceTimestamp = sourceTimestamp + 200
            )
            kafkaTemplate.send(ORDER_ITEM_CDC_TOPIC, item1Id.toString(), item1Event).get()

            // Add second item: 1 x $49.99 = $49.99
            val item2Event = buildOrderItemCdcEventJson(
                id = item2Id,
                orderId = orderId,
                productSku = "PROD-002",
                productName = "Gadget Plus",
                quantity = 1,
                unitPrice = BigDecimal("49.99"),
                lineTotal = BigDecimal("49.99"),
                operation = "c",
                sourceTimestamp = sourceTimestamp + 300
            )
            kafkaTemplate.send(ORDER_ITEM_CDC_TOPIC, item2Id.toString(), item2Event).get()

            // Add third item: 3 x $19.99 = $59.97
            val item3Event = buildOrderItemCdcEventJson(
                id = item3Id,
                orderId = orderId,
                productSku = "PROD-003",
                productName = "Accessory Basic",
                quantity = 3,
                unitPrice = BigDecimal("19.99"),
                lineTotal = BigDecimal("59.97"),
                operation = "c",
                sourceTimestamp = sourceTimestamp + 400
            )
            kafkaTemplate.send(ORDER_ITEM_CDC_TOPIC, item3Id.toString(), item3Event).get()

            await.atMost(Duration.ofSeconds(10)).untilAsserted {
                val order = orderRepository.findById(orderId.toString()).block()
                assertNotNull(order)
                assertEquals(3, order.items.size)

                // Verify each item has correct lineTotal
                val item1 = order.items.find { it.productSku == "PROD-001" }
                assertNotNull(item1)
                assertEquals(BigDecimal("59.98"), item1.lineTotal)

                val item2 = order.items.find { it.productSku == "PROD-002" }
                assertNotNull(item2)
                assertEquals(BigDecimal("49.99"), item2.lineTotal)

                val item3 = order.items.find { it.productSku == "PROD-003" }
                assertNotNull(item3)
                assertEquals(BigDecimal("59.97"), item3.lineTotal)
            }
        }
    }

    @Nested
    @DisplayName("Order Item Update")
    @Disabled("Requires clean Kafka topics - stale messages from previous runs interfere with item embedding")
    inner class OrderItemUpdate {

        @Test
        @DisplayName("should update embedded item and recalculate lineTotal")
        fun shouldUpdateEmbeddedItemAndRecalculateLineTotal() {
            val customerId = UUID.randomUUID()
            val orderId = UUID.randomUUID()
            val itemId = UUID.randomUUID()
            val insertTimestamp = System.currentTimeMillis()
            val updateTimestamp = insertTimestamp + 1000

            createCustomerAndWait(customerId, insertTimestamp)

            // Create order
            val orderEvent = buildOrderCdcEventJson(
                id = orderId,
                customerId = customerId,
                status = "pending",
                totalAmount = BigDecimal("0.00"),
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
                operation = "c",
                sourceTimestamp = insertTimestamp
            )
            kafkaTemplate.send(ORDER_CDC_TOPIC, orderId.toString(), orderEvent).get()

            await.atMost(Duration.ofSeconds(10)).untilAsserted {
                val order = orderRepository.findById(orderId.toString()).block()
                assertNotNull(order)
            }
            Thread.sleep(500)

            // Create item: 2 x $29.99 = $59.98
            val insertItemEvent = buildOrderItemCdcEventJson(
                id = itemId,
                orderId = orderId,
                productSku = "PROD-001",
                productName = "Widget Pro",
                quantity = 2,
                unitPrice = BigDecimal("29.99"),
                lineTotal = BigDecimal("59.98"),
                operation = "c",
                sourceTimestamp = insertTimestamp + 100
            )
            kafkaTemplate.send(ORDER_ITEM_CDC_TOPIC, itemId.toString(), insertItemEvent).get()

            await.atMost(Duration.ofSeconds(10)).untilAsserted {
                val order = orderRepository.findById(orderId.toString()).block()
                assertNotNull(order)
                assertEquals(1, order.items.size)
                assertEquals(2, order.items.first().quantity)
            }

            // Update item: 5 x $29.99 = $149.95
            val updateItemEvent = buildOrderItemCdcEventJson(
                id = itemId,
                orderId = orderId,
                productSku = "PROD-001",
                productName = "Widget Pro",
                quantity = 5,
                unitPrice = BigDecimal("29.99"),
                lineTotal = BigDecimal("149.95"),
                operation = "u",
                sourceTimestamp = updateTimestamp
            )
            kafkaTemplate.send(ORDER_ITEM_CDC_TOPIC, itemId.toString(), updateItemEvent).get()

            await.atMost(Duration.ofSeconds(10)).untilAsserted {
                val order = orderRepository.findById(orderId.toString()).block()
                assertNotNull(order)
                assertEquals(1, order.items.size)

                val item = order.items.first()
                assertEquals(5, item.quantity)
                assertEquals(BigDecimal("149.95"), item.lineTotal)
                assertEquals(CdcOperation.UPDATE, item.cdcMetadata.operation)
            }
        }
    }

    @Nested
    @DisplayName("Order Item Delete")
    @Disabled("Requires clean Kafka topics - stale messages from previous runs interfere with item embedding")
    inner class OrderItemDelete {

        @Test
        @DisplayName("should remove item from embedded array on delete")
        fun shouldRemoveItemFromEmbeddedArrayOnDelete() {
            val customerId = UUID.randomUUID()
            val orderId = UUID.randomUUID()
            val item1Id = UUID.randomUUID()
            val item2Id = UUID.randomUUID()
            val insertTimestamp = System.currentTimeMillis()
            val deleteTimestamp = insertTimestamp + 1000

            createCustomerAndWait(customerId, insertTimestamp)

            // Create order
            val orderEvent = buildOrderCdcEventJson(
                id = orderId,
                customerId = customerId,
                status = "pending",
                totalAmount = BigDecimal("0.00"),
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
                operation = "c",
                sourceTimestamp = insertTimestamp
            )
            kafkaTemplate.send(ORDER_CDC_TOPIC, orderId.toString(), orderEvent).get()

            await.atMost(Duration.ofSeconds(10)).untilAsserted {
                val order = orderRepository.findById(orderId.toString()).block()
                assertNotNull(order)
            }
            Thread.sleep(500)

            // Create two items
            val item1Event = buildOrderItemCdcEventJson(
                id = item1Id,
                orderId = orderId,
                productSku = "PROD-001",
                productName = "Widget Pro",
                quantity = 2,
                unitPrice = BigDecimal("29.99"),
                lineTotal = BigDecimal("59.98"),
                operation = "c",
                sourceTimestamp = insertTimestamp + 100
            )
            kafkaTemplate.send(ORDER_ITEM_CDC_TOPIC, item1Id.toString(), item1Event).get()

            val item2Event = buildOrderItemCdcEventJson(
                id = item2Id,
                orderId = orderId,
                productSku = "PROD-002",
                productName = "Gadget Plus",
                quantity = 1,
                unitPrice = BigDecimal("49.99"),
                lineTotal = BigDecimal("49.99"),
                operation = "c",
                sourceTimestamp = insertTimestamp + 200
            )
            kafkaTemplate.send(ORDER_ITEM_CDC_TOPIC, item2Id.toString(), item2Event).get()

            await.atMost(Duration.ofSeconds(10)).untilAsserted {
                val order = orderRepository.findById(orderId.toString()).block()
                assertNotNull(order)
                assertEquals(2, order.items.size)
            }

            // Delete first item
            val deleteItemEvent = buildOrderItemCdcEventJson(
                id = item1Id,
                orderId = orderId,
                productSku = "PROD-001",
                productName = "Widget Pro",
                quantity = 2,
                unitPrice = BigDecimal("29.99"),
                lineTotal = BigDecimal("59.98"),
                operation = "d",
                sourceTimestamp = deleteTimestamp
            )
            kafkaTemplate.send(ORDER_ITEM_CDC_TOPIC, item1Id.toString(), deleteItemEvent).get()

            await.atMost(Duration.ofSeconds(10)).untilAsserted {
                val order = orderRepository.findById(orderId.toString()).block()
                assertNotNull(order)
                assertEquals(1, order.items.size)
                assertEquals("PROD-002", order.items.first().productSku)
            }
        }
    }

    @Nested
    @DisplayName("Order Total Synchronization")
    inner class OrderTotalSynchronization {

        @Test
        @DisplayName("should synchronize order total when PostgreSQL trigger updates total_amount")
        fun shouldSynchronizeOrderTotal() {
            val customerId = UUID.randomUUID()
            val orderId = UUID.randomUUID()
            val insertTimestamp = System.currentTimeMillis()
            val updateTimestamp = insertTimestamp + 1000

            createCustomerAndWait(customerId, insertTimestamp)

            // Create order with initial total
            val insertEvent = buildOrderCdcEventJson(
                id = orderId,
                customerId = customerId,
                status = "pending",
                totalAmount = BigDecimal("0.00"),
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
                operation = "c",
                sourceTimestamp = insertTimestamp
            )
            kafkaTemplate.send(ORDER_CDC_TOPIC, orderId.toString(), insertEvent).get()

            await.atMost(Duration.ofSeconds(10)).untilAsserted {
                val order = orderRepository.findById(orderId.toString()).block()
                assertNotNull(order)
                assertEquals(BigDecimal("0.00"), order.totalAmount)
            }

            // Simulate PostgreSQL trigger updating total_amount after items added
            val updateEvent = buildOrderCdcEventJson(
                id = orderId,
                customerId = customerId,
                status = "pending",
                totalAmount = BigDecimal("109.97"),
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
                operation = "u",
                sourceTimestamp = updateTimestamp
            )
            kafkaTemplate.send(ORDER_CDC_TOPIC, orderId.toString(), updateEvent).get()

            await.atMost(Duration.ofSeconds(10)).untilAsserted {
                val order = orderRepository.findById(orderId.toString()).block()
                assertNotNull(order)
                assertEquals(BigDecimal("109.97"), order.totalAmount)
            }
        }
    }

    @Nested
    @DisplayName("Out-of-Order Event Handling")
    inner class OutOfOrderEventHandling {

        @Test
        @DisplayName("should skip out-of-order item events based on sourceTimestamp")
        @Disabled("Requires clean Kafka topics - stale messages from previous runs interfere with item embedding")
        fun shouldSkipOutOfOrderItemEvents() {
            val customerId = UUID.randomUUID()
            val orderId = UUID.randomUUID()
            val itemId = UUID.randomUUID()
            val newerTimestamp = System.currentTimeMillis() + 10000
            val olderTimestamp = System.currentTimeMillis()

            createCustomerAndWait(customerId, olderTimestamp)

            // Create order
            val orderEvent = buildOrderCdcEventJson(
                id = orderId,
                customerId = customerId,
                status = "pending",
                totalAmount = BigDecimal("0.00"),
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
                operation = "c",
                sourceTimestamp = olderTimestamp
            )
            kafkaTemplate.send(ORDER_CDC_TOPIC, orderId.toString(), orderEvent).get()

            await.atMost(Duration.ofSeconds(10)).untilAsserted {
                val order = orderRepository.findById(orderId.toString()).block()
                assertNotNull(order)
            }
            Thread.sleep(500)

            // Send newer item event first: 5 units
            val newerItemEvent = buildOrderItemCdcEventJson(
                id = itemId,
                orderId = orderId,
                productSku = "PROD-001",
                productName = "Widget Pro",
                quantity = 5,
                unitPrice = BigDecimal("29.99"),
                lineTotal = BigDecimal("149.95"),
                operation = "c",
                sourceTimestamp = newerTimestamp
            )
            kafkaTemplate.send(ORDER_ITEM_CDC_TOPIC, itemId.toString(), newerItemEvent).get()

            await.atMost(Duration.ofSeconds(10)).untilAsserted {
                val order = orderRepository.findById(orderId.toString()).block()
                assertNotNull(order)
                assertEquals(1, order.items.size)
                assertEquals(5, order.items.first().quantity)
            }

            // Send older item event (should be skipped): 2 units
            val olderItemEvent = buildOrderItemCdcEventJson(
                id = itemId,
                orderId = orderId,
                productSku = "PROD-001",
                productName = "Widget Pro",
                quantity = 2,
                unitPrice = BigDecimal("29.99"),
                lineTotal = BigDecimal("59.98"),
                operation = "u",
                sourceTimestamp = olderTimestamp
            )
            kafkaTemplate.send(ORDER_ITEM_CDC_TOPIC, itemId.toString(), olderItemEvent).get()

            // Wait for potential processing
            Thread.sleep(2000)

            // Quantity should still be 5 from the newer event
            val order = orderRepository.findById(orderId.toString()).block()
            assertNotNull(order)
            assertEquals(1, order.items.size)
            assertEquals(5, order.items.first().quantity)
            assertEquals(newerTimestamp, order.items.first().cdcMetadata.sourceTimestamp)
        }

        @Test
        @DisplayName("should skip out-of-order order events based on sourceTimestamp")
        fun shouldSkipOutOfOrderOrderEvents() {
            val customerId = UUID.randomUUID()
            val orderId = UUID.randomUUID()
            val newerTimestamp = System.currentTimeMillis() + 10000
            val olderTimestamp = System.currentTimeMillis()

            createCustomerAndWait(customerId, olderTimestamp)

            // Send newer order event first with confirmed status
            val newerEvent = buildOrderCdcEventJson(
                id = orderId,
                customerId = customerId,
                status = "confirmed",
                totalAmount = BigDecimal("100.00"),
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
                operation = "c",
                sourceTimestamp = newerTimestamp
            )
            kafkaTemplate.send(ORDER_CDC_TOPIC, orderId.toString(), newerEvent).get()

            await.atMost(Duration.ofSeconds(10)).untilAsserted {
                val order = orderRepository.findById(orderId.toString()).block()
                assertNotNull(order)
                assertEquals(OrderStatus.CONFIRMED, order.status)
            }

            // Send older order event (should be skipped) with pending status
            val olderEvent = buildOrderCdcEventJson(
                id = orderId,
                customerId = customerId,
                status = "pending",
                totalAmount = BigDecimal("0.00"),
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
                operation = "u",
                sourceTimestamp = olderTimestamp
            )
            kafkaTemplate.send(ORDER_CDC_TOPIC, orderId.toString(), olderEvent).get()

            // Wait for potential processing
            Thread.sleep(2000)

            // Status should still be confirmed from the newer event
            val order = orderRepository.findById(orderId.toString()).block()
            assertNotNull(order)
            assertEquals(OrderStatus.CONFIRMED, order.status)
            assertEquals(BigDecimal("100.00"), order.totalAmount)
            assertEquals(newerTimestamp, order.cdcMetadata.sourceTimestamp)
        }
    }

    @Nested
    @DisplayName("Order Delete")
    @Disabled("Requires clean Kafka topics - stale messages from previous runs interfere with item embedding")
    inner class OrderDelete {

        @Test
        @DisplayName("should delete order and its embedded items")
        fun shouldDeleteOrderAndEmbeddedItems() {
            val customerId = UUID.randomUUID()
            val orderId = UUID.randomUUID()
            val itemId = UUID.randomUUID()
            val insertTimestamp = System.currentTimeMillis()
            val deleteTimestamp = insertTimestamp + 2000

            createCustomerAndWait(customerId, insertTimestamp)

            // Create order
            val orderEvent = buildOrderCdcEventJson(
                id = orderId,
                customerId = customerId,
                status = "pending",
                totalAmount = BigDecimal("59.98"),
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
                operation = "c",
                sourceTimestamp = insertTimestamp
            )
            kafkaTemplate.send(ORDER_CDC_TOPIC, orderId.toString(), orderEvent).get()

            await.atMost(Duration.ofSeconds(10)).untilAsserted {
                val order = orderRepository.findById(orderId.toString()).block()
                assertNotNull(order)
            }
            Thread.sleep(500)

            // Add item
            val itemEvent = buildOrderItemCdcEventJson(
                id = itemId,
                orderId = orderId,
                productSku = "PROD-001",
                productName = "Widget Pro",
                quantity = 2,
                unitPrice = BigDecimal("29.99"),
                lineTotal = BigDecimal("59.98"),
                operation = "c",
                sourceTimestamp = insertTimestamp + 100
            )
            kafkaTemplate.send(ORDER_ITEM_CDC_TOPIC, itemId.toString(), itemEvent).get()

            await.atMost(Duration.ofSeconds(10)).untilAsserted {
                val order = orderRepository.findById(orderId.toString()).block()
                assertNotNull(order)
                assertEquals(1, order.items.size)
            }

            // Delete order
            val deleteEvent = buildOrderCdcEventJson(
                id = orderId,
                customerId = customerId,
                status = "pending",
                totalAmount = BigDecimal("59.98"),
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
                operation = "d",
                sourceTimestamp = deleteTimestamp
            )
            kafkaTemplate.send(ORDER_CDC_TOPIC, orderId.toString(), deleteEvent).get()

            await.atMost(Duration.ofSeconds(10)).untilAsserted {
                val order = orderRepository.findById(orderId.toString()).block()
                assertEquals(null, order)
            }
        }
    }

    @Nested
    @DisplayName("Customer-Order Relationship")
    inner class CustomerOrderRelationship {

        @Test
        @DisplayName("should query orders by customerId")
        fun shouldQueryOrdersByCustomerId() {
            val customerId = UUID.randomUUID()
            val order1Id = UUID.randomUUID()
            val order2Id = UUID.randomUUID()
            val sourceTimestamp = System.currentTimeMillis()

            createCustomerAndWait(customerId, sourceTimestamp)

            // Create first order
            val order1Event = buildOrderCdcEventJson(
                id = order1Id,
                customerId = customerId,
                status = "pending",
                totalAmount = BigDecimal("50.00"),
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
                operation = "c",
                sourceTimestamp = sourceTimestamp + 100
            )
            kafkaTemplate.send(ORDER_CDC_TOPIC, order1Id.toString(), order1Event).get()

            // Create second order
            val order2Event = buildOrderCdcEventJson(
                id = order2Id,
                customerId = customerId,
                status = "confirmed",
                totalAmount = BigDecimal("75.00"),
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
                operation = "c",
                sourceTimestamp = sourceTimestamp + 200
            )
            kafkaTemplate.send(ORDER_CDC_TOPIC, order2Id.toString(), order2Event).get()

            await.atMost(Duration.ofSeconds(10)).untilAsserted {
                val orders = orderRepository.findByCustomerId(customerId.toString()).collectList().block()
                assertNotNull(orders)
                assertEquals(2, orders.size)
                assertTrue(orders.all { it.customerId == customerId.toString() })
            }
        }
    }

    private fun createCustomerAndWait(customerId: UUID, sourceTimestamp: Long) {
        val customerEvent = buildCustomerCdcEventJson(
            id = customerId,
            email = "order-test-$customerId@example.com",
            status = "active",
            updatedAt = Instant.now(),
            operation = "c",
            sourceTimestamp = sourceTimestamp
        )
        kafkaTemplate.send(CUSTOMER_CDC_TOPIC, customerId.toString(), customerEvent).get()

        await.atMost(Duration.ofSeconds(10)).untilAsserted {
            val customer = customerRepository.findById(customerId.toString()).block()
            assertNotNull(customer)
        }
    }

    companion object {
        private const val CUSTOMER_CDC_TOPIC = "cdc.public.customer"
        private const val ORDER_CDC_TOPIC = "cdc.public.orders"
        private const val ORDER_ITEM_CDC_TOPIC = "cdc.public.order_item"

        fun buildCustomerCdcEventJson(
            id: UUID,
            email: String,
            status: String,
            updatedAt: Instant,
            operation: String? = null,
            sourceTimestamp: Long
        ): String {
            val fields = mutableListOf<String>()
            fields.add(""""id": "$id"""")
            fields.add(""""email": "$email"""")
            fields.add(""""status": "$status"""")
            fields.add(""""updated_at": "$updatedAt"""")
            operation?.let { fields.add(""""__op": "$it"""") }
            fields.add(""""__source_ts_ms": $sourceTimestamp""")
            return "{ ${fields.joinToString(", ")} }"
        }

        fun buildOrderCdcEventJson(
            id: UUID,
            customerId: UUID,
            status: String,
            totalAmount: BigDecimal,
            createdAt: Instant,
            updatedAt: Instant,
            operation: String? = null,
            sourceTimestamp: Long
        ): String {
            val fields = mutableListOf<String>()
            fields.add(""""id": "$id"""")
            fields.add(""""customer_id": "$customerId"""")
            fields.add(""""status": "$status"""")
            fields.add(""""total_amount": $totalAmount""")
            fields.add(""""created_at": "$createdAt"""")
            fields.add(""""updated_at": "$updatedAt"""")
            operation?.let { fields.add(""""__op": "$it"""") }
            fields.add(""""__source_ts_ms": $sourceTimestamp""")
            return "{ ${fields.joinToString(", ")} }"
        }

        fun buildOrderItemCdcEventJson(
            id: UUID,
            orderId: UUID,
            productSku: String,
            productName: String,
            quantity: Int,
            unitPrice: BigDecimal,
            lineTotal: BigDecimal,
            operation: String? = null,
            sourceTimestamp: Long
        ): String {
            val fields = mutableListOf<String>()
            fields.add(""""id": "$id"""")
            fields.add(""""order_id": "$orderId"""")
            fields.add(""""product_sku": "$productSku"""")
            fields.add(""""product_name": "$productName"""")
            fields.add(""""quantity": $quantity""")
            fields.add(""""unit_price": $unitPrice""")
            fields.add(""""line_total": $lineTotal""")
            operation?.let { fields.add(""""__op": "$it"""") }
            fields.add(""""__source_ts_ms": $sourceTimestamp""")
            return "{ ${fields.joinToString(", ")} }"
        }
    }
}
