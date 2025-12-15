package com.pintailconsultingllc.sagapattern.retry

import java.util.UUID

/**
 * Contract for retry orchestration of failed saga executions.
 *
 * Implementations manage the retry of sagas by resuming from the failed step
 * while optionally skipping steps whose results are still valid.
 *
 * This interface enables:
 * - Swapping implementations for testing
 * - Adding different retry strategies
 * - Applying cross-cutting concerns uniformly via decorators
 * - Cleaner dependency injection
 */
interface RetryableOrchestrator {

    /**
     * Execute a retry operation for a failed order.
     *
     * The retry operation:
     * 1. Checks retry eligibility
     * 2. Determines the resume point based on previous execution
     * 3. Executes remaining steps (skipping valid completed steps)
     * 4. Handles any new failures with compensation
     *
     * @param orderId The order to retry
     * @param request The retry request with optional updates (payment method, shipping address)
     * @return The retry result indicating success, failure, compensation, or ineligibility
     */
    suspend fun executeRetry(orderId: UUID, request: RetryRequest): SagaRetryResult
}
