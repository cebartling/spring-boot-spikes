// k6/scripts/spike-test.js
// Spike load testing with sudden VU increase
import { sleep, check } from 'k6';
import { config, getScenarioConfig } from './lib/config.js';
import * as pg from './lib/postgres.js';
import * as mongo from './lib/mongodb.js';
import * as metrics from './lib/metrics.js';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

export const options = {
  scenarios: {
    spike: getScenarioConfig('spike'),
  },
  thresholds: {
    'cdc_e2e_latency': ['p(95)<10000'], // Relaxed during spike
    'pg_write_duration': ['p(95)<500'],
    'cdc_success_rate': ['rate>0.90'], // Allow some failures during spike
  },
};

export function setup() {
  console.log('Starting spike test - sudden load increase to 500 VUs');
  console.log('Stages:');
  for (const stage of options.scenarios.spike.stages) {
    console.log(`  - ${stage.duration}: ${stage.target} VUs`);
  }
  pg.openConnection();
  mongo.openConnection();

  return {
    startTime: Date.now(),
    preSpikeCounts: {
      customers: mongo.countCustomers(),
    },
  };
}

export default function () {
  const customerId = uuidv4();
  const startTime = Date.now();

  const customer = {
    id: customerId,
    email: `spike-${__VU}-${__ITER}@loadtest.com`,
    status: 'active',
  };

  const writeResult = pg.insertCustomer(customer);

  if (!writeResult.success) {
    metrics.recordFailure('create');
    return;
  }

  // During spike, allow longer propagation time
  const readResult = mongo.findCustomer(customerId, 60, 500);

  if (readResult.found) {
    metrics.recordCdcLatency(startTime, Date.now());
    metrics.recordSuccess('create');
  } else {
    metrics.recordFailure('create');
  }

  // Minimal sleep to maximize load
  sleep(0.05);
}

export function teardown(data) {
  // Wait for pipeline to catch up after spike
  console.log('Waiting for CDC pipeline to catch up...');
  sleep(30);

  const postSpikeCounts = {
    customers: mongo.countCustomers(),
  };

  console.log(`Pre-spike customers: ${data.preSpikeCounts.customers}`);
  console.log(`Post-spike customers: ${postSpikeCounts.customers}`);

  const duration = (Date.now() - data.startTime) / 1000;
  console.log(`Spike test completed in ${duration.toFixed(2)} seconds`);

  pg.closeConnection();
  mongo.closeConnection();
}
