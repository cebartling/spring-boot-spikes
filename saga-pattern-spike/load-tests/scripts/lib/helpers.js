/**
 * Utility functions for k6 load tests.
 *
 * This module provides helper functions for common operations
 * like request handling, response validation, and logging.
 */

import { check, sleep } from 'k6';
import { THINK_TIME } from './config.js';

/**
 * Random sleep to simulate user think time.
 *
 * @param {number} min - Minimum sleep duration in seconds
 * @param {number} max - Maximum sleep duration in seconds
 */
export function randomSleep(min = THINK_TIME.min, max = THINK_TIME.max) {
    const duration = Math.random() * (max - min) + min;
    sleep(duration);
}

/**
 * Short pause between rapid operations.
 */
export function shortPause() {
    sleep(THINK_TIME.short);
}

/**
 * Longer pause for simulating user reading/thinking.
 */
export function longPause() {
    sleep(THINK_TIME.long);
}

/**
 * Check if HTTP response is successful (2xx status).
 *
 * @param {object} response - k6 HTTP response object
 * @param {string} name - Name for the check (for reporting)
 * @returns {boolean} True if all checks passed
 */
export function checkSuccess(response, name = 'request') {
    return check(response, {
        [`${name} status is 2xx`]: (r) => r.status >= 200 && r.status < 300,
        [`${name} has body`]: (r) => r.body && r.body.length > 0,
    });
}

/**
 * Check if HTTP response matches expected status code.
 *
 * @param {object} response - k6 HTTP response object
 * @param {number} expectedStatus - Expected HTTP status code
 * @param {string} name - Name for the check
 * @returns {boolean} True if status matches
 */
export function checkStatus(response, expectedStatus, name = 'request') {
    return check(response, {
        [`${name} status is ${expectedStatus}`]: (r) => r.status === expectedStatus,
    });
}

/**
 * Check if response time is within acceptable threshold.
 *
 * @param {object} response - k6 HTTP response object
 * @param {number} maxMs - Maximum acceptable response time in milliseconds
 * @param {string} name - Name for the check
 * @returns {boolean} True if response time is acceptable
 */
export function checkResponseTime(response, maxMs, name = 'request') {
    return check(response, {
        [`${name} response time < ${maxMs}ms`]: (r) => r.timings.duration < maxMs,
    });
}

/**
 * Parse JSON response body safely.
 *
 * @param {object} response - k6 HTTP response object
 * @returns {object|null} Parsed JSON or null if parsing fails
 */
export function parseJson(response) {
    try {
        return JSON.parse(response.body);
    } catch (e) {
        console.error(`Failed to parse JSON: ${e.message}`);
        return null;
    }
}

/**
 * Extract order ID from create order response.
 *
 * @param {object} response - k6 HTTP response object
 * @returns {string|null} Order ID or null if not found
 */
export function extractOrderId(response) {
    const body = parseJson(response);
    return body ? body.orderId : null;
}

/**
 * Log test iteration info (for debugging).
 *
 * @param {object} exec - k6 execution object
 * @param {string} message - Log message
 */
export function logIteration(exec, message) {
    console.log(`[VU ${exec.vu.idInTest}][Iter ${exec.scenario.iterationInTest}] ${message}`);
}

/**
 * Generate a weighted random choice.
 *
 * @param {Array<{weight: number, value: any}>} choices - Array of weighted choices
 * @returns {any} Selected value
 */
export function weightedRandom(choices) {
    const totalWeight = choices.reduce((sum, c) => sum + c.weight, 0);
    let random = Math.random() * totalWeight;

    for (const choice of choices) {
        random -= choice.weight;
        if (random <= 0) {
            return choice.value;
        }
    }

    return choices[choices.length - 1].value;
}

/**
 * Format duration in milliseconds to human-readable string.
 *
 * @param {number} ms - Duration in milliseconds
 * @returns {string} Formatted duration
 */
export function formatDuration(ms) {
    if (ms < 1000) {
        return `${ms.toFixed(0)}ms`;
    } else if (ms < 60000) {
        return `${(ms / 1000).toFixed(2)}s`;
    } else {
        return `${(ms / 60000).toFixed(2)}m`;
    }
}

/**
 * Create standard request options with custom headers.
 *
 * @param {object} additionalHeaders - Additional headers to include
 * @param {object} tags - Tags for this request
 * @returns {object} Request options object
 */
export function createRequestOptions(additionalHeaders = {}, tags = {}) {
    return {
        headers: {
            'Content-Type': 'application/json',
            'Accept': 'application/json',
            ...additionalHeaders,
        },
        tags: tags,
        timeout: '30s',
    };
}
