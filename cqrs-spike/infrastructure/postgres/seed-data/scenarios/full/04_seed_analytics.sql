-- infrastructure/postgres/seed-data/scenarios/full/04_seed_analytics.sql
-- Full scenario - Analytics Data

SET search_path TO read_model;

-- Create daily_sales_summary table if not exists
CREATE TABLE IF NOT EXISTS daily_sales_summary (
    summary_date DATE PRIMARY KEY,
    total_orders INTEGER DEFAULT 0,
    completed_orders INTEGER DEFAULT 0,
    cancelled_orders INTEGER DEFAULT 0,
    total_revenue DECIMAL(12, 2) DEFAULT 0.00,
    average_order_value DECIMAL(10, 2) DEFAULT 0.00,
    unique_customers INTEGER DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Clean existing data
TRUNCATE TABLE daily_sales_summary CASCADE;

-- Seed daily sales summary (last 14 days)
INSERT INTO daily_sales_summary (summary_date, total_orders, completed_orders, cancelled_orders, total_revenue, average_order_value, unique_customers)
VALUES
    (CURRENT_DATE - INTERVAL '14 days', 1, 0, 0, 139.97, 139.97, 1),
    (CURRENT_DATE - INTERVAL '13 days', 0, 0, 0, 0.00, 0.00, 0),
    (CURRENT_DATE - INTERVAL '12 days', 0, 1, 0, 139.97, 139.97, 1),
    (CURRENT_DATE - INTERVAL '11 days', 0, 0, 0, 0.00, 0.00, 0),
    (CURRENT_DATE - INTERVAL '10 days', 2, 1, 0, 339.96, 169.98, 2),
    (CURRENT_DATE - INTERVAL '9 days', 0, 0, 0, 0.00, 0.00, 0),
    (CURRENT_DATE - INTERVAL '8 days', 0, 1, 0, 199.99, 199.99, 1),
    (CURRENT_DATE - INTERVAL '7 days', 1, 1, 0, 349.94, 174.97, 2),
    (CURRENT_DATE - INTERVAL '6 days', 0, 0, 0, 0.00, 0.00, 0),
    (CURRENT_DATE - INTERVAL '5 days', 2, 1, 0, 409.96, 204.98, 2),
    (CURRENT_DATE - INTERVAL '4 days', 0, 0, 1, 0.00, 0.00, 1),
    (CURRENT_DATE - INTERVAL '3 days', 2, 1, 0, 359.92, 179.96, 2),
    (CURRENT_DATE - INTERVAL '2 days', 0, 0, 0, 0.00, 0.00, 0),
    (CURRENT_DATE - INTERVAL '1 day', 0, 0, 0, 0.00, 0.00, 0);

-- Create category_metrics table if not exists
CREATE TABLE IF NOT EXISTS category_metrics (
    category VARCHAR(100) PRIMARY KEY,
    product_count INTEGER DEFAULT 0,
    total_sold INTEGER DEFAULT 0,
    total_revenue DECIMAL(12, 2) DEFAULT 0.00,
    average_price_cents INTEGER DEFAULT 0,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Clean existing data
TRUNCATE TABLE category_metrics CASCADE;

-- Seed category metrics
INSERT INTO category_metrics (category, product_count, total_sold, total_revenue, average_price_cents, updated_at)
VALUES
    ('widgets', 1, 7, 174.93, 2499, NOW()),
    ('gadgets', 1, 3, 209.97, 6999, NOW()),
    ('tools', 1, 2, 399.98, 19999, NOW()),
    ('kits', 1, 1, 49.99, 4999, NOW() - INTERVAL '10 days');

-- Create customer_lifetime_value table if not exists
CREATE TABLE IF NOT EXISTS customer_lifetime_value (
    customer_id UUID PRIMARY KEY,
    customer_name VARCHAR(255) NOT NULL,
    first_order_date DATE,
    last_order_date DATE,
    total_orders INTEGER DEFAULT 0,
    total_spent DECIMAL(12, 2) DEFAULT 0.00,
    average_order_value DECIMAL(10, 2) DEFAULT 0.00,
    predicted_ltv DECIMAL(12, 2) DEFAULT 0.00,
    customer_segment VARCHAR(50),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Clean existing data
TRUNCATE TABLE customer_lifetime_value CASCADE;

-- Seed customer lifetime value
INSERT INTO customer_lifetime_value (customer_id, customer_name, first_order_date, last_order_date, total_orders, total_spent, average_order_value, predicted_ltv, customer_segment, updated_at)
VALUES
    ('750e8400-e29b-41d4-a716-446655440001', 'John Doe', CURRENT_DATE - INTERVAL '14 days', CURRENT_DATE - INTERVAL '10 days', 2, 309.97, 154.99, 619.94, 'high_value', NOW()),
    ('750e8400-e29b-41d4-a716-446655440002', 'Jane Smith', CURRENT_DATE - INTERVAL '7 days', CURRENT_DATE - INTERVAL '5 days', 2, 149.95, 174.97, 349.90, 'standard', NOW()),
    ('750e8400-e29b-41d4-a716-446655440003', 'Bob Wilson', CURRENT_DATE - INTERVAL '3 days', CURRENT_DATE - INTERVAL '3 days', 1, 0.00, 209.97, 209.97, 'new', NOW());

-- Create event_metrics table if not exists
CREATE TABLE IF NOT EXISTS event_metrics (
    event_type VARCHAR(100) PRIMARY KEY,
    total_count BIGINT DEFAULT 0,
    last_occurred_at TIMESTAMP WITH TIME ZONE,
    average_per_day DECIMAL(10, 2) DEFAULT 0.00,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Clean existing data
TRUNCATE TABLE event_metrics CASCADE;

-- Seed event metrics
INSERT INTO event_metrics (event_type, total_count, last_occurred_at, average_per_day, updated_at)
VALUES
    ('OrderCreated', 5, NOW() - INTERVAL '3 days', 0.36, NOW()),
    ('OrderShipped', 3, NOW() - INTERVAL '3 days', 0.21, NOW()),
    ('OrderDelivered', 2, NOW() - INTERVAL '5 days', 0.14, NOW()),
    ('OrderCancelled', 1, NOW() - INTERVAL '4 days', 0.07, NOW()),
    ('OrderPartialRefund', 1, NOW() - INTERVAL '7 days', 0.07, NOW()),
    ('CustomerRegistered', 3, NOW() - INTERVAL '10 days', 0.05, NOW()),
    ('CustomerProfileUpdated', 1, NOW() - INTERVAL '25 days', 0.03, NOW()),
    ('CustomerAddressAdded', 2, NOW() - INTERVAL '5 days', 0.05, NOW()),
    ('ProductCreated', 4, NOW() - INTERVAL '30 days', 0.07, NOW()),
    ('ProductPriceUpdated', 2, NOW() - INTERVAL '7 days', 0.03, NOW()),
    ('ProductDescriptionUpdated', 1, NOW() - INTERVAL '30 days', 0.02, NOW()),
    ('ProductDiscontinued', 1, NOW() - INTERVAL '10 days', 0.02, NOW()),
    ('InventoryInitialized', 1, NOW() - INTERVAL '60 days', 0.02, NOW()),
    ('StockAdded', 2, NOW() - INTERVAL '60 days', 0.03, NOW()),
    ('StockReserved', 1, NOW() - INTERVAL '14 days', 0.02, NOW()),
    ('StockReplenished', 1, NOW() - INTERVAL '1 day', 0.02, NOW());

-- Verify
SELECT 'Daily sales summaries seeded: ' || COUNT(*) FROM daily_sales_summary;
SELECT 'Category metrics seeded: ' || COUNT(*) FROM category_metrics;
SELECT 'Customer lifetime values seeded: ' || COUNT(*) FROM customer_lifetime_value;
SELECT 'Event metrics seeded: ' || COUNT(*) FROM event_metrics;
