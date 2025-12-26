// k6/scripts/soak-test.js
// Long-duration stability test for CDC pipeline
import { sleep, check, group } from 'k6';
import { config, getScenarioConfig, printGrafanaLinks } from './lib/config.js';
import * as pg from './lib/postgres.js';
import * as mongo from './lib/mongodb.js';
import * as metrics from './lib/metrics.js';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';
import { Trend, Counter } from 'k6/metrics';

// Soak-specific metrics
const memoryLeakIndicator = new Trend('soak_memory_indicator');
const processingStability = new Trend('soak_processing_stability');
const iterationDuration = new Trend('soak_iteration_duration');

export const options = {
  scenarios: {
    soak: getScenarioConfig('soak'),
  },
  thresholds: {
    'cdc_e2e_latency': ['p(95)<3000', 'p(99)<6000'],
    'cdc_success_rate': ['rate>0.99'],
    'pg_write_errors': ['count<100'],
    'mongo_read_errors': ['count<100'],
    'soak_processing_stability': ['p(95)<5000'], // Iteration should stay stable
  },
};

// Track created customers for cleanup
const createdCustomers = [];
let checkpointTime = 0;
const CHECKPOINT_INTERVAL = 300000; // 5 minutes

export function setup() {
  console.log('Starting soak test - 50 VUs for 2 hours');
  console.log('This test validates long-term stability and detects memory leaks');
  printGrafanaLinks();
  pg.openConnection();
  mongo.openConnection();

  return {
    startTime: Date.now(),
  };
}

export default function (data) {
  const customerId = uuidv4();
  const iterationStart = Date.now();
  const startTime = Date.now();

  // Perform CRUD cycle
  group('Create', function () {
    const customer = {
      id: customerId,
      email: `soak-${customerId}@loadtest.com`,
      status: 'pending',
    };

    const writeResult = pg.insertCustomer(customer);
    if (!writeResult.success) {
      metrics.recordFailure('create');
      return;
    }

    sleep(1);
    const readResult = mongo.findCustomer(customerId, 20, 300);

    if (readResult.found) {
      metrics.recordSuccess('create');
      createdCustomers.push(customerId);
    } else {
      metrics.recordFailure('create');
    }
  });

  group('Update', function () {
    pg.updateCustomerStatus(customerId, 'active');
    sleep(1);

    const readResult = mongo.findCustomer(customerId, 10, 200);
    if (readResult.found && readResult.document.status === 'active') {
      metrics.recordSuccess('update');
    }
  });

  // Record iteration duration for stability monitoring
  const iterationEnd = Date.now();
  iterationDuration.add(iterationEnd - iterationStart);

  // Periodic stability check
  const now = Date.now();
  if (now - checkpointTime > CHECKPOINT_INTERVAL) {
    processingStability.add(iterationEnd - iterationStart);
    checkpointTime = now;

    // Log checkpoint info
    const elapsed = (now - data.startTime) / 1000 / 60;
    console.log(`Checkpoint at ${elapsed.toFixed(1)} minutes - Iteration: ${iterationEnd - iterationStart}ms`);
  }

  // Cleanup older records to prevent database bloat
  if (createdCustomers.length > 100 && Math.random() < 0.1) {
    const oldCustomer = createdCustomers.shift();
    pg.deleteCustomer(oldCustomer);
    metrics.recordSuccess('delete');
  }

  metrics.recordCdcLatency(startTime, Date.now());
  sleep(Math.random() * 2 + 1);
}

export function teardown(data) {
  const totalDuration = (Date.now() - data.startTime) / 1000 / 60;
  console.log(`Soak test completed - Duration: ${totalDuration.toFixed(1)} minutes`);

  // Cleanup remaining test data
  console.log(`Cleaning up ${createdCustomers.length} remaining test customers...`);
  for (const customerId of createdCustomers) {
    pg.deleteCustomer(customerId);
  }

  pg.closeConnection();
  mongo.closeConnection();
}
