package com.pintailconsultingllc.cdcdebezium.acceptance

import com.pintailconsultingllc.cdcdebezium.document.AddressType
import com.pintailconsultingllc.cdcdebezium.document.CdcOperation
import com.pintailconsultingllc.cdcdebezium.repository.AddressMongoRepository
import com.pintailconsultingllc.cdcdebezium.repository.CustomerMongoRepository
import org.awaitility.kotlin.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.kafka.core.KafkaTemplate
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@DisplayName("Extended Schema - Address Entity (PLAN-015)")
class ExtendedSchemaAddressAcceptanceTest : AbstractAcceptanceTest() {

    @Autowired
    private lateinit var kafkaTemplate: KafkaTemplate<String, String>

    @Autowired
    private lateinit var addressRepository: AddressMongoRepository

    @Autowired
    private lateinit var customerRepository: CustomerMongoRepository

    @BeforeEach
    fun setUp() {
        addressRepository.deleteAll().block()
        customerRepository.deleteAll().block()
    }

    @Nested
    @DisplayName("Address CDC Event Capture")
    inner class AddressCdcEventCapture {

        @Test
        @DisplayName("should capture new address via CDC and persist to MongoDB within 5 seconds")
        fun shouldCaptureNewAddressViaCdc() {
            val customerId = UUID.randomUUID()
            val addressId = UUID.randomUUID()
            val sourceTimestamp = System.currentTimeMillis()

            // First create the customer
            val customerEvent = buildCustomerCdcEventJson(
                id = customerId,
                email = "address-test-$customerId@example.com",
                status = "active",
                updatedAt = Instant.now(),
                operation = "c",
                sourceTimestamp = sourceTimestamp
            )
            kafkaTemplate.send(CUSTOMER_CDC_TOPIC, customerId.toString(), customerEvent).get()

            await.atMost(Duration.ofSeconds(5)).untilAsserted {
                val customer = customerRepository.findById(customerId.toString()).block()
                assertNotNull(customer)
            }

            // Then create the address
            val addressEvent = buildAddressCdcEventJson(
                id = addressId,
                customerId = customerId,
                type = "shipping",
                street = "123 Main Street",
                city = "Minneapolis",
                state = "MN",
                postalCode = "55401",
                country = "USA",
                isDefault = true,
                updatedAt = Instant.now(),
                operation = "c",
                sourceTimestamp = sourceTimestamp + 100
            )
            kafkaTemplate.send(ADDRESS_CDC_TOPIC, addressId.toString(), addressEvent).get()

            await.atMost(Duration.ofSeconds(5)).untilAsserted {
                val address = addressRepository.findById(addressId.toString()).block()
                assertNotNull(address)
                assertEquals("123 Main Street", address.street)
                assertEquals("Minneapolis", address.city)
                assertEquals("MN", address.state)
                assertEquals("55401", address.postalCode)
                assertEquals("USA", address.country)
                assertEquals(true, address.isDefault)
                assertEquals(AddressType.SHIPPING, address.type)
            }
        }

        @Test
        @DisplayName("should record CDC metadata for new address")
        fun shouldRecordCdcMetadataForNewAddress() {
            val customerId = UUID.randomUUID()
            val addressId = UUID.randomUUID()
            val sourceTimestamp = System.currentTimeMillis()
            val beforeSend = Instant.now()

            // Create customer first
            val customerEvent = buildCustomerCdcEventJson(
                id = customerId,
                email = "metadata-test-$customerId@example.com",
                status = "active",
                updatedAt = Instant.now(),
                operation = "c",
                sourceTimestamp = sourceTimestamp
            )
            kafkaTemplate.send(CUSTOMER_CDC_TOPIC, customerId.toString(), customerEvent).get()

            await.atMost(Duration.ofSeconds(5)).untilAsserted {
                val customer = customerRepository.findById(customerId.toString()).block()
                assertNotNull(customer)
            }

            // Create address
            val addressEvent = buildAddressCdcEventJson(
                id = addressId,
                customerId = customerId,
                type = "billing",
                street = "456 Commerce Ave",
                city = "St. Paul",
                state = "MN",
                postalCode = "55102",
                country = "USA",
                isDefault = false,
                updatedAt = Instant.now(),
                operation = "c",
                sourceTimestamp = sourceTimestamp + 100
            )
            kafkaTemplate.send(ADDRESS_CDC_TOPIC, addressId.toString(), addressEvent).get()

            await.atMost(Duration.ofSeconds(5)).untilAsserted {
                val address = addressRepository.findById(addressId.toString()).block()
                assertNotNull(address)

                val metadata = address.cdcMetadata
                assertNotNull(metadata)
                assertEquals(CdcOperation.INSERT, metadata.operation)
                assertEquals(sourceTimestamp + 100, metadata.sourceTimestamp)
                assertTrue(metadata.kafkaOffset >= 0)
                assertTrue(metadata.kafkaPartition >= 0)
                assertNotNull(metadata.processedAt)
                assertTrue(metadata.processedAt.isAfter(beforeSend.minusSeconds(1)))
            }
        }
    }

    @Nested
    @DisplayName("Address Update Propagation")
    inner class AddressUpdatePropagation {

        @Test
        @DisplayName("should propagate address update to MongoDB correctly")
        fun shouldPropagateAddressUpdate() {
            val customerId = UUID.randomUUID()
            val addressId = UUID.randomUUID()
            val insertTimestamp = System.currentTimeMillis()
            val updateTimestamp = insertTimestamp + 1000

            // Create customer
            val customerEvent = buildCustomerCdcEventJson(
                id = customerId,
                email = "update-test-$customerId@example.com",
                status = "active",
                updatedAt = Instant.now(),
                operation = "c",
                sourceTimestamp = insertTimestamp
            )
            kafkaTemplate.send(CUSTOMER_CDC_TOPIC, customerId.toString(), customerEvent).get()

            await.atMost(Duration.ofSeconds(5)).untilAsserted {
                val customer = customerRepository.findById(customerId.toString()).block()
                assertNotNull(customer)
            }

            // Create address
            val insertEvent = buildAddressCdcEventJson(
                id = addressId,
                customerId = customerId,
                type = "home",
                street = "789 Oak Lane",
                city = "Bloomington",
                state = "MN",
                postalCode = "55420",
                country = "USA",
                isDefault = true,
                updatedAt = Instant.now(),
                operation = "c",
                sourceTimestamp = insertTimestamp
            )
            kafkaTemplate.send(ADDRESS_CDC_TOPIC, addressId.toString(), insertEvent).get()

            await.atMost(Duration.ofSeconds(5)).untilAsserted {
                val address = addressRepository.findById(addressId.toString()).block()
                assertNotNull(address)
                assertEquals("789 Oak Lane", address.street)
            }

            // Update address
            val updateEvent = buildAddressCdcEventJson(
                id = addressId,
                customerId = customerId,
                type = "home",
                street = "999 New Street",
                city = "Edina",
                state = "MN",
                postalCode = "55424",
                country = "USA",
                isDefault = true,
                updatedAt = Instant.now(),
                operation = "u",
                sourceTimestamp = updateTimestamp
            )
            kafkaTemplate.send(ADDRESS_CDC_TOPIC, addressId.toString(), updateEvent).get()

            await.atMost(Duration.ofSeconds(5)).untilAsserted {
                val address = addressRepository.findById(addressId.toString()).block()
                assertNotNull(address)
                assertEquals("999 New Street", address.street)
                assertEquals("Edina", address.city)
                assertEquals("55424", address.postalCode)
                assertEquals(CdcOperation.UPDATE, address.cdcMetadata.operation)
                assertEquals(updateTimestamp, address.cdcMetadata.sourceTimestamp)
            }
        }

        @Test
        @DisplayName("should skip stale address updates based on sourceTimestamp")
        fun shouldSkipStaleAddressUpdates() {
            val customerId = UUID.randomUUID()
            val addressId = UUID.randomUUID()
            val newerTimestamp = System.currentTimeMillis() + 10000
            val olderTimestamp = System.currentTimeMillis()

            // Create customer
            val customerEvent = buildCustomerCdcEventJson(
                id = customerId,
                email = "stale-test-$customerId@example.com",
                status = "active",
                updatedAt = Instant.now(),
                operation = "c",
                sourceTimestamp = olderTimestamp
            )
            kafkaTemplate.send(CUSTOMER_CDC_TOPIC, customerId.toString(), customerEvent).get()

            await.atMost(Duration.ofSeconds(5)).untilAsserted {
                val customer = customerRepository.findById(customerId.toString()).block()
                assertNotNull(customer)
            }

            // Create address with newer timestamp first
            val newerEvent = buildAddressCdcEventJson(
                id = addressId,
                customerId = customerId,
                type = "work",
                street = "Newer Street",
                city = "Minneapolis",
                state = "MN",
                postalCode = "55401",
                country = "USA",
                isDefault = false,
                updatedAt = Instant.now(),
                operation = "c",
                sourceTimestamp = newerTimestamp
            )
            kafkaTemplate.send(ADDRESS_CDC_TOPIC, addressId.toString(), newerEvent).get()

            await.atMost(Duration.ofSeconds(5)).untilAsserted {
                val address = addressRepository.findById(addressId.toString()).block()
                assertNotNull(address)
                assertEquals("Newer Street", address.street)
            }

            // Send older update (should be skipped)
            val olderEvent = buildAddressCdcEventJson(
                id = addressId,
                customerId = customerId,
                type = "work",
                street = "Older Street",
                city = "Minneapolis",
                state = "MN",
                postalCode = "55401",
                country = "USA",
                isDefault = false,
                updatedAt = Instant.now(),
                operation = "u",
                sourceTimestamp = olderTimestamp
            )
            kafkaTemplate.send(ADDRESS_CDC_TOPIC, addressId.toString(), olderEvent).get()

            // Wait for potential processing
            Thread.sleep(2000)

            // Street should still be "Newer Street"
            val address = addressRepository.findById(addressId.toString()).block()
            assertNotNull(address)
            assertEquals("Newer Street", address.street)
            assertEquals(newerTimestamp, address.cdcMetadata.sourceTimestamp)
        }
    }

    @Nested
    @DisplayName("Address Delete Propagation")
    inner class AddressDeletePropagation {

        @Test
        @DisplayName("should propagate address delete to MongoDB correctly")
        fun shouldPropagateAddressDelete() {
            val customerId = UUID.randomUUID()
            val addressId = UUID.randomUUID()
            val insertTimestamp = System.currentTimeMillis()
            val deleteTimestamp = insertTimestamp + 1000

            // Create customer
            val customerEvent = buildCustomerCdcEventJson(
                id = customerId,
                email = "delete-test-$customerId@example.com",
                status = "active",
                updatedAt = Instant.now(),
                operation = "c",
                sourceTimestamp = insertTimestamp
            )
            kafkaTemplate.send(CUSTOMER_CDC_TOPIC, customerId.toString(), customerEvent).get()

            await.atMost(Duration.ofSeconds(5)).untilAsserted {
                val customer = customerRepository.findById(customerId.toString()).block()
                assertNotNull(customer)
            }

            // Create address
            val insertEvent = buildAddressCdcEventJson(
                id = addressId,
                customerId = customerId,
                type = "billing",
                street = "To Be Deleted",
                city = "Minneapolis",
                state = "MN",
                postalCode = "55401",
                country = "USA",
                isDefault = false,
                updatedAt = Instant.now(),
                operation = "c",
                sourceTimestamp = insertTimestamp
            )
            kafkaTemplate.send(ADDRESS_CDC_TOPIC, addressId.toString(), insertEvent).get()

            await.atMost(Duration.ofSeconds(5)).untilAsserted {
                val address = addressRepository.findById(addressId.toString()).block()
                assertNotNull(address)
            }

            // Delete address
            val deleteEvent = buildAddressCdcEventJson(
                id = addressId,
                customerId = customerId,
                type = "billing",
                street = "To Be Deleted",
                city = "Minneapolis",
                state = "MN",
                postalCode = "55401",
                country = "USA",
                isDefault = false,
                updatedAt = Instant.now(),
                operation = "d",
                sourceTimestamp = deleteTimestamp
            )
            kafkaTemplate.send(ADDRESS_CDC_TOPIC, addressId.toString(), deleteEvent).get()

            await.atMost(Duration.ofSeconds(5)).untilAsserted {
                val address = addressRepository.findById(addressId.toString()).block()
                assertEquals(null, address)
            }
        }

        @Test
        @DisplayName("should handle delete of non-existent address without error")
        fun shouldHandleDeleteOfNonExistentAddress() {
            val customerId = UUID.randomUUID()
            val addressId = UUID.randomUUID()

            // Send delete for non-existent address (should not error)
            val deleteEvent = buildAddressCdcEventJson(
                id = addressId,
                customerId = customerId,
                type = "shipping",
                street = "Non-existent",
                city = "Minneapolis",
                state = "MN",
                postalCode = "55401",
                country = "USA",
                isDefault = false,
                updatedAt = Instant.now(),
                operation = "d",
                sourceTimestamp = System.currentTimeMillis()
            )
            kafkaTemplate.send(ADDRESS_CDC_TOPIC, addressId.toString(), deleteEvent).get()

            // Wait for processing
            Thread.sleep(2000)

            // Address should not exist
            val address = addressRepository.findById(addressId.toString()).block()
            assertEquals(null, address)
        }
    }

    @Nested
    @DisplayName("Customer-Address Relationship")
    inner class CustomerAddressRelationship {

        @Test
        @DisplayName("should maintain customer-address relationship via customerId")
        fun shouldMaintainCustomerAddressRelationship() {
            val customerId = UUID.randomUUID()
            val address1Id = UUID.randomUUID()
            val address2Id = UUID.randomUUID()
            val sourceTimestamp = System.currentTimeMillis()

            // Create customer
            val customerEvent = buildCustomerCdcEventJson(
                id = customerId,
                email = "relationship-test-$customerId@example.com",
                status = "active",
                updatedAt = Instant.now(),
                operation = "c",
                sourceTimestamp = sourceTimestamp
            )
            kafkaTemplate.send(CUSTOMER_CDC_TOPIC, customerId.toString(), customerEvent).get()

            await.atMost(Duration.ofSeconds(5)).untilAsserted {
                val customer = customerRepository.findById(customerId.toString()).block()
                assertNotNull(customer)
            }

            // Create first address
            val address1Event = buildAddressCdcEventJson(
                id = address1Id,
                customerId = customerId,
                type = "shipping",
                street = "First Address",
                city = "Minneapolis",
                state = "MN",
                postalCode = "55401",
                country = "USA",
                isDefault = true,
                updatedAt = Instant.now(),
                operation = "c",
                sourceTimestamp = sourceTimestamp + 100
            )
            kafkaTemplate.send(ADDRESS_CDC_TOPIC, address1Id.toString(), address1Event).get()

            // Create second address
            val address2Event = buildAddressCdcEventJson(
                id = address2Id,
                customerId = customerId,
                type = "billing",
                street = "Second Address",
                city = "St. Paul",
                state = "MN",
                postalCode = "55102",
                country = "USA",
                isDefault = false,
                updatedAt = Instant.now(),
                operation = "c",
                sourceTimestamp = sourceTimestamp + 200
            )
            kafkaTemplate.send(ADDRESS_CDC_TOPIC, address2Id.toString(), address2Event).get()

            await.atMost(Duration.ofSeconds(5)).untilAsserted {
                val addresses = addressRepository.findByCustomerId(customerId.toString()).collectList().block()
                assertNotNull(addresses)
                assertEquals(2, addresses.size)
            }

            // Verify relationship query
            val customerAddresses = addressRepository.findByCustomerId(customerId.toString()).collectList().block()
            assertNotNull(customerAddresses)
            assertEquals(2, customerAddresses.size)
            assertTrue(customerAddresses.all { it.customerId == customerId.toString() })
            assertTrue(customerAddresses.any { it.street == "First Address" })
            assertTrue(customerAddresses.any { it.street == "Second Address" })
        }

        @Test
        @DisplayName("should query addresses by customerId and type")
        fun shouldQueryAddressesByCustomerIdAndType() {
            val customerId = UUID.randomUUID()
            val shippingAddressId = UUID.randomUUID()
            val billingAddressId = UUID.randomUUID()
            val sourceTimestamp = System.currentTimeMillis()

            // Create customer
            val customerEvent = buildCustomerCdcEventJson(
                id = customerId,
                email = "type-query-test-$customerId@example.com",
                status = "active",
                updatedAt = Instant.now(),
                operation = "c",
                sourceTimestamp = sourceTimestamp
            )
            kafkaTemplate.send(CUSTOMER_CDC_TOPIC, customerId.toString(), customerEvent).get()

            await.atMost(Duration.ofSeconds(5)).untilAsserted {
                val customer = customerRepository.findById(customerId.toString()).block()
                assertNotNull(customer)
            }

            // Create shipping address
            val shippingEvent = buildAddressCdcEventJson(
                id = shippingAddressId,
                customerId = customerId,
                type = "shipping",
                street = "Shipping Address",
                city = "Minneapolis",
                state = "MN",
                postalCode = "55401",
                country = "USA",
                isDefault = true,
                updatedAt = Instant.now(),
                operation = "c",
                sourceTimestamp = sourceTimestamp + 100
            )
            kafkaTemplate.send(ADDRESS_CDC_TOPIC, shippingAddressId.toString(), shippingEvent).get()

            // Create billing address
            val billingEvent = buildAddressCdcEventJson(
                id = billingAddressId,
                customerId = customerId,
                type = "billing",
                street = "Billing Address",
                city = "St. Paul",
                state = "MN",
                postalCode = "55102",
                country = "USA",
                isDefault = true,
                updatedAt = Instant.now(),
                operation = "c",
                sourceTimestamp = sourceTimestamp + 200
            )
            kafkaTemplate.send(ADDRESS_CDC_TOPIC, billingAddressId.toString(), billingEvent).get()

            await.atMost(Duration.ofSeconds(5)).untilAsserted {
                val addresses = addressRepository.findByCustomerId(customerId.toString()).collectList().block()
                assertNotNull(addresses)
                assertEquals(2, addresses.size)
            }

            // Query by customerId and type
            val shippingAddress = addressRepository
                .findByCustomerIdAndType(customerId.toString(), AddressType.SHIPPING)
                .block()
            assertNotNull(shippingAddress)
            assertEquals("Shipping Address", shippingAddress.street)
            assertEquals(AddressType.SHIPPING, shippingAddress.type)

            val billingAddress = addressRepository
                .findByCustomerIdAndType(customerId.toString(), AddressType.BILLING)
                .block()
            assertNotNull(billingAddress)
            assertEquals("Billing Address", billingAddress.street)
            assertEquals(AddressType.BILLING, billingAddress.type)
        }

        @Test
        @DisplayName("should not return addresses for different customer")
        fun shouldNotReturnAddressesForDifferentCustomer() {
            val customer1Id = UUID.randomUUID()
            val customer2Id = UUID.randomUUID()
            val address1Id = UUID.randomUUID()
            val address2Id = UUID.randomUUID()
            val sourceTimestamp = System.currentTimeMillis()

            // Create customer 1
            val customer1Event = buildCustomerCdcEventJson(
                id = customer1Id,
                email = "customer1-$customer1Id@example.com",
                status = "active",
                updatedAt = Instant.now(),
                operation = "c",
                sourceTimestamp = sourceTimestamp
            )
            kafkaTemplate.send(CUSTOMER_CDC_TOPIC, customer1Id.toString(), customer1Event).get()

            // Create customer 2
            val customer2Event = buildCustomerCdcEventJson(
                id = customer2Id,
                email = "customer2-$customer2Id@example.com",
                status = "active",
                updatedAt = Instant.now(),
                operation = "c",
                sourceTimestamp = sourceTimestamp
            )
            kafkaTemplate.send(CUSTOMER_CDC_TOPIC, customer2Id.toString(), customer2Event).get()

            await.atMost(Duration.ofSeconds(5)).untilAsserted {
                assertNotNull(customerRepository.findById(customer1Id.toString()).block())
                assertNotNull(customerRepository.findById(customer2Id.toString()).block())
            }

            // Create address for customer 1
            val address1Event = buildAddressCdcEventJson(
                id = address1Id,
                customerId = customer1Id,
                type = "shipping",
                street = "Customer 1 Address",
                city = "Minneapolis",
                state = "MN",
                postalCode = "55401",
                country = "USA",
                isDefault = true,
                updatedAt = Instant.now(),
                operation = "c",
                sourceTimestamp = sourceTimestamp + 100
            )
            kafkaTemplate.send(ADDRESS_CDC_TOPIC, address1Id.toString(), address1Event).get()

            // Create address for customer 2
            val address2Event = buildAddressCdcEventJson(
                id = address2Id,
                customerId = customer2Id,
                type = "billing",
                street = "Customer 2 Address",
                city = "St. Paul",
                state = "MN",
                postalCode = "55102",
                country = "USA",
                isDefault = true,
                updatedAt = Instant.now(),
                operation = "c",
                sourceTimestamp = sourceTimestamp + 200
            )
            kafkaTemplate.send(ADDRESS_CDC_TOPIC, address2Id.toString(), address2Event).get()

            await.atMost(Duration.ofSeconds(5)).untilAsserted {
                val allAddresses = addressRepository.findAll().collectList().block()
                assertNotNull(allAddresses)
                assertEquals(2, allAddresses.size)
            }

            // Query addresses by customer 1 - should only return customer 1's address
            val customer1Addresses = addressRepository.findByCustomerId(customer1Id.toString()).collectList().block()
            assertNotNull(customer1Addresses)
            assertEquals(1, customer1Addresses.size)
            assertEquals("Customer 1 Address", customer1Addresses[0].street)
            assertEquals(customer1Id.toString(), customer1Addresses[0].customerId)

            // Query addresses by customer 2 - should only return customer 2's address
            val customer2Addresses = addressRepository.findByCustomerId(customer2Id.toString()).collectList().block()
            assertNotNull(customer2Addresses)
            assertEquals(1, customer2Addresses.size)
            assertEquals("Customer 2 Address", customer2Addresses[0].street)
            assertEquals(customer2Id.toString(), customer2Addresses[0].customerId)
        }
    }

    @Nested
    @DisplayName("Address Type Validation")
    inner class AddressTypeValidation {

        @Test
        @DisplayName("should accept billing address type")
        fun shouldAcceptBillingType() {
            val customerId = UUID.randomUUID()
            val addressId = UUID.randomUUID()
            val sourceTimestamp = System.currentTimeMillis()

            createCustomerAndWait(customerId, sourceTimestamp)

            val addressEvent = buildAddressCdcEventJson(
                id = addressId,
                customerId = customerId,
                type = "billing",
                street = "Billing Street",
                city = "Minneapolis",
                state = "MN",
                postalCode = "55401",
                country = "USA",
                isDefault = true,
                updatedAt = Instant.now(),
                operation = "c",
                sourceTimestamp = sourceTimestamp + 100
            )
            kafkaTemplate.send(ADDRESS_CDC_TOPIC, addressId.toString(), addressEvent).get()

            await.atMost(Duration.ofSeconds(5)).untilAsserted {
                val address = addressRepository.findById(addressId.toString()).block()
                assertNotNull(address)
                assertEquals(AddressType.BILLING, address.type)
            }
        }

        @Test
        @DisplayName("should accept shipping address type")
        fun shouldAcceptShippingType() {
            val customerId = UUID.randomUUID()
            val addressId = UUID.randomUUID()
            val sourceTimestamp = System.currentTimeMillis()

            createCustomerAndWait(customerId, sourceTimestamp)

            val addressEvent = buildAddressCdcEventJson(
                id = addressId,
                customerId = customerId,
                type = "shipping",
                street = "Shipping Street",
                city = "Minneapolis",
                state = "MN",
                postalCode = "55401",
                country = "USA",
                isDefault = true,
                updatedAt = Instant.now(),
                operation = "c",
                sourceTimestamp = sourceTimestamp + 100
            )
            kafkaTemplate.send(ADDRESS_CDC_TOPIC, addressId.toString(), addressEvent).get()

            await.atMost(Duration.ofSeconds(5)).untilAsserted {
                val address = addressRepository.findById(addressId.toString()).block()
                assertNotNull(address)
                assertEquals(AddressType.SHIPPING, address.type)
            }
        }

        @Test
        @DisplayName("should accept home address type")
        fun shouldAcceptHomeType() {
            val customerId = UUID.randomUUID()
            val addressId = UUID.randomUUID()
            val sourceTimestamp = System.currentTimeMillis()

            createCustomerAndWait(customerId, sourceTimestamp)

            val addressEvent = buildAddressCdcEventJson(
                id = addressId,
                customerId = customerId,
                type = "home",
                street = "Home Street",
                city = "Minneapolis",
                state = "MN",
                postalCode = "55401",
                country = "USA",
                isDefault = true,
                updatedAt = Instant.now(),
                operation = "c",
                sourceTimestamp = sourceTimestamp + 100
            )
            kafkaTemplate.send(ADDRESS_CDC_TOPIC, addressId.toString(), addressEvent).get()

            await.atMost(Duration.ofSeconds(5)).untilAsserted {
                val address = addressRepository.findById(addressId.toString()).block()
                assertNotNull(address)
                assertEquals(AddressType.HOME, address.type)
            }
        }

        @Test
        @DisplayName("should accept work address type")
        fun shouldAcceptWorkType() {
            val customerId = UUID.randomUUID()
            val addressId = UUID.randomUUID()
            val sourceTimestamp = System.currentTimeMillis()

            createCustomerAndWait(customerId, sourceTimestamp)

            val addressEvent = buildAddressCdcEventJson(
                id = addressId,
                customerId = customerId,
                type = "work",
                street = "Work Street",
                city = "Minneapolis",
                state = "MN",
                postalCode = "55401",
                country = "USA",
                isDefault = false,
                updatedAt = Instant.now(),
                operation = "c",
                sourceTimestamp = sourceTimestamp + 100
            )
            kafkaTemplate.send(ADDRESS_CDC_TOPIC, addressId.toString(), addressEvent).get()

            await.atMost(Duration.ofSeconds(5)).untilAsserted {
                val address = addressRepository.findById(addressId.toString()).block()
                assertNotNull(address)
                assertEquals(AddressType.WORK, address.type)
            }
        }

        @Test
        @DisplayName("should default to shipping type for unknown type")
        fun shouldDefaultToShippingForUnknownType() {
            val customerId = UUID.randomUUID()
            val addressId = UUID.randomUUID()
            val sourceTimestamp = System.currentTimeMillis()

            createCustomerAndWait(customerId, sourceTimestamp)

            val addressEvent = buildAddressCdcEventJson(
                id = addressId,
                customerId = customerId,
                type = "unknown_type",
                street = "Unknown Type Street",
                city = "Minneapolis",
                state = "MN",
                postalCode = "55401",
                country = "USA",
                isDefault = false,
                updatedAt = Instant.now(),
                operation = "c",
                sourceTimestamp = sourceTimestamp + 100
            )
            kafkaTemplate.send(ADDRESS_CDC_TOPIC, addressId.toString(), addressEvent).get()

            await.atMost(Duration.ofSeconds(5)).untilAsserted {
                val address = addressRepository.findById(addressId.toString()).block()
                assertNotNull(address)
                assertEquals(AddressType.SHIPPING, address.type)
            }
        }
    }

    @Nested
    @DisplayName("Customer Cascade Delete")
    inner class CustomerCascadeDelete {

        @Test
        @DisplayName("should handle cascade delete scenario by deleting addresses when customer deleted events arrive")
        fun shouldHandleCascadeDeleteScenario() {
            val customerId = UUID.randomUUID()
            val address1Id = UUID.randomUUID()
            val address2Id = UUID.randomUUID()
            val insertTimestamp = System.currentTimeMillis()
            val deleteTimestamp = insertTimestamp + 2000

            // Create customer
            val customerEvent = buildCustomerCdcEventJson(
                id = customerId,
                email = "cascade-test-$customerId@example.com",
                status = "active",
                updatedAt = Instant.now(),
                operation = "c",
                sourceTimestamp = insertTimestamp
            )
            kafkaTemplate.send(CUSTOMER_CDC_TOPIC, customerId.toString(), customerEvent).get()

            await.atMost(Duration.ofSeconds(5)).untilAsserted {
                val customer = customerRepository.findById(customerId.toString()).block()
                assertNotNull(customer)
            }

            // Create two addresses for the customer
            val address1Event = buildAddressCdcEventJson(
                id = address1Id,
                customerId = customerId,
                type = "shipping",
                street = "Address 1",
                city = "Minneapolis",
                state = "MN",
                postalCode = "55401",
                country = "USA",
                isDefault = true,
                updatedAt = Instant.now(),
                operation = "c",
                sourceTimestamp = insertTimestamp + 100
            )
            kafkaTemplate.send(ADDRESS_CDC_TOPIC, address1Id.toString(), address1Event).get()

            val address2Event = buildAddressCdcEventJson(
                id = address2Id,
                customerId = customerId,
                type = "billing",
                street = "Address 2",
                city = "St. Paul",
                state = "MN",
                postalCode = "55102",
                country = "USA",
                isDefault = true,
                updatedAt = Instant.now(),
                operation = "c",
                sourceTimestamp = insertTimestamp + 200
            )
            kafkaTemplate.send(ADDRESS_CDC_TOPIC, address2Id.toString(), address2Event).get()

            await.atMost(Duration.ofSeconds(5)).untilAsserted {
                val addresses = addressRepository.findByCustomerId(customerId.toString()).collectList().block()
                assertNotNull(addresses)
                assertEquals(2, addresses.size)
            }

            // Simulate cascade delete by sending delete events for addresses first (as Debezium would)
            val deleteAddress1Event = buildAddressCdcEventJson(
                id = address1Id,
                customerId = customerId,
                type = "shipping",
                street = "Address 1",
                city = "Minneapolis",
                state = "MN",
                postalCode = "55401",
                country = "USA",
                isDefault = true,
                updatedAt = Instant.now(),
                operation = "d",
                sourceTimestamp = deleteTimestamp
            )
            kafkaTemplate.send(ADDRESS_CDC_TOPIC, address1Id.toString(), deleteAddress1Event).get()

            val deleteAddress2Event = buildAddressCdcEventJson(
                id = address2Id,
                customerId = customerId,
                type = "billing",
                street = "Address 2",
                city = "St. Paul",
                state = "MN",
                postalCode = "55102",
                country = "USA",
                isDefault = true,
                updatedAt = Instant.now(),
                operation = "d",
                sourceTimestamp = deleteTimestamp + 100
            )
            kafkaTemplate.send(ADDRESS_CDC_TOPIC, address2Id.toString(), deleteAddress2Event).get()

            // Then delete the customer
            val deleteCustomerEvent = buildCustomerCdcEventJson(
                id = customerId,
                email = "cascade-test-$customerId@example.com",
                status = "active",
                updatedAt = Instant.now(),
                operation = "d",
                sourceTimestamp = deleteTimestamp + 200
            )
            kafkaTemplate.send(CUSTOMER_CDC_TOPIC, customerId.toString(), deleteCustomerEvent).get()

            await.atMost(Duration.ofSeconds(5)).untilAsserted {
                // Customer should be deleted
                val customer = customerRepository.findById(customerId.toString()).block()
                assertEquals(null, customer)

                // All addresses should be deleted (no orphans)
                val addresses = addressRepository.findByCustomerId(customerId.toString()).collectList().block()
                assertNotNull(addresses)
                assertEquals(0, addresses.size)
            }
        }
    }

    private fun createCustomerAndWait(customerId: UUID, sourceTimestamp: Long) {
        val customerEvent = buildCustomerCdcEventJson(
            id = customerId,
            email = "test-$customerId@example.com",
            status = "active",
            updatedAt = Instant.now(),
            operation = "c",
            sourceTimestamp = sourceTimestamp
        )
        kafkaTemplate.send(CUSTOMER_CDC_TOPIC, customerId.toString(), customerEvent).get()

        await.atMost(Duration.ofSeconds(5)).untilAsserted {
            val customer = customerRepository.findById(customerId.toString()).block()
            assertNotNull(customer)
        }
    }

    companion object {
        private const val CUSTOMER_CDC_TOPIC = "cdc.public.customer"
        private const val ADDRESS_CDC_TOPIC = "cdc.public.address"

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

        fun buildAddressCdcEventJson(
            id: UUID,
            customerId: UUID,
            type: String,
            street: String,
            city: String,
            state: String?,
            postalCode: String,
            country: String,
            isDefault: Boolean,
            updatedAt: Instant,
            operation: String? = null,
            sourceTimestamp: Long
        ): String {
            val fields = mutableListOf<String>()
            fields.add(""""id": "$id"""")
            fields.add(""""customer_id": "$customerId"""")
            fields.add(""""type": "$type"""")
            fields.add(""""street": "$street"""")
            fields.add(""""city": "$city"""")
            state?.let { fields.add(""""state": "$it"""") }
            fields.add(""""postal_code": "$postalCode"""")
            fields.add(""""country": "$country"""")
            fields.add(""""is_default": $isDefault""")
            fields.add(""""updated_at": "$updatedAt"""")
            operation?.let { fields.add(""""__op": "$it"""") }
            fields.add(""""__source_ts_ms": $sourceTimestamp""")
            return "{ ${fields.joinToString(", ")} }"
        }
    }
}
