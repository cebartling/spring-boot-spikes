-- infrastructure/postgres/seed-data/scenarios/standard/02_seed_read_models.sql
-- Standard scenario - Read Model Data

SET search_path TO read_model;

-- Create order_summary table if not exists
CREATE TABLE IF NOT EXISTS order_summary (
    order_id UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    total_amount DECIMAL(10, 2) NOT NULL,
    status VARCHAR(50) NOT NULL,
    item_count INTEGER DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Clean existing data
TRUNCATE TABLE order_summary CASCADE;

-- Seed order summaries
INSERT INTO order_summary (order_id, customer_id, total_amount, status, item_count, created_at, updated_at)
VALUES
    ('650e8400-e29b-41d4-a716-446655440001', '750e8400-e29b-41d4-a716-446655440001', 109.97, 'DELIVERED', 3, NOW() - INTERVAL '5 days', NOW() - INTERVAL '2 days'),
    ('650e8400-e29b-41d4-a716-446655440002', '750e8400-e29b-41d4-a716-446655440001', 99.98, 'SHIPPED', 2, NOW() - INTERVAL '3 days', NOW() - INTERVAL '1 day'),
    ('650e8400-e29b-41d4-a716-446655440003', '750e8400-e29b-41d4-a716-446655440002', 89.97, 'PENDING', 3, NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day');

-- Create customer_summary table if not exists
CREATE TABLE IF NOT EXISTS customer_summary (
    customer_id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    phone VARCHAR(50),
    total_orders INTEGER DEFAULT 0,
    total_spent DECIMAL(12, 2) DEFAULT 0.00,
    registered_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Clean existing data
TRUNCATE TABLE customer_summary CASCADE;

-- Seed customer summaries
INSERT INTO customer_summary (customer_id, name, email, phone, total_orders, total_spent, registered_at, updated_at)
VALUES
    ('750e8400-e29b-41d4-a716-446655440001', 'John Doe', 'john.doe@example.com', '+1-555-123-4567', 2, 209.95, NOW() - INTERVAL '10 days', NOW() - INTERVAL '1 day'),
    ('750e8400-e29b-41d4-a716-446655440002', 'Jane Smith', 'jane.smith@example.com', NULL, 1, 89.97, NOW() - INTERVAL '7 days', NOW() - INTERVAL '1 day');

-- Create product_catalog table if not exists
CREATE TABLE IF NOT EXISTS product_catalog (
    product_id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    price_cents INTEGER NOT NULL,
    category VARCHAR(100),
    available BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Clean existing data
TRUNCATE TABLE product_catalog CASCADE;

-- Seed product catalog
INSERT INTO product_catalog (product_id, name, description, price_cents, category, available, created_at, updated_at)
VALUES
    ('850e8400-e29b-41d4-a716-446655440001', 'Widget Pro', 'Professional grade widget', 2999, 'widgets', true, NOW() - INTERVAL '30 days', NOW() - INTERVAL '30 days'),
    ('850e8400-e29b-41d4-a716-446655440002', 'Gadget Deluxe', 'Luxury gadget', 3999, 'gadgets', true, NOW() - INTERVAL '30 days', NOW() - INTERVAL '15 days');

-- Verify
SELECT 'Order summaries seeded: ' || COUNT(*) FROM order_summary;
SELECT 'Customer summaries seeded: ' || COUNT(*) FROM customer_summary;
SELECT 'Product catalog seeded: ' || COUNT(*) FROM product_catalog;
