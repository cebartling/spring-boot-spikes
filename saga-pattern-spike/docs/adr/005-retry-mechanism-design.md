# ADR-005: Retry Mechanism Design

## Status

Accepted

## Context

Failed saga executions need a retry mechanism that:

1. **Resumes intelligently**: Skip already-completed steps, resume from failure point
2. **Supports updates**: Allow changing payment method or shipping address before retry
3. **Tracks history**: Maintain audit trail of all retry attempts
4. **Prevents abuse**: Limit retry attempts to avoid infinite loops
5. **Handles concurrency**: Prevent multiple simultaneous retries for the same order

Our order processing failures can be:
- **Transient**: Network timeouts, service temporarily unavailable
- **Permanent**: Invalid payment method, item out of stock
- **Fixable**: Wrong address, expired card (user can provide new data)

## Decision

Implement a **dedicated RetryOrchestrator** with context reconstruction capabilities:

### Retry Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                       RetryOrchestrator                         │
├─────────────────────────────────────────────────────────────────┤
│  1. Validate retry eligibility                                  │
│  2. Reconstruct context from original execution                 │
│  3. Apply user updates (address, payment)                       │
│  4. Create new saga execution                                   │
│  5. Skip completed steps                                        │
│  6. Resume from failed step                                     │
└─────────────────────────────────────────────────────────────────┘
```

### Context Reconstruction

Use `RetryContextBuilder` to reconstruct saga context from previous execution:

```kotlin
class RetryContextBuilder {
    suspend fun buildContext(
        order: Order,
        originalExecution: SagaExecution,
        request: RetryRequest
    ): SagaContext {
        // Load completed step data
        val stepResults = sagaStepResultRepository
            .findBySagaExecutionIdOrderByStepOrder(originalExecution.id)

        // Reconstruct or use updated values
        val shippingAddress = request.updatedShippingAddress
            ?: reconstructFromStepData(stepResults, "shippingAddress")
            ?: throw RetryException("Cannot determine shipping address")

        return SagaContext(
            order = order,
            sagaExecutionId = newExecutionId,
            shippingAddress = shippingAddress,
            paymentMethodId = paymentMethodId
        )
    }
}
```

### Step Skipping Strategy

Skip steps that completed successfully in the original execution:

```kotlin
for ((index, step) in orderedSteps.withIndex()) {
    if (step.getStepName() in completedStepNames) {
        // Skip - already completed
        recordSkippedStep(step)
        continue
    }

    // Execute step
    val result = step.execute(context)
    // ...
}
```

### Retry Attempt Tracking

```kotlin
data class RetryAttempt(
    val id: UUID,
    val orderId: UUID,
    val originalExecutionId: UUID,
    val retryExecutionId: UUID?,
    val attemptNumber: Int,
    val resumedFromStep: String?,
    val skippedSteps: List<String>,
    val outcome: RetryOutcome?,
    val failureReason: String?,
    val createdAt: Instant,
    val completedAt: Instant?
)

enum class RetryOutcome {
    SUCCESS,
    FAILED,
    PARTIAL_SUCCESS
}
```

## Consequences

### Positive

- **Efficient retries**: Completed steps are not re-executed, saving time and resources.

- **User empowerment**: Users can fix issues (wrong address, expired card) and retry.

- **Complete audit trail**: Every retry attempt is tracked with outcome.

- **Idempotent design**: Retry mechanism is safe to invoke multiple times.

- **Flexible updates**: Retry request can include updated data without losing original context.

### Negative

- **Context reconstruction complexity**: Extracting data from previous step results requires careful serialization/deserialization.

- **State dependency**: Skipped steps assume external service state hasn't changed (e.g., inventory still reserved).

- **Potential stale data**: If original execution data is corrupted or incomplete, retry may fail.

### Mitigations

- **Validation before retry**: Verify required context data exists before attempting retry.

- **Idempotent compensation**: If retry fails after partial progress, compensation handles both old and new state.

- **Clear error messages**: When context reconstruction fails, provide actionable error messages.

## Implementation Details

### RetryRequest

```kotlin
data class RetryRequest(
    val updatedShippingAddress: ShippingAddress? = null,
    val updatedPaymentMethodId: String? = null,
    val reason: String? = null
)
```

### RetryOrchestrator Flow

```kotlin
class RetryOrchestrator {
    suspend fun executeRetry(orderId: UUID, request: RetryRequest): SagaRetryResult {
        // 1. Validate eligibility
        val order = validateOrderForRetry(orderId)
        val originalExecution = findOriginalExecution(orderId)

        // 2. Check retry limits
        val attemptCount = retryAttemptRepository.countByOrderId(orderId)
        if (attemptCount >= maxRetries) {
            throw RetryException("Maximum retry attempts exceeded")
        }

        // 3. Create retry attempt record
        val retryAttempt = createRetryAttempt(order, originalExecution, request)

        // 4. Build context (with reconstruction)
        val context = retryContextBuilder.buildContext(order, originalExecution, request)

        // 5. Determine skip list
        val completedSteps = getCompletedStepNames(originalExecution)

        // 6. Execute retry saga
        return executeSagaWithSkips(context, completedSteps, retryAttempt)
    }
}
```

### Concurrency Prevention

```kotlin
// Before creating retry attempt
if (retryAttemptRepository.existsActiveRetryByOrderId(orderId)) {
    throw RetryException("Retry already in progress for this order")
}
```

## Alternatives Considered

### Full Re-execution

Re-run entire saga from beginning, compensating all previous work first.

Rejected because:
- Wasteful for transient failures near end of saga
- User data already validated doesn't need re-validation
- External services may charge for repeated operations

### Checkpoint-Based Retry

Store explicit checkpoints that can be restored exactly.

Deferred because:
- Additional storage complexity
- Current step-result approach provides sufficient data
- Can be added later for high-value scenarios

### Automatic Retry with Backoff

Automatically retry failed sagas without user intervention.

Partially implemented:
- Steps can be marked as `retryable` with automatic retry
- Saga-level retry requires user initiation
- Prevents infinite loops on permanent failures

## Configuration

```yaml
saga:
  retry:
    max-attempts: 3
    require-user-initiation: true
    preserve-completed-steps: true
```

## References

- [Retry Pattern](https://docs.microsoft.com/en-us/azure/architecture/patterns/retry)
- [Idempotency Patterns](https://blog.jonathanoliver.com/idempotency-patterns/)
- [Saga Pattern Recovery](https://microservices.io/post/sagas/2019/04/17/how-sagas-handle-failures.html)
