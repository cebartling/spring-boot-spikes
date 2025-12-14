# k6 Load Testing

This directory contains load testing scripts for the Saga Pattern Spike application using [k6](https://k6.io/).

## Prerequisites

### Option 1: Install k6 locally

```bash
# macOS
brew install k6

# Windows (Chocolatey)
choco install k6

# Linux (Debian/Ubuntu)
sudo gpg -k
sudo gpg --no-default-keyring --keyring /usr/share/keyrings/k6-archive-keyring.gpg --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys C5AD17C747E3415A3642D57D77C6C491D6AC1D69
echo "deb [signed-by=/usr/share/keyrings/k6-archive-keyring.gpg] https://dl.k6.io/deb stable main" | sudo tee /etc/apt/sources.list.d/k6.list
sudo apt-get update
sudo apt-get install k6
```

### Option 2: Use Docker (no installation required)

```bash
# Run tests using Docker
make load-test-docker
```

## Quick Start

1. **Start infrastructure**:
   ```bash
   make infra-up
   ```

2. **Start the application**:
   ```bash
   make run
   ```

3. **Run smoke test**:
   ```bash
   make load-test-smoke
   ```

4. **View results in Grafana**:
   Open http://localhost:3000 and navigate to the "k6 Load Testing" dashboard.

## Test Scenarios

### Smoke Test (`smoke.js`)

Quick validation that the system works under minimal load.

| Parameter | Value |
|-----------|-------|
| Virtual Users | 1-2 |
| Duration | 1 minute |
| p95 Threshold | < 500ms |
| Error Rate | < 1% |

```bash
make load-test-smoke
# Or directly:
k6 run load-tests/scripts/scenarios/smoke.js
```

### Load Test (`load.js`)

Validates system under expected normal production load.

| Parameter | Value |
|-----------|-------|
| Virtual Users | 10-50 (ramping) |
| Duration | 5 minutes |
| p95 Threshold | < 1000ms |
| Error Rate | < 5% |

**Stages**:
1. Ramp up to 50 VUs (2 min)
2. Hold at 50 VUs (2 min)
3. Ramp down (1 min)

```bash
make load-test-load
```

### Stress Test (`stress.js`)

Finds system breaking points and observes recovery behavior.

| Parameter | Value |
|-----------|-------|
| Virtual Users | 100-200 (with spike) |
| Duration | 10 minutes |
| p95 Threshold | < 3000ms |
| Error Rate | < 15% |

**Stages**:
1. Ramp up to 100 VUs (3 min)
2. Spike to 200 VUs (1 min)
3. Recovery to 100 VUs (2 min)
4. Hold at 100 VUs (2 min)
5. Ramp down (2 min)

```bash
make load-test-stress
```

### Soak Test (`soak.js`)

Detects memory leaks and performance degradation over time.

| Parameter | Value |
|-----------|-------|
| Virtual Users | 30 (constant) |
| Duration | 30 minutes |
| p95 Threshold | < 1500ms |
| Error Rate | < 5% |

```bash
make load-test-soak

# Or with custom duration:
k6 run --env SOAK_DURATION=60m load-tests/scripts/scenarios/soak.js
```

## Directory Structure

```
load-tests/
├── scripts/
│   ├── lib/
│   │   ├── config.js           # Shared configuration
│   │   ├── helpers.js          # Utility functions
│   │   └── data-generators.js  # Test data generation
│   ├── scenarios/
│   │   ├── smoke.js            # Smoke test
│   │   ├── load.js             # Load test
│   │   ├── stress.js           # Stress test
│   │   └── soak.js             # Soak test
│   └── api/
│       ├── orders.js           # Order API tests
│       └── health.js           # Health check tests
├── results/                     # Test output (gitignored)
└── README.md                    # This file
```

## Configuration

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `BASE_URL` | `http://localhost:8080` | Application base URL |
| `SOAK_DURATION` | `26m` | Soak test duration |
| `K6_PROMETHEUS_PUSHGATEWAY_URL` | `http://localhost:9091` | Pushgateway URL |

### Custom Base URL

```bash
# Use custom URL
k6 run --env BASE_URL=http://my-server:8080 load-tests/scripts/scenarios/smoke.js

# Or with make
BASE_URL=http://my-server:8080 make load-test-smoke
```

## Metrics and Visualization

### Grafana Dashboard

The k6 Load Testing dashboard is automatically provisioned in Grafana at http://localhost:3000.

**Dashboard Panels**:
- **Overview**: Active VUs, request rate, error rate, p95 latency
- **Response Times**: Percentile trends, VU count over time
- **Throughput**: Request rate by endpoint, error rate timeline
- **Custom Metrics**: Orders created, saga duration
- **Data Transfer**: Sent/received rates, timing breakdown

### Prometheus Metrics

k6 metrics are available via the Prometheus Pushgateway at http://localhost:9091.

Key metrics:
- `k6_http_reqs_total` - Total HTTP requests
- `k6_http_req_duration` - Request duration (with quantiles)
- `k6_http_req_failed_total` - Failed requests
- `k6_vus` - Active virtual users
- `k6_iterations_total` - Test iterations

### JSON Output

All test runs save JSON results to `load-tests/results/`:

```bash
# View latest results
ls -la load-tests/results/
```

## Writing Custom Tests

### Basic Test Structure

```javascript
import { THRESHOLDS, VU_CONFIG, COMMON_TAGS } from '../lib/config.js';
import { createOrder, getOrderStatus } from '../api/orders.js';
import { checkHealth } from '../api/health.js';
import { randomSleep } from '../lib/helpers.js';

export const options = {
    vus: 10,
    duration: '1m',
    thresholds: THRESHOLDS.load,
    tags: { ...COMMON_TAGS, testType: 'custom' },
};

export function setup() {
    const health = checkHealth();
    if (!health.success) throw new Error('App not healthy');
    return {};
}

export default function () {
    const result = createOrder();
    if (result.success) {
        getOrderStatus(result.orderId);
    }
    randomSleep(1, 2);
}
```

### Using Data Generators

```javascript
import { generateCreateOrderRequest, generateCustomerId } from '../lib/data-generators.js';

// Generate random order
const orderData = generateCreateOrderRequest();

// Generate consistent customer per VU
const customerId = getVUCustomerId(__VU);
```

## Troubleshooting

### Application Not Healthy

```
Error: Application is not healthy
```

Ensure the application is running and accessible:
```bash
curl http://localhost:8080/actuator/health
```

### High Error Rate

Check WireMock mappings and ensure external service mocks are configured:
```bash
curl http://localhost:8081/__admin/mappings
```

### No Metrics in Grafana

1. Verify Pushgateway is running: `curl http://localhost:9091/-/healthy`
2. Check Prometheus targets: `curl http://localhost:9090/api/v1/targets`
3. Verify k6 is exporting metrics

### Resource Constraints

For local testing with limited resources:
- Use smoke test for validation
- Reduce VU counts in load/stress tests
- Monitor system resources during tests

## Best Practices

1. **Always run smoke test first** to validate basic functionality
2. **Start infrastructure before tests** (`make infra-up`)
3. **Monitor Grafana during tests** for real-time insights
4. **Run stress/soak tests carefully** - they can impact system stability
5. **Review results after each test** - check for degradation patterns
6. **Clean up test data periodically** to prevent database bloat

## References

- [k6 Documentation](https://k6.io/docs/)
- [k6 Test Types](https://k6.io/docs/test-types/)
- [k6 Prometheus Output](https://k6.io/docs/results-output/real-time/prometheus-remote-write/)
- [Grafana k6 Dashboards](https://grafana.com/grafana/dashboards/?search=k6)
