/**
 * Stress Test Scenario
 *
 * Purpose: Find system breaking points and observe recovery behavior.
 * Pushes the system beyond normal load to identify limits.
 *
 * Configuration:
 * - Virtual Users: 100-200 (aggressive ramping with spike)
 * - Duration: 10 minutes
 * - Stages:
 *   - Ramp up to 100 VUs over 3 minutes
 *   - Spike to 200 VUs for 1 minute
 *   - Recovery back to 100 VUs over 2 minutes
 *   - Hold at 100 VUs for 2 minutes
 *   - Ramp down over 2 minutes
 *
 * This test is designed to:
 * - Find the breaking point of the system
 * - Observe error rates under extreme load
 * - Verify system recovery after spike
 *
 * Usage:
 *   k6 run load-tests/scripts/scenarios/stress.js
 *   k6 run --env BASE_URL=http://localhost:8080 load-tests/scripts/scenarios/stress.js
 */

import { sleep, check } from 'k6';
import { Counter, Trend, Rate, Gauge } from 'k6/metrics';
import { THRESHOLDS, VU_CONFIG, COMMON_TAGS } from '../lib/config.js';
import { createOrder, getOrderStatus, completeOrderFlow } from '../api/orders.js';
import { checkHealth } from '../api/health.js';
import { randomSleep, weightedRandom } from '../lib/helpers.js';
import { generateCreateOrderRequest } from '../lib/data-generators.js';

// Custom metrics for stress test analysis
const orderCreatedCounter = new Counter('stress_orders_created');
const orderFailedCounter = new Counter('stress_orders_failed');
const successRate = new Rate('stress_success_rate');
const orderDuration = new Trend('stress_order_duration_ms');
const activeOrders = new Gauge('stress_active_orders');

// Test configuration
export const options = {
    stages: VU_CONFIG.stress.stages,
    thresholds: {
        ...THRESHOLDS.stress,
        stress_success_rate: ['rate>0.85'], // At least 85% success during stress
    },
    tags: {
        ...COMMON_TAGS,
        testType: 'stress',
    },
    userAgent: 'k6-stress-test/1.0',
    // More aggressive settings for stress testing
    batch: 10,
    batchPerHost: 5,
};

// VU-local state
const vuState = {
    orderCount: 0,
    failureCount: 0,
    lastOrderId: null,
};

// Setup function
export function setup() {
    console.log('=== Stress Test Starting ===');
    console.log('WARNING: This test will push the system to its limits!');
    console.log('Configuration:');
    console.log('  - Peak VUs: 200');
    console.log('  - Duration: ~10 minutes');
    console.log('  - Includes spike and recovery phases');

    const healthResult = checkHealth();
    if (!healthResult.success) {
        console.error('Application health check failed!');
        throw new Error('Application is not healthy');
    }

    console.log('Application is healthy. Starting stress test...');
    return {
        startTime: new Date().toISOString(),
        initialHealth: healthResult.body,
    };
}

// Main test function
export default function (data) {
    // Weighted action selection - heavier on writes during stress
    const action = weightedRandom([
        { weight: 80, value: 'createOrder' },
        { weight: 15, value: 'checkStatus' },
        { weight: 5, value: 'fullFlow' },
    ]);

    switch (action) {
        case 'createOrder':
            performStressOrderCreation();
            break;
        case 'checkStatus':
            performStressStatusCheck();
            break;
        case 'fullFlow':
            performFullOrderFlow();
            break;
    }

    // Minimal think time during stress test
    randomSleep(0.1, 0.5);
}

/**
 * Create order under stress conditions.
 * Tracks success/failure metrics carefully.
 */
function performStressOrderCreation() {
    const startTime = Date.now();
    activeOrders.add(1);

    const orderData = generateCreateOrderRequest();
    const result = createOrder(orderData);

    const duration = Date.now() - startTime;
    orderDuration.add(duration);
    activeOrders.add(-1);

    if (result.success && result.orderId) {
        orderCreatedCounter.add(1);
        successRate.add(1);
        vuState.orderCount++;
        vuState.lastOrderId = result.orderId;
    } else {
        orderFailedCounter.add(1);
        successRate.add(0);
        vuState.failureCount++;

        // Log failure details for analysis
        if (vuState.failureCount % 10 === 0) {
            console.warn(`VU failures: ${vuState.failureCount}, Status: ${result.response?.status}`);
        }
    }
}

/**
 * Check status under stress conditions.
 */
function performStressStatusCheck() {
    if (!vuState.lastOrderId) {
        performStressOrderCreation();
        return;
    }

    const result = getOrderStatus(vuState.lastOrderId);
    successRate.add(result.success ? 1 : 0);
}

/**
 * Perform full order flow under stress.
 * More expensive but validates end-to-end functionality.
 */
function performFullOrderFlow() {
    const startTime = Date.now();

    const result = completeOrderFlow();

    const duration = Date.now() - startTime;
    orderDuration.add(duration);

    if (result.success) {
        orderCreatedCounter.add(1);
        successRate.add(1);
    } else {
        orderFailedCounter.add(1);
        successRate.add(0);
    }
}

// Teardown function
export function teardown(data) {
    console.log('=== Stress Test Complete ===');
    console.log(`Test started at: ${data.startTime}`);
    console.log(`Test ended at: ${new Date().toISOString()}`);

    // Final health check to see if system recovered
    console.log('Performing post-stress health check...');
    sleep(2); // Brief pause before health check

    const healthResult = checkHealth();
    if (healthResult.success) {
        console.log('System recovered: Health check passed');
    } else {
        console.error('WARNING: System may not have fully recovered!');
    }
}
