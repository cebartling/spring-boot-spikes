// k6/scripts/lib/metrics.js
// Custom metrics definitions for CDC pipeline load testing
import { Trend, Counter, Rate, Gauge } from 'k6/metrics';

// CDC End-to-End Latency
export const cdcE2ELatency = new Trend('cdc_e2e_latency', true);

// CDC Operation Counts
export const cdcCreates = new Counter('cdc_creates');
export const cdcUpdates = new Counter('cdc_updates');
export const cdcDeletes = new Counter('cdc_deletes');

// Success/Failure rates
export const cdcSuccessRate = new Rate('cdc_success_rate');
export const cdcReplicationSuccess = new Rate('cdc_replication_success');

// Pipeline health
export const cdcPipelineActive = new Gauge('cdc_pipeline_active');
export const cdcConsumerLag = new Gauge('cdc_consumer_lag_estimate');

// Test tracking
export const testIterations = new Counter('test_iterations');
export const testVUs = new Gauge('test_vus');

export function recordCdcLatency(startTime, endTime) {
  cdcE2ELatency.add(endTime - startTime);
}

export function recordSuccess(operationType) {
  cdcSuccessRate.add(true);
  cdcReplicationSuccess.add(true);

  switch (operationType) {
    case 'create':
      cdcCreates.add(1);
      break;
    case 'update':
      cdcUpdates.add(1);
      break;
    case 'delete':
      cdcDeletes.add(1);
      break;
  }
}

export function recordFailure(operationType) {
  cdcSuccessRate.add(false);
  cdcReplicationSuccess.add(false);
}
