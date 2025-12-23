# PLAN-022: k6 Load Testing Infrastructure

## Objective

Set up k6 load testing infrastructure with custom extensions (xk6-sql, xk6-mongo) for directly testing the CDC pipeline from PostgreSQL through to MongoDB materialization.

## Parent Feature

[FEATURE-002](../features/FEATURE-002.md) - Section 2.7.1-2.7.2: k6 Infrastructure & Extensions

## Dependencies

- PLAN-011: MongoDB Infrastructure Setup
- PLAN-019: Grafana LGTM Infrastructure

## Changes

### Files to Create/Modify

| File | Purpose |
|------|---------|
| `k6/Dockerfile` | Custom k6 build with extensions |
| `k6/docker-compose.k6.yml` | k6 services configuration |
| `k6/scripts/lib/config.js` | Shared configuration |
| `k6/scripts/lib/postgres.js` | PostgreSQL helper module |
| `k6/scripts/lib/mongodb.js` | MongoDB helper module |
| `k6/scripts/lib/metrics.js` | Custom metrics definitions |
| `docker-compose.yml` | Add k6 service |

### k6 Architecture

```mermaid
flowchart TB
    subgraph K6["k6 Load Generator"]
        SCRIPT[Test Script]
        EXT_SQL[xk6-sql Extension]
        EXT_MONGO[xk6-mongo Extension]
        METRICS[Custom Metrics]
    end

    subgraph CDC["CDC Pipeline"]
        PG[(PostgreSQL)]
        DEB[Debezium]
        KAFKA[Kafka]
        CONSUMER[Consumer]
        MONGO[(MongoDB)]
    end

    subgraph OBS["Observability"]
        PROM[(Prometheus)]
        GRAFANA[Grafana]
    end

    K6 -->|Write| PG
    K6 -->|Read| MONGO
    PG --> DEB
    DEB --> KAFKA
    KAFKA --> CONSUMER
    CONSUMER --> MONGO

    K6 -->|Push Metrics| PROM
    PROM --> GRAFANA
```

### Custom k6 Dockerfile

```dockerfile
# k6/Dockerfile
FROM golang:1.21-alpine AS builder

# Install xk6
RUN go install go.k6.io/xk6/cmd/xk6@latest

# Build k6 with extensions
RUN xk6 build \
    --with github.com/grafana/xk6-sql@latest \
    --with github.com/grafana/xk6-sql-driver-postgres@latest \
    --with github.com/oleiade/xk6-mongo@latest \
    --with github.com/grafana/xk6-output-prometheus-remote@latest \
    --output /k6

FROM alpine:3.19

# Install runtime dependencies
RUN apk add --no-cache ca-certificates

# Copy k6 binary
COPY --from=builder /k6 /usr/bin/k6

# Create non-root user
RUN adduser -D -u 1000 k6user
USER k6user

WORKDIR /scripts

ENTRYPOINT ["k6"]
```

### Docker Compose k6 Services (docker-compose.k6.yml)

```yaml
version: '3.8'

services:
  k6:
    build:
      context: ./k6
      dockerfile: Dockerfile
    container_name: cdc-k6
    volumes:
      - ./k6/scripts:/scripts:ro
      - ./k6/results:/results
    environment:
      K6_PROMETHEUS_RW_SERVER_URL: http://prometheus:9090/api/v1/write
      K6_PROMETHEUS_RW_TREND_AS_NATIVE_HISTOGRAM: "true"
      K6_PROMETHEUS_RW_PUSH_INTERVAL: 5s
      POSTGRES_HOST: postgres
      POSTGRES_PORT: 5432
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
      POSTGRES_DB: cdc_source
      MONGODB_URI: mongodb://cdc_app:cdc_app_password@mongodb:27017/cdc_materialized?authSource=cdc_materialized
    depends_on:
      postgres:
        condition: service_healthy
      mongodb:
        condition: service_healthy
      prometheus:
        condition: service_healthy
    networks:
      - cdc-network
    profiles:
      - load-test

  k6-dashboard:
    image: grafana/grafana:11.0.0
    container_name: cdc-k6-dashboard
    ports:
      - "3001:3000"
    environment:
      GF_AUTH_ANONYMOUS_ENABLED: "true"
      GF_AUTH_ANONYMOUS_ORG_ROLE: Viewer
    volumes:
      - ./k6/grafana/provisioning:/etc/grafana/provisioning:ro
    depends_on:
      - prometheus
    networks:
      - cdc-network
    profiles:
      - load-test

networks:
  cdc-network:
    external: true
```

### Shared Configuration (lib/config.js)

```javascript
// k6/scripts/lib/config.js
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
```

### PostgreSQL Helper Module (lib/postgres.js)

```javascript
// k6/scripts/lib/postgres.js
import sql from 'k6/x/sql';
import { config } from './config.js';
import { Trend, Counter } from 'k6/metrics';

// Custom metrics
const pgWriteDuration = new Trend('pg_write_duration', true);
const pgWriteErrors = new Counter('pg_write_errors');
const pgRecordsInserted = new Counter('pg_records_inserted');

let db = null;

export function openConnection() {
  if (!db) {
    db = sql.open('postgres', config.postgres.connectionString);
  }
  return db;
}

export function closeConnection() {
  if (db) {
    db.close();
    db = null;
  }
}

export function insertCustomer(customer) {
  const start = Date.now();
  try {
    const result = db.exec(`
      INSERT INTO customer (id, email, status, created_at, updated_at)
      VALUES ($1, $2, $3, NOW(), NOW())
      ON CONFLICT (id) DO UPDATE SET
        email = EXCLUDED.email,
        status = EXCLUDED.status,
        updated_at = NOW()
      RETURNING id
    `, customer.id, customer.email, customer.status);

    pgWriteDuration.add(Date.now() - start);
    pgRecordsInserted.add(1);
    return { success: true, id: customer.id, timestamp: Date.now() };
  } catch (error) {
    pgWriteDuration.add(Date.now() - start);
    pgWriteErrors.add(1);
    return { success: false, error: error.message };
  }
}

export function insertAddress(address) {
  const start = Date.now();
  try {
    db.exec(`
      INSERT INTO address (id, customer_id, type, street, city, state, postal_code, country, is_default, created_at, updated_at)
      VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, NOW(), NOW())
      ON CONFLICT (id) DO UPDATE SET
        street = EXCLUDED.street,
        city = EXCLUDED.city,
        updated_at = NOW()
    `, address.id, address.customerId, address.type, address.street,
       address.city, address.state, address.postalCode, address.country, address.isDefault);

    pgWriteDuration.add(Date.now() - start);
    pgRecordsInserted.add(1);
    return { success: true, id: address.id };
  } catch (error) {
    pgWriteDuration.add(Date.now() - start);
    pgWriteErrors.add(1);
    return { success: false, error: error.message };
  }
}

export function insertOrder(order) {
  const start = Date.now();
  try {
    db.exec(`
      INSERT INTO orders (id, customer_id, status, total_amount, created_at, updated_at)
      VALUES ($1, $2, $3, $4, NOW(), NOW())
    `, order.id, order.customerId, order.status, order.totalAmount);

    pgWriteDuration.add(Date.now() - start);
    pgRecordsInserted.add(1);
    return { success: true, id: order.id };
  } catch (error) {
    pgWriteDuration.add(Date.now() - start);
    pgWriteErrors.add(1);
    return { success: false, error: error.message };
  }
}

export function updateCustomerStatus(customerId, newStatus) {
  const start = Date.now();
  try {
    db.exec(`
      UPDATE customer SET status = $1, updated_at = NOW()
      WHERE id = $2
    `, newStatus, customerId);

    pgWriteDuration.add(Date.now() - start);
    return { success: true };
  } catch (error) {
    pgWriteDuration.add(Date.now() - start);
    pgWriteErrors.add(1);
    return { success: false, error: error.message };
  }
}

export function deleteCustomer(customerId) {
  const start = Date.now();
  try {
    db.exec(`DELETE FROM customer WHERE id = $1`, customerId);
    pgWriteDuration.add(Date.now() - start);
    return { success: true };
  } catch (error) {
    pgWriteDuration.add(Date.now() - start);
    pgWriteErrors.add(1);
    return { success: false, error: error.message };
  }
}
```

### MongoDB Helper Module (lib/mongodb.js)

```javascript
// k6/scripts/lib/mongodb.js
import mongo from 'k6/x/mongo';
import { config } from './config.js';
import { Trend, Counter, Gauge } from 'k6/metrics';

// Custom metrics
const mongoReadDuration = new Trend('mongo_read_duration', true);
const mongoReadErrors = new Counter('mongo_read_errors');
const mongoDocumentsFound = new Counter('mongo_documents_found');
const mongoDocumentsNotFound = new Counter('mongo_documents_not_found');

let client = null;

export function openConnection() {
  if (!client) {
    client = mongo.newClient(config.mongodb.uri);
  }
  return client;
}

export function closeConnection() {
  if (client) {
    client.close();
    client = null;
  }
}

export function findCustomer(customerId, maxRetries = 10, retryDelayMs = 500) {
  const start = Date.now();

  for (let attempt = 0; attempt < maxRetries; attempt++) {
    try {
      const collection = client.database(config.mongodb.database).collection(config.mongodb.collections.customers);
      const result = collection.findOne({ _id: customerId });

      if (result) {
        mongoReadDuration.add(Date.now() - start);
        mongoDocumentsFound.add(1);
        return {
          found: true,
          document: result,
          attempts: attempt + 1,
          latency: Date.now() - start
        };
      }

      // Not found yet, wait and retry
      if (attempt < maxRetries - 1) {
        sleep(retryDelayMs / 1000);
      }
    } catch (error) {
      mongoReadDuration.add(Date.now() - start);
      mongoReadErrors.add(1);
      return { found: false, error: error.message };
    }
  }

  mongoReadDuration.add(Date.now() - start);
  mongoDocumentsNotFound.add(1);
  return {
    found: false,
    attempts: maxRetries,
    latency: Date.now() - start
  };
}

export function findAddress(addressId) {
  const start = Date.now();
  try {
    const collection = client.database(config.mongodb.database).collection(config.mongodb.collections.addresses);
    const result = collection.findOne({ _id: addressId });

    mongoReadDuration.add(Date.now() - start);
    if (result) {
      mongoDocumentsFound.add(1);
    } else {
      mongoDocumentsNotFound.add(1);
    }
    return { found: !!result, document: result };
  } catch (error) {
    mongoReadDuration.add(Date.now() - start);
    mongoReadErrors.add(1);
    return { found: false, error: error.message };
  }
}

export function findOrder(orderId) {
  const start = Date.now();
  try {
    const collection = client.database(config.mongodb.database).collection(config.mongodb.collections.orders);
    const result = collection.findOne({ _id: orderId });

    mongoReadDuration.add(Date.now() - start);
    if (result) {
      mongoDocumentsFound.add(1);
    } else {
      mongoDocumentsNotFound.add(1);
    }
    return { found: !!result, document: result };
  } catch (error) {
    mongoReadDuration.add(Date.now() - start);
    mongoReadErrors.add(1);
    return { found: false, error: error.message };
  }
}

export function countCustomers() {
  try {
    const collection = client.database(config.mongodb.database).collection(config.mongodb.collections.customers);
    return collection.count({});
  } catch (error) {
    mongoReadErrors.add(1);
    return -1;
  }
}

// Import sleep from k6
import { sleep } from 'k6';
```

### Custom Metrics Module (lib/metrics.js)

```javascript
// k6/scripts/lib/metrics.js
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
```

### Basic Health Check Script

```javascript
// k6/scripts/health-check.js
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
```

## Directory Structure

```
k6/
├── Dockerfile
├── docker-compose.k6.yml
├── scripts/
│   ├── lib/
│   │   ├── config.js
│   │   ├── postgres.js
│   │   ├── mongodb.js
│   │   └── metrics.js
│   ├── health-check.js
│   └── ... (test scenarios in PLAN-023)
├── results/
│   └── .gitkeep
└── grafana/
    └── provisioning/
        └── dashboards/
            └── k6-results.json
```

## Commands to Run

```bash
# Create k6 directory structure
mkdir -p k6/scripts/lib k6/results k6/grafana/provisioning/dashboards
touch k6/results/.gitkeep

# Build custom k6 image
docker compose -f k6/docker-compose.k6.yml build k6

# Verify k6 extensions are loaded
docker compose -f k6/docker-compose.k6.yml run --rm k6 version

# Run health check
docker compose -f k6/docker-compose.k6.yml run --rm k6 run /scripts/health-check.js

# Start main CDC stack (if not running)
docker compose up -d

# Wait for all services to be healthy
docker compose ps --format json | jq -r '.[] | select(.Health != "healthy") | .Name'

# Run k6 with Prometheus output
docker compose -f k6/docker-compose.k6.yml run --rm k6 run \
  --out experimental-prometheus-rw \
  /scripts/health-check.js

# View k6 metrics in Grafana
open http://localhost:3000/d/k6-results

# Clean up test data
docker compose exec postgres psql -U postgres -d cdc_source -c \
  "DELETE FROM customer WHERE email LIKE '%@test.com';"
```

## Acceptance Criteria

- [ ] Custom k6 image builds successfully with xk6-sql and xk6-mongo extensions
- [ ] k6 can connect to PostgreSQL (health check passes)
- [ ] k6 can connect to MongoDB (health check passes)
- [ ] k6 can write test records to PostgreSQL
- [ ] k6 metrics export to Prometheus (pg_write_duration, mongo_read_duration)
- [ ] Shared configuration loads correctly (PostgreSQL, MongoDB, thresholds)
- [ ] Helper modules work correctly with custom metrics and error counting

## k6 Extension Capabilities

```mermaid
flowchart LR
    subgraph XK6SQL["xk6-sql Extension"]
        PG[PostgreSQL Driver]
        MYSQL[MySQL Driver]
        SQLITE[SQLite Driver]
    end

    subgraph XK6MONGO["xk6-mongo Extension"]
        CRUD[CRUD Operations]
        AGG[Aggregation]
        IDX[Index Operations]
    end

    subgraph XK6PROM["xk6-prometheus-rw"]
        RW[Remote Write]
        HIST[Native Histograms]
    end

    K6[k6 Core] --> XK6SQL
    K6 --> XK6MONGO
    K6 --> XK6PROM
```

## Estimated Complexity

Medium-High - Requires building custom k6 binary and configuring multiple extensions.

## Notes

- xk6-sql requires separate driver module for PostgreSQL
- xk6-mongo uses official MongoDB Go driver
- Prometheus remote write enables real-time metric streaming
- Custom metrics prefixed with `k6_` in Prometheus
- Consider using k6 Cloud for distributed load testing
- Docker build caches Go modules for faster rebuilds
- Health check script validates infrastructure before full tests
- Results directory should be gitignored but tracked with .gitkeep
