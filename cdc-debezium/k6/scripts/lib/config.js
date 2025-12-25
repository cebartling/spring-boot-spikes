// k6/scripts/lib/config.js
// Shared configuration for k6 load testing scripts

export const config = {
  postgres: {
    connectionString: `postgres://${__ENV.POSTGRES_USER}:${__ENV.POSTGRES_PASSWORD}@${__ENV.POSTGRES_HOST}:${__ENV.POSTGRES_PORT}/${__ENV.POSTGRES_DB}?sslmode=disable`,
  },
  mongodb: {
    uri: __ENV.MONGODB_URI,
    database: 'cdc_materialized',
    collections: {
      customers: 'customers',
      addresses: 'addresses',
      orders: 'orders',
    },
  },
  grafana: {
    baseUrl: __ENV.GRAFANA_URL || 'http://localhost:3000',
    dashboards: {
      cdcOverview: '/d/cdc-overview/cdc-pipeline-overview',
      consumerPerformance: '/d/consumer-performance/consumer-performance',
      mongodbOperations: '/d/mongodb-operations/mongodb-operations',
      logsExplorer: '/d/logs-explorer/logs-explorer',
    },
  },
  thresholds: {
    // E2E latency thresholds
    cdcLatencyP95: 2000, // 2 seconds
    cdcLatencyP99: 5000, // 5 seconds

    // Database operation thresholds
    pgWriteP95: 100, // 100ms
    mongoReadP95: 50, // 50ms

    // Error rate threshold
    maxErrorRate: 0.01, // 1%
  },
  scenarios: {
    // Default VU counts
    baseline: 10,
    stress: 100,
    spike: 500,
    soak: 50,
  },
};

export function getScenarioConfig(scenario) {
  const scenarios = {
    baseline: {
      executor: 'constant-vus',
      vus: config.scenarios.baseline,
      duration: '5m',
    },
    stress: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '2m', target: 50 },
        { duration: '5m', target: 100 },
        { duration: '2m', target: 150 },
        { duration: '5m', target: 100 },
        { duration: '2m', target: 0 },
      ],
    },
    spike: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 10 },
        { duration: '1m', target: 500 },
        { duration: '30s', target: 10 },
      ],
    },
    soak: {
      executor: 'constant-vus',
      vus: config.scenarios.soak,
      duration: '2h',
    },
  };

  return scenarios[scenario] || scenarios.baseline;
}

export function printGrafanaLinks() {
  const baseUrl = config.grafana.baseUrl;
  console.log('');
  console.log('╔══════════════════════════════════════════════════════════════════╗');
  console.log('║                    Grafana Monitoring Dashboards                 ║');
  console.log('╠══════════════════════════════════════════════════════════════════╣');
  console.log(`║  CDC Overview:          ${baseUrl}${config.grafana.dashboards.cdcOverview}`);
  console.log(`║  Consumer Performance:  ${baseUrl}${config.grafana.dashboards.consumerPerformance}`);
  console.log(`║  MongoDB Operations:    ${baseUrl}${config.grafana.dashboards.mongodbOperations}`);
  console.log(`║  Logs Explorer:         ${baseUrl}${config.grafana.dashboards.logsExplorer}`);
  console.log('╚══════════════════════════════════════════════════════════════════╝');
  console.log('');
}
