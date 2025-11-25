-- infrastructure/postgres/seed-data/scenarios/full/02_seed_read_models.sql
-- Full scenario - Comprehensive Read Model Data

SET search_path TO read_model;

-- Create order_summary table if not exists
CREATE TABLE IF NOT EXISTS order_summary (
    order_id UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    customer_name VARCHAR(255),
    total_amount DECIMAL(10, 2) NOT NULL,
    status VARCHAR(50) NOT NULL,
    item_count INTEGER DEFAULT 0,
    shipping_address JSONB,
    tracking_number VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Clean existing data
TRUNCATE TABLE order_summary CASCADE;

-- Seed order summaries
INSERT INTO order_summary (order_id, customer_id, customer_name, total_amount, status, item_count, shipping_address, tracking_number, created_at, updated_at)
VALUES
    ('650e8400-e29b-41d4-a716-446655440001', '750e8400-e29b-41d4-a716-446655440001', 'John Doe', 109.98, 'DELIVERED', 3, '{"street": "123 Main St", "city": "Springfield", "state": "IL", "zip": "62701"}'::jsonb, 'FDX-12345678', NOW() - INTERVAL '14 days', NOW() - INTERVAL '7 days'),
    ('650e8400-e29b-41d4-a716-446655440002', '750e8400-e29b-41d4-a716-446655440001', 'John Doe', 199.99, 'DELIVERED', 1, '{"street": "123 Main St", "city": "Springfield", "state": "IL", "zip": "62701"}'::jsonb, 'UPS-87654321', NOW() - INTERVAL '10 days', NOW() - INTERVAL '5 days'),
    ('650e8400-e29b-41d4-a716-446655440003', '750e8400-e29b-41d4-a716-446655440002', 'Jane Smith', 149.95, 'SHIPPED', 5, '{"street": "456 Oak Ave", "city": "Chicago", "state": "IL", "zip": "60601"}'::jsonb, 'USPS-11111111', NOW() - INTERVAL '7 days', NOW() - INTERVAL '3 days'),
    ('650e8400-e29b-41d4-a716-446655440004', '750e8400-e29b-41d4-a716-446655440003', 'Bob Wilson', 209.97, 'PENDING', 3, NULL, NULL, NOW() - INTERVAL '3 days', NOW() - INTERVAL '3 days'),
    ('650e8400-e29b-41d4-a716-446655440005', '750e8400-e29b-41d4-a716-446655440002', 'Jane Smith', 199.99, 'CANCELLED', 1, NULL, NULL, NOW() - INTERVAL '5 days', NOW() - INTERVAL '4 days');

-- Create customer_summary table if not exists
CREATE TABLE IF NOT EXISTS customer_summary (
    customer_id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    phone VARCHAR(50),
    total_orders INTEGER DEFAULT 0,
    completed_orders INTEGER DEFAULT 0,
    cancelled_orders INTEGER DEFAULT 0,
    total_spent DECIMAL(12, 2) DEFAULT 0.00,
    average_order_value DECIMAL(10, 2) DEFAULT 0.00,
    default_address JSONB,
    registered_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Clean existing data
TRUNCATE TABLE customer_summary CASCADE;

-- Seed customer summaries
INSERT INTO customer_summary (customer_id, name, email, phone, total_orders, completed_orders, cancelled_orders, total_spent, average_order_value, default_address, registered_at, updated_at)
VALUES
    ('750e8400-e29b-41d4-a716-446655440001', 'John Doe', 'john.doe@example.com', '+1-555-123-4567', 2, 2, 0, 309.97, 154.99, '{"street": "123 Main St", "city": "Springfield", "state": "IL", "zip": "62701"}'::jsonb, NOW() - INTERVAL '30 days', NOW() - INTERVAL '5 days'),
    ('750e8400-e29b-41d4-a716-446655440002', 'Jane Smith', 'jane.smith@example.com', NULL, 2, 0, 1, 149.95, 174.97, '{"street": "456 Oak Ave", "city": "Chicago", "state": "IL", "zip": "60601"}'::jsonb, NOW() - INTERVAL '20 days', NOW() - INTERVAL '3 days'),
    ('750e8400-e29b-41d4-a716-446655440003', 'Bob Wilson', 'bob.wilson@example.com', NULL, 1, 0, 0, 0.00, 209.97, NULL, NOW() - INTERVAL '10 days', NOW() - INTERVAL '3 days');

-- Create product_catalog table if not exists
CREATE TABLE IF NOT EXISTS product_catalog (
    product_id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    sku VARCHAR(50) UNIQUE,
    price_cents INTEGER NOT NULL,
    category VARCHAR(100),
    available BOOLEAN DEFAULT true,
    stock_quantity INTEGER DEFAULT 0,
    times_ordered INTEGER DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Clean existing data
TRUNCATE TABLE product_catalog CASCADE;

-- Seed product catalog
INSERT INTO product_catalog (product_id, name, description, sku, price_cents, category, available, stock_quantity, times_ordered, created_at, updated_at)
VALUES
    ('850e8400-e29b-41d4-a716-446655440001', 'Widget Pro', 'Professional grade widget for all your needs', 'WGT-PRO-001', 2499, 'widgets', true, 143, 7, NOW() - INTERVAL '60 days', NOW() - INTERVAL '15 days'),
    ('850e8400-e29b-41d4-a716-446655440002', 'Gadget Deluxe', 'Premium gadget with advanced features and extended warranty', 'GDG-DLX-001', 6999, 'gadgets', true, 47, 3, NOW() - INTERVAL '60 days', NOW() - INTERVAL '7 days'),
    ('850e8400-e29b-41d4-a716-446655440003', 'Super Tool', 'Multi-purpose tool for professionals', 'TL-SPR-001', 19999, 'tools', true, 25, 2, NOW() - INTERVAL '45 days', NOW() - INTERVAL '45 days'),
    ('850e8400-e29b-41d4-a716-446655440004', 'Basic Kit', 'Starter kit for beginners', 'KIT-BSC-001', 4999, 'kits', false, 0, 1, NOW() - INTERVAL '30 days', NOW() - INTERVAL '10 days');

-- Create inventory_view table if not exists
CREATE TABLE IF NOT EXISTS inventory_view (
    product_id UUID PRIMARY KEY,
    product_name VARCHAR(255) NOT NULL,
    sku VARCHAR(50),
    warehouse_location VARCHAR(100),
    quantity_available INTEGER DEFAULT 0,
    quantity_reserved INTEGER DEFAULT 0,
    reorder_point INTEGER DEFAULT 10,
    last_restocked_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Clean existing data
TRUNCATE TABLE inventory_view CASCADE;

-- Seed inventory view
INSERT INTO inventory_view (product_id, product_name, sku, warehouse_location, quantity_available, quantity_reserved, reorder_point, last_restocked_at, updated_at)
VALUES
    ('850e8400-e29b-41d4-a716-446655440001', 'Widget Pro', 'WGT-PRO-001', 'Warehouse A', 143, 0, 20, NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day'),
    ('850e8400-e29b-41d4-a716-446655440002', 'Gadget Deluxe', 'GDG-DLX-001', 'Warehouse A', 47, 2, 10, NOW() - INTERVAL '60 days', NOW() - INTERVAL '3 days'),
    ('850e8400-e29b-41d4-a716-446655440003', 'Super Tool', 'TL-SPR-001', 'Warehouse A', 25, 0, 5, NOW() - INTERVAL '45 days', NOW() - INTERVAL '10 days'),
    ('850e8400-e29b-41d4-a716-446655440004', 'Basic Kit', 'KIT-BSC-001', 'Warehouse A', 0, 0, 0, NULL, NOW() - INTERVAL '10 days');

-- Verify
SELECT 'Order summaries seeded: ' || COUNT(*) FROM order_summary;
SELECT 'Customer summaries seeded: ' || COUNT(*) FROM customer_summary;
SELECT 'Product catalog seeded: ' || COUNT(*) FROM product_catalog;
SELECT 'Inventory views seeded: ' || COUNT(*) FROM inventory_view;
