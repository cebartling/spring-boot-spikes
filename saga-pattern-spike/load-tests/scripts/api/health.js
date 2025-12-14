/**
 * Health check API test functions for k6.
 *
 * This module provides functions for testing the Spring Boot
 * Actuator health and metrics endpoints.
 */

import http from 'k6/http';
import { check } from 'k6';
import { ENDPOINTS } from '../lib/config.js';
import { parseJson } from '../lib/helpers.js';

/**
 * Check application health endpoint.
 *
 * @param {object} tags - Additional tags for metrics
 * @returns {object} Response with health status
 */
export function checkHealth(tags = {}) {
    const params = {
        headers: { 'Accept': 'application/json' },
        tags: { name: 'health_check', ...tags },
        timeout: '10s',
    };

    const response = http.get(ENDPOINTS.health, params);

    const success = check(response, {
        'health status is 200': (r) => r.status === 200,
        'health status is UP': (r) => {
            const body = parseJson(r);
            return body && body.status === 'UP';
        },
    });

    return {
        response,
        success,
        body: parseJson(response),
    };
}

/**
 * Get Prometheus metrics endpoint.
 *
 * @param {object} tags - Additional tags for metrics
 * @returns {object} Response with metrics data
 */
export function getPrometheusMetrics(tags = {}) {
    const params = {
        headers: { 'Accept': 'text/plain' },
        tags: { name: 'prometheus_metrics', ...tags },
        timeout: '10s',
    };

    const response = http.get(ENDPOINTS.prometheus, params);

    const success = check(response, {
        'prometheus status is 200': (r) => r.status === 200,
        'prometheus has metrics': (r) => r.body && r.body.includes('jvm_'),
    });

    return {
        response,
        success,
        body: response.body,
    };
}

/**
 * Pre-flight check to ensure the application is ready.
 * Useful to run before starting load tests.
 *
 * @param {number} maxRetries - Maximum number of retry attempts
 * @param {number} retryDelayMs - Delay between retries in milliseconds
 * @returns {boolean} True if application is ready
 */
export function waitForApplicationReady(maxRetries = 30, retryDelayMs = 2000) {
    for (let i = 0; i < maxRetries; i++) {
        const result = checkHealth();
        if (result.success) {
            console.log(`Application ready after ${i + 1} attempt(s)`);
            return true;
        }
        console.log(`Waiting for application... attempt ${i + 1}/${maxRetries}`);
        // Note: k6's sleep is in seconds
        sleep(retryDelayMs / 1000);
    }
    console.error(`Application not ready after ${maxRetries} attempts`);
    return false;
}

import { sleep } from 'k6';
