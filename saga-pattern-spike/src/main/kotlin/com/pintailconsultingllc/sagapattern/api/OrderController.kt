package com.pintailconsultingllc.sagapattern.api

import com.pintailconsultingllc.sagapattern.api.dto.CreateOrderRequest
import com.pintailconsultingllc.sagapattern.api.dto.OrderFailureResponse
import com.pintailconsultingllc.sagapattern.api.dto.OrderResponse
import com.pintailconsultingllc.sagapattern.service.OrderCreationResult
import com.pintailconsultingllc.sagapattern.service.OrderService
import io.micrometer.observation.annotation.Observed
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * REST controller for order management.
 */
@RestController
@RequestMapping("/api/orders")
class OrderController(
    private val orderService: OrderService
) {
    private val logger = LoggerFactory.getLogger(OrderController::class.java)

    /**
     * Create a new order and process it through the saga.
     *
     * @param request The order creation request
     * @return Response with order details or failure information
     */
    @PostMapping
    @Observed(name = "http.order.create", contextualName = "post-orders")
    suspend fun createOrder(@RequestBody request: CreateOrderRequest): ResponseEntity<Any> {
        logger.info("Received order creation request for customer ${request.customerId}")

        return when (val result = orderService.createOrder(request)) {
            is OrderCreationResult.Success -> {
                logger.info("Order created successfully: ${result.response.orderId}")
                ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(result.response)
            }
            is OrderCreationResult.Failure -> {
                logger.warn("Order creation failed: ${result.response.failureReason}")
                ResponseEntity
                    .status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(result.response)
            }
        }
    }

    /**
     * Get an order by ID.
     *
     * @param orderId The order ID to retrieve
     * @return The order details or 404 if not found
     */
    @GetMapping("/{orderId}")
    @Observed(name = "http.order.get", contextualName = "get-order")
    suspend fun getOrder(@PathVariable orderId: UUID): ResponseEntity<OrderResponse> {
        logger.info("Retrieving order: $orderId")

        val order = orderService.getOrder(orderId)
            ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok(OrderResponse.fromOrder(order))
    }

    /**
     * Get all orders for a customer.
     *
     * @param customerId The customer ID
     * @return List of orders for the customer
     */
    @GetMapping("/customer/{customerId}")
    @Observed(name = "http.order.list", contextualName = "list-orders")
    suspend fun getOrdersForCustomer(@PathVariable customerId: UUID): ResponseEntity<List<OrderResponse>> {
        logger.info("Retrieving orders for customer: $customerId")

        val orders = orderService.getOrdersForCustomer(customerId)

        return ResponseEntity.ok(orders.map { OrderResponse.fromOrder(it) })
    }
}
