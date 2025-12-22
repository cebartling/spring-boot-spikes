package com.pintailconsultingllc.cdcdebezium.service

import com.pintailconsultingllc.cdcdebezium.TestFixtures.createOrderEvent
import com.pintailconsultingllc.cdcdebezium.TestFixtures.createOrderItemEvent
import com.pintailconsultingllc.cdcdebezium.document.CdcMetadata
import com.pintailconsultingllc.cdcdebezium.document.CdcOperation
import com.pintailconsultingllc.cdcdebezium.document.OrderDocument
import com.pintailconsultingllc.cdcdebezium.document.OrderItemEmbedded
import com.pintailconsultingllc.cdcdebezium.document.OrderStatus
import com.pintailconsultingllc.cdcdebezium.repository.OrderMongoRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class OrderMongoServiceTest {

    private lateinit var orderRepository: OrderMongoRepository
    private lateinit var orderMongoService: OrderMongoService

    @BeforeEach
    fun setUp() {
        orderRepository = mockk()
        orderMongoService = OrderMongoService(orderRepository)
    }

    private fun createDocument(
        id: String = UUID.randomUUID().toString(),
        customerId: String = UUID.randomUUID().toString(),
        status: OrderStatus = OrderStatus.PENDING,
        totalAmount: BigDecimal = BigDecimal("100.00"),
        items: List<OrderItemEmbedded> = emptyList(),
        createdAt: Instant = Instant.now(),
        updatedAt: Instant = Instant.now(),
        sourceTimestamp: Long = 1000L,
        operation: CdcOperation = CdcOperation.INSERT,
        kafkaOffset: Long = 0L,
        kafkaPartition: Int = 0
    ) = OrderDocument(
        id = id,
        customerId = customerId,
        status = status,
        totalAmount = totalAmount,
        items = items,
        createdAt = createdAt,
        updatedAt = updatedAt,
        cdcMetadata = CdcMetadata(
            sourceTimestamp = sourceTimestamp,
            operation = operation,
            kafkaOffset = kafkaOffset,
            kafkaPartition = kafkaPartition
        )
    )

    private fun createItemEmbedded(
        id: String = UUID.randomUUID().toString(),
        productSku: String = "PROD-001",
        productName: String = "Test Product",
        quantity: Int = 1,
        unitPrice: BigDecimal = BigDecimal("10.00"),
        lineTotal: BigDecimal = BigDecimal("10.00"),
        sourceTimestamp: Long = 1000L
    ) = OrderItemEmbedded(
        id = id,
        productSku = productSku,
        productName = productName,
        quantity = quantity,
        unitPrice = unitPrice,
        lineTotal = lineTotal,
        cdcMetadata = CdcMetadata(
            sourceTimestamp = sourceTimestamp,
            operation = CdcOperation.INSERT,
            kafkaOffset = 0L,
            kafkaPartition = 0
        )
    )

    @Nested
    inner class UpsertOrder {

        @Test
        fun `inserts new order when not found`() {
            val event = createOrderEvent(sourceTimestamp = 1000L)
            every { orderRepository.findById(event.id.toString()) } returns Mono.empty()
            every { orderRepository.save(any()) } answers { Mono.just(firstArg()) }

            StepVerifier.create(orderMongoService.upsertOrder(event, 100L, 0))
                .expectNextMatches { it.id == event.id.toString() && it.customerId == event.customerId.toString() }
                .verifyComplete()

            verify(exactly = 1) { orderRepository.findById(event.id.toString()) }
            verify(exactly = 1) { orderRepository.save(any()) }
        }

        @Test
        fun `inserts with INSERT operation when document is new`() {
            val event = createOrderEvent(sourceTimestamp = 1000L)
            every { orderRepository.findById(event.id.toString()) } returns Mono.empty()
            every { orderRepository.save(any()) } answers { Mono.just(firstArg()) }

            StepVerifier.create(orderMongoService.upsertOrder(event, 100L, 0))
                .expectNextMatches { it.cdcMetadata.operation == CdcOperation.INSERT }
                .verifyComplete()
        }

        @Test
        fun `updates existing order when incoming event is newer`() {
            val id = UUID.randomUUID()
            val customerId = UUID.randomUUID()
            val existing = createDocument(id = id.toString(), customerId = customerId.toString(), sourceTimestamp = 1000L)
            val event = createOrderEvent(id = id, customerId = customerId, sourceTimestamp = 2000L)

            every { orderRepository.findById(id.toString()) } returns Mono.just(existing)
            every { orderRepository.save(any()) } answers { Mono.just(firstArg()) }

            StepVerifier.create(orderMongoService.upsertOrder(event, 200L, 1))
                .expectNextMatches { it.cdcMetadata.sourceTimestamp == 2000L }
                .verifyComplete()

            verify(exactly = 1) { orderRepository.save(any()) }
        }

        @Test
        fun `preserves existing items when updating order`() {
            val id = UUID.randomUUID()
            val customerId = UUID.randomUUID()
            val existingItem = createItemEmbedded(id = "item-1", productSku = "SKU-123")
            val existing = createDocument(
                id = id.toString(),
                customerId = customerId.toString(),
                items = listOf(existingItem),
                sourceTimestamp = 1000L
            )
            val event = createOrderEvent(id = id, customerId = customerId, status = "confirmed", sourceTimestamp = 2000L)

            every { orderRepository.findById(id.toString()) } returns Mono.just(existing)
            every { orderRepository.save(any()) } answers { Mono.just(firstArg()) }

            StepVerifier.create(orderMongoService.upsertOrder(event, 200L, 1))
                .expectNextMatches { it.items.size == 1 && it.items[0].productSku == "SKU-123" }
                .verifyComplete()
        }

        @Test
        fun `skips update when incoming event is older`() {
            val id = UUID.randomUUID()
            val customerId = UUID.randomUUID()
            val existing = createDocument(id = id.toString(), customerId = customerId.toString(), sourceTimestamp = 2000L)
            val event = createOrderEvent(id = id, customerId = customerId, sourceTimestamp = 1000L)

            every { orderRepository.findById(id.toString()) } returns Mono.just(existing)

            StepVerifier.create(orderMongoService.upsertOrder(event, 100L, 0))
                .expectNextMatches { it.cdcMetadata.sourceTimestamp == 2000L }
                .verifyComplete()

            verify(exactly = 0) { orderRepository.save(any()) }
        }

        @Test
        fun `maps order status correctly`() {
            val event = createOrderEvent(status = "confirmed", sourceTimestamp = 1000L)
            every { orderRepository.findById(event.id.toString()) } returns Mono.empty()
            every { orderRepository.save(any()) } answers { Mono.just(firstArg()) }

            StepVerifier.create(orderMongoService.upsertOrder(event, 100L, 0))
                .expectNextMatches { it.status == OrderStatus.CONFIRMED }
                .verifyComplete()
        }

        @Test
        fun `captures kafka offset and partition`() {
            val event = createOrderEvent(sourceTimestamp = 1000L)
            every { orderRepository.findById(event.id.toString()) } returns Mono.empty()
            every { orderRepository.save(any()) } answers { Mono.just(firstArg()) }

            StepVerifier.create(orderMongoService.upsertOrder(event, 12345L, 3))
                .expectNextMatches {
                    it.cdcMetadata.kafkaOffset == 12345L && it.cdcMetadata.kafkaPartition == 3
                }
                .verifyComplete()
        }
    }

    @Nested
    inner class DeleteOrder {

        @Test
        fun `deletes existing order when event is newer`() {
            val id = UUID.randomUUID().toString()
            val existing = createDocument(id = id, sourceTimestamp = 1000L)

            every { orderRepository.findById(id) } returns Mono.just(existing)
            every { orderRepository.deleteById(id) } returns Mono.empty()

            StepVerifier.create(orderMongoService.deleteOrder(id, 2000L))
                .verifyComplete()

            verify(exactly = 1) { orderRepository.findById(id) }
            verify(exactly = 1) { orderRepository.deleteById(id) }
        }

        @Test
        fun `succeeds when order does not exist`() {
            val id = UUID.randomUUID().toString()

            every { orderRepository.findById(id) } returns Mono.empty()

            StepVerifier.create(orderMongoService.deleteOrder(id, 1000L))
                .verifyComplete()

            verify(exactly = 1) { orderRepository.findById(id) }
            verify(exactly = 0) { orderRepository.deleteById(any<String>()) }
        }

        @Test
        fun `skips delete when event is stale`() {
            val id = UUID.randomUUID().toString()
            val existing = createDocument(id = id, sourceTimestamp = 2000L)

            every { orderRepository.findById(id) } returns Mono.just(existing)

            StepVerifier.create(orderMongoService.deleteOrder(id, 1000L))
                .verifyComplete()

            verify(exactly = 0) { orderRepository.deleteById(any<String>()) }
        }
    }

    @Nested
    inner class UpsertOrderItem {

        @Test
        fun `adds new item to existing order`() {
            val orderId = UUID.randomUUID()
            val itemId = UUID.randomUUID()
            val order = createDocument(id = orderId.toString(), items = emptyList(), sourceTimestamp = 1000L)
            val event = createOrderItemEvent(id = itemId, orderId = orderId, sourceTimestamp = 2000L)

            every { orderRepository.findById(orderId.toString()) } returns Mono.just(order)
            every { orderRepository.save(any()) } answers { Mono.just(firstArg()) }

            StepVerifier.create(orderMongoService.upsertOrderItem(event, 100L, 0))
                .expectNextMatches { it.items.size == 1 && it.items[0].id == itemId.toString() }
                .verifyComplete()
        }

        @Test
        fun `updates existing item when event is newer`() {
            val orderId = UUID.randomUUID()
            val itemId = UUID.randomUUID()
            val existingItem = createItemEmbedded(id = itemId.toString(), quantity = 1, sourceTimestamp = 1000L)
            val order = createDocument(id = orderId.toString(), items = listOf(existingItem), sourceTimestamp = 500L)
            val event = createOrderItemEvent(id = itemId, orderId = orderId, quantity = 5, sourceTimestamp = 2000L)

            every { orderRepository.findById(orderId.toString()) } returns Mono.just(order)
            every { orderRepository.save(any()) } answers { Mono.just(firstArg()) }

            StepVerifier.create(orderMongoService.upsertOrderItem(event, 100L, 0))
                .expectNextMatches { it.items.size == 1 && it.items[0].quantity == 5 }
                .verifyComplete()
        }

        @Test
        fun `skips update when item event is stale`() {
            val orderId = UUID.randomUUID()
            val itemId = UUID.randomUUID()
            val existingItem = createItemEmbedded(id = itemId.toString(), quantity = 5, sourceTimestamp = 2000L)
            val order = createDocument(id = orderId.toString(), items = listOf(existingItem), sourceTimestamp = 500L)
            val event = createOrderItemEvent(id = itemId, orderId = orderId, quantity = 1, sourceTimestamp = 1000L)

            every { orderRepository.findById(orderId.toString()) } returns Mono.just(order)

            StepVerifier.create(orderMongoService.upsertOrderItem(event, 100L, 0))
                .expectNextMatches { it.items[0].quantity == 5 }
                .verifyComplete()

            verify(exactly = 0) { orderRepository.save(any()) }
        }

        @Test
        fun `returns empty when order does not exist`() {
            val orderId = UUID.randomUUID()
            val itemId = UUID.randomUUID()
            val event = createOrderItemEvent(id = itemId, orderId = orderId, sourceTimestamp = 1000L)

            every { orderRepository.findById(orderId.toString()) } returns Mono.empty()

            StepVerifier.create(orderMongoService.upsertOrderItem(event, 100L, 0))
                .verifyComplete()

            verify(exactly = 0) { orderRepository.save(any()) }
        }

        @Test
        fun `preserves other items when adding new item`() {
            val orderId = UUID.randomUUID()
            val existingItemId = UUID.randomUUID()
            val newItemId = UUID.randomUUID()
            val existingItem = createItemEmbedded(id = existingItemId.toString(), productSku = "SKU-001")
            val order = createDocument(id = orderId.toString(), items = listOf(existingItem), sourceTimestamp = 500L)
            val event = createOrderItemEvent(id = newItemId, orderId = orderId, productSku = "SKU-002", sourceTimestamp = 1000L)

            every { orderRepository.findById(orderId.toString()) } returns Mono.just(order)
            every { orderRepository.save(any()) } answers { Mono.just(firstArg()) }

            StepVerifier.create(orderMongoService.upsertOrderItem(event, 100L, 0))
                .expectNextMatches { doc ->
                    doc.items.size == 2 &&
                        doc.items.any { it.productSku == "SKU-001" } &&
                        doc.items.any { it.productSku == "SKU-002" }
                }
                .verifyComplete()
        }
    }

    @Nested
    inner class DeleteOrderItem {

        @Test
        fun `removes item from order when event is newer`() {
            val orderId = UUID.randomUUID().toString()
            val itemId = UUID.randomUUID().toString()
            val item = createItemEmbedded(id = itemId, sourceTimestamp = 1000L)
            val order = createDocument(id = orderId, items = listOf(item), sourceTimestamp = 500L)

            every { orderRepository.findById(orderId) } returns Mono.just(order)
            every { orderRepository.save(any()) } answers { Mono.just(firstArg()) }

            StepVerifier.create(orderMongoService.deleteOrderItem(orderId, itemId, 2000L))
                .expectNextMatches { it.items.isEmpty() }
                .verifyComplete()
        }

        @Test
        fun `skips delete when item event is stale`() {
            val orderId = UUID.randomUUID().toString()
            val itemId = UUID.randomUUID().toString()
            val item = createItemEmbedded(id = itemId, sourceTimestamp = 2000L)
            val order = createDocument(id = orderId, items = listOf(item), sourceTimestamp = 500L)

            every { orderRepository.findById(orderId) } returns Mono.just(order)

            StepVerifier.create(orderMongoService.deleteOrderItem(orderId, itemId, 1000L))
                .expectNextMatches { it.items.size == 1 }
                .verifyComplete()

            verify(exactly = 0) { orderRepository.save(any()) }
        }

        @Test
        fun `returns empty when order does not exist`() {
            val orderId = UUID.randomUUID().toString()
            val itemId = UUID.randomUUID().toString()

            every { orderRepository.findById(orderId) } returns Mono.empty()

            StepVerifier.create(orderMongoService.deleteOrderItem(orderId, itemId, 1000L))
                .verifyComplete()

            verify(exactly = 0) { orderRepository.save(any()) }
        }

        @Test
        fun `preserves other items when deleting one item`() {
            val orderId = UUID.randomUUID().toString()
            val itemToKeepId = UUID.randomUUID().toString()
            val itemToDeleteId = UUID.randomUUID().toString()
            val itemToKeep = createItemEmbedded(id = itemToKeepId, productSku = "SKU-001", sourceTimestamp = 1000L)
            val itemToDelete = createItemEmbedded(id = itemToDeleteId, productSku = "SKU-002", sourceTimestamp = 1000L)
            val order = createDocument(id = orderId, items = listOf(itemToKeep, itemToDelete), sourceTimestamp = 500L)

            every { orderRepository.findById(orderId) } returns Mono.just(order)
            every { orderRepository.save(any()) } answers { Mono.just(firstArg()) }

            StepVerifier.create(orderMongoService.deleteOrderItem(orderId, itemToDeleteId, 2000L))
                .expectNextMatches { doc ->
                    doc.items.size == 1 && doc.items[0].productSku == "SKU-001"
                }
                .verifyComplete()
        }
    }
}
