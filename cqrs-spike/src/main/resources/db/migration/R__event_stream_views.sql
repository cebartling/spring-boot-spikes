-- Repeatable Migration: Event stream views
-- Description: Create and maintain views and materialized views for event store querying and monitoring
-- Note: This migration is repeatable and will re-run when the checksum changes

SET search_path TO event_store, public;

-- Drop existing views (required for repeatable migrations)
DROP VIEW IF EXISTS v_recent_events CASCADE;
DROP MATERIALIZED VIEW IF EXISTS mv_event_statistics CASCADE;

-- Recent Events View
-- Provides a quick view of the most recent 1000 events across all aggregates
-- Useful for debugging, monitoring, and event replay scenarios
CREATE OR REPLACE VIEW v_recent_events AS
SELECT
    de.event_id,
    de.event_type,
    es.aggregate_type,
    es.aggregate_id,
    de.aggregate_version,
    de.occurred_at,
    de.correlation_id,
    de.causation_id,
    de.user_id,
    de.event_data,
    de.metadata
FROM domain_event de
JOIN event_stream es ON de.stream_id = es.stream_id
ORDER BY de.occurred_at DESC
LIMIT 1000;

COMMENT ON VIEW v_recent_events IS
    'Most recent 1000 events across all aggregates. Useful for debugging, monitoring, and event replay.';

-- Event Statistics Materialized View
-- Provides aggregate statistics for monitoring and reporting
-- Refresh periodically for updated statistics
CREATE MATERIALIZED VIEW mv_event_statistics AS
SELECT
    es.aggregate_type,
    COUNT(DISTINCT es.aggregate_id) as aggregate_count,
    COUNT(de.event_id) as event_count,
    MAX(de.occurred_at) as last_event_time,
    MIN(de.occurred_at) as first_event_time,
    AVG(es.version) as avg_version,
    MAX(es.version) as max_version,
    MIN(es.version) as min_version
FROM event_stream es
LEFT JOIN domain_event de ON es.stream_id = de.stream_id
GROUP BY es.aggregate_type;

CREATE UNIQUE INDEX idx_mv_event_stats_type ON mv_event_statistics(aggregate_type);

COMMENT ON MATERIALIZED VIEW mv_event_statistics IS
    'Aggregate statistics for monitoring and reporting. Refresh with: REFRESH MATERIALIZED VIEW mv_event_statistics;';

-- Event Type Statistics View
-- Provides breakdown of events by type
CREATE OR REPLACE VIEW v_event_type_stats AS
SELECT
    event_type,
    COUNT(*) as event_count,
    MAX(occurred_at) as last_occurred,
    MIN(occurred_at) as first_occurred,
    COUNT(DISTINCT stream_id) as affected_streams
FROM domain_event
GROUP BY event_type
ORDER BY event_count DESC;

COMMENT ON VIEW v_event_type_stats IS
    'Statistics grouped by event type. Shows frequency and distribution of different event types.';

-- Aggregate Stream View
-- Provides detailed view of each aggregate stream
CREATE OR REPLACE VIEW v_aggregate_streams AS
SELECT
    es.stream_id,
    es.aggregate_type,
    es.aggregate_id,
    es.version,
    es.created_at,
    es.updated_at,
    COUNT(de.event_id) as event_count,
    EXTRACT(EPOCH FROM (es.updated_at - es.created_at)) as lifetime_seconds
FROM event_stream es
LEFT JOIN domain_event de ON es.stream_id = de.stream_id
GROUP BY es.stream_id, es.aggregate_type, es.aggregate_id, es.version, es.created_at, es.updated_at
ORDER BY es.updated_at DESC;

COMMENT ON VIEW v_aggregate_streams IS
    'Detailed view of all aggregate streams with statistics. Includes event counts and stream lifetime.';

-- Correlation Tracking View
-- Helps track related events across multiple aggregates
CREATE OR REPLACE VIEW v_correlated_events AS
SELECT
    de.correlation_id,
    COUNT(DISTINCT de.stream_id) as affected_streams,
    COUNT(de.event_id) as event_count,
    MIN(de.occurred_at) as first_event,
    MAX(de.occurred_at) as last_event,
    ARRAY_AGG(DISTINCT de.event_type ORDER BY de.event_type) as event_types,
    ARRAY_AGG(DISTINCT es.aggregate_type ORDER BY es.aggregate_type) as aggregate_types
FROM domain_event de
JOIN event_stream es ON de.stream_id = es.stream_id
WHERE de.correlation_id IS NOT NULL
GROUP BY de.correlation_id
ORDER BY last_event DESC;

COMMENT ON VIEW v_correlated_events IS
    'Tracks events related by correlation_id. Useful for tracing distributed operations across aggregates.';
