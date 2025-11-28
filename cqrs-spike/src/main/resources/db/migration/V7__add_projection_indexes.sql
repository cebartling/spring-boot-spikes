-- Add indexes for projection event ordering
-- This enables projections to process events in a consistent global order

-- Create composite index for efficient projection queries
-- Events are ordered by occurred_at and event_id for consistent global ordering
CREATE INDEX IF NOT EXISTS idx_domain_event_occurred_event_id
    ON event_store.domain_event(occurred_at ASC, event_id ASC);

-- Create index for counting events after a position (lag calculation)
CREATE INDEX IF NOT EXISTS idx_domain_event_occurred_at
    ON event_store.domain_event(occurred_at ASC);

-- Add comment documenting the ordering strategy
COMMENT ON INDEX event_store.idx_domain_event_occurred_event_id IS
    'Composite index for global event ordering in projections. Uses occurred_at + event_id for consistent ordering across all streams.';
