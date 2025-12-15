package com.pintailconsultingllc.sagapattern.saga.execution

import com.pintailconsultingllc.sagapattern.domain.Order
import com.pintailconsultingllc.sagapattern.domain.OrderStatus
import com.pintailconsultingllc.sagapattern.domain.SagaStepResult
import com.pintailconsultingllc.sagapattern.domain.ShippingAddress
import com.pintailconsultingllc.sagapattern.history.ErrorInfo
import com.pintailconsultingllc.sagapattern.metrics.SagaMetrics
import com.pintailconsultingllc.sagapattern.repository.SagaExecutionRepository
import com.pintailconsultingllc.sagapattern.repository.SagaStepResultRepository
import com.pintailconsultingllc.sagapattern.saga.CompensationResult
import com.pintailconsultingllc.sagapattern.saga.SagaContext
import com.pintailconsultingllc.sagapattern.saga.SagaStep
import com.pintailconsultingllc.sagapattern.saga.StepResult
import com.pintailconsultingllc.sagapattern.saga.event.SagaEventRecorder
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.stub
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Unit tests for StepExecutor.
 */
@Tag("unit")
class StepExecutorTest {

    private lateinit var sagaStepResultRepository: SagaStepResultRepository
    private lateinit var sagaExecutionRepository: SagaExecutionRepository
    private lateinit var sagaMetrics: SagaMetrics
    private lateinit var sagaEventRecorder: SagaEventRecorder
    private lateinit var stepExecutor: StepExecutor
    private lateinit var context: SagaContext

    private val sagaExecutionId = UUID.randomUUID()
    private val orderId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        sagaStepResultRepository = mock()
        sagaExecutionRepository = mock()
        sagaMetrics = mock()
        sagaEventRecorder = mock()

        stepExecutor = StepExecutor(
            sagaStepResultRepository = sagaStepResultRepository,
            sagaExecutionRepository = sagaExecutionRepository,
            sagaMetrics = sagaMetrics,
            sagaEventRecorder = sagaEventRecorder
        )

        val order = Order(
            id = orderId,
            customerId = UUID.randomUUID(),
            totalAmountInCents = 9999L,
            status = OrderStatus.PROCESSING
        )

        context = SagaContext(
            order = order,
            sagaExecutionId = sagaExecutionId,
            customerId = order.customerId,
            paymentMethodId = "test-payment",
            shippingAddress = ShippingAddress(
                street = "123 Main St",
                city = "Anytown",
                state = "CA",
                postalCode = "90210",
                country = "US"
            )
        )

        // Configure default repository behavior using stub
        sagaStepResultRepository.stub {
            onBlocking { save(any()) } doAnswer { invocation ->
                invocation.getArgument<SagaStepResult>(0)
            }
            onBlocking { markInProgress(any(), any()) } doReturn 1
            onBlocking { markCompleted(any(), any(), any()) } doReturn 1
            onBlocking { markFailed(any(), any(), any()) } doReturn 1
        }

        sagaExecutionRepository.stub {
            onBlocking { updateCurrentStep(any(), any()) } doReturn 1
        }

        // Configure metrics mock - use doAnswer to execute the suspend block
        sagaMetrics.stub {
            onBlocking { timeStepSuspend<StepResult>(any(), any()) } doAnswer { invocation ->
                @Suppress("UNCHECKED_CAST")
                val block = invocation.getArgument<suspend () -> StepResult>(1)
                runBlocking { block() }
            }
        }
    }

    @Test
    fun `executeSteps returns AllSucceeded when all steps succeed`() = runTest {
        val step1 = createMockStep("Step1", 1, StepResult.success(mapOf("key1" to "value1")))
        val step2 = createMockStep("Step2", 2, StepResult.success(mapOf("key2" to "value2")))
        val steps = listOf(step1, step2)

        val outcome = stepExecutor.executeSteps(
            steps = steps,
            context = context,
            sagaExecutionId = sagaExecutionId
        )

        assertIs<StepExecutionOutcome.AllSucceeded>(outcome)
        verify(sagaStepResultRepository, times(2)).save(any())
        verify(sagaStepResultRepository, times(2)).markInProgress(any(), any())
        verify(sagaStepResultRepository, times(2)).markCompleted(any(), anyOrNull(), any())
        verify(sagaExecutionRepository, times(2)).updateCurrentStep(eq(sagaExecutionId), any())
        verify(sagaMetrics).stepCompleted("Step1")
        verify(sagaMetrics).stepCompleted("Step2")
    }

    @Test
    fun `executeSteps returns Failed when a step fails`() = runTest {
        val step1 = createMockStep("Step1", 1, StepResult.success())
        val step2 = createMockStep("Step2", 2, StepResult.failure("Step failed", "ERR_001"))
        val step3 = createMockStep("Step3", 3, StepResult.success())
        val steps = listOf(step1, step2, step3)

        val outcome = stepExecutor.executeSteps(
            steps = steps,
            context = context,
            sagaExecutionId = sagaExecutionId
        )

        assertIs<StepExecutionOutcome.Failed>(outcome)
        assertEquals(step2, outcome.step)
        assertEquals(1, outcome.stepIndex)
        assertEquals("Step failed", outcome.result.errorMessage)
        assertEquals("ERR_001", outcome.result.errorCode)

        // Step3 should not have been executed
        verify(step1).execute(context)
        verify(step2).execute(context)
        verify(step3, never()).execute(any())

        // Only 2 steps should have records created
        verify(sagaStepResultRepository, times(2)).save(any())
        verify(sagaStepResultRepository, times(1)).markCompleted(any(), anyOrNull(), any())
        verify(sagaStepResultRepository, times(1)).markFailed(any(), eq("Step failed"), any())
    }

    @Test
    fun `executeSteps skips steps based on predicate`() = runTest {
        val step1 = createMockStep("Step1", 1, StepResult.success())
        val step2 = createMockStep("Step2", 2, StepResult.success())
        val step3 = createMockStep("Step3", 3, StepResult.success())
        val steps = listOf(step1, step2, step3)

        // Skip Step2
        val outcome = stepExecutor.executeSteps(
            steps = steps,
            context = context,
            sagaExecutionId = sagaExecutionId,
            skipPredicate = { step -> step.getStepName() == "Step2" }
        )

        assertIs<StepExecutionOutcome.AllSucceeded>(outcome)

        // Step2 should not have been executed
        verify(step1).execute(context)
        verify(step2, never()).execute(any())
        verify(step3).execute(context)

        // 3 records created (including skipped)
        verify(sagaStepResultRepository, times(3)).save(any())

        // Only 2 steps marked as in progress (skipped ones are not)
        verify(sagaStepResultRepository, times(2)).markInProgress(any(), any())

        // 2 completed, 1 skipped (skip creates record with SKIPPED status)
        verify(sagaStepResultRepository, times(2)).markCompleted(any(), anyOrNull(), any())
        verify(sagaMetrics, times(3)).stepCompleted(any())
    }

    @Test
    fun `executeSteps records events when recordEvents is true`() = runTest {
        val step1 = createMockStep("Step1", 1, StepResult.success(mapOf("data" to "value")))
        val steps = listOf(step1)

        stepExecutor.executeSteps(
            steps = steps,
            context = context,
            sagaExecutionId = sagaExecutionId,
            recordEvents = true
        )

        verify(sagaEventRecorder).recordStepStarted(orderId, sagaExecutionId, "Step1")
        verify(sagaEventRecorder).recordStepCompleted(eq(orderId), eq(sagaExecutionId), eq("Step1"), any())
    }

    @Test
    fun `executeSteps does not record events when recordEvents is false`() = runTest {
        val step1 = createMockStep("Step1", 1, StepResult.success())
        val steps = listOf(step1)

        stepExecutor.executeSteps(
            steps = steps,
            context = context,
            sagaExecutionId = sagaExecutionId,
            recordEvents = false
        )

        verify(sagaEventRecorder, never()).recordStepStarted(any(), any(), any())
        verify(sagaEventRecorder, never()).recordStepCompleted(any(), any(), any(), any())
    }

    @Test
    fun `executeSteps records failure event when step fails and recordEvents is true`() = runTest {
        val step1 = createMockStep("Step1", 1, StepResult.failure("Error occurred", "ERR_001"))
        val steps = listOf(step1)

        stepExecutor.executeSteps(
            steps = steps,
            context = context,
            sagaExecutionId = sagaExecutionId,
            recordEvents = true
        )

        verify(sagaEventRecorder).recordStepStarted(orderId, sagaExecutionId, "Step1")
        verify(sagaEventRecorder).recordStepFailed(eq(orderId), eq(sagaExecutionId), eq("Step1"), any<ErrorInfo>())
    }

    @Test
    fun `executeSteps with empty list returns AllSucceeded`() = runTest {
        val outcome = stepExecutor.executeSteps(
            steps = emptyList(),
            context = context,
            sagaExecutionId = sagaExecutionId
        )

        assertIs<StepExecutionOutcome.AllSucceeded>(outcome)
        verify(sagaStepResultRepository, never()).save(any())
    }

    @Test
    fun `executeSteps updates current step correctly`() = runTest {
        val step1 = createMockStep("Step1", 1, StepResult.success())
        val step2 = createMockStep("Step2", 2, StepResult.success())
        val step3 = createMockStep("Step3", 3, StepResult.success())
        val steps = listOf(step1, step2, step3)

        stepExecutor.executeSteps(
            steps = steps,
            context = context,
            sagaExecutionId = sagaExecutionId
        )

        verify(sagaExecutionRepository).updateCurrentStep(sagaExecutionId, 1)
        verify(sagaExecutionRepository).updateCurrentStep(sagaExecutionId, 2)
        verify(sagaExecutionRepository).updateCurrentStep(sagaExecutionId, 3)
    }

    @Test
    fun `executeSteps handles step with empty data`() = runTest {
        val step1 = createMockStep("Step1", 1, StepResult.success(emptyMap()))
        val steps = listOf(step1)

        val outcome = stepExecutor.executeSteps(
            steps = steps,
            context = context,
            sagaExecutionId = sagaExecutionId
        )

        assertIs<StepExecutionOutcome.AllSucceeded>(outcome)
        // Data should be null when empty
        verify(sagaStepResultRepository).markCompleted(any(), eq(null), any())
    }

    @Test
    fun `executeSteps handles step with non-empty data`() = runTest {
        val step1 = createMockStep("Step1", 1, StepResult.success(mapOf("key" to "value")))
        val steps = listOf(step1)

        val outcome = stepExecutor.executeSteps(
            steps = steps,
            context = context,
            sagaExecutionId = sagaExecutionId
        )

        assertIs<StepExecutionOutcome.AllSucceeded>(outcome)
        // Data should be serialized JSON when not empty
        verify(sagaStepResultRepository).markCompleted(any(), eq("""{"key":"value"}"""), any())
    }

    @Test
    fun `executeSteps records metrics for each step`() = runTest {
        val step1 = createMockStep("Step1", 1, StepResult.success())
        val step2 = createMockStep("Step2", 2, StepResult.success())
        val steps = listOf(step1, step2)

        stepExecutor.executeSteps(
            steps = steps,
            context = context,
            sagaExecutionId = sagaExecutionId
        )

        verify(sagaMetrics).timeStepSuspend<StepResult>(eq("Step1"), any())
        verify(sagaMetrics).timeStepSuspend<StepResult>(eq("Step2"), any())
        verify(sagaMetrics).stepCompleted("Step1")
        verify(sagaMetrics).stepCompleted("Step2")
    }

    @Test
    fun `first step failure returns correct stepIndex of zero`() = runTest {
        val step1 = createMockStep("Step1", 1, StepResult.failure("First step failed"))
        val steps = listOf(step1)

        val outcome = stepExecutor.executeSteps(
            steps = steps,
            context = context,
            sagaExecutionId = sagaExecutionId
        )

        assertIs<StepExecutionOutcome.Failed>(outcome)
        assertEquals(0, outcome.stepIndex)
        assertEquals("Step1", outcome.step.getStepName())
    }

    private fun createMockStep(name: String, order: Int, result: StepResult): SagaStep {
        val step: SagaStep = mock {
            on { getStepName() } doReturn name
            on { getStepOrder() } doReturn order
            onBlocking { execute(any()) } doReturn result
            onBlocking { compensate(any()) } doReturn CompensationResult.success("Compensated")
        }
        return step
    }
}
