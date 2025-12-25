// k6/scripts/baseline-test.js
// Baseline performance measurement test for CDC pipeline
import { sleep, check, group } from 'k6';
import { config, getScenarioConfig } from './lib/config.js';
import * as pg from './lib/postgres.js';
import * as mongo from './lib/mongodb.js';
import * as metrics from './lib/metrics.js';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

export const options = {
  scenarios: {
    baseline: getScenarioConfig('baseline'),
  },
  thresholds: {
    'cdc_e2e_latency': ['p(95)<2000', 'p(99)<5000'],
    'pg_write_duration': ['p(95)<100'],
    'mongo_read_duration': ['p(95)<50'],
    'cdc_success_rate': ['rate>0.99'],
    'checks': ['rate>0.95'],
  },
};

export function setup() {
  console.log('Starting baseline performance test');
  console.log(`Configuration: ${options.scenarios.baseline.vus} VUs for ${options.scenarios.baseline.duration}`);
  pg.openConnection();
  mongo.openConnection();
  return { startTime: Date.now() };
}

export default function () {
  const customerId = uuidv4();
  const startTime = Date.now();

  group('Create Customer', function () {
    const customer = {
      id: customerId,
      email: `baseline-${customerId}@loadtest.com`,
      status: 'active',
    };

    const writeResult = pg.insertCustomer(customer);

    check(writeResult, {
      'PostgreSQL write successful': (r) => r.success === true,
    });

    if (!writeResult.success) {
      metrics.recordFailure('create');
      return;
    }

    // Wait for CDC propagation and verify in MongoDB
    sleep(1);

    const readResult = mongo.findCustomer(customerId, 15, 200);

    const success = check(readResult, {
      'Customer replicated to MongoDB': (r) => r.found === true,
      'Customer data matches': (r) =>
        r.found && r.document.email === customer.email,
    });

    if (success) {
      metrics.recordCdcLatency(startTime, Date.now());
      metrics.recordSuccess('create');
    } else {
      metrics.recordFailure('create');
    }
  });

  // Cleanup
  pg.deleteCustomer(customerId);
  sleep(0.5);
}

export function teardown(data) {
  const duration = (Date.now() - data.startTime) / 1000;
  console.log(`Baseline test completed in ${duration.toFixed(2)} seconds`);
  pg.closeConnection();
  mongo.closeConnection();
}
