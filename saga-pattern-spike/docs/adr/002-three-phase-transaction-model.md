# ADR-002: Three-Phase Transaction Model

## Status

Accepted

## Context

External HTTP calls (to inventory, payment, shipping services) should not be made inside database transactions because:

1. **Connection holding**: HTTP calls can be slow (network latency, service processing time), holding database connections unnecessarily.

2. **Transaction semantics mismatch**: HTTP calls cannot participate in database rollback. If a transaction rolls back after an HTTP call succeeded, the external service state is inconsistent.

3. **Resource exhaustion**: Long-running transactions holding database connections can exhaust the connection pool under load.

4. **Timeout complexity**: Database transaction timeouts interact poorly with HTTP timeouts, making failure scenarios unpredictable.

Our initial implementation wrapped the entire saga execution in a single `@Transactional` block, causing these problems during load testing.

## Decision

Separate saga execution into three distinct phases:

### Phase 1: Initialization (Transactional)

```kotlin
val sagaExecution = initializeSaga(context)
```

- Create saga execution record
- Update order status to PROCESSING
- Commit transaction
- Release database connection

### Phase 2: Step Execution (Non-Transactional)

```kotlin
for (step in orderedSteps) {
    val result = step.execute(context)
    // Each step manages its own persistence
}
```

- Execute steps sequentially
- Each step can make external HTTP calls freely
- Step results are persisted individually (small, fast transactions)
- No database connection held during HTTP calls

### Phase 3: Finalization (Transactional)

```kotlin
return completeSuccessfulSaga(context, sagaExecution, duration)
```

- Update final saga status (COMPLETED or trigger compensation)
- Update order status
- Commit transaction

## Consequences

### Positive

- **Resource efficiency**: Database connections are released during HTTP calls, improving connection pool utilization.

- **Clear failure boundaries**: Each phase has distinct failure modes and recovery strategies.

- **Predictable timeouts**: HTTP timeouts don't affect database transactions; each can be configured independently.

- **Better observability**: Each phase can be independently monitored and traced.

- **Improved throughput**: More concurrent sagas can run with the same connection pool size.

### Negative

- **Increased complexity**: Three phases instead of one requires more careful state management.

- **Partial state visibility**: Between phases, saga state may be partially committed and visible to other queries.

- **Manual consistency**: Developers must ensure each phase correctly handles the previous phase's state.

### Mitigations

- **State machine enforcement**: Saga status values (`PENDING`, `IN_PROGRESS`, `COMPLETED`, `FAILED`, `COMPENSATING`, `COMPENSATED`) enforce valid transitions.

- **Idempotency**: Steps are designed to be idempotent, allowing safe retry if a phase fails after partial completion.

- **Clear documentation**: Phase boundaries and responsibilities are documented in the orchestrator.

## Implementation Details

```kotlin
class OrderSagaOrchestrator {
    suspend fun executeSaga(context: SagaContext): SagaResult {
        // Phase 1: Initialize (transactional)
        val sagaExecution = initializeSaga(context)

        // Phase 2: Execute steps (non-transactional)
        for ((index, step) in orderedSteps.withIndex()) {
            val stepResult = executeStepWithRecording(step, context, sagaExecution, index)
            if (!stepResult.isSuccess()) {
                return handleStepFailure(...)
            }
        }

        // Phase 3: Finalize (transactional)
        return completeSuccessfulSaga(context, sagaExecution, duration)
    }

    @Transactional
    private suspend fun initializeSaga(context: SagaContext): SagaExecution {
        // Create execution record, update order status
    }

    @Transactional
    private suspend fun completeSuccessfulSaga(...): SagaResult {
        // Update saga status, order status
    }
}
```

## Alternatives Considered

### Single Transaction with Read Committed Isolation

Rejected because:
- Still holds connection during HTTP calls
- Doesn't solve resource exhaustion under load

### Async/Event-Based Step Execution

Considered using message queues between steps, but rejected because:
- Added infrastructure complexity
- Increased latency for simple flows
- Harder to maintain ordered execution guarantees

### Saga State Machine with Persistent Queue

Considered but deferred because:
- Over-engineering for current scale
- Can be added later if needed for recovery scenarios

## References

- [Spring Transaction Management](https://docs.spring.io/spring-framework/reference/data-access/transaction.html)
- [R2DBC Connection Pooling](https://r2dbc.io/spec/0.8.1.RELEASE/spec/html/#connections.factory)
- [Distributed Transactions in Microservices](https://developers.redhat.com/articles/2021/09/21/distributed-transaction-patterns-microservices-compared)
