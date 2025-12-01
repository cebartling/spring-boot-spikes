-- Migration: Fix append_events function search path
-- Version: 8
-- Description: Add SET search_path to append_events function for proper schema resolution
--              in R2DBC connections that don't inherit session search_path

-- Drop and recreate the function with explicit search_path configuration
-- This ensures the function always finds tables in the event_store schema

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
    -- Get or create stream with row-level lock
    SELECT stream_id, version INTO v_stream_id, v_current_version
    FROM event_store.event_stream
    WHERE aggregate_type = p_aggregate_type AND aggregate_id = p_aggregate_id
    FOR UPDATE;

    IF v_stream_id IS NULL THEN
        -- Create new stream for this aggregate
        INSERT INTO event_store.event_stream (aggregate_type, aggregate_id, version)
        VALUES (p_aggregate_type, p_aggregate_id, 0)
        RETURNING stream_id, version INTO v_stream_id, v_current_version;
    END IF;

    -- Optimistic concurrency check
    -- Ensures no other transaction has modified the stream since we read it
    IF v_current_version != p_expected_version THEN
        RAISE EXCEPTION 'Concurrency conflict: expected version %, actual version %',
            p_expected_version, v_current_version
            USING ERRCODE = 'serialization_failure';
    END IF;

    -- Append events to the stream
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
            correlation_id,
            user_id
        ) VALUES (
            v_stream_id,
            v_event->>'event_type',
            COALESCE((v_event->>'event_version')::INTEGER, 1),
            v_new_version,
            v_event->'event_data',
            v_event->'metadata',
            (v_event->>'causation_id')::UUID,
            (v_event->>'correlation_id')::UUID,
            v_event->>'user_id'
        );
    END LOOP;

    -- Update stream version
    UPDATE event_store.event_stream
    SET version = v_new_version
    WHERE stream_id = v_stream_id;

    RETURN v_stream_id;
END;
$$ LANGUAGE plpgsql
SET search_path = event_store, public;

COMMENT ON FUNCTION event_store.append_events(VARCHAR, UUID, INTEGER, JSONB[]) IS
    'Atomically append events to aggregate stream with optimistic locking. Uses schema-qualified names and fixed search_path for R2DBC compatibility.';
