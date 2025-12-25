# k6 Load Testing Infrastructure

This directory contains the k6 load testing infrastructure for performance testing the CDC pipeline from PostgreSQL through Kafka to MongoDB materialization.

## Overview

The load testing setup uses a custom k6 build with extensions for direct database access:

- **xk6-sql**: Direct PostgreSQL write operations
- **xk6-mongo**: Direct MongoDB read operations for verification
- **xk6-output-prometheus-remote**: Real-time metrics export to Prometheus

## Prerequisites

- Docker and Docker Compose
- CDC infrastructure running (`docker compose up -d` from project root)
- Debezium connector deployed and running

## Quick Start

### 1. Build the Custom k6 Image

```bash
# From the project root directory
docker compose -f k6/docker-compose.k6.yml build k6
```

### 2. Verify the Build

```bash
# Check k6 version and installed extensions
docker compose -f k6/docker-compose.k6.yml run --rm k6 version
```

Expected output:
```
k6 v0.51.0 (go1.23.12, linux/arm64)
Extensions:
  github.com/GhMartingit/xk6-mongo v0.1.3, k6/x/mongo [js]
  github.com/grafana/xk6-output-prometheus-remote v0.5.0, xk6-prometheus-rw [output]
  github.com/grafana/xk6-sql v0.4.1, k6/x/sql [js]
```

### 3. Run Health Check

```bash
# Verify connectivity to PostgreSQL and MongoDB
docker compose -f k6/docker-compose.k6.yml run --rm k6 run /scripts/health-check.js
```

### 4. Run with Prometheus Metrics

```bash
# Run with real-time metrics export
docker compose -f k6/docker-compose.k6.yml run --rm k6 run \
  --out experimental-prometheus-rw \
  /scripts/health-check.js
```

## Directory Structure

```
k6/
├── Dockerfile                 # Custom k6 build with extensions
├── docker-compose.k6.yml      # k6 services configuration
├── README.md                  # This file
├── scripts/
│   ├── health-check.js        # Infrastructure validation script
│   └── lib/
│       ├── config.js          # Shared configuration
│       ├── postgres.js        # PostgreSQL helper functions
│       ├── mongodb.js         # MongoDB helper functions
│       └── metrics.js         # Custom CDC metrics
├── results/                   # Test results output
│   └── .gitkeep
└── grafana/
    └── provisioning/
        └── dashboards/        # k6 Grafana dashboards
```

## Configuration

### Environment Variables

The following environment variables are configured in `docker-compose.k6.yml`:

| Variable | Default | Description |
|----------|---------|-------------|
| `POSTGRES_HOST` | `postgres` | PostgreSQL hostname |
| `POSTGRES_PORT` | `5432` | PostgreSQL port |
| `POSTGRES_USER` | `postgres` | PostgreSQL username |
| `POSTGRES_PASSWORD` | `postgres` | PostgreSQL password |
| `POSTGRES_DB` | `cdc_source` | PostgreSQL database |
| `MONGODB_URI` | `mongodb://...` | MongoDB connection string |
| `K6_PROMETHEUS_RW_SERVER_URL` | `http://prometheus:9090/api/v1/write` | Prometheus remote write URL |

### Thresholds

Default thresholds are defined in `scripts/lib/config.js`:

```javascript
thresholds: {
  cdcLatencyP95: 2000,  // 2 seconds E2E latency
  cdcLatencyP99: 5000,  // 5 seconds E2E latency
  pgWriteP95: 100,      // 100ms PostgreSQL write
  mongoReadP95: 50,     // 50ms MongoDB read
  maxErrorRate: 0.01,   // 1% error rate
}
```

### Scenarios

Pre-configured scenarios in `scripts/lib/config.js`:

| Scenario | VUs | Duration | Description |
|----------|-----|----------|-------------|
| `baseline` | 10 | 5 minutes | Steady-state baseline |
| `stress` | 0-150 | 16 minutes | Ramping stress test |
| `spike` | 10-500-10 | 2 minutes | Sudden load spike |
| `soak` | 50 | 2 hours | Long-running stability |

## Writing Test Scripts

### Basic Structure

```javascript
import { config } from './lib/config.js';
import * as pg from './lib/postgres.js';
import * as mongo from './lib/mongodb.js';
import { check } from 'k6';

export const options = {
  vus: 10,
  duration: '5m',
  thresholds: {
    checks: ['rate>0.95'],
    pg_write_duration: ['p(95)<100'],
  },
};

export function setup() {
  pg.openConnection();
  mongo.openConnection();
}

export default function() {
  // Write to PostgreSQL
  const customer = {
    id: `test-${__VU}-${__ITER}`,
    email: `test-${__VU}-${__ITER}@example.com`,
    status: 'active',
  };

  const writeResult = pg.insertCustomer(customer);
  check(writeResult, {
    'write succeeded': (r) => r.success === true,
  });

  // Verify in MongoDB (with retries for CDC propagation)
  const readResult = mongo.findCustomer(customer.id);
  check(readResult, {
    'document replicated': (r) => r.found === true,
  });
}

export function teardown() {
  pg.closeConnection();
  mongo.closeConnection();
}
```

### Available Helper Functions

#### PostgreSQL (`lib/postgres.js`)

```javascript
pg.openConnection()           // Open database connection
pg.closeConnection()          // Close database connection
pg.insertCustomer(customer)   // Insert/upsert customer
pg.insertAddress(address)     // Insert/upsert address
pg.insertOrder(order)         // Insert order
pg.updateCustomerStatus(id, status)  // Update customer status
pg.deleteCustomer(id)         // Delete customer
```

#### MongoDB (`lib/mongodb.js`)

```javascript
mongo.openConnection()        // Open MongoDB connection
mongo.closeConnection()       // Close MongoDB connection
mongo.findCustomer(id, maxRetries, retryDelayMs)  // Find with retry
mongo.findAddress(id)         // Find address
mongo.findOrder(id)           // Find order
mongo.countCustomers()        // Count customers
```

#### Custom Metrics (`lib/metrics.js`)

```javascript
import {
  cdcE2ELatency,
  cdcCreates,
  cdcUpdates,
  cdcDeletes,
  cdcSuccessRate,
  recordCdcLatency,
  recordSuccess,
  recordFailure,
} from './lib/metrics.js';
```

## Running Tests

### Basic Run

```bash
docker compose -f k6/docker-compose.k6.yml run --rm k6 run /scripts/health-check.js
```

### With Prometheus Output

```bash
docker compose -f k6/docker-compose.k6.yml run --rm k6 run \
  --out experimental-prometheus-rw \
  /scripts/your-test.js
```

### With Custom Duration/VUs

```bash
docker compose -f k6/docker-compose.k6.yml run --rm k6 run \
  --vus 50 \
  --duration 10m \
  /scripts/your-test.js
```

### Save Results to File

```bash
docker compose -f k6/docker-compose.k6.yml run --rm k6 run \
  --out json=/results/test-results.json \
  /scripts/your-test.js
```

## Viewing Metrics

### Prometheus

Metrics are available in Prometheus at `http://localhost:9090`. Query examples:

```promql
# PostgreSQL write latency P95
histogram_quantile(0.95, sum(rate(k6_pg_write_duration_bucket[1m])) by (le))

# MongoDB read latency P95
histogram_quantile(0.95, sum(rate(k6_mongo_read_duration_bucket[1m])) by (le))

# CDC success rate
sum(rate(k6_cdc_success_rate_total[1m])) / sum(rate(k6_cdc_success_rate_total[1m]))

# Total records inserted
sum(k6_pg_records_inserted_total)
```

### Grafana

1. Open Grafana at `http://localhost:3000`
2. Navigate to Dashboards
3. Look for k6 dashboards (if provisioned)

## Troubleshooting

### Connection Refused Errors

Ensure the CDC infrastructure is running:

```bash
docker compose ps
docker compose logs postgres mongodb
```

### Extension Not Found

Verify extensions are installed:

```bash
docker compose -f k6/docker-compose.k6.yml run --rm k6 version
```

### Network Issues

Ensure k6 container is on the same network:

```bash
docker network ls | grep cdc-network
docker network inspect cdc-network
```

### Rebuild Image

If you modify the Dockerfile:

```bash
docker compose -f k6/docker-compose.k6.yml build --no-cache k6
```

## Extension Versions

| Extension | Version | Notes |
|-----------|---------|-------|
| k6 | v0.51.0 | Base k6 version |
| xk6-sql | v0.4.1 | Pre-modularization (includes PostgreSQL driver) |
| xk6-mongo | v0.1.3 | GhMartingit fork, Go 1.23 compatible |
| xk6-output-prometheus-remote | v0.5.0 | Prometheus remote write support |

## References

- [k6 Documentation](https://k6.io/docs/)
- [xk6-sql Extension](https://github.com/grafana/xk6-sql)
- [xk6-mongo Extension](https://github.com/GhMartingit/xk6-mongo)
- [k6 Prometheus Remote Write](https://k6.io/docs/results-output/real-time/prometheus-remote-write/)
