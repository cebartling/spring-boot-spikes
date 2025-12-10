package com.pintailconsultingllc.sagapattern.api

import com.pintailconsultingllc.sagapattern.api.dto.CreateOrderRequest
import com.pintailconsultingllc.sagapattern.api.dto.OrderResponse
import com.pintailconsultingllc.sagapattern.api.dto.OrderStatusResponse
import com.pintailconsultingllc.sagapattern.progress.OrderProgressService
import com.pintailconsultingllc.sagapattern.service.OrderCreationResult
import com.pintailconsultingllc.sagapattern.service.OrderService
import io.micrometer.observation.annotation.Observed
import kotlinx.coroutines.reactor.mono
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * Reactive REST controller for order management using Spring WebFlux.
 */
@RestController
@RequestMapping("/api/orders")
class OrderController(
    private val orderService: OrderService,
    private val orderProgressService: OrderProgressService
) {
    private val logger = LoggerFactory.getLogger(OrderController::class.java)

    /**
     * Create a new order and process it through the saga.
     *
     * @param request The order creation request
     * @return Mono with order details or failure information
     */
    @PostMapping
    @Observed(name = "http.order.create", contextualName = "post-orders")
    fun createOrder(@RequestBody request: CreateOrderRequest): Mono<ResponseEntity<Any>> {
        logger.info("Received order creation request for customer ${request.customerId}")

        return mono {
            when (val result = orderService.createOrder(request)) {
                is OrderCreationResult.Success -> {
                    logger.info("Order created successfully: ${result.response.orderId}")
                    ResponseEntity
                        .status(HttpStatus.CREATED)
                        .body(result.response as Any)
                }
                is OrderCreationResult.Failure -> {
                    logger.warn("Order creation failed: ${result.response.error.message}")
                    ResponseEntity
                        .status(HttpStatus.UNPROCESSABLE_ENTITY)
                        .body(result.response as Any)
                }
            }
        }
    }

    /**
     * Get an order by ID.
     *
     * @param orderId The order ID to retrieve
     * @return Mono with order details or 404 if not found
     */
    @GetMapping("/{orderId}")
    @Observed(name = "http.order.get", contextualName = "get-order")
    fun getOrder(@PathVariable orderId: UUID): Mono<ResponseEntity<OrderResponse>> {
        logger.info("Retrieving order: $orderId")

        return mono {
            val order = orderService.getOrder(orderId)
            if (order != null) {
                ResponseEntity.ok(OrderResponse.fromOrder(order))
            } else {
                ResponseEntity.notFound().build()
            }
        }
    }

    /**
     * Get all orders for a customer.
     *
     * @param customerId The customer ID
     * @return Flux of orders for the customer
     */
    @GetMapping("/customer/{customerId}")
    @Observed(name = "http.order.list", contextualName = "list-orders")
    fun getOrdersForCustomer(@PathVariable customerId: UUID): Mono<ResponseEntity<List<OrderResponse>>> {
        logger.info("Retrieving orders for customer: $customerId")

        return mono {
            val orders = orderService.getOrdersForCustomer(customerId)
            ResponseEntity.ok(orders.map { OrderResponse.fromOrder(it) })
        }
    }

    /**
     * Get the processing status of an order.
     *
     * Provides real-time visibility into the saga execution progress,
     * including individual step statuses and overall completion state.
     *
     * @param orderId The order ID to get status for
     * @return Mono with order status or 404 if not found
     */
    @GetMapping("/{orderId}/status")
    @Observed(name = "http.order.status", contextualName = "get-order-status")
    fun getOrderStatus(@PathVariable orderId: UUID): Mono<ResponseEntity<OrderStatusResponse>> {
        logger.info("Retrieving status for order: $orderId")

        return mono {
            val progress = orderProgressService.getProgress(orderId)
            if (progress != null) {
                ResponseEntity.ok(OrderStatusResponse.fromOrderProgress(progress))
            } else {
                ResponseEntity.notFound().build()
            }
        }
    }
}
