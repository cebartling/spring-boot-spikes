// k6/scripts/e2e-latency-test.js
// End-to-end CDC latency measurement with precise timing
import { sleep, check } from 'k6';
import { config } from './lib/config.js';
import * as pg from './lib/postgres.js';
import * as mongo from './lib/mongodb.js';
import { Trend, Counter, Rate } from 'k6/metrics';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

// Precise E2E metrics
const e2eLatency = new Trend('e2e_cdc_latency_ms', true);
const propagationSuccess = new Rate('e2e_propagation_success');
const propagationAttempts = new Counter('e2e_propagation_attempts');

// Latency breakdown
const pgWriteLatency = new Trend('e2e_pg_write_ms', true);
const cdcPropagationLatency = new Trend('e2e_cdc_propagation_ms', true);
const mongoConfirmLatency = new Trend('e2e_mongo_confirm_ms', true);

export const options = {
  scenarios: {
    e2e_latency: {
      executor: 'constant-arrival-rate',
      rate: 10, // 10 iterations per second
      timeUnit: '1s',
      duration: '5m',
      preAllocatedVUs: 20,
      maxVUs: 50,
    },
  },
  thresholds: {
    'e2e_cdc_latency_ms': ['p(50)<500', 'p(95)<2000', 'p(99)<5000'],
    'e2e_propagation_success': ['rate>0.99'],
    'e2e_cdc_propagation_ms': ['p(95)<1500'],
  },
};

export function setup() {
  console.log('Starting E2E latency measurement test');
  console.log('This test measures precise CDC propagation times');
  console.log(`Rate: ${options.scenarios.e2e_latency.rate} iterations/second`);
  console.log(`Duration: ${options.scenarios.e2e_latency.duration}`);
  pg.openConnection();
  mongo.openConnection();
  return { startTime: Date.now() };
}

export default function () {
  const customerId = uuidv4();
  const testStartTime = Date.now();

  // Phase 1: Write to PostgreSQL
  const pgWriteStart = Date.now();
  const customer = {
    id: customerId,
    email: `e2e-${customerId}@latency.test`,
    status: 'active',
  };

  const writeResult = pg.insertCustomer(customer);
  const pgWriteEnd = Date.now();
  pgWriteLatency.add(pgWriteEnd - pgWriteStart);

  if (!writeResult.success) {
    propagationSuccess.add(false);
    return;
  }

  // Phase 2: Poll MongoDB until document appears
  const propagationStart = Date.now();
  let found = false;
  let attempts = 0;
  const maxAttempts = 100;
  const pollInterval = 50; // 50ms

  while (!found && attempts < maxAttempts) {
    attempts++;
    propagationAttempts.add(1);

    const result = mongo.findCustomer(customerId, 1, 0); // Single check, no internal retry
    if (result.found) {
      found = true;
      const propagationEnd = Date.now();

      // Record latencies
      cdcPropagationLatency.add(propagationEnd - propagationStart);
      mongoConfirmLatency.add(propagationEnd - pgWriteEnd);
      e2eLatency.add(propagationEnd - testStartTime);

      propagationSuccess.add(true);

      // Log detailed timing for debugging (sample 1% of results)
      if (Math.random() < 0.01) {
        console.log(
          `E2E Latency: ${propagationEnd - testStartTime}ms ` +
            `(PG: ${pgWriteEnd - pgWriteStart}ms, ` +
            `CDC: ${propagationEnd - propagationStart}ms, ` +
            `Attempts: ${attempts})`
        );
      }
    } else {
      sleep(pollInterval / 1000);
    }
  }

  if (!found) {
    propagationSuccess.add(false);
    console.log(
      `TIMEOUT: Customer ${customerId} not found after ${maxAttempts} attempts (${Date.now() - testStartTime}ms)`
    );
  }

  // Cleanup
  pg.deleteCustomer(customerId);
}

export function teardown(data) {
  const duration = (Date.now() - data.startTime) / 1000;
  console.log(`E2E latency test completed in ${duration.toFixed(2)} seconds`);
  pg.closeConnection();
  mongo.closeConnection();
}
