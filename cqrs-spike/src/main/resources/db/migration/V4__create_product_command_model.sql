-- Migration: Create Product aggregate table in command_model schema
-- Version: 4
-- Description: Create product table for Product aggregate (AC1 - Product Command Model)

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

-- Index for status queries (excluding soft-deleted products)
CREATE INDEX idx_product_status
    ON product(status)
    WHERE deleted_at IS NULL;

-- Index for version-based queries (optimistic locking)
CREATE INDEX idx_product_version
    ON product(id, version);

-- Index for active products
CREATE INDEX idx_product_active
    ON product(id)
    WHERE status = 'ACTIVE' AND deleted_at IS NULL;

-- Add comments for documentation
COMMENT ON TABLE product IS 'Product aggregate for command model - stores current product state';
COMMENT ON COLUMN product.id IS 'Unique identifier for the product (UUID)';
COMMENT ON COLUMN product.sku IS 'Stock Keeping Unit - unique product identifier (3-50 alphanumeric chars with hyphens)';
COMMENT ON COLUMN product.name IS 'Product display name (1-255 characters)';
COMMENT ON COLUMN product.description IS 'Optional product description (up to 5000 characters)';
COMMENT ON COLUMN product.price_cents IS 'Product price in cents (must be positive)';
COMMENT ON COLUMN product.status IS 'Product lifecycle status: DRAFT, ACTIVE, or DISCONTINUED';
COMMENT ON COLUMN product.version IS 'Aggregate version for optimistic concurrency control';
COMMENT ON COLUMN product.created_at IS 'Timestamp when product was created';
COMMENT ON COLUMN product.updated_at IS 'Timestamp of last modification';
COMMENT ON COLUMN product.deleted_at IS 'Timestamp of soft deletion (NULL if not deleted)';
