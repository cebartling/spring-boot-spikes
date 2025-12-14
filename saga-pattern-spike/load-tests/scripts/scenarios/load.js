/**
 * Load Test Scenario
 *
 * Purpose: Validate system under expected normal production load.
 * Tests system behavior with realistic traffic patterns.
 *
 * Configuration:
 * - Virtual Users: 10-50 (ramping pattern)
 * - Duration: 5 minutes
 * - Stages:
 *   - Ramp up to 50 VUs over 2 minutes
 *   - Hold at 50 VUs for 2 minutes
 *   - Ramp down to 0 over 1 minute
 *
 * Traffic Distribution:
 * - 70% Create orders
 * - 20% Get order status
 * - 10% List customer orders
 *
 * Usage:
 *   k6 run load-tests/scripts/scenarios/load.js
 *   k6 run --env BASE_URL=http://localhost:8080 load-tests/scripts/scenarios/load.js
 */

import { sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { THRESHOLDS, VU_CONFIG, COMMON_TAGS } from '../lib/config.js';
import { createOrder, getOrder, getOrderStatus, getCustomerOrders, getOrderHistory } from '../api/orders.js';
import { checkHealth } from '../api/health.js';
import { randomSleep, weightedRandom } from '../lib/helpers.js';
import { generateCustomerId } from '../lib/data-generators.js';

// Custom metrics
const orderCreatedCounter = new Counter('orders_created');
const orderStatusChecks = new Counter('order_status_checks');
const customerOrdersChecks = new Counter('customer_orders_checks');
const sagaDuration = new Trend('saga_duration_ms');

// Test configuration
export const options = {
    stages: VU_CONFIG.load.stages,
    thresholds: {
        ...THRESHOLDS.load,
        orders_created: ['count>50'],
    },
    tags: {
        ...COMMON_TAGS,
        testType: 'load',
    },
    userAgent: 'k6-load-test/1.0',
};

// Shared state for created orders (per VU)
const vuState = {
    createdOrders: [],
    customerId: null,
};

// Setup function
export function setup() {
    console.log('=== Load Test Starting ===');
    console.log('Configuration:');
    console.log('  - Stages: ramp to 50 VUs, hold, ramp down');
    console.log('  - Duration: ~5 minutes');

    const healthResult = checkHealth();
    if (!healthResult.success) {
        console.error('Application health check failed!');
        throw new Error('Application is not healthy');
    }

    console.log('Application is healthy. Starting load test...');
    return {
        startTime: new Date().toISOString(),
    };
}

// Main test function
export default function (data) {
    // Initialize VU-specific state
    if (!vuState.customerId) {
        vuState.customerId = generateCustomerId();
    }

    // Weighted action selection
    const action = weightedRandom([
        { weight: 70, value: 'createOrder' },
        { weight: 20, value: 'checkStatus' },
        { weight: 10, value: 'listOrders' },
    ]);

    switch (action) {
        case 'createOrder':
            performCreateOrder();
            break;
        case 'checkStatus':
            performStatusCheck();
            break;
        case 'listOrders':
            performListOrders();
            break;
    }

    // Think time between operations
    randomSleep(0.5, 2);
}

/**
 * Create a new order and track it.
 */
function performCreateOrder() {
    const startTime = Date.now();

    const result = createOrder({
        customerId: vuState.customerId,
    });

    if (result.success && result.orderId) {
        const duration = Date.now() - startTime;
        sagaDuration.add(duration);
        orderCreatedCounter.add(1);

        // Keep track of created orders for status checks
        vuState.createdOrders.push(result.orderId);

        // Limit stored orders to prevent memory growth
        if (vuState.createdOrders.length > 10) {
            vuState.createdOrders.shift();
        }
    }
}

/**
 * Check status of a previously created order.
 */
function performStatusCheck() {
    if (vuState.createdOrders.length === 0) {
        // No orders to check, create one instead
        performCreateOrder();
        return;
    }

    // Pick a random order from recent orders
    const orderId = vuState.createdOrders[
        Math.floor(Math.random() * vuState.createdOrders.length)
    ];

    const result = getOrderStatus(orderId);
    if (result.success) {
        orderStatusChecks.add(1);
    }

    // Sometimes also get the full order details
    if (Math.random() < 0.3) {
        getOrder(orderId);
    }
}

/**
 * List orders for the VU's customer.
 */
function performListOrders() {
    const result = getCustomerOrders(vuState.customerId);
    if (result.success) {
        customerOrdersChecks.add(1);
    }
}

// Teardown function
export function teardown(data) {
    console.log('=== Load Test Complete ===');
    console.log(`Test started at: ${data.startTime}`);
    console.log(`Test ended at: ${new Date().toISOString()}`);
}
