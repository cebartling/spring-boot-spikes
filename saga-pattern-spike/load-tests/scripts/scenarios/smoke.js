/**
 * Smoke Test Scenario
 *
 * Purpose: Quick validation that the system works under minimal load.
 * This is the first test to run to ensure basic functionality.
 *
 * Configuration:
 * - Virtual Users: 1-2
 * - Duration: 1 minute
 * - Thresholds: Strict (p95 < 500ms, error rate < 1%)
 *
 * Usage:
 *   k6 run load-tests/scripts/scenarios/smoke.js
 *   k6 run --env BASE_URL=http://localhost:8080 load-tests/scripts/scenarios/smoke.js
 */

import { sleep } from 'k6';
import { THRESHOLDS, VU_CONFIG, COMMON_TAGS } from '../lib/config.js';
import { createOrder, getOrder, getOrderStatus } from '../api/orders.js';
import { checkHealth } from '../api/health.js';
import { randomSleep } from '../lib/helpers.js';

// Test configuration
export const options = {
    vus: VU_CONFIG.smoke.vus,
    duration: VU_CONFIG.smoke.duration,
    thresholds: THRESHOLDS.smoke,
    tags: {
        ...COMMON_TAGS,
        testType: 'smoke',
    },
    // Fail fast on errors during smoke test
    noConnectionReuse: false,
    userAgent: 'k6-smoke-test/1.0',
};

// Setup function runs once before the test
export function setup() {
    console.log('=== Smoke Test Starting ===');
    console.log('Verifying application health...');

    const healthResult = checkHealth();
    if (!healthResult.success) {
        console.error('Application health check failed! Aborting test.');
        throw new Error('Application is not healthy');
    }

    console.log('Application is healthy. Starting smoke test...');
    return { startTime: new Date().toISOString() };
}

// Main test function - runs for each VU iteration
export default function (data) {
    // Step 1: Create an order
    const createResult = createOrder();

    if (!createResult.success) {
        console.error(`Failed to create order: ${createResult.response.status}`);
        sleep(1);
        return;
    }

    const orderId = createResult.orderId;
    console.log(`Created order: ${orderId}`);

    // Small pause to simulate user behavior
    randomSleep(0.5, 1);

    // Step 2: Get order status
    const statusResult = getOrderStatus(orderId);
    if (statusResult.success) {
        const status = statusResult.body;
        console.log(`Order ${orderId} status: ${status?.overallStatus || 'unknown'}`);
    }

    randomSleep(0.3, 0.5);

    // Step 3: Retrieve order details
    const orderResult = getOrder(orderId);
    if (orderResult.success) {
        console.log(`Retrieved order ${orderId} successfully`);
    }

    // Pause before next iteration
    randomSleep(1, 2);
}

// Teardown function runs once after all VUs complete
export function teardown(data) {
    console.log('=== Smoke Test Complete ===');
    console.log(`Test started at: ${data.startTime}`);
    console.log(`Test ended at: ${new Date().toISOString()}`);
}
