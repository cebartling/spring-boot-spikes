-- =====================================================
-- Shopping Cart Schema
-- =====================================================
-- This schema supports shopping cart functionality with:
-- - Shopping carts (active/abandoned/converted)
-- - Cart items with product references
-- - Cart state tracking (creation, modification, expiration)
-- - Pricing snapshots to handle price changes
-- =====================================================

-- Shopping Carts Table
-- Represents a shopping session for a customer
CREATE TABLE shopping_carts (
    id BIGSERIAL PRIMARY KEY,
    cart_uuid UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    user_id VARCHAR(255),  -- Optional: for guest vs authenticated users
    session_id VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE, ABANDONED, CONVERTED, EXPIRED
    currency_code VARCHAR(3) NOT NULL DEFAULT 'USD',
    subtotal_cents BIGINT NOT NULL DEFAULT 0,  -- Amount in cents
    tax_amount_cents BIGINT NOT NULL DEFAULT 0,  -- Amount in cents
    discount_amount_cents BIGINT NOT NULL DEFAULT 0,  -- Amount in cents
    total_amount_cents BIGINT NOT NULL DEFAULT 0,  -- Amount in cents
    item_count INTEGER NOT NULL DEFAULT 0,
    metadata JSONB,  -- Store additional cart data (promo codes, gift messages, etc.)
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE,  -- When cart should be considered abandoned
    converted_at TIMESTAMP WITH TIME ZONE,  -- When cart was converted to order
    CONSTRAINT valid_cart_status CHECK (status IN ('ACTIVE', 'ABANDONED', 'CONVERTED', 'EXPIRED')),
    CONSTRAINT valid_amounts CHECK (
        subtotal_cents >= 0 AND
        tax_amount_cents >= 0 AND
        discount_amount_cents >= 0 AND
        total_amount_cents >= 0
    ),
    CONSTRAINT valid_item_count CHECK (item_count >= 0)
);

-- Cart Items Table
-- Individual items in a shopping cart
CREATE TABLE cart_items (
    id BIGSERIAL PRIMARY KEY,
    cart_id BIGINT NOT NULL REFERENCES shopping_carts(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products(id),
    sku VARCHAR(100) NOT NULL,  -- Snapshot of SKU at time of add
    product_name VARCHAR(255) NOT NULL,  -- Snapshot of name at time of add
    quantity INTEGER NOT NULL,
    unit_price_cents BIGINT NOT NULL,  -- Price in cents at time of add (snapshot)
    line_total_cents BIGINT NOT NULL,  -- quantity * unit_price_cents
    discount_amount_cents BIGINT NOT NULL DEFAULT 0,  -- Discount in cents
    metadata JSONB,  -- Product options, customizations, etc.
    added_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT valid_quantity CHECK (quantity > 0),
    CONSTRAINT valid_prices CHECK (
        unit_price_cents >= 0 AND
        line_total_cents >= 0 AND
        discount_amount_cents >= 0
    ),
    CONSTRAINT unique_product_per_cart UNIQUE (cart_id, product_id)
);

-- Cart State History Table
-- Tracks cart state changes and important events
CREATE TABLE cart_state_history (
    id BIGSERIAL PRIMARY KEY,
    cart_id BIGINT NOT NULL REFERENCES shopping_carts(id) ON DELETE CASCADE,
    event_type VARCHAR(50) NOT NULL,  -- CREATED, ITEM_ADDED, ITEM_REMOVED, ITEM_UPDATED, ABANDONED, CONVERTED, EXPIRED
    previous_status VARCHAR(50),
    new_status VARCHAR(50),
    event_data JSONB,  -- Additional context about the event
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT valid_event_type CHECK (event_type IN (
        'CREATED', 'ITEM_ADDED', 'ITEM_REMOVED', 'ITEM_UPDATED',
        'QUANTITY_CHANGED', 'ABANDONED', 'CONVERTED', 'EXPIRED', 'RESTORED'
    ))
);

-- Indexes for Shopping Carts
CREATE INDEX idx_shopping_carts_user_id ON shopping_carts(user_id) WHERE user_id IS NOT NULL;
CREATE INDEX idx_shopping_carts_session_id ON shopping_carts(session_id);
CREATE INDEX idx_shopping_carts_status ON shopping_carts(status);
CREATE INDEX idx_shopping_carts_created_at ON shopping_carts(created_at);
CREATE INDEX idx_shopping_carts_expires_at ON shopping_carts(expires_at) WHERE expires_at IS NOT NULL;
CREATE INDEX idx_shopping_carts_updated_at ON shopping_carts(updated_at);
CREATE INDEX idx_shopping_carts_uuid ON shopping_carts(cart_uuid);

-- Indexes for Cart Items
CREATE INDEX idx_cart_items_cart_id ON cart_items(cart_id);
CREATE INDEX idx_cart_items_product_id ON cart_items(product_id);
CREATE INDEX idx_cart_items_sku ON cart_items(sku);
CREATE INDEX idx_cart_items_added_at ON cart_items(added_at);

-- Indexes for Cart State History
CREATE INDEX idx_cart_state_history_cart_id ON cart_state_history(cart_id);
CREATE INDEX idx_cart_state_history_event_type ON cart_state_history(event_type);
CREATE INDEX idx_cart_state_history_created_at ON cart_state_history(created_at);

-- Function to update shopping cart totals
CREATE OR REPLACE FUNCTION update_cart_totals()
RETURNS TRIGGER AS $$
BEGIN
    -- Update cart totals and item count
    UPDATE shopping_carts
    SET
        subtotal_cents = COALESCE((
            SELECT SUM(line_total_cents - discount_amount_cents)
            FROM cart_items
            WHERE cart_id = NEW.cart_id
        ), 0),
        item_count = COALESCE((
            SELECT SUM(quantity)
            FROM cart_items
            WHERE cart_id = NEW.cart_id
        ), 0),
        total_amount_cents = COALESCE((
            SELECT SUM(line_total_cents - discount_amount_cents)
            FROM cart_items
            WHERE cart_id = NEW.cart_id
        ), 0) + tax_amount_cents - discount_amount_cents,
        updated_at = CURRENT_TIMESTAMP
    WHERE id = NEW.cart_id;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Function to update cart totals on item deletion
CREATE OR REPLACE FUNCTION update_cart_totals_on_delete()
RETURNS TRIGGER AS $$
BEGIN
    -- Update cart totals and item count
    UPDATE shopping_carts
    SET
        subtotal_cents = COALESCE((
            SELECT SUM(line_total_cents - discount_amount_cents)
            FROM cart_items
            WHERE cart_id = OLD.cart_id
        ), 0),
        item_count = COALESCE((
            SELECT SUM(quantity)
            FROM cart_items
            WHERE cart_id = OLD.cart_id
        ), 0),
        total_amount_cents = COALESCE((
            SELECT SUM(line_total_cents - discount_amount_cents)
            FROM cart_items
            WHERE cart_id = OLD.cart_id
        ), 0) + tax_amount_cents - discount_amount_cents,
        updated_at = CURRENT_TIMESTAMP
    WHERE id = OLD.cart_id;

    RETURN OLD;
END;
$$ LANGUAGE plpgsql;

-- Function to update cart updated_at timestamp
CREATE OR REPLACE FUNCTION update_cart_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Function to calculate line total for cart items
CREATE OR REPLACE FUNCTION calculate_line_total()
RETURNS TRIGGER AS $$
BEGIN
    NEW.line_total_cents = NEW.quantity * NEW.unit_price_cents;
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Triggers for Cart Items
CREATE TRIGGER trigger_calculate_line_total
    BEFORE INSERT OR UPDATE OF quantity, unit_price_cents ON cart_items
    FOR EACH ROW
    EXECUTE FUNCTION calculate_line_total();

CREATE TRIGGER trigger_update_cart_totals_insert
    AFTER INSERT ON cart_items
    FOR EACH ROW
    EXECUTE FUNCTION update_cart_totals();

CREATE TRIGGER trigger_update_cart_totals_update
    AFTER UPDATE OF quantity, line_total_cents, discount_amount_cents ON cart_items
    FOR EACH ROW
    EXECUTE FUNCTION update_cart_totals();

CREATE TRIGGER trigger_update_cart_totals_delete
    AFTER DELETE ON cart_items
    FOR EACH ROW
    EXECUTE FUNCTION update_cart_totals_on_delete();

-- Trigger for Shopping Carts
CREATE TRIGGER trigger_update_cart_timestamp
    BEFORE UPDATE ON shopping_carts
    FOR EACH ROW
    EXECUTE FUNCTION update_cart_timestamp();

-- Insert sample data comments
COMMENT ON TABLE shopping_carts IS 'Shopping cart sessions with totals and state tracking';
COMMENT ON TABLE cart_items IS 'Individual items in shopping carts with price snapshots';
COMMENT ON TABLE cart_state_history IS 'Audit trail of cart state changes and events';
COMMENT ON COLUMN shopping_carts.cart_uuid IS 'Public UUID for cart identification (instead of exposing internal ID)';
COMMENT ON COLUMN shopping_carts.user_id IS 'Optional user identifier - NULL for guest carts';
COMMENT ON COLUMN shopping_carts.session_id IS 'Session identifier for tracking guest carts';
COMMENT ON COLUMN shopping_carts.expires_at IS 'Timestamp when cart should be considered abandoned';
COMMENT ON COLUMN cart_items.unit_price_cents IS 'Price in cents snapshot at time of adding to cart';
COMMENT ON COLUMN cart_items.sku IS 'SKU snapshot at time of adding to cart';
