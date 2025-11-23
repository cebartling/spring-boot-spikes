-- Event Store Schema
-- Creates tables for event sourcing pattern

-- Event Stream table (one per aggregate)
CREATE TABLE IF NOT EXISTS event_store.event_stream (
    stream_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    aggregate_type VARCHAR(255) NOT NULL,
    aggregate_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    version INTEGER NOT NULL DEFAULT 0,
    UNIQUE (aggregate_type, aggregate_id)
);

CREATE INDEX idx_event_stream_aggregate ON event_store.event_stream (aggregate_type, aggregate_id);

COMMENT ON TABLE event_store.event_stream IS 'Aggregate event streams';
COMMENT ON COLUMN event_store.event_stream.version IS 'Current version for optimistic locking';

-- Domain Events table (immutable event log)
CREATE TABLE IF NOT EXISTS event_store.domain_event (
    event_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    stream_id UUID NOT NULL REFERENCES event_store.event_stream(stream_id),
    event_type VARCHAR(255) NOT NULL,
    event_version INTEGER NOT NULL,
    aggregate_version INTEGER NOT NULL,
    event_data JSONB NOT NULL,
    metadata JSONB,
    occurred_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    causation_id UUID,
    correlation_id UUID
);

CREATE INDEX idx_domain_event_stream ON event_store.domain_event (stream_id, aggregate_version);
CREATE INDEX idx_domain_event_type ON event_store.domain_event (event_type);
CREATE INDEX idx_domain_event_occurred ON event_store.domain_event (occurred_at DESC);
CREATE INDEX idx_domain_event_correlation ON event_store.domain_event (correlation_id);
CREATE INDEX idx_domain_event_data ON event_store.domain_event USING GIN(event_data);

COMMENT ON TABLE event_store.domain_event IS 'Immutable domain events';
COMMENT ON COLUMN event_store.domain_event.aggregate_version IS 'Version of aggregate after this event';
COMMENT ON COLUMN event_store.domain_event.causation_id IS 'ID of command that caused this event';
COMMENT ON COLUMN event_store.domain_event.correlation_id IS 'Correlation ID for tracking related events';

-- Function for appending events with optimistic locking
CREATE OR REPLACE FUNCTION event_store.append_events(
    p_aggregate_type VARCHAR,
    p_aggregate_id UUID,
    p_expected_version INTEGER,
    p_events JSONB[]
) RETURNS UUID AS $$
DECLARE
    v_stream_id UUID;
    v_current_version INTEGER;
    v_event JSONB;
    v_new_version INTEGER;
BEGIN
    -- Get or create stream
    INSERT INTO event_store.event_stream (aggregate_type, aggregate_id, version)
    VALUES (p_aggregate_type, p_aggregate_id, 0)
    ON CONFLICT (aggregate_type, aggregate_id) DO NOTHING
    RETURNING stream_id, version INTO v_stream_id, v_current_version;

    IF v_stream_id IS NULL THEN
        SELECT stream_id, version INTO v_stream_id, v_current_version
        FROM event_store.event_stream
        WHERE aggregate_type = p_aggregate_type
          AND aggregate_id = p_aggregate_id;
    END IF;

    -- Optimistic concurrency check
    IF v_current_version != p_expected_version THEN
        RAISE EXCEPTION 'Concurrency violation: expected version %, but current version is %',
            p_expected_version, v_current_version;
    END IF;

    -- Append events
    v_new_version := v_current_version;
    FOREACH v_event IN ARRAY p_events LOOP
        v_new_version := v_new_version + 1;

        INSERT INTO event_store.domain_event (
            stream_id,
            event_type,
            event_version,
            aggregate_version,
            event_data,
            metadata,
            causation_id,
            correlation_id
        ) VALUES (
            v_stream_id,
            v_event->>'event_type',
            (v_event->>'event_version')::INTEGER,
            v_new_version,
            v_event->'event_data',
            v_event->'metadata',
            (v_event->>'causation_id')::UUID,
            (v_event->>'correlation_id')::UUID
        );
    END LOOP;

    -- Update stream version
    UPDATE event_store.event_stream
    SET version = v_new_version
    WHERE stream_id = v_stream_id;

    RETURN v_stream_id;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION event_store.append_events IS 'Atomically append events to an aggregate stream with optimistic locking';

-- Log completion
DO $$
BEGIN
    RAISE NOTICE 'Event store tables created successfully';
END $$;
