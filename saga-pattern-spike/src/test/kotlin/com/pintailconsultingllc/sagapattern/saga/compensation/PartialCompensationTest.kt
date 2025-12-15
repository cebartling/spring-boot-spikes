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
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for partial compensation scenarios.
 *
 * These tests verify behavior when some compensation actions succeed
 * while others fail, ensuring proper tracking and reporting.
 */
@Tag("unit")
@DisplayName("Partial Compensation Tests")
class PartialCompensationTest {

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
    @DisplayName("Single Step Compensation Failure")
    inner class SingleStepCompensationFailure {

        @Test
        @DisplayName("Should report partial success when one of two compensations fails")
        fun shouldReportPartialSuccessWhenOneCompensationFails() = runTest {
            val successStep = createMockStep("Step1", 1, CompensationResult.success("Compensated"))
            val failureStep = createMockStep("Step2", 2, CompensationResult.failure("Compensation failed"))

            val request = CompensationRequest(
                context = context,
                sagaExecution = sagaExecution,
                completedSteps = listOf(successStep, failureStep),
                failedStep = createMockStep("Step3", 3, CompensationResult.success()),
                failureReason = "Step3 failed",
                recordSagaFailedEvent = false
            )

            val summary = compensationOrchestrator.executeCompensation(request)

            assertFalse(summary.allCompensationsSuccessful)
            // Step1 returns success -> compensatedSteps, Step2 returns failure -> failedCompensations
            assertEquals(listOf("Step1"), summary.compensatedSteps)
            assertEquals(listOf("Step2"), summary.failedCompensations)
        }

        @Test
        @DisplayName("Should continue compensating remaining steps after one fails")
        fun shouldContinueCompensatingAfterFailure() = runTest {
            val step1 = createMockStep("Step1", 1, CompensationResult.success("Compensated 1"))
            val step2 = createMockStep("Step2", 2, CompensationResult.failure("Failed"))
            val step3 = createMockStep("Step3", 3, CompensationResult.success("Compensated 3"))

            val request = CompensationRequest(
                context = context,
                sagaExecution = sagaExecution,
                completedSteps = listOf(step1, step2, step3),
                failedStep = createMockStep("Step4", 4, CompensationResult.success()),
                failureReason = "Step4 failed",
                recordSagaFailedEvent = false
            )

            val summary = compensationOrchestrator.executeCompensation(request)

            // All steps should have been attempted (compensate called on each)
            // Step3 compensates first (reverse order), then Step2, then Step1
            assertEquals(listOf("Step3", "Step1"), summary.compensatedSteps)
            assertEquals(listOf("Step2"), summary.failedCompensations)
        }
    }

    @Nested
    @DisplayName("Multiple Step Compensation Failures")
    inner class MultipleStepCompensationFailures {

        @Test
        @DisplayName("Should track all failures when multiple compensations fail")
        fun shouldTrackAllFailures() = runTest {
            val step1 = createMockStep("Step1", 1, CompensationResult.failure("Failed 1"))
            val step2 = createMockStep("Step2", 2, CompensationResult.failure("Failed 2"))
            val step3 = createMockStep("Step3", 3, CompensationResult.success("Compensated"))

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
            assertEquals(listOf("Step3"), summary.compensatedSteps)
            assertTrue(summary.failedCompensations.containsAll(listOf("Step1", "Step2")))
        }

        @Test
        @DisplayName("Should report no successful compensations when all fail")
        fun shouldReportNoSuccessWhenAllFail() = runTest {
            val step1 = createMockStep("Step1", 1, CompensationResult.failure("Failed 1"))
            val step2 = createMockStep("Step2", 2, CompensationResult.failure("Failed 2"))

            val request = CompensationRequest(
                context = context,
                sagaExecution = sagaExecution,
                completedSteps = listOf(step1, step2),
                failedStep = createMockStep("Step3", 3, CompensationResult.success()),
                failureReason = "Step3 failed",
                recordSagaFailedEvent = false
            )

            val summary = compensationOrchestrator.executeCompensation(request)

            assertFalse(summary.allCompensationsSuccessful)
            assertTrue(summary.compensatedSteps.isEmpty())
            assertEquals(2, summary.failedCompensations.size)
        }
    }

    @Nested
    @DisplayName("Compensation Summary")
    inner class CompensationSummaryTests {

        @Test
        @DisplayName("Should create summary with correct failed step information")
        fun shouldCreateSummaryWithCorrectFailedStepInfo() = runTest {
            val step1 = createMockStep("Inventory", 1, CompensationResult.success("Released"))

            val request = CompensationRequest(
                context = context,
                sagaExecution = sagaExecution,
                completedSteps = listOf(step1),
                failedStep = createMockStep("Payment", 2, CompensationResult.success()),
                failureReason = "Payment declined",
                recordSagaFailedEvent = false
            )

            val summary = compensationOrchestrator.executeCompensation(request)

            assertEquals("Payment", summary.failedStep)
            assertEquals("Payment declined", summary.failureReason)
        }

        @Test
        @DisplayName("Should include all compensated steps in order")
        fun shouldIncludeAllCompensatedStepsInOrder() = runTest {
            val step1 = createMockStep("Step1", 1, CompensationResult.success("Done 1"))
            val step2 = createMockStep("Step2", 2, CompensationResult.success("Done 2"))
            val step3 = createMockStep("Step3", 3, CompensationResult.success("Done 3"))

            val request = CompensationRequest(
                context = context,
                sagaExecution = sagaExecution,
                completedSteps = listOf(step1, step2, step3),
                failedStep = createMockStep("Step4", 4, CompensationResult.success()),
                failureReason = "Step4 failed",
                recordSagaFailedEvent = false
            )

            val summary = compensationOrchestrator.executeCompensation(request)

            assertTrue(summary.allCompensationsSuccessful)
            // Compensation happens in reverse order
            assertEquals(listOf("Step3", "Step2", "Step1"), summary.compensatedSteps)
            assertTrue(summary.failedCompensations.isEmpty())
        }
    }

    @Nested
    @DisplayName("Event Recording")
    inner class EventRecordingTests {

        @Test
        @DisplayName("Should record compensation failed event for failed steps")
        fun shouldRecordCompensationFailedEvent() = runTest {
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

        @Test
        @DisplayName("Should record step compensated event for successful steps")
        fun shouldRecordStepCompensatedEvent() = runTest {
            val step1 = createMockStep("Step1", 1, CompensationResult.success("Done"))

            val request = CompensationRequest(
                context = context,
                sagaExecution = sagaExecution,
                completedSteps = listOf(step1),
                failedStep = createMockStep("Step2", 2, CompensationResult.success()),
                failureReason = "Step2 failed",
                recordSagaFailedEvent = false
            )

            compensationOrchestrator.executeCompensation(request)

            verify(sagaEventRecorder).recordStepCompensated(
                context.order.id,
                sagaExecution.id,
                "Step1"
            )
        }

        @Test
        @DisplayName("Should not record saga failed event when flag is false")
        fun shouldNotRecordSagaFailedWhenFlagIsFalse() = runTest {
            val step1 = createMockStep("Step1", 1, CompensationResult.success("Done"))

            val request = CompensationRequest(
                context = context,
                sagaExecution = sagaExecution,
                completedSteps = listOf(step1),
                failedStep = createMockStep("Step2", 2, CompensationResult.success()),
                failureReason = "Step2 failed",
                recordSagaFailedEvent = false
            )

            compensationOrchestrator.executeCompensation(request)

            verify(sagaEventRecorder, never()).recordSagaFailed(any(), any(), any(), any())
        }

        @Test
        @DisplayName("Should record saga failed event when flag is true")
        fun shouldRecordSagaFailedWhenFlagIsTrue() = runTest {
            val step1 = createMockStep("Step1", 1, CompensationResult.success("Done"))

            val request = CompensationRequest(
                context = context,
                sagaExecution = sagaExecution,
                completedSteps = listOf(step1),
                failedStep = createMockStep("Step2", 2, CompensationResult.success()),
                failureReason = "Step2 failed",
                recordSagaFailedEvent = true
            )

            compensationOrchestrator.executeCompensation(request)

            verify(sagaEventRecorder).recordSagaFailed(
                any(),
                any(),
                any(),
                any()
            )
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
}
