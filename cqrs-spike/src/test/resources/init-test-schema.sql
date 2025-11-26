-- Test schema initialization for integration tests
-- Creates full schema needed for Product aggregate and event store testing

-- Create schemas
CREATE SCHEMA IF NOT EXISTS command_model;
CREATE SCHEMA IF NOT EXISTS event_store;

-- Create UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ============================================================================
-- Event Store Schema (must be created first for foreign key references)
-- ============================================================================

SET search_path TO event_store, public;

-- Event Stream table
CREATE TABLE event_stream (
    stream_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    aggregate_type VARCHAR(255) NOT NULL,
    aggregate_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    version INTEGER NOT NULL DEFAULT 0,
    UNIQUE (aggregate_type, aggregate_id)
);

CREATE INDEX idx_event_stream_aggregate ON event_stream(aggregate_type, aggregate_id);
CREATE INDEX idx_event_stream_updated ON event_stream(updated_at DESC);

-- Domain Events table
CREATE TABLE domain_event (
    event_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    stream_id UUID NOT NULL REFERENCES event_stream(stream_id) ON DELETE CASCADE,
    event_type VARCHAR(255) NOT NULL,
    event_version INTEGER NOT NULL DEFAULT 1,
    aggregate_version INTEGER NOT NULL,
    event_data JSONB NOT NULL,
    metadata JSONB,
    occurred_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    causation_id UUID,
    correlation_id UUID,
    user_id VARCHAR(255)
);

CREATE INDEX idx_domain_event_stream ON domain_event(stream_id, aggregate_version);
CREATE INDEX idx_domain_event_type ON domain_event(event_type);
CREATE INDEX idx_domain_event_occurred ON domain_event(occurred_at DESC);
CREATE INDEX idx_domain_event_type_time ON domain_event(event_type, occurred_at);
CREATE INDEX idx_domain_event_correlation ON domain_event(correlation_id) WHERE correlation_id IS NOT NULL;
CREATE INDEX idx_domain_event_causation ON domain_event(causation_id) WHERE causation_id IS NOT NULL;

-- Trigger function to update event_stream.updated_at
CREATE OR REPLACE FUNCTION update_stream_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    UPDATE event_stream
    SET updated_at = NEW.occurred_at
    WHERE stream_id = NEW.stream_id;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_update_stream_timestamp
AFTER INSERT ON domain_event
FOR EACH ROW
EXECUTE FUNCTION update_stream_timestamp();

-- Event Append Function for atomic event appending with optimistic locking
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

-- ============================================================================
-- Command Model Schema
-- ============================================================================

SET search_path TO command_model, public;

-- Product Aggregate Table
CREATE TABLE product (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    sku VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    price_cents INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ,
    CONSTRAINT chk_product_price_positive CHECK (price_cents > 0),
    CONSTRAINT chk_product_status_valid CHECK (status IN ('DRAFT', 'ACTIVE', 'DISCONTINUED')),
    CONSTRAINT chk_product_sku_format CHECK (sku ~ '^[A-Za-z0-9\-]{3,50}$')
);

-- Unique constraint on SKU (excluding soft-deleted products)
CREATE UNIQUE INDEX idx_product_sku_unique
    ON product(sku)
    WHERE deleted_at IS NULL;

-- Index for status queries
CREATE INDEX idx_product_status
    ON product(status)
    WHERE deleted_at IS NULL;

-- Index for version-based queries (optimistic locking)
CREATE INDEX idx_product_version
    ON product(id, version);
