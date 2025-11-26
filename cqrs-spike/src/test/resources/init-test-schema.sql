-- Test schema initialization for integration tests
-- Creates minimal schema needed for Product aggregate testing

-- Create schemas
CREATE SCHEMA IF NOT EXISTS command_model;
CREATE SCHEMA IF NOT EXISTS event_store;

-- Create UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Product Aggregate Table in command_model schema
CREATE TABLE command_model.product (
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
    ON command_model.product(sku)
    WHERE deleted_at IS NULL;

-- Index for status queries
CREATE INDEX idx_product_status
    ON command_model.product(status)
    WHERE deleted_at IS NULL;

-- Index for version-based queries (optimistic locking)
CREATE INDEX idx_product_version
    ON command_model.product(id, version);

-- Event store tables (minimal for testing)
CREATE TABLE event_store.event_stream (
    stream_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    aggregate_type VARCHAR(255) NOT NULL,
    aggregate_id UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE (aggregate_type, aggregate_id)
);

CREATE TABLE event_store.domain_event (
    event_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    stream_id UUID REFERENCES event_store.event_stream(stream_id) ON DELETE CASCADE,
    event_type VARCHAR(255) NOT NULL,
    event_data JSONB NOT NULL,
    metadata JSONB,
    version BIGINT NOT NULL,
    occurred_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_domain_event_stream ON event_store.domain_event(stream_id, version);
