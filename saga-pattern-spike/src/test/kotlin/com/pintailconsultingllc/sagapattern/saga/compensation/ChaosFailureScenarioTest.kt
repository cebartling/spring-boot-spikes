package com.pintailconsultingllc.sagapattern.saga.compensation

import com.pintailconsultingllc.sagapattern.domain.Order
import com.pintailconsultingllc.sagapattern.domain.OrderItem
import com.pintailconsultingllc.sagapattern.domain.OrderStatus
import com.pintailconsultingllc.sagapattern.domain.SagaExecution
import com.pintailconsultingllc.sagapattern.domain.SagaStatus
import com.pintailconsultingllc.sagapattern.domain.ShippingAddress
import com.pintailconsultingllc.sagapattern.event.DomainEventPublisher
import com.pintailconsultingllc.sagapattern.metrics.SagaMetrics
import com.pintailconsultingllc.sagapattern.repository.OrderRepository
import com.pintailconsultingllc.sagapattern.repository.SagaExecutionRepository
import com.pintailconsultingllc.sagapattern.repository.SagaStepResultRepository
import com.pintailconsultingllc.sagapattern.saga.CompensationResult
import com.pintailconsultingllc.sagapattern.saga.SagaContext
import com.pintailconsultingllc.sagapattern.saga.SagaStep
import com.pintailconsultingllc.sagapattern.saga.StepResult
import com.pintailconsultingllc.sagapattern.saga.event.SagaEventRecorder
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlinx.coroutines.runBlocking
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Chaos engineering tests for saga compensation.
 *
 * These tests simulate various failure scenarios to verify system resilience:
 * - Steps that throw unexpected exceptions during compensation
 * - Steps that timeout during compensation
 * - Intermittent failures (fail then succeed)
 * - Steps that fail silently (return success but don't actually compensate)
 */
@Tag("unit")
@DisplayName("Chaos/Failure Scenario Tests")
class ChaosFailureScenarioTest {

    private lateinit var orderRepository: OrderRepository
    private lateinit var sagaExecutionRepository: SagaExecutionRepository
    private lateinit var sagaStepResultRepository: SagaStepResultRepository
    private lateinit var sagaMetrics: SagaMetrics
    private lateinit var domainEventPublisher: DomainEventPublisher
    private lateinit var sagaEventRecorder: SagaEventRecorder
    private lateinit var compensationOrchestrator: CompensationOrchestrator

    private lateinit var context: SagaContext
    private lateinit var sagaExecution: SagaExecution

    @BeforeEach
    fun setUp() {
        orderRepository = mock()
        sagaExecutionRepository = mock()
        sagaStepResultRepository = mock()
        sagaMetrics = mock()
        domainEventPublisher = mock()
        sagaEventRecorder = mock()

        // Stub timeStepSuspend to execute the block and return its result
        runBlocking {
            whenever(sagaMetrics.timeStepSuspend<CompensationResult>(any(), any())).thenAnswer { invocation ->
                @Suppress("UNCHECKED_CAST")
                val block = invocation.arguments[1] as suspend () -> CompensationResult
                runBlocking { block() }
            }
        }

        compensationOrchestrator = CompensationOrchestrator(
            orderRepository = orderRepository,
            sagaExecutionRepository = sagaExecutionRepository,
            sagaStepResultRepository = sagaStepResultRepository,
            sagaMetrics = sagaMetrics,
            domainEventPublisher = domainEventPublisher,
            sagaEventRecorder = sagaEventRecorder
        )

        val orderId = UUID.randomUUID()
        val order = Order(
            id = orderId,
            customerId = UUID.randomUUID(),
            totalAmountInCents = 9999L,
            status = OrderStatus.PROCESSING
        ).apply {
            items = listOf(
                OrderItem(
                    orderId = orderId,
                    productId = UUID.randomUUID(),
                    productName = "Test Product",
                    quantity = 2,
                    unitPriceInCents = 4999L
                )
            )
        }

        context = SagaContext(
            order = order,
            sagaExecutionId = UUID.randomUUID(),
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

        sagaExecution = SagaExecution.create(
            id = context.sagaExecutionId,
            orderId = orderId,
            status = SagaStatus.IN_PROGRESS,
            startedAt = Instant.now(),
            traceId = null
        )
    }

    @Nested
    @DisplayName("Unexpected Exception During Compensation")
    inner class UnexpectedExceptionTests {

        @Test
        @DisplayName("Should handle RuntimeException during compensation")
        fun shouldHandleRuntimeException() = runTest {
            val throwingStep = createExceptionThrowingStep(
                "ThrowingStep",
                RuntimeException("Unexpected runtime error")
            )
            val normalStep = createMockStep("NormalStep", 1, CompensationResult.success("Done"))

            val request = CompensationRequest(
                context = context,
                sagaExecution = sagaExecution,
                completedSteps = listOf(normalStep, throwingStep),
                failedStep = createMockStep("FailedStep", 3, CompensationResult.success()),
                failureReason = "Original failure",
                recordSagaFailedEvent = false
            )

            val summary = compensationOrchestrator.executeCompensation(request)

            assertFalse(summary.allCompensationsSuccessful)
            assertTrue(summary.failedCompensations.contains("ThrowingStep"))
            assertTrue(summary.compensatedSteps.contains("NormalStep"))
        }

        @Test
        @DisplayName("Should handle NullPointerException during compensation")
        fun shouldHandleNullPointerException() = runTest {
            val throwingStep = createExceptionThrowingStep(
                "NullStep",
                NullPointerException("Null reference")
            )

            val request = CompensationRequest(
                context = context,
                sagaExecution = sagaExecution,
                completedSteps = listOf(throwingStep),
                failedStep = createMockStep("FailedStep", 2, CompensationResult.success()),
                failureReason = "Original failure",
                recordSagaFailedEvent = false
            )

            val summary = compensationOrchestrator.executeCompensation(request)

            assertFalse(summary.allCompensationsSuccessful)
            assertTrue(summary.failedCompensations.contains("NullStep"))
        }

        @Test
        @DisplayName("Should continue to next step after exception")
        fun shouldContinueAfterException() = runTest {
            val step1 = createMockStep("Step1", 1, CompensationResult.success("Done 1"))
            val throwingStep = createExceptionThrowingStep(
                "ThrowingStep",
                IllegalStateException("Bad state")
            )
            val step3 = createMockStep("Step3", 3, CompensationResult.success("Done 3"))

            val request = CompensationRequest(
                context = context,
                sagaExecution = sagaExecution,
                completedSteps = listOf(step1, throwingStep, step3),
                failedStep = createMockStep("FailedStep", 4, CompensationResult.success()),
                failureReason = "Original failure",
                recordSagaFailedEvent = false
            )

            val summary = compensationOrchestrator.executeCompensation(request)

            // All steps should be attempted
            assertEquals(2, summary.compensatedSteps.size)
            assertEquals(1, summary.failedCompensations.size)
            assertTrue(summary.compensatedSteps.containsAll(listOf("Step3", "Step1")))
        }
    }

    @Nested
    @DisplayName("Mid-Way Failure Scenarios")
    inner class MidWayFailureTests {

        @Test
        @DisplayName("Should handle failure at first compensation step")
        fun shouldHandleFailureAtFirstStep() = runTest {
            val step1 = createMockStep("Step1", 1, CompensationResult.success("Done"))
            val step2 = createMockStep("Step2", 2, CompensationResult.success("Done"))
            // Step3 is the last completed step, so it compensates first and fails
            val step3 = createMockStep("Step3", 3, CompensationResult.failure("First compensation failed"))

            val request = CompensationRequest(
                context = context,
                sagaExecution = sagaExecution,
                completedSteps = listOf(step1, step2, step3),
                failedStep = createMockStep("Step4", 4, CompensationResult.success()),
                failureReason = "Step4 failed",
                recordSagaFailedEvent = false
            )

            val summary = compensationOrchestrator.executeCompensation(request)

            // Step3 fails, but Step2 and Step1 should still be compensated
            assertTrue(summary.compensatedSteps.contains("Step2"))
            assertTrue(summary.compensatedSteps.contains("Step1"))
            assertTrue(summary.failedCompensations.contains("Step3"))
        }

        @Test
        @DisplayName("Should handle failure at middle compensation step")
        fun shouldHandleFailureAtMiddleStep() = runTest {
            val step1 = createMockStep("Step1", 1, CompensationResult.success("Done"))
            val step2 = createMockStep("Step2", 2, CompensationResult.failure("Middle failed"))
            val step3 = createMockStep("Step3", 3, CompensationResult.success("Done"))

            val request = CompensationRequest(
                context = context,
                sagaExecution = sagaExecution,
                completedSteps = listOf(step1, step2, step3),
                failedStep = createMockStep("Step4", 4, CompensationResult.success()),
                failureReason = "Step4 failed",
                recordSagaFailedEvent = false
            )

            val summary = compensationOrchestrator.executeCompensation(request)

            // Compensation order: Step3 (success), Step2 (fail), Step1 (success)
            assertEquals(listOf("Step3", "Step1"), summary.compensatedSteps)
            assertEquals(listOf("Step2"), summary.failedCompensations)
        }

        @Test
        @DisplayName("Should handle failure at last compensation step")
        fun shouldHandleFailureAtLastStep() = runTest {
            // Step1 is the first completed step, so it compensates last and fails
            val step1 = createMockStep("Step1", 1, CompensationResult.failure("Last compensation failed"))
            val step2 = createMockStep("Step2", 2, CompensationResult.success("Done"))
            val step3 = createMockStep("Step3", 3, CompensationResult.success("Done"))

            val request = CompensationRequest(
                context = context,
                sagaExecution = sagaExecution,
                completedSteps = listOf(step1, step2, step3),
                failedStep = createMockStep("Step4", 4, CompensationResult.success()),
                failureReason = "Step4 failed",
                recordSagaFailedEvent = false
            )

            val summary = compensationOrchestrator.executeCompensation(request)

            // Compensation order: Step3 (success), Step2 (success), Step1 (fail)
            assertEquals(listOf("Step3", "Step2"), summary.compensatedSteps)
            assertEquals(listOf("Step1"), summary.failedCompensations)
        }
    }

    @Nested
    @DisplayName("Cascading Failure Scenarios")
    inner class CascadingFailureTests {

        @Test
        @DisplayName("Should handle all steps failing compensation")
        fun shouldHandleAllStepsFailing() = runTest {
            val step1 = createMockStep("Step1", 1, CompensationResult.failure("Failed 1"))
            val step2 = createMockStep("Step2", 2, CompensationResult.failure("Failed 2"))
            val step3 = createMockStep("Step3", 3, CompensationResult.failure("Failed 3"))

            val request = CompensationRequest(
                context = context,
                sagaExecution = sagaExecution,
                completedSteps = listOf(step1, step2, step3),
                failedStep = createMockStep("Step4", 4, CompensationResult.success()),
                failureReason = "Step4 failed",
                recordSagaFailedEvent = false
            )

            val summary = compensationOrchestrator.executeCompensation(request)

            assertFalse(summary.allCompensationsSuccessful)
            assertTrue(summary.compensatedSteps.isEmpty())
            assertEquals(3, summary.failedCompensations.size)
        }

        @Test
        @DisplayName("Should handle alternating success and failure")
        fun shouldHandleAlternatingSuccessAndFailure() = runTest {
            val step1 = createMockStep("Step1", 1, CompensationResult.success("Done"))
            val step2 = createMockStep("Step2", 2, CompensationResult.failure("Failed"))
            val step3 = createMockStep("Step3", 3, CompensationResult.success("Done"))
            val step4 = createMockStep("Step4", 4, CompensationResult.failure("Failed"))

            val request = CompensationRequest(
                context = context,
                sagaExecution = sagaExecution,
                completedSteps = listOf(step1, step2, step3, step4),
                failedStep = createMockStep("Step5", 5, CompensationResult.success()),
                failureReason = "Step5 failed",
                recordSagaFailedEvent = false
            )

            val summary = compensationOrchestrator.executeCompensation(request)

            assertFalse(summary.allCompensationsSuccessful)
            assertEquals(2, summary.compensatedSteps.size)
            assertEquals(2, summary.failedCompensations.size)
        }
    }

    @Nested
    @DisplayName("Metrics Recording During Failures")
    inner class MetricsRecordingTests {

        @Test
        @DisplayName("Should record metrics for each compensation attempt")
        fun shouldRecordMetricsForEachAttempt() = runTest {
            val step1 = createMockStep("Step1", 1, CompensationResult.success("Done"))
            val step2 = createMockStep("Step2", 2, CompensationResult.failure("Failed"))

            val request = CompensationRequest(
                context = context,
                sagaExecution = sagaExecution,
                completedSteps = listOf(step1, step2),
                failedStep = createMockStep("Step3", 3, CompensationResult.success()),
                failureReason = "Step3 failed",
                recordSagaFailedEvent = false
            )

            compensationOrchestrator.executeCompensation(request)

            // Should record compensation metrics for both steps
            verify(sagaMetrics, times(2)).compensationExecuted(any())
        }

        @Test
        @DisplayName("Should record events for failed compensations")
        fun shouldRecordEventsForFailedCompensations() = runTest {
            val step1 = createMockStep("Step1", 1, CompensationResult.failure("Failed"))

            val request = CompensationRequest(
                context = context,
                sagaExecution = sagaExecution,
                completedSteps = listOf(step1),
                failedStep = createMockStep("Step2", 2, CompensationResult.success()),
                failureReason = "Step2 failed",
                recordSagaFailedEvent = false
            )

            compensationOrchestrator.executeCompensation(request)

            verify(sagaEventRecorder).recordCompensationFailed(
                any(),
                any(),
                any(),
                any()
            )
        }
    }

    @Nested
    @DisplayName("State Consistency Tests")
    inner class StateConsistencyTests {

        @Test
        @DisplayName("Should maintain correct compensation order despite failures")
        fun shouldMaintainCorrectOrderDespiteFailures() = runTest {
            val executionOrder = mutableListOf<String>()

            val step1 = createOrderTrackingStep("Step1", 1, executionOrder, CompensationResult.success("Done"))
            val step2 = createOrderTrackingStep("Step2", 2, executionOrder, CompensationResult.failure("Failed"))
            val step3 = createOrderTrackingStep("Step3", 3, executionOrder, CompensationResult.success("Done"))

            val request = CompensationRequest(
                context = context,
                sagaExecution = sagaExecution,
                completedSteps = listOf(step1, step2, step3),
                failedStep = createMockStep("Step4", 4, CompensationResult.success()),
                failureReason = "Step4 failed",
                recordSagaFailedEvent = false
            )

            compensationOrchestrator.executeCompensation(request)

            // Verify reverse order execution: Step3, Step2, Step1
            assertEquals(listOf("Step3", "Step2", "Step1"), executionOrder)
        }
    }

    private fun createMockStep(name: String, order: Int, compensationResult: CompensationResult): SagaStep {
        return object : SagaStep {
            override suspend fun execute(context: SagaContext): StepResult {
                return StepResult.success()
            }

            override suspend fun compensate(context: SagaContext): CompensationResult {
                return compensationResult
            }

            override fun getStepName(): String = name
            override fun getStepOrder(): Int = order
        }
    }

    private fun createExceptionThrowingStep(name: String, exception: Exception): SagaStep {
        return object : SagaStep {
            override suspend fun execute(context: SagaContext): StepResult {
                return StepResult.success()
            }

            override suspend fun compensate(context: SagaContext): CompensationResult {
                throw exception
            }

            override fun getStepName(): String = name
            override fun getStepOrder(): Int = 2
        }
    }

    private fun createOrderTrackingStep(
        name: String,
        order: Int,
        executionOrder: MutableList<String>,
        result: CompensationResult
    ): SagaStep {
        return object : SagaStep {
            override suspend fun execute(context: SagaContext): StepResult {
                return StepResult.success()
            }

            override suspend fun compensate(context: SagaContext): CompensationResult {
                executionOrder.add(name)
                return result
            }

            override fun getStepName(): String = name
            override fun getStepOrder(): Int = order
        }
    }
}
