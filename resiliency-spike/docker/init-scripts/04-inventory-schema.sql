-- Product Inventory Database Schema
-- This script creates tables for managing product inventory across multiple locations

-- Create inventory locations table (warehouses, stores, distribution centers)
CREATE TABLE IF NOT EXISTS inventory_locations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    type VARCHAR(50) NOT NULL, -- WAREHOUSE, STORE, DISTRIBUTION_CENTER, SUPPLIER
    address TEXT,
    city VARCHAR(100),
    state VARCHAR(50),
    postal_code VARCHAR(20),
    country VARCHAR(50),
    is_active BOOLEAN DEFAULT TRUE,
    metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Create index on location code
CREATE INDEX IF NOT EXISTS idx_inventory_locations_code
    ON inventory_locations(code);

-- Create index on location type
CREATE INDEX IF NOT EXISTS idx_inventory_locations_type
    ON inventory_locations(type);

-- Create index on active status
CREATE INDEX IF NOT EXISTS idx_inventory_locations_active
    ON inventory_locations(is_active);

-- Create inventory stock table (current stock levels per product per location)
CREATE TABLE IF NOT EXISTS inventory_stock (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    product_id UUID NOT NULL,
    location_id UUID NOT NULL,
    quantity_on_hand INTEGER NOT NULL DEFAULT 0,
    quantity_reserved INTEGER NOT NULL DEFAULT 0,
    quantity_available INTEGER NOT NULL DEFAULT 0, -- computed: on_hand - reserved
    reorder_point INTEGER DEFAULT 0,
    reorder_quantity INTEGER DEFAULT 0,
    last_stock_check TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE RESTRICT,
    FOREIGN KEY (location_id) REFERENCES inventory_locations(id) ON DELETE RESTRICT,
    UNIQUE (product_id, location_id)
);

-- Create index on product_id for faster lookups
CREATE INDEX IF NOT EXISTS idx_inventory_stock_product
    ON inventory_stock(product_id);

-- Create index on location_id
CREATE INDEX IF NOT EXISTS idx_inventory_stock_location
    ON inventory_stock(location_id);

-- Create index on quantity_available for low stock queries
CREATE INDEX IF NOT EXISTS idx_inventory_stock_available
    ON inventory_stock(quantity_available);

-- Create compound index for product + location queries
CREATE INDEX IF NOT EXISTS idx_inventory_stock_product_location
    ON inventory_stock(product_id, location_id);

-- Create inventory transactions table (all inventory movements)
CREATE TABLE IF NOT EXISTS inventory_transactions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    product_id UUID NOT NULL,
    location_id UUID NOT NULL,
    transaction_type VARCHAR(50) NOT NULL, -- RECEIPT, SHIPMENT, ADJUSTMENT, TRANSFER_IN, TRANSFER_OUT, RETURN, DAMAGE, RECOUNT
    quantity INTEGER NOT NULL,
    reference_number VARCHAR(100),
    reason VARCHAR(255),
    notes TEXT,
    performed_by VARCHAR(100),
    transaction_date TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE RESTRICT,
    FOREIGN KEY (location_id) REFERENCES inventory_locations(id) ON DELETE RESTRICT
);

-- Create index on product_id
CREATE INDEX IF NOT EXISTS idx_inventory_transactions_product
    ON inventory_transactions(product_id);

-- Create index on location_id
CREATE INDEX IF NOT EXISTS idx_inventory_transactions_location
    ON inventory_transactions(location_id);

-- Create index on transaction_type
CREATE INDEX IF NOT EXISTS idx_inventory_transactions_type
    ON inventory_transactions(transaction_type);

-- Create index on transaction_date for time-based queries
CREATE INDEX IF NOT EXISTS idx_inventory_transactions_date
    ON inventory_transactions(transaction_date DESC);

-- Create index on reference_number for lookup
CREATE INDEX IF NOT EXISTS idx_inventory_transactions_reference
    ON inventory_transactions(reference_number);

-- Create inventory reservations table (reserved stock for orders)
CREATE TABLE IF NOT EXISTS inventory_reservations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    product_id UUID NOT NULL,
    location_id UUID NOT NULL,
    quantity INTEGER NOT NULL,
    reservation_type VARCHAR(50) NOT NULL, -- ORDER, TRANSFER, HOLD, ALLOCATION
    reference_number VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, FULFILLED, CANCELLED, EXPIRED
    reserved_by VARCHAR(100),
    reserved_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    expires_at TIMESTAMP WITH TIME ZONE,
    fulfilled_at TIMESTAMP WITH TIME ZONE,
    notes TEXT,
    metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE RESTRICT,
    FOREIGN KEY (location_id) REFERENCES inventory_locations(id) ON DELETE RESTRICT
);

-- Create index on product_id
CREATE INDEX IF NOT EXISTS idx_inventory_reservations_product
    ON inventory_reservations(product_id);

-- Create index on location_id
CREATE INDEX IF NOT EXISTS idx_inventory_reservations_location
    ON inventory_reservations(location_id);

-- Create index on status
CREATE INDEX IF NOT EXISTS idx_inventory_reservations_status
    ON inventory_reservations(status);

-- Create index on reference_number
CREATE INDEX IF NOT EXISTS idx_inventory_reservations_reference
    ON inventory_reservations(reference_number);

-- Create index on expires_at for expiration queries
CREATE INDEX IF NOT EXISTS idx_inventory_reservations_expires
    ON inventory_reservations(expires_at);

-- Create GIN indexes on metadata JSONB columns
CREATE INDEX IF NOT EXISTS idx_inventory_locations_metadata
    ON inventory_locations USING GIN (metadata);

CREATE INDEX IF NOT EXISTS idx_inventory_transactions_metadata
    ON inventory_transactions USING GIN (metadata);

CREATE INDEX IF NOT EXISTS idx_inventory_reservations_metadata
    ON inventory_reservations USING GIN (metadata);

-- Create triggers to automatically update updated_at
CREATE TRIGGER update_inventory_locations_updated_at
    BEFORE UPDATE ON inventory_locations
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_inventory_stock_updated_at
    BEFORE UPDATE ON inventory_stock
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_inventory_reservations_updated_at
    BEFORE UPDATE ON inventory_reservations
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Create a function to update quantity_available when on_hand or reserved changes
CREATE OR REPLACE FUNCTION update_inventory_stock_available()
RETURNS TRIGGER AS $$
BEGIN
    NEW.quantity_available = NEW.quantity_on_hand - NEW.quantity_reserved;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create trigger to automatically calculate quantity_available
CREATE TRIGGER calculate_inventory_stock_available
    BEFORE INSERT OR UPDATE OF quantity_on_hand, quantity_reserved ON inventory_stock
    FOR EACH ROW
    EXECUTE FUNCTION update_inventory_stock_available();

-- Grant permissions
GRANT ALL PRIVILEGES ON TABLE inventory_locations TO resiliency_user;
GRANT ALL PRIVILEGES ON TABLE inventory_stock TO resiliency_user;
GRANT ALL PRIVILEGES ON TABLE inventory_transactions TO resiliency_user;
GRANT ALL PRIVILEGES ON TABLE inventory_reservations TO resiliency_user;

-- Insert sample inventory locations
INSERT INTO inventory_locations (code, name, type, address, city, state, postal_code, country, metadata)
VALUES
    ('WH-CENTRAL', 'Central Warehouse', 'WAREHOUSE', '1000 Storage Way', 'Indianapolis', 'IN', '46204', 'USA',
     '{"capacity": 100000, "manager": "John Smith", "operating_hours": "24/7"}'::jsonb),
    ('WH-WEST', 'West Coast Distribution Center', 'DISTRIBUTION_CENTER', '2500 Pacific Blvd', 'Los Angeles', 'CA', '90021', 'USA',
     '{"capacity": 75000, "manager": "Jane Doe", "operating_hours": "6am-10pm"}'::jsonb),
    ('STORE-NYC', 'New York Flagship Store', 'STORE', '500 5th Avenue', 'New York', 'NY', '10110', 'USA',
     '{"store_size": "20000 sqft", "manager": "Bob Wilson", "operating_hours": "9am-9pm"}'::jsonb),
    ('STORE-CHI', 'Chicago Store', 'STORE', '100 Michigan Ave', 'Chicago', 'IL', '60611', 'USA',
     '{"store_size": "15000 sqft", "manager": "Alice Johnson", "operating_hours": "10am-8pm"}'::jsonb)
ON CONFLICT (code) DO NOTHING;

-- Insert sample inventory stock (linking to products that should exist)
-- Using dynamic queries to link to existing products
INSERT INTO inventory_stock (product_id, location_id, quantity_on_hand, quantity_reserved, reorder_point, reorder_quantity)
SELECT
    p.id,
    l.id,
    CASE
        WHEN l.type = 'WAREHOUSE' THEN 500
        WHEN l.type = 'DISTRIBUTION_CENTER' THEN 200
        ELSE 50
    END as quantity_on_hand,
    CASE
        WHEN l.type = 'STORE' THEN 5
        ELSE 20
    END as quantity_reserved,
    50 as reorder_point,
    100 as reorder_quantity
FROM products p
CROSS JOIN inventory_locations l
WHERE p.sku IN ('COMP-LAPTOP-001', 'PHONE-FLAG-001', 'BOOK-TECH-001')
LIMIT 12
ON CONFLICT (product_id, location_id) DO NOTHING;

-- Insert sample inventory transactions
INSERT INTO inventory_transactions (product_id, location_id, transaction_type, quantity, reference_number, reason, performed_by, transaction_date)
SELECT
    p.id,
    l.id,
    'RECEIPT',
    500,
    'PO-' || LPAD(FLOOR(RANDOM() * 10000)::TEXT, 5, '0'),
    'Initial stock receipt',
    'system',
    NOW() - INTERVAL '7 days'
FROM products p
CROSS JOIN inventory_locations l
WHERE p.sku = 'COMP-LAPTOP-001' AND l.code = 'WH-CENTRAL'
LIMIT 1;

-- Insert sample reservations
INSERT INTO inventory_reservations (product_id, location_id, quantity, reservation_type, reference_number, status, reserved_by, expires_at)
SELECT
    p.id,
    l.id,
    10,
    'ORDER',
    'ORD-' || LPAD(FLOOR(RANDOM() * 10000)::TEXT, 5, '0'),
    'ACTIVE',
    'customer@example.com',
    NOW() + INTERVAL '7 days'
FROM products p
CROSS JOIN inventory_locations l
WHERE p.sku = 'PHONE-FLAG-001' AND l.code = 'WH-CENTRAL'
LIMIT 1;
