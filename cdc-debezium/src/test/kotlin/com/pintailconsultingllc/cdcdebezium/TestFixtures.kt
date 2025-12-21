package com.pintailconsultingllc.cdcdebezium

import com.pintailconsultingllc.cdcdebezium.dto.AddressCdcEvent
import com.pintailconsultingllc.cdcdebezium.dto.CustomerCdcEvent
import java.time.Instant
import java.util.UUID

object TestFixtures {

    val TEST_UUID: UUID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
    val TEST_UUID_STRING = "550e8400-e29b-41d4-a716-446655440000"
    val TEST_CUSTOMER_UUID: UUID = UUID.fromString("660e8400-e29b-41d4-a716-446655440001")
    const val TEST_EMAIL = "test@example.com"
    const val TEST_STATUS = "active"
    const val TEST_ADDRESS_TYPE = "shipping"
    const val TEST_STREET = "123 Main St"
    const val TEST_CITY = "Springfield"
    const val TEST_STATE = "IL"
    const val TEST_POSTAL_CODE = "62701"
    const val TEST_COUNTRY = "USA"
    val TEST_INSTANT: Instant = Instant.parse("2024-01-15T10:30:00Z")
    const val TEST_SOURCE_TIMESTAMP = 1705315800000L

    fun createEvent(
        id: UUID = UUID.randomUUID(),
        email: String? = TEST_EMAIL,
        status: String? = TEST_STATUS,
        updatedAt: Instant? = TEST_INSTANT,
        deleted: String? = null,
        operation: String? = null,
        sourceTimestamp: Long? = null
    ) = CustomerCdcEvent(
        id = id,
        email = email,
        status = status,
        updatedAt = updatedAt,
        deleted = deleted,
        operation = operation,
        sourceTimestamp = sourceTimestamp
    )

    fun buildJson(
        id: String = TEST_UUID_STRING,
        email: String? = TEST_EMAIL,
        status: String? = TEST_STATUS,
        updatedAt: String? = null,
        deleted: String? = null,
        operation: String? = null,
        sourceTimestamp: Long? = null,
        extraFields: Map<String, Any> = emptyMap()
    ): String {
        val fields = mutableListOf<String>()
        fields.add(""""id": "$id"""")
        fields.add(""""email": ${email?.let { "\"$it\"" } ?: "null"}""")
        fields.add(""""status": ${status?.let { "\"$it\"" } ?: "null"}""")
        fields.add(""""updated_at": ${updatedAt?.let { "\"$it\"" } ?: "null"}""")
        deleted?.let { fields.add(""""__deleted": "$it"""") }
        operation?.let { fields.add(""""__op": "$it"""") }
        sourceTimestamp?.let { fields.add(""""__source_ts_ms": $it""") }
        extraFields.forEach { (key, value) ->
            val jsonValue = when (value) {
                is String -> "\"$value\""
                else -> value.toString()
            }
            fields.add(""""$key": $jsonValue""")
        }
        return "{\n${fields.joinToString(",\n") { "    $it" }}\n}"
    }

    fun createAddressEvent(
        id: UUID = UUID.randomUUID(),
        customerId: UUID = TEST_CUSTOMER_UUID,
        type: String? = TEST_ADDRESS_TYPE,
        street: String? = TEST_STREET,
        city: String? = TEST_CITY,
        state: String? = TEST_STATE,
        postalCode: String? = TEST_POSTAL_CODE,
        country: String? = TEST_COUNTRY,
        isDefault: Boolean? = false,
        updatedAt: Instant? = TEST_INSTANT,
        deleted: String? = null,
        operation: String? = null,
        sourceTimestamp: Long? = null
    ) = AddressCdcEvent(
        id = id,
        customerId = customerId,
        type = type,
        street = street,
        city = city,
        state = state,
        postalCode = postalCode,
        country = country,
        isDefault = isDefault,
        updatedAt = updatedAt,
        deleted = deleted,
        operation = operation,
        sourceTimestamp = sourceTimestamp
    )
}
