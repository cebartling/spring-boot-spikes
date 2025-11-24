-- Migration: Create event append function
-- Version: 3
-- Description: Create append_events function for atomic event appending with optimistic locking

SET search_path TO event_store, public;

-- Event Append Function
-- Atomically appends one or more events to an aggregate stream with optimistic concurrency control
-- Parameters:
--   p_aggregate_type: Type of aggregate (e.g., 'Order', 'Product')
--   p_aggregate_id: Unique identifier for the aggregate instance
--   p_expected_version: Expected current version of the stream (for optimistic locking)
--   p_events: Array of events to append as JSONB objects
-- Returns: UUID of the stream
-- Throws: serialization_failure exception on concurrency conflict
CREATE OR REPLACE FUNCTION append_events(
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
    FROM event_stream
    WHERE aggregate_type = p_aggregate_type AND aggregate_id = p_aggregate_id
    FOR UPDATE;

    IF v_stream_id IS NULL THEN
        -- Create new stream for this aggregate
        INSERT INTO event_stream (aggregate_type, aggregate_id, version)
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

        INSERT INTO domain_event (
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
    UPDATE event_stream
    SET version = v_new_version
    WHERE stream_id = v_stream_id;

    RETURN v_stream_id;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION append_events(VARCHAR, UUID, INTEGER, JSONB[]) IS
    'Atomically append events to aggregate stream with optimistic locking. Throws serialization_failure on version conflict.';

-- Example usage:
-- SELECT append_events(
--     'Order',
--     '123e4567-e89b-12d3-a456-426614174000'::UUID,
--     0,
--     ARRAY[
--         '{"event_type": "OrderCreated", "event_data": {"order_id": "123", "total": 100}}'::JSONB,
--         '{"event_type": "OrderItemAdded", "event_data": {"item_id": "456", "quantity": 2}}'::JSONB
--     ]::JSONB[]
-- );
