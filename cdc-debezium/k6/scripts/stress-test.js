// k6/scripts/stress-test.js
// Stress testing with ramping VUs to find breaking points
import { sleep, check } from 'k6';
import { config, getScenarioConfig } from './lib/config.js';
import * as pg from './lib/postgres.js';
import * as mongo from './lib/mongodb.js';
import * as metrics from './lib/metrics.js';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

export const options = {
  scenarios: {
    stress: getScenarioConfig('stress'),
  },
  thresholds: {
    'cdc_e2e_latency': ['p(95)<5000', 'p(99)<10000'],
    'pg_write_duration': ['p(95)<200'],
    'cdc_success_rate': ['rate>0.95'],
    'checks': ['rate>0.90'],
  },
};

export function setup() {
  console.log('Starting stress test - ramping from 0 to 150 VUs');
  console.log('Stages:');
  for (const stage of options.scenarios.stress.stages) {
    console.log(`  - ${stage.duration}: ${stage.target} VUs`);
  }
  pg.openConnection();
  mongo.openConnection();
  return { startTime: Date.now() };
}

export default function () {
  const customerId = uuidv4();
  const startTime = Date.now();

  // Create customer
  const customer = {
    id: customerId,
    email: `stress-${customerId}@loadtest.com`,
    status: 'active',
  };

  const writeResult = pg.insertCustomer(customer);

  if (!writeResult.success) {
    metrics.recordFailure('create');
    return;
  }

  // Poll MongoDB for replication (with increasing timeout under load)
  const maxRetries = Math.min(30, 10 + Math.floor(__VU / 10));
  const readResult = mongo.findCustomer(customerId, maxRetries, 300);

  if (readResult.found) {
    metrics.recordCdcLatency(startTime, Date.now());
    metrics.recordSuccess('create');
  } else {
    metrics.recordFailure('create');
    console.log(`Customer ${customerId} not found after ${maxRetries} retries`);
  }

  // Variable sleep based on VU count (backpressure simulation)
  sleep(Math.random() * 0.5 + 0.1);
}

export function teardown(data) {
  const duration = (Date.now() - data.startTime) / 1000;
  console.log(`Stress test completed in ${duration.toFixed(2)} seconds`);
  pg.closeConnection();
  mongo.closeConnection();
}
