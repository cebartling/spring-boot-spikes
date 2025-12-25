// k6/scripts/mixed-workload-test.js
// Realistic mixed operation workload test
import { sleep, check, group } from 'k6';
import { config } from './lib/config.js';
import * as pg from './lib/postgres.js';
import * as mongo from './lib/mongodb.js';
import * as metrics from './lib/metrics.js';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';
import { randomItem } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

// Workload distribution
const WORKLOAD = {
  CREATE_CUSTOMER: 0.3, // 30%
  UPDATE_CUSTOMER: 0.25, // 25%
  CREATE_ADDRESS: 0.15, // 15%
  CREATE_ORDER: 0.2, // 20%
  DELETE_CUSTOMER: 0.1, // 10%
};

export const options = {
  scenarios: {
    mixed: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '1m', target: 20 },
        { duration: '3m', target: 50 },
        { duration: '3m', target: 50 },
        { duration: '1m', target: 20 },
        { duration: '1m', target: 0 },
      ],
    },
  },
  thresholds: {
    'cdc_success_rate': ['rate>0.95'],
    'cdc_e2e_latency': ['p(95)<3000'],
    'checks': ['rate>0.90'],
  },
};

// Track created entities for updates/deletes
const activeCustomers = [];
const activeAddresses = [];

export function setup() {
  console.log('Starting mixed workload test');
  console.log('Workload distribution:');
  for (const [op, pct] of Object.entries(WORKLOAD)) {
    console.log(`  - ${op}: ${(pct * 100).toFixed(0)}%`);
  }
  pg.openConnection();
  mongo.openConnection();

  // Seed some initial customers
  console.log('Seeding initial customers...');
  for (let i = 0; i < 10; i++) {
    const id = uuidv4();
    pg.insertCustomer({
      id: id,
      email: `seed-${id}@mixed.test`,
      status: 'active',
    });
    activeCustomers.push(id);
  }

  sleep(5); // Wait for seed data to propagate
  return { startTime: Date.now() };
}

export default function () {
  const rand = Math.random();
  let cumulativeProbability = 0;

  // Select operation based on workload distribution
  for (const [operation, probability] of Object.entries(WORKLOAD)) {
    cumulativeProbability += probability;
    if (rand < cumulativeProbability) {
      executeOperation(operation);
      break;
    }
  }

  sleep(Math.random() * 0.5 + 0.1);
}

function executeOperation(operation) {
  const startTime = Date.now();

  switch (operation) {
    case 'CREATE_CUSTOMER':
      group('Create Customer', () => createCustomer(startTime));
      break;
    case 'UPDATE_CUSTOMER':
      group('Update Customer', () => updateCustomer(startTime));
      break;
    case 'CREATE_ADDRESS':
      group('Create Address', () => createAddress(startTime));
      break;
    case 'CREATE_ORDER':
      group('Create Order', () => createOrder(startTime));
      break;
    case 'DELETE_CUSTOMER':
      group('Delete Customer', () => deleteCustomer(startTime));
      break;
  }
}

function createCustomer(startTime) {
  const customerId = uuidv4();
  const customer = {
    id: customerId,
    email: `mixed-${customerId}@loadtest.com`,
    status: 'pending',
  };

  const result = pg.insertCustomer(customer);
  if (result.success) {
    activeCustomers.push(customerId);

    sleep(1);
    const found = mongo.findCustomer(customerId, 15, 200);
    if (found.found) {
      metrics.recordCdcLatency(startTime, Date.now());
      metrics.recordSuccess('create');
    } else {
      metrics.recordFailure('create');
    }
  }
}

function updateCustomer(startTime) {
  if (activeCustomers.length === 0) return;

  const customerId = randomItem(activeCustomers);
  const newStatus = randomItem(['active', 'suspended', 'pending']);

  const result = pg.updateCustomerStatus(customerId, newStatus);
  if (result.success) {
    sleep(1);
    const found = mongo.findCustomer(customerId, 10, 200);
    if (found.found && found.document.status === newStatus) {
      metrics.recordCdcLatency(startTime, Date.now());
      metrics.recordSuccess('update');
    } else {
      metrics.recordFailure('update');
    }
  }
}

function createAddress(startTime) {
  if (activeCustomers.length === 0) return;

  const addressId = uuidv4();
  const customerId = randomItem(activeCustomers);
  const address = {
    id: addressId,
    customerId: customerId,
    type: randomItem(['billing', 'shipping']),
    street: `${Math.floor(Math.random() * 9999)} Test Street`,
    city: randomItem(['New York', 'Los Angeles', 'Chicago', 'Houston']),
    state: randomItem(['NY', 'CA', 'IL', 'TX']),
    postalCode: String(Math.floor(Math.random() * 90000) + 10000),
    country: 'USA',
    isDefault: Math.random() < 0.2,
  };

  const result = pg.insertAddress(address);
  if (result.success) {
    activeAddresses.push(addressId);
    metrics.recordSuccess('create');
  }
}

function createOrder(startTime) {
  if (activeCustomers.length === 0) return;

  const orderId = uuidv4();
  const customerId = randomItem(activeCustomers);
  const order = {
    id: orderId,
    customerId: customerId,
    status: 'pending',
    totalAmount: (Math.random() * 500 + 10).toFixed(2),
  };

  const result = pg.insertOrder(order);
  if (result.success) {
    metrics.recordSuccess('create');
  }
}

function deleteCustomer(startTime) {
  if (activeCustomers.length <= 10) return; // Keep minimum pool

  const index = Math.floor(Math.random() * activeCustomers.length);
  const customerId = activeCustomers.splice(index, 1)[0];

  const result = pg.deleteCustomer(customerId);
  if (result.success) {
    metrics.recordSuccess('delete');
  }
}

export function teardown(data) {
  const duration = (Date.now() - data.startTime) / 1000;
  console.log(`Mixed workload test completed in ${duration.toFixed(2)} seconds`);
  console.log(`Final active customers: ${activeCustomers.length}`);

  // Cleanup
  console.log('Cleaning up test data...');
  for (const customerId of activeCustomers) {
    pg.deleteCustomer(customerId);
  }

  pg.closeConnection();
  mongo.closeConnection();
}
