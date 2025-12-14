/**
 * Soak Test Scenario
 *
 * Purpose: Detect memory leaks, resource exhaustion, and performance
 * degradation over extended periods of time.
 *
 * Configuration:
 * - Virtual Users: 20-30 (constant moderate load)
 * - Duration: 30+ minutes
 * - Stages:
 *   - Ramp up to 30 VUs over 2 minutes
 *   - Hold at 30 VUs for 26 minutes
 *   - Ramp down over 2 minutes
 *
 * Key Observations:
 * - Response time trends (should not increase over time)
 * - Memory usage patterns
 * - Database connection pool stability
 * - Error rate trends
 *
 * Usage:
 *   k6 run load-tests/scripts/scenarios/soak.js
 *   k6 run --env BASE_URL=http://localhost:8080 load-tests/scripts/scenarios/soak.js
 *
 * For longer soak tests:
 *   k6 run --env SOAK_DURATION=60m load-tests/scripts/scenarios/soak.js
 */

import { sleep, check } from 'k6';
import { Counter, Trend, Rate, Gauge } from 'k6/metrics';
import { THRESHOLDS, COMMON_TAGS } from '../lib/config.js';
import { createOrder, getOrder, getOrderStatus, getCustomerOrders, getOrderHistory } from '../api/orders.js';
import { checkHealth, getPrometheusMetrics } from '../api/health.js';
import { randomSleep, weightedRandom } from '../lib/helpers.js';
import { generateCustomerId, getVUCustomerId } from '../lib/data-generators.js';

// Custom metrics for soak test analysis
const orderCreatedCounter = new Counter('soak_orders_created');
const totalOperations = new Counter('soak_total_operations');
const successRate = new Rate('soak_success_rate');
const orderDuration = new Trend('soak_order_duration_ms');
const readDuration = new Trend('soak_read_duration_ms');
const healthCheckDuration = new Trend('soak_health_check_ms');

// Parse duration from environment or use default
const soakDuration = __ENV.SOAK_DURATION || '26m';

// Test configuration
export const options = {
    stages: [
        { duration: '2m', target: 30 },      // Ramp up
        { duration: soakDuration, target: 30 }, // Sustained load
        { duration: '2m', target: 0 },       // Ramp down
    ],
    thresholds: {
        ...THRESHOLDS.soak,
        soak_success_rate: ['rate>0.95'], // Higher success rate expected during soak
        // Response time should not degrade - checked manually via trends
    },
    tags: {
        ...COMMON_TAGS,
        testType: 'soak',
    },
    userAgent: 'k6-soak-test/1.0',
};

// VU-local state with consistent customer per VU
let vuCustomerId = null;
let vuOrders = [];
let iterationCount = 0;
let healthCheckInterval = 0;

// Setup function
export function setup() {
    console.log('=== Soak Test Starting ===');
    console.log('Purpose: Detect memory leaks and performance degradation');
    console.log('Configuration:');
    console.log(`  - Sustained VUs: 30`);
    console.log(`  - Soak duration: ${soakDuration}`);
    console.log('  - Periodic health checks enabled');

    const healthResult = checkHealth();
    if (!healthResult.success) {
        console.error('Application health check failed!');
        throw new Error('Application is not healthy');
    }

    console.log('Application is healthy. Starting soak test...');
    console.log('Monitor Grafana dashboards for memory and performance trends.');

    return {
        startTime: new Date().toISOString(),
        initialHealth: healthResult.body,
    };
}

// Main test function
export default function (data) {
    // Initialize VU state
    if (!vuCustomerId) {
        vuCustomerId = getVUCustomerId(__VU);
    }

    iterationCount++;
    healthCheckInterval++;

    // Periodic health check (every ~100 iterations per VU)
    if (healthCheckInterval >= 100) {
        performHealthCheck();
        healthCheckInterval = 0;
    }

    // Weighted action selection - balanced for sustained operation
    const action = weightedRandom([
        { weight: 50, value: 'createOrder' },
        { weight: 25, value: 'readOperations' },
        { weight: 15, value: 'statusCheck' },
        { weight: 10, value: 'historyCheck' },
    ]);

    switch (action) {
        case 'createOrder':
            performCreateOrder();
            break;
        case 'readOperations':
            performReadOperations();
            break;
        case 'statusCheck':
            performStatusCheck();
            break;
        case 'historyCheck':
            performHistoryCheck();
            break;
    }

    totalOperations.add(1);

    // Moderate think time for realistic sustained load
    randomSleep(1, 3);
}

/**
 * Create order during soak test.
 */
function performCreateOrder() {
    const startTime = Date.now();

    const result = createOrder({
        customerId: vuCustomerId,
    });

    const duration = Date.now() - startTime;
    orderDuration.add(duration);

    if (result.success && result.orderId) {
        orderCreatedCounter.add(1);
        successRate.add(1);

        // Track orders for subsequent reads
        vuOrders.push(result.orderId);

        // Keep a rolling window of orders
        if (vuOrders.length > 20) {
            vuOrders.shift();
        }
    } else {
        successRate.add(0);
    }
}

/**
 * Perform read operations (get order, list orders).
 */
function performReadOperations() {
    const startTime = Date.now();

    // List customer orders
    const listResult = getCustomerOrders(vuCustomerId);
    successRate.add(listResult.success ? 1 : 0);

    // Get specific order if available
    if (vuOrders.length > 0) {
        const orderId = vuOrders[Math.floor(Math.random() * vuOrders.length)];
        const getResult = getOrder(orderId);
        successRate.add(getResult.success ? 1 : 0);
    }

    const duration = Date.now() - startTime;
    readDuration.add(duration);
}

/**
 * Check status of existing orders.
 */
function performStatusCheck() {
    if (vuOrders.length === 0) {
        performCreateOrder();
        return;
    }

    const startTime = Date.now();
    const orderId = vuOrders[Math.floor(Math.random() * vuOrders.length)];

    const result = getOrderStatus(orderId);
    successRate.add(result.success ? 1 : 0);

    const duration = Date.now() - startTime;
    readDuration.add(duration);
}

/**
 * Check order history.
 */
function performHistoryCheck() {
    if (vuOrders.length === 0) {
        performCreateOrder();
        return;
    }

    const startTime = Date.now();
    const orderId = vuOrders[Math.floor(Math.random() * vuOrders.length)];

    const result = getOrderHistory(orderId);
    successRate.add(result.success ? 1 : 0);

    const duration = Date.now() - startTime;
    readDuration.add(duration);
}

/**
 * Periodic health check during soak test.
 */
function performHealthCheck() {
    const startTime = Date.now();
    const result = checkHealth();

    const duration = Date.now() - startTime;
    healthCheckDuration.add(duration);

    if (!result.success) {
        console.warn(`Health check failed at iteration ${iterationCount}`);
    }
}

// Teardown function
export function teardown(data) {
    console.log('=== Soak Test Complete ===');
    console.log(`Test started at: ${data.startTime}`);
    console.log(`Test ended at: ${new Date().toISOString()}`);

    // Calculate test duration
    const startMs = new Date(data.startTime).getTime();
    const endMs = new Date().getTime();
    const durationMinutes = ((endMs - startMs) / 1000 / 60).toFixed(2);
    console.log(`Total duration: ${durationMinutes} minutes`);

    // Final health check
    console.log('Performing final health check...');
    const healthResult = checkHealth();
    if (healthResult.success) {
        console.log('Final health check: PASSED');
    } else {
        console.error('Final health check: FAILED');
        console.error('System may have degraded during soak test!');
    }

    console.log('\nReview Grafana dashboards for:');
    console.log('  - Memory usage trends');
    console.log('  - Response time degradation');
    console.log('  - Database connection pool');
    console.log('  - GC activity');
}
