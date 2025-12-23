package com.pintailconsultingllc.cdcdebezium.acceptance

import com.pintailconsultingllc.cdcdebezium.consumer.CdcEventRouter
import com.pintailconsultingllc.cdcdebezium.repository.AddressMongoRepository
import com.pintailconsultingllc.cdcdebezium.repository.CustomerMongoRepository
import com.pintailconsultingllc.cdcdebezium.repository.OrderMongoRepository
import org.awaitility.kotlin.await
import org.junit.jupiter.api.BeforeEach
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

@DisplayName("Multi-Table CDC Consumer Architecture")
class MultiTableCdcConsumerAcceptanceTest : AbstractAcceptanceTest() {

    @Autowired
    private lateinit var kafkaTemplate: KafkaTemplate<String, String>

    @Autowired
    private lateinit var customerRepository: CustomerMongoRepository

    @Autowired
    private lateinit var addressRepository: AddressMongoRepository

    @Autowired
    private lateinit var orderRepository: OrderMongoRepository

    @Autowired
    private lateinit var cdcEventRouter: CdcEventRouter

    @BeforeEach
    fun setUp() {
        customerRepository.deleteAll().block()
        addressRepository.deleteAll().block()
        orderRepository.deleteAll().block()
    }

    @Nested
    @DisplayName("Router Discovery")
    inner class RouterDiscovery {

        @Test
        @DisplayName("should discover all 4 handlers on startup")
        fun shouldDiscoverAllHandlers() {
            assertEquals(4, cdcEventRouter.getHandlerCount())
        }

        @Test
        @DisplayName("should have all registered topics")
        fun shouldHaveAllRegisteredTopics() {
            val topics = cdcEventRouter.getRegisteredTopics()
            assertTrue(topics.contains("cdc.public.customer"))
            assertTrue(topics.contains("cdc.public.address"))
            assertTrue(topics.contains("cdc.public.orders"))
            assertTrue(topics.contains("cdc.public.order_item"))
        }
    }

    @Nested
    @DisplayName("Event Routing")
    inner class EventRouting {

        @Test
        @DisplayName("should route customer event to CustomerEventHandler")
        fun shouldRouteCustomerEvent() {
            val customerId = UUID.randomUUID()
            val email = "routing-test-${customerId}@example.com"

            val cdcEvent = buildCustomerCdcEventJson(
                id = customerId,
                email = email,
                status = "active",
                updatedAt = Instant.now(),
                operation = "c",
                sourceTimestamp = System.currentTimeMillis()
            )

            kafkaTemplate.send(CUSTOMER_CDC_TOPIC, customerId.toString(), cdcEvent).get()

            await.atMost(Duration.ofSeconds(10)).untilAsserted {
                val customer = customerRepository.findById(customerId.toString()).block()
                assertNotNull(customer)
                assertEquals(email, customer.email)
            }
        }

        @Test
        @DisplayName("should route address event to AddressEventHandler")
        fun shouldRouteAddressEvent() {
            val addressId = UUID.randomUUID()
            val customerId = UUID.randomUUID()

            val cdcEvent = buildAddressCdcEventJson(
                id = addressId,
                customerId = customerId,
                type = "billing",
                street = "123 Test St",
                city = "Test City",
                state = "TS",
                postalCode = "12345",
                country = "US",
                operation = "c",
                sourceTimestamp = System.currentTimeMillis()
            )

            kafkaTemplate.send(ADDRESS_CDC_TOPIC, addressId.toString(), cdcEvent).get()

            await.atMost(Duration.ofSeconds(10)).untilAsserted {
                val address = addressRepository.findById(addressId.toString()).block()
                assertNotNull(address)
                assertEquals("123 Test St", address.street)
                assertEquals("Test City", address.city)
            }
        }

        @Test
        @DisplayName("should route order event to OrderEventHandler")
        fun shouldRouteOrderEvent() {
            val orderId = UUID.randomUUID()
            val customerId = UUID.randomUUID()

            val cdcEvent = buildOrderCdcEventJson(
                id = orderId,
                customerId = customerId,
                status = "pending",
                totalAmount = BigDecimal("99.99"),
                operation = "c",
                sourceTimestamp = System.currentTimeMillis()
            )

            kafkaTemplate.send(ORDERS_CDC_TOPIC, orderId.toString(), cdcEvent).get()

            await.atMost(Duration.ofSeconds(10)).untilAsserted {
                val order = orderRepository.findById(orderId.toString()).block()
                assertNotNull(order)
                assertEquals("pending", order.status.name.lowercase())
            }
        }
    }

    @Nested
    @DisplayName("Cross-Entity Processing")
    inner class CrossEntityProcessing {

        @Test
        @DisplayName("should process events across multiple entity types concurrently")
        fun shouldProcessMultipleEntityTypesConcurrently() {
            val customerId = UUID.randomUUID()
            val addressId = UUID.randomUUID()
            val orderId = UUID.randomUUID()

            val customerEvent = buildCustomerCdcEventJson(
                id = customerId,
                email = "multi-entity-${customerId}@example.com",
                status = "active",
                updatedAt = Instant.now(),
                operation = "c",
                sourceTimestamp = System.currentTimeMillis()
            )

            val addressEvent = buildAddressCdcEventJson(
                id = addressId,
                customerId = customerId,
                type = "shipping",
                street = "456 Multi St",
                city = "Multi City",
                state = "MC",
                postalCode = "67890",
                country = "US",
                operation = "c",
                sourceTimestamp = System.currentTimeMillis()
            )

            val orderEvent = buildOrderCdcEventJson(
                id = orderId,
                customerId = customerId,
                status = "confirmed",
                totalAmount = BigDecimal("250.00"),
                operation = "c",
                sourceTimestamp = System.currentTimeMillis()
            )

            kafkaTemplate.send(CUSTOMER_CDC_TOPIC, customerId.toString(), customerEvent).get()
            kafkaTemplate.send(ADDRESS_CDC_TOPIC, addressId.toString(), addressEvent).get()
            kafkaTemplate.send(ORDERS_CDC_TOPIC, orderId.toString(), orderEvent).get()

            await.atMost(Duration.ofSeconds(15)).untilAsserted {
                val customer = customerRepository.findById(customerId.toString()).block()
                val address = addressRepository.findById(addressId.toString()).block()
                val order = orderRepository.findById(orderId.toString()).block()

                assertNotNull(customer)
                assertNotNull(address)
                assertNotNull(order)

                assertEquals("multi-entity-${customerId}@example.com", customer.email)
                assertEquals("456 Multi St", address.street)
                assertEquals("confirmed", order.status.name.lowercase())
            }
        }
    }

    @Nested
    @DisplayName("Handler Isolation")
    inner class HandlerIsolation {

        @Test
        @DisplayName("should continue processing other entity types when one fails validation")
        fun shouldIsolateHandlerFailures() {
            val validCustomerId = UUID.randomUUID()
            val invalidCustomerId = UUID.randomUUID()
            val addressId = UUID.randomUUID()

            val validCustomerEvent = buildCustomerCdcEventJson(
                id = validCustomerId,
                email = "valid-${validCustomerId}@example.com",
                status = "active",
                updatedAt = Instant.now(),
                operation = "c",
                sourceTimestamp = System.currentTimeMillis()
            )

            val invalidCustomerEvent = buildCustomerCdcEventJson(
                id = invalidCustomerId,
                email = "invalid-email",
                status = "active",
                updatedAt = Instant.now(),
                operation = "c",
                sourceTimestamp = System.currentTimeMillis()
            )

            val addressEvent = buildAddressCdcEventJson(
                id = addressId,
                customerId = validCustomerId,
                type = "home",
                street = "789 Isolation St",
                city = "Isolation City",
                state = "IC",
                postalCode = "11111",
                country = "US",
                operation = "c",
                sourceTimestamp = System.currentTimeMillis()
            )

            kafkaTemplate.send(CUSTOMER_CDC_TOPIC, invalidCustomerId.toString(), invalidCustomerEvent).get()
            kafkaTemplate.send(CUSTOMER_CDC_TOPIC, validCustomerId.toString(), validCustomerEvent).get()
            kafkaTemplate.send(ADDRESS_CDC_TOPIC, addressId.toString(), addressEvent).get()

            await.atMost(Duration.ofSeconds(10)).untilAsserted {
                val validCustomer = customerRepository.findById(validCustomerId.toString()).block()
                val address = addressRepository.findById(addressId.toString()).block()

                assertNotNull(validCustomer)
                assertNotNull(address)
            }
        }
    }

    companion object {
        private const val CUSTOMER_CDC_TOPIC = "cdc.public.customer"
        private const val ADDRESS_CDC_TOPIC = "cdc.public.address"
        private const val ORDERS_CDC_TOPIC = "cdc.public.orders"
        private const val ORDER_ITEM_CDC_TOPIC = "cdc.public.order_item"

        fun buildCustomerCdcEventJson(
            id: UUID,
            email: String,
            status: String,
            updatedAt: Instant,
            operation: String? = null,
            deleted: String? = null,
            sourceTimestamp: Long
        ): String {
            val fields = mutableListOf<String>()
            fields.add(""""id": "$id"""")
            fields.add(""""email": "$email"""")
            fields.add(""""status": "$status"""")
            fields.add(""""updated_at": "$updatedAt"""")
            operation?.let { fields.add(""""__op": "$it"""") }
            deleted?.let { fields.add(""""__deleted": "$it"""") }
            fields.add(""""__source_ts_ms": $sourceTimestamp""")
            return "{ ${fields.joinToString(", ")} }"
        }

        fun buildAddressCdcEventJson(
            id: UUID,
            customerId: UUID,
            type: String,
            street: String,
            city: String,
            state: String,
            postalCode: String,
            country: String,
            operation: String? = null,
            deleted: String? = null,
            sourceTimestamp: Long
        ): String {
            val fields = mutableListOf<String>()
            fields.add(""""id": "$id"""")
            fields.add(""""customer_id": "$customerId"""")
            fields.add(""""type": "$type"""")
            fields.add(""""street": "$street"""")
            fields.add(""""city": "$city"""")
            fields.add(""""state": "$state"""")
            fields.add(""""postal_code": "$postalCode"""")
            fields.add(""""country": "$country"""")
            fields.add(""""is_default": false""")
            fields.add(""""updated_at": "${Instant.now()}"""")
            operation?.let { fields.add(""""__op": "$it"""") }
            deleted?.let { fields.add(""""__deleted": "$it"""") }
            fields.add(""""__source_ts_ms": $sourceTimestamp""")
            return "{ ${fields.joinToString(", ")} }"
        }

        fun buildOrderCdcEventJson(
            id: UUID,
            customerId: UUID,
            status: String,
            totalAmount: BigDecimal,
            operation: String? = null,
            deleted: String? = null,
            sourceTimestamp: Long
        ): String {
            val fields = mutableListOf<String>()
            fields.add(""""id": "$id"""")
            fields.add(""""customer_id": "$customerId"""")
            fields.add(""""status": "$status"""")
            fields.add(""""total_amount": $totalAmount""")
            fields.add(""""created_at": "${Instant.now()}"""")
            fields.add(""""updated_at": "${Instant.now()}"""")
            operation?.let { fields.add(""""__op": "$it"""") }
            deleted?.let { fields.add(""""__deleted": "$it"""") }
            fields.add(""""__source_ts_ms": $sourceTimestamp""")
            return "{ ${fields.joinToString(", ")} }"
        }
    }
}
