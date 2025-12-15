package com.pintailconsultingllc.sagapattern.service

import com.pintailconsultingllc.sagapattern.api.dto.CreateOrderRequest
import com.pintailconsultingllc.sagapattern.api.dto.OrderFailureResponse
import com.pintailconsultingllc.sagapattern.api.dto.OrderResponse
import com.pintailconsultingllc.sagapattern.domain.Order
import com.pintailconsultingllc.sagapattern.domain.OrderItem
import com.pintailconsultingllc.sagapattern.domain.OrderStatus
import com.pintailconsultingllc.sagapattern.domain.ShippingAddress
import com.pintailconsultingllc.sagapattern.history.OrderEventService
import com.pintailconsultingllc.sagapattern.observability.TraceContextService
import com.pintailconsultingllc.sagapattern.repository.OrderItemRepository
import com.pintailconsultingllc.sagapattern.repository.OrderRepository
import com.pintailconsultingllc.sagapattern.saga.SagaContext
import com.pintailconsultingllc.sagapattern.saga.SagaOrchestrator
import com.pintailconsultingllc.sagapattern.saga.SagaResult
import io.micrometer.observation.annotation.Observed
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Service for order management and saga orchestration.
 */
@Service
class OrderService(
    private val orderRepository: OrderRepository,
    private val orderItemRepository: OrderItemRepository,
    private val sagaOrchestrator: SagaOrchestrator,
    private val orderEventService: OrderEventService,
    private val traceContextService: TraceContextService
) {
    private val logger = LoggerFactory.getLogger(OrderService::class.java)

    /**
     * Create a new order and execute the order saga.
     *
     * @param request The order creation request
     * @return Result containing either success or failure response
     */
    @Observed(name = "order.create", contextualName = "create-order")
    @Transactional
    suspend fun createOrder(request: CreateOrderRequest): OrderCreationResult {
        logger.info("Creating order for customer {}", request.customerId)

        // Create the order entity
        val order = Order.create(
            customerId = request.customerId,
            totalAmountInCents = request.calculateTotalInCents(),
            status = OrderStatus.PENDING
        )

        // Save the order
        val savedOrder = orderRepository.save(order)
        logger.info("Order created with ID: {}", savedOrder.id)

        // Record order created event
        orderEventService.recordOrderCreated(savedOrder.id)

        // Create and save order items
        val orderItems = request.items.map { item ->
            OrderItem.create(
                orderId = savedOrder.id,
                productId = item.productId,
                productName = item.productName,
                quantity = item.quantity,
                unitPriceInCents = item.unitPriceInCents
            )
        }
        orderItems.forEach { orderItemRepository.save(it) }

        // Prepare saga context
        val context = SagaContext(
            order = savedOrder.withItems(orderItems),
            sagaExecutionId = UUID.randomUUID(),
            customerId = request.customerId,
            paymentMethodId = request.paymentMethodId,
            shippingAddress = ShippingAddress(
                street = request.shippingAddress.street,
                city = request.shippingAddress.city,
                state = request.shippingAddress.state,
                postalCode = request.shippingAddress.postalCode,
                country = request.shippingAddress.country
            )
        )

        // Execute the saga
        val traceId = traceContextService.getCurrentTraceId()
        return when (val result = sagaOrchestrator.executeSaga(context)) {
            is SagaResult.Success -> {
                logger.info("Order {} completed successfully", savedOrder.id)
                OrderCreationResult.Success(OrderResponse.fromSuccess(result, traceId))
            }
            is SagaResult.Failed -> {
                logger.warn("Order {} failed: {}", savedOrder.id, result.failureReason)
                OrderCreationResult.Failure(OrderFailureResponse.fromFailed(result, traceId))
            }
            is SagaResult.Compensated -> {
                logger.warn("Order {} compensated: {}", savedOrder.id, result.failureReason)
                OrderCreationResult.Failure(OrderFailureResponse.fromCompensated(result, traceId))
            }
            is SagaResult.PartiallyCompensated -> {
                logger.error("Order {} partially compensated: {}", savedOrder.id, result.failureReason)
                OrderCreationResult.Failure(OrderFailureResponse.fromPartiallyCompensated(result, traceId))
            }
        }
    }

    /**
     * Get an order by ID.
     */
    suspend fun getOrder(orderId: UUID): Order? {
        val order = orderRepository.findById(orderId) ?: return null
        val items = orderItemRepository.findByOrderId(orderId)
        return order.withItems(items)
    }

    /**
     * Get all orders for a customer.
     */
    suspend fun getOrdersForCustomer(customerId: UUID): List<Order> {
        return orderRepository.findByCustomerId(customerId)
    }
}

/**
 * Sealed class representing the result of order creation.
 */
sealed class OrderCreationResult {
    data class Success(val response: OrderResponse) : OrderCreationResult()
    data class Failure(val response: OrderFailureResponse) : OrderCreationResult()
}
