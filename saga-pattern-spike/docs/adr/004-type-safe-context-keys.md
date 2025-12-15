# ADR-004: Type-Safe Context Keys

## Status

Accepted

## Context

Saga steps need to share data during execution. For example:
- Inventory step produces a `reservationId` that compensation needs
- Payment step produces an `authorizationId` for voiding
- Shipping step produces a `trackingNumber` for notifications

Initial implementation used string keys with `Any` values:

```kotlin
// Error-prone approach
context.put("reservationId", reservationId)
val id = context.get("reservationId") as String  // Runtime cast, potential ClassCastException
```

Problems with this approach:
1. **Typos**: `"reservationId"` vs `"reservation_id"` - fails silently
2. **Type mismatches**: Storing `String`, casting to `UUID` - runtime exception
3. **Discoverability**: No IDE support for available keys
4. **Refactoring**: Renaming a key requires searching all usages as strings

## Decision

Implement **type-safe context keys** using a generic `ContextKey<T>` class:

```kotlin
class ContextKey<T>(val name: String)

class SagaContext {
    private val data = mutableMapOf<String, Any>()

    fun <T> putData(key: ContextKey<T>, value: T) {
        data[key.name] = value as Any
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> getData(key: ContextKey<T>): T? {
        return data[key.name] as? T
    }

    companion object {
        val RESERVATION_ID = ContextKey<String>("reservationId")
        val AUTHORIZATION_ID = ContextKey<String>("authorizationId")
        val TRACKING_NUMBER = ContextKey<String>("trackingNumber")
        val ESTIMATED_DELIVERY = ContextKey<String>("estimatedDelivery")
    }
}
```

Usage:

```kotlin
// Compile-time type safety
context.putData(SagaContext.RESERVATION_ID, response.reservationId)
val id: String? = context.getData(SagaContext.RESERVATION_ID)

// Compiler error: type mismatch
context.putData(SagaContext.RESERVATION_ID, 123)  // Error: Int not assignable to String
```

## Consequences

### Positive

- **Compile-time safety**: Type mismatches caught at compile time, not runtime.

- **IDE support**: Auto-completion for available keys, navigation to definitions.

- **Refactoring**: Rename key in one place, IDE updates all usages.

- **Documentation**: Keys are defined in one location with clear types.

- **Discoverability**: Developers can browse `SagaContext.Companion` to see available keys.

### Negative

- **Slight ceremony**: Must define keys before use (vs ad-hoc strings).

- **Unchecked cast internally**: The `getData` implementation uses an unchecked cast, though it's safe given the `putData` contract.

- **Key proliferation**: Many keys can clutter the companion object.

### Mitigations

- **Organized companions**: Group related keys with comments or move to separate key definition files if needed.

- **Suppressed warnings**: The internal unchecked cast warning is explicitly suppressed with documentation.

## Implementation Details

### ContextKey Definition

```kotlin
/**
 * Type-safe key for saga context data.
 *
 * @param T The type of value associated with this key
 * @property name The string identifier used for storage
 */
class ContextKey<T>(val name: String)
```

### SagaContext Methods

```kotlin
class SagaContext(
    val order: Order,
    val sagaExecutionId: UUID,
    // ... other required fields
) {
    private val data = mutableMapOf<String, Any>()

    /**
     * Store a typed value in the context.
     */
    fun <T : Any> putData(key: ContextKey<T>, value: T) {
        data[key.name] = value
    }

    /**
     * Retrieve a typed value from the context.
     * Returns null if key is not present.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> getData(key: ContextKey<T>): T? {
        return data[key.name] as? T
    }

    /**
     * Check if a key is present in the context.
     */
    fun <T> hasData(key: ContextKey<T>): Boolean {
        return data.containsKey(key.name)
    }

    /**
     * Get all data as a map (for serialization/debugging).
     */
    fun getAllData(): Map<String, Any> = data.toMap()
}
```

### Standard Keys

```kotlin
companion object {
    // Inventory step data
    val RESERVATION_ID = ContextKey<String>("reservationId")

    // Payment step data
    val AUTHORIZATION_ID = ContextKey<String>("authorizationId")

    // Shipping step data
    val TRACKING_NUMBER = ContextKey<String>("trackingNumber")
    val ESTIMATED_DELIVERY = ContextKey<String>("estimatedDelivery")

    // Notification step data
    val NOTIFICATION_ID = ContextKey<String>("notificationId")
}
```

## Alternatives Considered

### Sealed Class for Context Data

```kotlin
sealed class ContextData {
    data class ReservationId(val value: String) : ContextData()
    data class AuthorizationId(val value: String) : ContextData()
}
```

Rejected because:
- More boilerplate per key
- Harder to add new keys
- Wrapper classes add ceremony

### Data Class with All Fields

```kotlin
data class SagaContextData(
    var reservationId: String? = null,
    var authorizationId: String? = null,
    // ... all possible fields
)
```

Rejected because:
- Explosion of fields as saga grows
- Hard to know which fields are set at any point
- Nullable everything loses type safety benefits

### Map with Extension Functions

```kotlin
fun SagaContext.setReservationId(id: String) = put("reservationId", id)
fun SagaContext.getReservationId(): String? = get("reservationId") as? String
```

Rejected because:
- Extension functions scattered across files
- No single source of truth for available keys
- Same runtime cast issues

## References

- [Type-Safe Builders in Kotlin](https://kotlinlang.org/docs/type-safe-builders.html)
- [Effective Java - Type-Safe Heterogeneous Containers](https://www.informit.com/articles/article.aspx?p=2861454&seqNum=8)
