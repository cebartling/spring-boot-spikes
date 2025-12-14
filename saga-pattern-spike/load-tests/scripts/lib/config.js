/**
 * Shared configuration for k6 load tests.
 *
 * This module provides centralized configuration for all test scenarios,
 * including base URLs, thresholds, and common tags.
 */

// Base URL for the Spring Boot application
export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// API endpoints
export const ENDPOINTS = {
    orders: `${BASE_URL}/api/orders`,
    health: `${BASE_URL}/actuator/health`,
    prometheus: `${BASE_URL}/actuator/prometheus`,
};

// Default HTTP request parameters
export const DEFAULT_PARAMS = {
    headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
    },
    timeout: '30s',
};

// Common thresholds for all scenarios
export const THRESHOLDS = {
    smoke: {
        http_req_duration: ['p(95)<500', 'p(99)<1000'],
        http_req_failed: ['rate<0.01'],
        http_reqs: ['rate>1'],
    },
    load: {
        http_req_duration: ['p(95)<1000', 'p(99)<2000'],
        http_req_failed: ['rate<0.05'],
        http_reqs: ['rate>10'],
    },
    stress: {
        http_req_duration: ['p(95)<3000', 'p(99)<5000'],
        http_req_failed: ['rate<0.15'],
    },
    soak: {
        http_req_duration: ['p(95)<1500', 'p(99)<3000'],
        http_req_failed: ['rate<0.05'],
    },
};

// Common tags for metrics
export const COMMON_TAGS = {
    project: 'saga-pattern-spike',
    environment: __ENV.ENVIRONMENT || 'local',
};

// Sleep durations (in seconds) for think time simulation
export const THINK_TIME = {
    min: 0.5,
    max: 2.0,
    short: 0.3,
    long: 3.0,
};

// Virtual user configurations for different scenarios
export const VU_CONFIG = {
    smoke: {
        vus: 2,
        duration: '1m',
    },
    load: {
        stages: [
            { duration: '2m', target: 50 },  // Ramp up
            { duration: '2m', target: 50 },  // Steady state
            { duration: '1m', target: 0 },   // Ramp down
        ],
    },
    stress: {
        stages: [
            { duration: '3m', target: 100 }, // Ramp up
            { duration: '1m', target: 200 }, // Spike
            { duration: '2m', target: 100 }, // Recovery
            { duration: '2m', target: 100 }, // Steady
            { duration: '2m', target: 0 },   // Ramp down
        ],
    },
    soak: {
        stages: [
            { duration: '2m', target: 30 },  // Ramp up
            { duration: '26m', target: 30 }, // Sustained load
            { duration: '2m', target: 0 },   // Ramp down
        ],
    },
};

// Prometheus Pushgateway URL (for metrics export)
export const PROMETHEUS_PUSHGATEWAY_URL = __ENV.K6_PROMETHEUS_PUSHGATEWAY_URL || 'http://localhost:9091';
