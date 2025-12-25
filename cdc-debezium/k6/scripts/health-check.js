// k6/scripts/health-check.js
// Health check script to validate CDC pipeline infrastructure
import { config } from './lib/config.js';
import * as pg from './lib/postgres.js';
import * as mongo from './lib/mongodb.js';
import { check } from 'k6';

export const options = {
  vus: 1,
  iterations: 1,
  thresholds: {
    checks: ['rate==1'],
  },
};

export function setup() {
  console.log('Running CDC pipeline health check...');
}

export default function () {
  // Test PostgreSQL connection
  const pgDb = pg.openConnection();
  check(pgDb, {
    'PostgreSQL connected': (db) => db !== null,
  });

  // Test MongoDB connection
  const mongoClient = mongo.openConnection();
  check(mongoClient, {
    'MongoDB connected': (client) => client !== null,
  });

  // Test basic write to PostgreSQL
  const testCustomer = {
    id: '00000000-0000-0000-0000-000000000000',
    email: 'health-check@test.com',
    status: 'active',
  };
  const writeResult = pg.insertCustomer(testCustomer);
  check(writeResult, {
    'PostgreSQL write successful': (r) => r.success === true,
  });

  // Cleanup
  pg.deleteCustomer(testCustomer.id);

  console.log('Health check complete.');
}

export function teardown() {
  pg.closeConnection();
  mongo.closeConnection();
}
