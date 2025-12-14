/**
 * Order API test functions for k6.
 *
 * This module provides reusable functions for testing
 * the Order REST API endpoints.
 */

import http from 'k6/http';
import { check } from 'k6';
import { ENDPOINTS, DEFAULT_PARAMS } from '../lib/config.js';
import { parseJson, checkSuccess, checkStatus } from '../lib/helpers.js';
import { generateCreateOrderRequest } from '../lib/data-generators.js';

/**
 * Create a new order.
 *
 * @param {object} orderData - Order creation request (optional, will generate if not provided)
 * @param {object} tags - Additional tags for metrics
 * @returns {object} Response with parsed body
 */
export function createOrder(orderData = null, tags = {}) {
    const payload = JSON.stringify(orderData || generateCreateOrderRequest());
    const params = {
        ...DEFAULT_PARAMS,
        tags: { name: 'create_order', ...tags },
    };

    const response = http.post(ENDPOINTS.orders, payload, params);

    const success = check(response, {
        'create order status is 201': (r) => r.status === 201,
        'create order has orderId': (r) => {
            const body = parseJson(r);
            return body && body.orderId;
        },
    });

    return {
        response,
        success,
        orderId: success ? parseJson(response)?.orderId : null,
        body: parseJson(response),
    };
}

/**
 * Get an order by ID.
 *
 * @param {string} orderId - Order UUID
 * @param {object} tags - Additional tags for metrics
 * @returns {object} Response with parsed body
 */
export function getOrder(orderId, tags = {}) {
    const params = {
        ...DEFAULT_PARAMS,
        tags: { name: 'get_order', ...tags },
    };

    const response = http.get(`${ENDPOINTS.orders}/${orderId}`, params);

    const success = checkSuccess(response, 'get order');

    return {
        response,
        success,
        body: parseJson(response),
    };
}

/**
 * Get order status.
 *
 * @param {string} orderId - Order UUID
 * @param {object} tags - Additional tags for metrics
 * @returns {object} Response with parsed body
 */
export function getOrderStatus(orderId, tags = {}) {
    const params = {
        ...DEFAULT_PARAMS,
        tags: { name: 'get_order_status', ...tags },
    };

    const response = http.get(`${ENDPOINTS.orders}/${orderId}/status`, params);

    const success = check(response, {
        'get status is 200 or 404': (r) => r.status === 200 || r.status === 404,
    });

    return {
        response,
        success,
        body: parseJson(response),
    };
}

/**
 * Get orders for a customer.
 *
 * @param {string} customerId - Customer UUID
 * @param {object} tags - Additional tags for metrics
 * @returns {object} Response with parsed body
 */
export function getCustomerOrders(customerId, tags = {}) {
    const params = {
        ...DEFAULT_PARAMS,
        tags: { name: 'get_customer_orders', ...tags },
    };

    const response = http.get(`${ENDPOINTS.orders}/customer/${customerId}`, params);

    const success = checkSuccess(response, 'get customer orders');

    return {
        response,
        success,
        body: parseJson(response),
    };
}

/**
 * Get order history.
 *
 * @param {string} orderId - Order UUID
 * @param {object} tags - Additional tags for metrics
 * @returns {object} Response with parsed body
 */
export function getOrderHistory(orderId, tags = {}) {
    const params = {
        ...DEFAULT_PARAMS,
        tags: { name: 'get_order_history', ...tags },
    };

    const response = http.get(`${ENDPOINTS.orders}/${orderId}/history`, params);

    const success = check(response, {
        'get history is 200 or 404': (r) => r.status === 200 || r.status === 404,
    });

    return {
        response,
        success,
        body: parseJson(response),
    };
}

/**
 * Get order timeline.
 *
 * @param {string} orderId - Order UUID
 * @param {object} tags - Additional tags for metrics
 * @returns {object} Response with parsed body
 */
export function getOrderTimeline(orderId, tags = {}) {
    const params = {
        ...DEFAULT_PARAMS,
        tags: { name: 'get_order_timeline', ...tags },
    };

    const response = http.get(`${ENDPOINTS.orders}/${orderId}/timeline`, params);

    const success = check(response, {
        'get timeline is 200 or 404': (r) => r.status === 200 || r.status === 404,
    });

    return {
        response,
        success,
        body: parseJson(response),
    };
}

/**
 * Get order events.
 *
 * @param {string} orderId - Order UUID
 * @param {object} tags - Additional tags for metrics
 * @returns {object} Response with parsed body
 */
export function getOrderEvents(orderId, tags = {}) {
    const params = {
        ...DEFAULT_PARAMS,
        tags: { name: 'get_order_events', ...tags },
    };

    const response = http.get(`${ENDPOINTS.orders}/${orderId}/events`, params);

    const success = check(response, {
        'get events is 200 or 404': (r) => r.status === 200 || r.status === 404,
    });

    return {
        response,
        success,
        body: parseJson(response),
    };
}

/**
 * Check retry eligibility for an order.
 *
 * @param {string} orderId - Order UUID
 * @param {object} tags - Additional tags for metrics
 * @returns {object} Response with parsed body
 */
export function checkRetryEligibility(orderId, tags = {}) {
    const params = {
        ...DEFAULT_PARAMS,
        tags: { name: 'check_retry_eligibility', ...tags },
    };

    const response = http.get(`${ENDPOINTS.orders}/${orderId}/retry/eligibility`, params);

    const success = checkSuccess(response, 'check retry eligibility');

    return {
        response,
        success,
        body: parseJson(response),
    };
}

/**
 * Complete order flow: create order, check status, get details.
 * Useful for simulating a complete user journey.
 *
 * @param {object} options - Options for the flow
 * @returns {object} Results of each step
 */
export function completeOrderFlow(options = {}) {
    // Step 1: Create order
    const createResult = createOrder(options.orderData);

    if (!createResult.success || !createResult.orderId) {
        return {
            success: false,
            step: 'create',
            error: 'Failed to create order',
            createResult,
        };
    }

    // Step 2: Get order status
    const statusResult = getOrderStatus(createResult.orderId);

    // Step 3: Get order details
    const detailsResult = getOrder(createResult.orderId);

    return {
        success: createResult.success && statusResult.success && detailsResult.success,
        orderId: createResult.orderId,
        createResult,
        statusResult,
        detailsResult,
    };
}
