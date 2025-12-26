// k6/scripts/chaos-resilience-test.js
// Chaos resilience testing for CDC pipeline
// Run this test while injecting chaos using chaos/run-chaos.sh
import { sleep } from 'k6';
import { config, printGrafanaLinks } from './lib/config.js';
import * as pg from './lib/postgres.js';
import * as mongo from './lib/mongodb.js';
import * as metrics from './lib/metrics.js';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';
import { Trend, Counter, Rate, Gauge } from 'k6/metrics';

// Chaos-specific metrics
const chaosPhase = new Gauge('chaos_phase');
const recoveryTime = new Trend('chaos_recovery_time', true);
const eventsLostDuringChaos = new Counter('chaos_events_lost');
const chaosResilience = new Rate('chaos_resilience_rate');

// Test phases
const PHASES = {
  BASELINE: 1,
  CHAOS: 2,
  RECOVERY: 3,
  VERIFICATION: 4,
};

export const options = {
  scenarios: {
    chaos_test: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '1m', target: 20 },   // Baseline ramp-up
        { duration: '2m', target: 20 },   // Baseline steady
        { duration: '1m', target: 20 },   // Chaos injection (external)
        { duration: '2m', target: 20 },   // Recovery
        { duration: '1m', target: 0 },    // Ramp-down
      ],
    },
  },
  thresholds: {
    'chaos_resilience_rate': ['rate>0.90'],   // 90% events should succeed even with chaos
    'chaos_recovery_time': ['p(95)<30000'],   // Recovery within 30s
    'cdc_success_rate': ['rate>0.85'],        // Allow more failures during chaos
  },
};

// Shared state for tracking events
const createdCustomerIds = [];

export function setup() {
  console.log('=== CHAOS RESILIENCE TEST ===');
  console.log('This test should be run with external chaos injection');
  console.log('Use chaos/run-chaos.sh to inject failures during the test');
  console.log('');
  console.log('Timeline:');
  console.log('  0-1m:  Baseline ramp-up');
  console.log('  1-3m:  Baseline steady state');
  console.log('  3-4m:  Chaos injection window (run chaos script now!)');
  console.log('  4-6m:  Recovery period');
  console.log('  6-7m:  Ramp-down and verification');
  console.log('');
  printGrafanaLinks();

  pg.openConnection();
  mongo.openConnection();

  return {
    startTime: Date.now(),
    baselineEvents: 0,
  };
}

export default function (data) {
  const testTime = (Date.now() - data.startTime) / 1000;
  const currentPhase = determinePhase(testTime);
  chaosPhase.add(currentPhase);

  const customerId = uuidv4();
  const startTime = Date.now();

  // Create customer
  const customer = {
    id: customerId,
    email: `chaos-${customerId}@resilience.test`,
    status: 'active',
  };

  const writeResult = pg.insertCustomer(customer);

  if (!writeResult.success) {
    chaosResilience.add(false);
    metrics.recordFailure('create');

    // Expected during chaos - log but don't fail immediately
    if (currentPhase === PHASES.CHAOS) {
      console.log(`Write failed during chaos phase: ${writeResult.error}`);
    }
    return;
  }

  // Track created customer for cleanup
  createdCustomerIds.push(customerId);

  // Verification with extended timeout during recovery
  const maxRetries = currentPhase === PHASES.RECOVERY ? 60 : 30;
  const retryDelay = currentPhase === PHASES.RECOVERY ? 1000 : 200;

  const readResult = mongo.findCustomer(customerId, maxRetries, retryDelay);

  if (readResult.found) {
    chaosResilience.add(true);
    metrics.recordSuccess('create');
    metrics.recordCdcLatency(startTime, Date.now());

    // Track recovery time if we were in chaos/recovery phase
    if (currentPhase >= PHASES.CHAOS) {
      recoveryTime.add(Date.now() - startTime);
    }
  } else {
    chaosResilience.add(false);
    metrics.recordFailure('create');

    if (currentPhase === PHASES.VERIFICATION) {
      // Final verification phase - count as lost
      eventsLostDuringChaos.add(1);
      console.log(`Event potentially lost: ${customerId}`);
    }
  }

  // Adaptive sleep based on phase
  const sleepTime = currentPhase === PHASES.CHAOS ? 0.5 : 0.2;
  sleep(sleepTime);
}

function determinePhase(testTimeSeconds) {
  if (testTimeSeconds < 180) return PHASES.BASELINE;       // 0-3 min
  if (testTimeSeconds < 240) return PHASES.CHAOS;          // 3-4 min
  if (testTimeSeconds < 360) return PHASES.RECOVERY;       // 4-6 min
  return PHASES.VERIFICATION;                               // 6+ min
}

export function teardown(data) {
  const duration = (Date.now() - data.startTime) / 1000;

  console.log('');
  console.log('=== CHAOS TEST RESULTS ===');
  console.log(`Total duration: ${duration.toFixed(0)}s`);
  console.log(`Total customers created: ${createdCustomerIds.length}`);
  console.log('');
  console.log('Check Grafana dashboards for detailed metrics:');
  printGrafanaLinks();

  // Note: Cleanup is not performed to allow post-test analysis
  // Run the following to clean up test data:
  // DELETE FROM customer WHERE email LIKE 'chaos-%@resilience.test';

  pg.closeConnection();
  mongo.closeConnection();
}
