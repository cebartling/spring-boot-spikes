-- Read Model Schema for Product Queries
-- Part of AC4: Product Read Model implementation

-- Ensure read_model schema exists
CREATE SCHEMA IF NOT EXISTS read_model;

-- Product Read Model Table
CREATE TABLE IF NOT EXISTS read_model.product (
    id UUID PRIMARY KEY,
    sku VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    price_cents INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 1,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,

    -- Denormalized fields for common queries
    price_display VARCHAR(20),
    search_text TEXT,
    last_event_id UUID,

    -- Constraints
    CONSTRAINT chk_rm_price_positive CHECK (price_cents > 0),
    CONSTRAINT chk_rm_status_valid CHECK (status IN ('DRAFT', 'ACTIVE', 'DISCONTINUED'))
);

-- Indexes for common query patterns
CREATE INDEX IF NOT EXISTS idx_rm_product_status
    ON read_model.product(status)
    WHERE NOT is_deleted;

CREATE UNIQUE INDEX IF NOT EXISTS idx_rm_product_sku
    ON read_model.product(sku)
    WHERE NOT is_deleted;

CREATE INDEX IF NOT EXISTS idx_rm_product_name
    ON read_model.product(name)
    WHERE NOT is_deleted;

CREATE INDEX IF NOT EXISTS idx_rm_product_price
    ON read_model.product(price_cents)
    WHERE NOT is_deleted;

CREATE INDEX IF NOT EXISTS idx_rm_product_created_at
    ON read_model.product(created_at DESC)
    WHERE NOT is_deleted;

CREATE INDEX IF NOT EXISTS idx_rm_product_updated_at
    ON read_model.product(updated_at DESC)
    WHERE NOT is_deleted;

-- Full-text search index for name and description
CREATE INDEX IF NOT EXISTS idx_rm_product_search
    ON read_model.product USING gin(to_tsvector('english', coalesce(name, '') || ' ' || coalesce(description, '')))
    WHERE NOT is_deleted;

-- Cursor-based pagination support (composite indexes)
CREATE INDEX IF NOT EXISTS idx_rm_product_cursor_created
    ON read_model.product(created_at, id)
    WHERE NOT is_deleted;

CREATE INDEX IF NOT EXISTS idx_rm_product_cursor_name
    ON read_model.product(name, id)
    WHERE NOT is_deleted;

CREATE INDEX IF NOT EXISTS idx_rm_product_cursor_price
    ON read_model.product(price_cents, id)
    WHERE NOT is_deleted;

-- Projection Position Tracking Table
CREATE TABLE IF NOT EXISTS read_model.projection_position (
    projection_name VARCHAR(100) PRIMARY KEY,
    last_event_id UUID,
    last_event_sequence BIGINT,
    last_processed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    events_processed BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_projection_name_format CHECK (projection_name ~ '^[a-zA-Z0-9_-]+$')
);

-- Comments for documentation
COMMENT ON TABLE read_model.product IS
    'Denormalized read model for product queries. Updated via event projections.';

COMMENT ON TABLE read_model.projection_position IS
    'Tracks the last processed event for each projection to enable resumable processing.';

COMMENT ON COLUMN read_model.product.last_event_id IS
    'The ID of the last event that updated this record. Used for idempotency.';

COMMENT ON COLUMN read_model.product.search_text IS
    'Pre-computed searchable text combining name and description for full-text search.';

COMMENT ON COLUMN read_model.product.price_display IS
    'Pre-formatted price string for display (e.g., "$19.99").';
