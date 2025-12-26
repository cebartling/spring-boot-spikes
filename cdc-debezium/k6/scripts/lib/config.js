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
      k6LoadTesting: '/d/k6-load-testing/k6-load-testing',
      cdcOverview: '/d/cdc-overview/cdc-pipeline-overview',
      consumerPerformance: '/d/consumer-performance/consumer-performance',
      mongodbOperations: '/d/mongodb-operations/mongodb-operations',
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
  const dashboards = [
    { name: 'k6 Load Testing (primary)', path: config.grafana.dashboards.k6LoadTesting },
    { name: 'CDC Overview', path: config.grafana.dashboards.cdcOverview },
    { name: 'Consumer Performance', path: config.grafana.dashboards.consumerPerformance },
    { name: 'MongoDB Operations', path: config.grafana.dashboards.mongodbOperations },
  ];

  // Calculate the max URL length for proper box sizing
  const maxUrlLength = Math.max(...dashboards.map((d) => d.name.length + baseUrl.length + d.path.length + 4));
  const boxWidth = Math.max(maxUrlLength + 4, 50);
  const horizontalLine = '═'.repeat(boxWidth);
  const titlePadding = Math.floor((boxWidth - 28) / 2);

  console.log('');
  console.log(`╔${horizontalLine}╗`);
  console.log(`║${' '.repeat(titlePadding)}Grafana Monitoring Dashboards${' '.repeat(boxWidth - titlePadding - 29)}║`);
  console.log(`╠${horizontalLine}╣`);

  for (const dashboard of dashboards) {
    const url = `${baseUrl}${dashboard.path}`;
    const line = `  ${dashboard.name}: ${url}`;
    const padding = boxWidth - line.length;
    console.log(`║${line}${' '.repeat(padding)}║`);
  }

  console.log(`╚${horizontalLine}╝`);
  console.log('');
}
