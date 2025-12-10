package com.pintailconsultingllc.sagapattern.progress

import com.pintailconsultingllc.sagapattern.repository.OrderRepository
import com.pintailconsultingllc.sagapattern.repository.SagaExecutionRepository
import com.pintailconsultingllc.sagapattern.repository.SagaStepResultRepository
import com.pintailconsultingllc.sagapattern.saga.SagaStepRegistry
import io.micrometer.observation.annotation.Observed
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Service for retrieving order progress information.
 *
 * Aggregates data from saga execution and step result tables
 * to provide a unified view of order processing status.
 */
@Service
class OrderProgressService(
    private val orderRepository: OrderRepository,
    private val sagaExecutionRepository: SagaExecutionRepository,
    private val sagaStepResultRepository: SagaStepResultRepository,
    private val sagaStepRegistry: SagaStepRegistry
) {
    private val logger = LoggerFactory.getLogger(OrderProgressService::class.java)

    /**
     * Get the progress of an order's saga execution.
     *
     * @param orderId The order to get progress for
     * @return OrderProgress if order exists and has saga execution, null otherwise
     */
    @Observed(name = "order.progress.get", contextualName = "get-order-progress")
    suspend fun getProgress(orderId: UUID): OrderProgress? {
        logger.debug("Getting progress for order {}", orderId)

        // Verify order exists
        val order = orderRepository.findById(orderId)
        if (order == null) {
            logger.debug("Order {} not found", orderId)
            return null
        }

        // Get saga execution for the order
        val sagaExecution = sagaExecutionRepository.findByOrderId(orderId)
        if (sagaExecution == null) {
            logger.debug("No saga execution found for order {}", orderId)
            // Order exists but saga hasn't started - return queued status
            return OrderProgress.initial(orderId, sagaStepRegistry.getStepNames())
        }

        // Get all step results for the saga execution
        val stepResults = sagaStepResultRepository
            .findBySagaExecutionIdOrderByStepOrder(sagaExecution.id)

        // Build and return progress
        return OrderProgress.fromSagaExecution(sagaExecution, stepResults)
    }

    /**
     * Check if an order exists.
     *
     * @param orderId The order to check
     * @return true if order exists
     */
    suspend fun orderExists(orderId: UUID): Boolean {
        return orderRepository.findById(orderId) != null
    }

    /**
     * Check if a customer owns an order.
     *
     * @param orderId The order to check
     * @param customerId The customer to verify
     * @return true if the customer owns the order
     */
    suspend fun isOrderOwner(orderId: UUID, customerId: UUID): Boolean {
        return orderRepository.existsByIdAndCustomerId(orderId, customerId)
    }

}
