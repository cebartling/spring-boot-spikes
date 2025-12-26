# Grafana Dashboards

This document describes the Grafana dashboards available for monitoring the CDC pipeline.

## Dashboard Overview

| Dashboard | URL | Purpose |
|-----------|-----|---------|
| **k6 Load Testing** | `/d/k6-load-testing` | Real-time k6 test metrics |
| **CDC Pipeline Overview** | `/d/cdc-overview` | High-level CDC pipeline health |
| **Consumer Performance** | `/d/consumer-perf` | Kafka consumer metrics |
| **MongoDB Operations** | `/d/mongodb-ops` | MongoDB connection pool and k6 metrics |
| **Service Map & Traces** | `/d/service-map` | Distributed tracing visualization |
| **Logs Explorer** | `/d/logs-explorer` | Centralized log analysis |

---

## k6 Load Testing Dashboard

**URL:** `http://localhost:3000/d/k6-load-testing/k6-load-testing`

Primary dashboard for monitoring k6 load tests in real-time. Displays metrics only when k6 tests are actively running.

### Panels

| Panel | Type | Description |
|-------|------|-------------|
| **Active Virtual Users** | Stat | Current number of virtual users executing tests. Shows the concurrency level of the test. |
| **CDC Success Rate** | Stat | Percentage of CDC operations that completed successfully (data replicated from PostgreSQL to MongoDB). Color-coded: red (<90%), yellow (90-99%), green (>99%). |
| **Total Iterations** | Stat | Cumulative count of test iterations completed since test start. |
| **Iterations/sec** | Stat | Current rate of test iterations per second. Indicates test throughput. |
| **CDC End-to-End Latency** | Time Series | Latency percentiles (p50, p90, p95, p99) measuring time from PostgreSQL write to MongoDB read confirmation. Key metric for CDC pipeline performance. |
| **Virtual Users Over Time** | Time Series | Historical view of VU count and max VUs throughout the test run. Useful for ramping scenarios. |
| **PostgreSQL Write Duration** | Time Series | Latency percentiles (p50, p95, p99) for writing records to PostgreSQL source database. |
| **MongoDB Read Duration** | Time Series | Latency percentiles (p50, p95, p99) for reading/verifying records in MongoDB target database. |
| **CDC Operations** | Time Series (Bars) | Stacked bar chart showing create/update/delete operations per second. Visualizes operation mix. |
| **Errors** | Time Series | Error rates for PostgreSQL writes, MongoDB reads, and check failures. Should remain at zero for healthy tests. |
| **Iteration Duration** | Time Series | Full iteration duration percentiles (p50, p95, p99). Includes all operations within a single test iteration. |

---

## CDC Pipeline Overview Dashboard

**URL:** `http://localhost:3000/d/cdc-overview/cdc-pipeline-overview`

High-level dashboard showing overall CDC pipeline health combining consumer metrics and k6 test results.

### Panels

| Panel | Type | Description |
|-------|------|-------------|
| **Records Consumed (Rate)** | Stat | Current rate of Kafka records consumed by the CDC consumer. Indicates pipeline throughput. Color thresholds: green (normal), yellow (>100/sec), red (>500/sec). |
| **Consumer Lag** | Gauge | Maximum lag across all partitions. Shows how far behind the consumer is from the latest messages. Green (0-1000), yellow (1000-5000), red (>5000). |
| **k6 CDC Success Rate** | Stat | CDC replication success percentage from active k6 tests. Color-coded by success level. |
| **Kafka Listener Latency (p99)** | Stat | 99th percentile latency for processing Kafka messages. Green (<100ms), yellow (100-500ms), red (>500ms). |
| **Records Consumed Over Time** | Time Series | Historical view of records/sec consumed and consumer lag. Shows throughput trends. |
| **k6 Load Test Operations** | Time Series | PostgreSQL inserts/sec, MongoDB reads/sec, and CDC creates/sec during k6 tests. |
| **Kafka Listener Latency Distribution** | Heatmap | Visual distribution of Kafka message processing latencies. Identifies latency patterns. |
| **End-to-End Latency (k6 Tests)** | Time Series | CDC E2E latency percentiles (p50, p95, p99) from k6 tests. |
| **Database Operation Latency (k6)** | Time Series | PostgreSQL write p95 and MongoDB read p95 latencies. |
| **Recent Traces** | Table | Latest distributed traces from the CDC consumer service. Links to Tempo for detailed trace analysis. |

---

## Consumer Performance Dashboard

**URL:** `http://localhost:3000/d/consumer-perf/consumer-performance`

Detailed Kafka consumer metrics for monitoring the CDC consumer's interaction with Kafka.

### Panels

| Panel | Type | Description |
|-------|------|-------------|
| **Consumer Group Lag by Topic** | Time Series | Per-partition consumer lag for CDC topics. Shows which partitions are falling behind. |
| **Records Consumed Rate** | Time Series | Records/sec consumed grouped by topic. Shows consumption throughput per topic. |
| **Fetch Latency** | Time Series | Average and maximum fetch latency from Kafka brokers. High values indicate broker or network issues. |
| **Bytes Consumed Rate** | Time Series | Data throughput in bytes/sec from Kafka. Useful for capacity planning. |
| **Commit Rate** | Time Series | Rate of offset commits to Kafka. Indicates acknowledgement frequency. |
| **Rebalances (1h)** | Stat | Number of consumer group rebalances in the last hour. Frequent rebalances indicate instability. Green (0-5), yellow (5-10), red (>10). |
| **Assigned Partitions** | Stat | Number of Kafka partitions assigned to this consumer. Should match expected partition count. |
| **Fetch Rate** | Time Series | Number of fetch requests per second to Kafka brokers. |
| **Last Poll (seconds ago)** | Stat | Seconds since last consumer poll. Green (<30s), yellow (30-60s), red (>60s indicates consumer stall). |
| **Last Heartbeat (seconds ago)** | Stat | Seconds since last heartbeat to coordinator. Green (<10s), yellow (10-30s), red (>30s indicates potential session timeout). |
| **Network I/O Rate** | Time Series | Incoming and outgoing bytes/sec. Shows network utilization. |

---

## MongoDB Operations Dashboard

**URL:** `http://localhost:3000/d/mongodb-ops/mongodb-operations`

MongoDB connection pool status and k6 test metrics for the MongoDB target database.

### Panels

| Panel | Type | Description |
|-------|------|-------------|
| **Connection Pool Size** | Time Series | MongoDB driver connection pool size and currently checked-out connections. Shows pool utilization. |
| **Connection Pool Wait Queue** | Time Series | Number of operations waiting for a connection. High values indicate pool exhaustion. |
| **Checkout Failures** | Stat | Connection checkout failures in the last hour. Any failures indicate configuration or capacity issues. Green (0), yellow (1), red (>10). |
| **k6: MongoDB Read Latency** | Time Series | MongoDB read operation latency percentiles (p50, p95, p99) from k6 tests. Only populated during test runs. |
| **k6: Documents Found** | Time Series | Rate of documents found per second during k6 tests. Shows verification throughput. |
| **k6: CDC Success Rate** | Gauge | Percentage of successful CDC replications from k6 tests. Red (<90%), yellow (90-95%), green (>95%). |
| **k6: CDC E2E Latency** | Time Series | End-to-end latency percentiles (p50, p95, p99) from PostgreSQL write to MongoDB read confirmation. |

---

## Service Map & Traces Dashboard

**URL:** `http://localhost:3000/d/service-map/service-map`

Distributed tracing visualization using Tempo. Shows service dependencies and trace analysis.

### Panels

| Panel | Type | Description |
|-------|------|-------------|
| **Service Dependency Graph** | Node Graph | Visual representation of service-to-service calls. Shows how the CDC consumer connects to Kafka, PostgreSQL, and MongoDB. |
| **Trace Search** | Traces | Search interface for traces from the CDC consumer. Click traces to see detailed span breakdowns. |
| **Span Duration Histogram** | Histogram | Distribution of span durations by operation name (p95). Identifies slow operations. |
| **Error Traces** | Table | Recent traces with error status. Quickly identify failed operations and their trace IDs. |

---

## Logs Explorer Dashboard

**URL:** `http://localhost:3000/d/logs-explorer/logs-explorer`

Centralized log analysis using Loki. Provides filtering and search capabilities for CDC consumer logs.

### Panels

| Panel | Type | Description |
|-------|------|-------------|
| **Log Volume by Level** | Time Series (Bars) | Stacked bar chart showing log counts by level over time. Red (ERROR), yellow (WARN), green (INFO), blue (DEBUG). Useful for spotting error spikes. |
| **Application Logs** | Logs | Full log stream from the CDC consumer with timestamps, levels, and message content. Supports search and filtering. |
| **Error Logs** | Logs | Filtered view showing only ERROR level logs. Quickly identify and investigate errors. |
| **Logs Integration Info** | Text | Information about the logging pipeline and available labels. |

### Variables

| Variable | Options | Description |
|----------|---------|-------------|
| **level** | All, Error, Warn, Info, Debug | Filter logs by severity level. |

---

## Data Sources

| Source | Type | Purpose |
|--------|------|---------|
| **Prometheus** | Metrics | Stores time-series metrics from CDC consumer and k6 tests |
| **Tempo** | Traces | Stores distributed traces for request flow analysis |
| **Loki** | Logs | Stores application logs for centralized log analysis |

---

## Accessing Dashboards

All dashboards are accessible at `http://localhost:3000` with default credentials:
- **Username:** admin
- **Password:** admin

Dashboards auto-refresh at intervals specified in their configuration (typically 5-30 seconds).
