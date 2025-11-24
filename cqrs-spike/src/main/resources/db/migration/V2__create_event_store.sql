-- Migration: Create event store tables
-- Version: 2
-- Description: Create event_stream and domain_event tables for event sourcing

SET search_path TO event_store, public;

-- Event Stream table
-- Tracks aggregate event streams with versioning for optimistic locking
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

COMMENT ON TABLE event_stream IS 'Aggregate event streams for event sourcing';
COMMENT ON COLUMN event_stream.stream_id IS 'Unique identifier for the event stream';
COMMENT ON COLUMN event_stream.aggregate_type IS 'Type of aggregate (e.g., Order, Product)';
COMMENT ON COLUMN event_stream.aggregate_id IS 'Unique identifier for the aggregate instance';
COMMENT ON COLUMN event_stream.version IS 'Current version for optimistic locking';
COMMENT ON COLUMN event_stream.created_at IS 'Timestamp when stream was created';
COMMENT ON COLUMN event_stream.updated_at IS 'Timestamp of last event in stream';

-- Domain Events table
-- Stores immutable domain events in append-only fashion
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
CREATE INDEX idx_domain_event_correlation ON domain_event(correlation_id) WHERE correlation_id IS NOT NULL;
CREATE INDEX idx_domain_event_causation ON domain_event(causation_id) WHERE causation_id IS NOT NULL;
CREATE INDEX idx_domain_event_data ON domain_event USING GIN(event_data);
CREATE INDEX idx_domain_event_metadata ON domain_event USING GIN(metadata);

COMMENT ON TABLE domain_event IS 'Immutable domain events in append-only log';
COMMENT ON COLUMN domain_event.event_id IS 'Unique identifier for the event';
COMMENT ON COLUMN domain_event.stream_id IS 'Reference to the event stream';
COMMENT ON COLUMN domain_event.event_type IS 'Type of domain event (e.g., OrderCreated)';
COMMENT ON COLUMN domain_event.event_version IS 'Schema version of the event type';
COMMENT ON COLUMN domain_event.aggregate_version IS 'Version of aggregate after applying this event';
COMMENT ON COLUMN domain_event.event_data IS 'Event payload as JSON';
COMMENT ON COLUMN domain_event.metadata IS 'Additional metadata as JSON';
COMMENT ON COLUMN domain_event.occurred_at IS 'Timestamp when event occurred';
COMMENT ON COLUMN domain_event.causation_id IS 'ID of command that caused this event';
COMMENT ON COLUMN domain_event.correlation_id IS 'Correlation ID for tracking related events across aggregates';
COMMENT ON COLUMN domain_event.user_id IS 'User who triggered the event';

-- Trigger function to update event_stream.updated_at
-- Automatically updates the stream timestamp when new events are added
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

COMMENT ON FUNCTION update_stream_timestamp() IS 'Updates event_stream.updated_at when events are appended';
