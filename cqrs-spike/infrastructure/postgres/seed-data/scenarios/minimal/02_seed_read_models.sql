-- infrastructure/postgres/seed-data/scenarios/minimal/02_seed_read_models.sql
-- Minimal scenario - Read Model Data

SET search_path TO read_model;

-- Create order_summary table if not exists
CREATE TABLE IF NOT EXISTS order_summary (
    order_id UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    total_amount DECIMAL(10, 2) NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Clean existing data
TRUNCATE TABLE order_summary CASCADE;

-- Seed read model (projected from events)
INSERT INTO order_summary (order_id, customer_id, total_amount, status, created_at, updated_at)
VALUES
    ('650e8400-e29b-41d4-a716-446655440001', '750e8400-e29b-41d4-a716-446655440001', 59.98, 'SHIPPED', NOW() - INTERVAL '2 days', NOW() - INTERVAL '1 day'),
    ('650e8400-e29b-41d4-a716-446655440002', '750e8400-e29b-41d4-a716-446655440001', 149.99, 'PENDING', NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day');

-- Create customer_summary table if not exists
CREATE TABLE IF NOT EXISTS customer_summary (
    customer_id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    total_orders INTEGER DEFAULT 0,
    total_spent DECIMAL(12, 2) DEFAULT 0.00,
    registered_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Clean existing data
TRUNCATE TABLE customer_summary CASCADE;

-- Seed customer summary
INSERT INTO customer_summary (customer_id, name, email, total_orders, total_spent, registered_at, updated_at)
VALUES
    ('750e8400-e29b-41d4-a716-446655440001', 'John Doe', 'customer@example.com', 2, 209.97, NOW() - INTERVAL '3 days', NOW() - INTERVAL '1 day');

-- Verify
SELECT 'Order summaries seeded: ' || COUNT(*) FROM order_summary;
SELECT 'Customer summaries seeded: ' || COUNT(*) FROM customer_summary;
