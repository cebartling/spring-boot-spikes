// k6/scripts/smoke-test.js
// Quick smoke test to validate CDC pipeline configuration (15-30 seconds)
import { sleep, check, group } from 'k6';
import * as pg from './lib/postgres.js';
import * as mongo from './lib/mongodb.js';
import * as metrics from './lib/metrics.js';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

export const options = {
  scenarios: {
    smoke: {
      executor: 'constant-vus',
      vus: 2,
      duration: '20s',
    },
  },
  thresholds: {
    'cdc_success_rate': ['rate>0.90'],
    'checks': ['rate>0.90'],
    'pg_write_duration': ['p(95)<200'],
    'mongo_read_duration': ['p(95)<100'],
  },
};

export function setup() {
  console.log('Starting CDC pipeline smoke test');
  console.log('Configuration: 2 VUs for 20 seconds');
  pg.openConnection();
  mongo.openConnection();
  return { startTime: Date.now() };
}

export default function () {
  const customerId = uuidv4();
  const startTime = Date.now();

  group('Smoke Test - Create and Verify Customer', function () {
    const customer = {
      id: customerId,
      email: `smoke-${customerId}@test.com`,
      status: 'active',
    };

    // Write to PostgreSQL
    const writeResult = pg.insertCustomer(customer);

    const writeSuccess = check(writeResult, {
      'PostgreSQL write successful': (r) => r.success === true,
    });

    if (!writeSuccess) {
      metrics.recordFailure('create');
      console.log(`FAIL: PostgreSQL write failed for ${customerId}`);
      return;
    }

    // Wait for CDC propagation
    sleep(1);

    // Verify in MongoDB
    const readResult = mongo.findCustomer(customerId, 10, 200);

    const verifySuccess = check(readResult, {
      'Customer replicated to MongoDB': (r) => r.found === true,
      'Customer email matches': (r) => r.found && r.document.email === customer.email,
      'Customer status matches': (r) => r.found && r.document.status === customer.status,
    });

    if (verifySuccess) {
      metrics.recordCdcLatency(startTime, Date.now());
      metrics.recordSuccess('create');
    } else {
      metrics.recordFailure('create');
      if (!readResult.found) {
        console.log(`FAIL: Customer ${customerId} not found in MongoDB after ${readResult.attempts} attempts`);
      }
    }
  });

  // Cleanup
  pg.deleteCustomer(customerId);
  sleep(0.3);
}

export function teardown(data) {
  const duration = (Date.now() - data.startTime) / 1000;
  console.log(`Smoke test completed in ${duration.toFixed(2)} seconds`);
  pg.closeConnection();
  mongo.closeConnection();
}
