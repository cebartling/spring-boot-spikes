package com.pintailconsultingllc.sagapattern.saga

/**
 * Contract for saga execution orchestration.
 *
 * Implementations coordinate the execution of saga steps,
 * handle failures, and trigger compensation when necessary.
 *
 * This interface enables:
 * - Swapping implementations for testing
 * - Adding new orchestrator types (e.g., for different saga patterns)
 * - Applying cross-cutting concerns uniformly via decorators
 * - Cleaner dependency injection
 */
interface SagaOrchestrator {

    /**
     * Execute a saga for the given context.
     *
     * The saga execution follows a three-phase model:
     * 1. Initialization - Create execution records and update order status
     * 2. Step execution - Execute each step in sequence
     * 3. Finalization - Update final saga/order state based on outcome
     *
     * If any step fails, compensation is triggered for completed steps.
     *
     * @param context The saga context with order and metadata
     * @return The saga result indicating success, failure, or compensation status
     */
    suspend fun executeSaga(context: SagaContext): SagaResult
}
