# ADR-003: Compensation Strategy

## Status

Accepted

## Context

When a saga step fails, previously completed steps must be compensated (rolled back) to maintain consistency. The compensation strategy affects:

1. **Reliability**: How consistently can we undo completed work?
2. **Complexity**: How much compensation logic must each step implement?
3. **Observability**: How well can we track compensation progress?
4. **Recovery**: What happens if compensation itself fails?

Our saga has four main steps that may need compensation:
- Inventory reservation (release reserved items)
- Payment authorization (void authorization)
- Shipping scheduling (cancel shipment)
- Notification sending (no compensation needed - already sent)

## Decision

Implement a **dedicated CompensationOrchestrator** with the following strategy:

### Reverse Order Compensation

Compensate steps in reverse order of execution:

```kotlin
val stepsToCompensate = completedSteps.reversed()
for (step in stepsToCompensate) {
    step.compensate(context)
}
```

This ensures dependencies are respected (e.g., cancel shipment before releasing inventory).

### Best-Effort Compensation

If a compensation action fails:
1. Record the failure
2. Continue compensating remaining steps
3. Mark saga as `PARTIALLY_COMPENSATED` if any compensation failed

```kotlin
sealed class CompensationResult {
    data class Success(val compensatedSteps: List<String>) : CompensationResult()
    data class PartialSuccess(
        val compensatedSteps: List<String>,
        val failedSteps: List<CompensationFailure>
    ) : CompensationResult()
    data class Failure(val reason: String) : CompensationResult()
}
```

### Compensation Data Preservation

Step execution data needed for compensation is stored in `SagaStepResult.stepData`:

```kotlin
// During execution
context.putData(SagaContext.RESERVATION_ID, reservationId)
stepResult.stepData = objectMapper.writeValueAsString(stepDataMap)

// During compensation
val reservationId = context.getData(SagaContext.RESERVATION_ID)
inventoryClient.releaseReservation(reservationId)
```

## Consequences

### Positive

- **Clear separation**: `CompensationOrchestrator` handles all compensation logic, keeping `OrderSagaOrchestrator` focused on forward execution.

- **Graceful degradation**: Partial compensation is tracked, allowing manual intervention for stuck sagas.

- **Comprehensive audit trail**: All compensation attempts and outcomes are recorded as domain events.

- **Testable**: Compensation logic can be unit tested independently of execution logic.

### Negative

- **Data dependency**: Compensation requires data from execution phase; if data is lost, compensation may fail.

- **Eventual consistency window**: Between failure detection and compensation completion, the system is inconsistent.

- **Manual intervention required**: Partially compensated sagas require human review.

### Mitigations

- **Required data validation**: Steps validate required context data before execution, failing fast if missing.

- **Event sourcing**: All state changes are recorded as events, enabling reconstruction if needed.

- **Alerting**: Partially compensated sagas trigger alerts for operations team.

## Implementation Details

### CompensationOrchestrator

```kotlin
@Component
class CompensationOrchestrator(
    private val steps: List<SagaStep>,
    private val sagaEventRecorder: SagaEventRecorder,
    private val sagaMetrics: SagaMetrics
) {
    suspend fun compensate(
        context: SagaContext,
        completedStepNames: List<String>
    ): CompensationResult {
        sagaEventRecorder.recordCompensationStarted(context)

        val compensatedSteps = mutableListOf<String>()
        val failedSteps = mutableListOf<CompensationFailure>()

        // Reverse order
        val stepsToCompensate = steps
            .filter { it.getStepName() in completedStepNames }
            .sortedByDescending { it.getStepOrder() }

        for (step in stepsToCompensate) {
            try {
                step.compensate(context)
                compensatedSteps.add(step.getStepName())
            } catch (e: Exception) {
                failedSteps.add(CompensationFailure(step.getStepName(), e.message))
                // Continue with remaining steps
            }
        }

        return when {
            failedSteps.isEmpty() -> CompensationResult.Success(compensatedSteps)
            compensatedSteps.isNotEmpty() -> CompensationResult.PartialSuccess(compensatedSteps, failedSteps)
            else -> CompensationResult.Failure("All compensations failed")
        }
    }
}
```

### Step Compensation Contract

```kotlin
abstract class AbstractSagaStep {
    suspend fun compensate(context: SagaContext): CompensationResult {
        return try {
            doCompensate(context)
            CompensationResult.success(getStepName())
        } catch (e: SagaServiceException) {
            CompensationResult.failure("Failed to compensate ${getStepName()}: ${e.message}")
        }
    }

    protected abstract suspend fun doCompensate(context: SagaContext)
}
```

## Alternatives Considered

### Stop on First Compensation Failure

Rejected because:
- Leaves more steps uncompensated
- External services may have timeouts that resolve themselves
- Better to attempt all compensations and report partial success

### Compensation Retry with Backoff

Considered adding automatic retry for failed compensations, but deferred because:
- Adds complexity to compensation flow
- May cause cascading delays
- Can be added later for specific high-value compensations

### Compensation Saga (Saga of Sagas)

Rejected because:
- Over-engineering for current needs
- Compensation operations are typically simpler than forward operations
- Our best-effort approach handles most failure scenarios

## References

- [Saga Pattern - Compensation](https://learn.microsoft.com/en-us/azure/architecture/reference-architectures/saga/saga#compensating-transactions)
- [Compensating Transaction Pattern](https://docs.microsoft.com/en-us/azure/architecture/patterns/compensating-transaction)
- [Handling Failures in Sagas](https://microservices.io/post/sagas/2019/04/17/how-sagas-handle-failures.html)
