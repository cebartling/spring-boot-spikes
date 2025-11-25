-- infrastructure/postgres/seed-data/scenarios/standard/01_seed_events.sql
-- Standard scenario - Event Store Data (extends minimal)

SET search_path TO event_store;

-- Clean existing data (in reverse dependency order)
TRUNCATE TABLE domain_event CASCADE;
TRUNCATE TABLE event_stream CASCADE;

-- Seed event streams (more data than minimal)
INSERT INTO event_stream (stream_id, aggregate_type, aggregate_id, version, created_at, updated_at)
VALUES
    -- Orders
    ('550e8400-e29b-41d4-a716-446655440001', 'Order', '650e8400-e29b-41d4-a716-446655440001', 3, NOW() - INTERVAL '5 days', NOW() - INTERVAL '2 days'),
    ('550e8400-e29b-41d4-a716-446655440002', 'Order', '650e8400-e29b-41d4-a716-446655440002', 2, NOW() - INTERVAL '3 days', NOW() - INTERVAL '1 day'),
    ('550e8400-e29b-41d4-a716-446655440003', 'Order', '650e8400-e29b-41d4-a716-446655440003', 1, NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day'),
    -- Customers
    ('550e8400-e29b-41d4-a716-446655440010', 'Customer', '750e8400-e29b-41d4-a716-446655440001', 2, NOW() - INTERVAL '10 days', NOW() - INTERVAL '5 days'),
    ('550e8400-e29b-41d4-a716-446655440011', 'Customer', '750e8400-e29b-41d4-a716-446655440002', 1, NOW() - INTERVAL '7 days', NOW() - INTERVAL '7 days'),
    -- Products
    ('550e8400-e29b-41d4-a716-446655440020', 'Product', '850e8400-e29b-41d4-a716-446655440001', 1, NOW() - INTERVAL '30 days', NOW() - INTERVAL '30 days'),
    ('550e8400-e29b-41d4-a716-446655440021', 'Product', '850e8400-e29b-41d4-a716-446655440002', 2, NOW() - INTERVAL '30 days', NOW() - INTERVAL '15 days');

-- Order 1 events (completed order)
INSERT INTO domain_event (event_id, stream_id, event_type, event_version, aggregate_version, event_data, metadata, occurred_at, correlation_id, user_id) VALUES
    ('860e8400-e29b-41d4-a716-446655440001', '550e8400-e29b-41d4-a716-446655440001', 'OrderCreated', 1, 1,
     '{"orderId": "650e8400-e29b-41d4-a716-446655440001", "customerId": "750e8400-e29b-41d4-a716-446655440001", "items": [{"productId": "850e8400-e29b-41d4-a716-446655440001", "quantity": 2, "price": 29.99}, {"productId": "850e8400-e29b-41d4-a716-446655440002", "quantity": 1, "price": 49.99}], "totalAmount": 109.97}'::jsonb,
     '{"ipAddress": "192.168.1.100", "userAgent": "Mozilla/5.0"}'::jsonb,
     NOW() - INTERVAL '5 days', '960e8400-e29b-41d4-a716-446655440001', 'user-001'),
    ('860e8400-e29b-41d4-a716-446655440002', '550e8400-e29b-41d4-a716-446655440001', 'OrderShipped', 1, 2,
     '{"orderId": "650e8400-e29b-41d4-a716-446655440001", "shippingAddress": {"street": "123 Main St", "city": "Springfield", "zip": "12345"}, "trackingNumber": "TRACK001"}'::jsonb,
     '{"carrier": "FedEx"}'::jsonb,
     NOW() - INTERVAL '4 days', '960e8400-e29b-41d4-a716-446655440001', 'system'),
    ('860e8400-e29b-41d4-a716-446655440003', '550e8400-e29b-41d4-a716-446655440001', 'OrderDelivered', 1, 3,
     '{"orderId": "650e8400-e29b-41d4-a716-446655440001", "deliveredAt": "2024-01-20T14:30:00Z", "signedBy": "John Doe"}'::jsonb,
     '{"deliveryAgent": "FedEx Driver #42"}'::jsonb,
     NOW() - INTERVAL '2 days', '960e8400-e29b-41d4-a716-446655440001', 'system');

-- Order 2 events (shipped order)
INSERT INTO domain_event (event_id, stream_id, event_type, event_version, aggregate_version, event_data, metadata, occurred_at, correlation_id, user_id) VALUES
    ('860e8400-e29b-41d4-a716-446655440004', '550e8400-e29b-41d4-a716-446655440002', 'OrderCreated', 1, 1,
     '{"orderId": "650e8400-e29b-41d4-a716-446655440002", "customerId": "750e8400-e29b-41d4-a716-446655440001", "items": [{"productId": "850e8400-e29b-41d4-a716-446655440002", "quantity": 2, "price": 49.99}], "totalAmount": 99.98}'::jsonb,
     '{"ipAddress": "192.168.1.100"}'::jsonb,
     NOW() - INTERVAL '3 days', '960e8400-e29b-41d4-a716-446655440002', 'user-001'),
    ('860e8400-e29b-41d4-a716-446655440005', '550e8400-e29b-41d4-a716-446655440002', 'OrderShipped', 1, 2,
     '{"orderId": "650e8400-e29b-41d4-a716-446655440002", "shippingAddress": {"street": "123 Main St", "city": "Springfield", "zip": "12345"}, "trackingNumber": "TRACK002"}'::jsonb,
     '{"carrier": "UPS"}'::jsonb,
     NOW() - INTERVAL '1 day', '960e8400-e29b-41d4-a716-446655440002', 'system');

-- Order 3 events (pending order)
INSERT INTO domain_event (event_id, stream_id, event_type, event_version, aggregate_version, event_data, metadata, occurred_at, correlation_id, user_id) VALUES
    ('860e8400-e29b-41d4-a716-446655440006', '550e8400-e29b-41d4-a716-446655440003', 'OrderCreated', 1, 1,
     '{"orderId": "650e8400-e29b-41d4-a716-446655440003", "customerId": "750e8400-e29b-41d4-a716-446655440002", "items": [{"productId": "850e8400-e29b-41d4-a716-446655440001", "quantity": 3, "price": 29.99}], "totalAmount": 89.97}'::jsonb,
     '{"ipAddress": "192.168.1.101"}'::jsonb,
     NOW() - INTERVAL '1 day', '960e8400-e29b-41d4-a716-446655440003', 'user-002');

-- Customer events
INSERT INTO domain_event (event_id, stream_id, event_type, event_version, aggregate_version, event_data, metadata, occurred_at, correlation_id, user_id) VALUES
    ('860e8400-e29b-41d4-a716-446655440010', '550e8400-e29b-41d4-a716-446655440010', 'CustomerRegistered', 1, 1,
     '{"customerId": "750e8400-e29b-41d4-a716-446655440001", "email": "john.doe@example.com", "name": "John Doe", "registeredAt": "2024-01-10T10:00:00Z"}'::jsonb,
     '{"source": "web", "campaign": "winter2024"}'::jsonb,
     NOW() - INTERVAL '10 days', '960e8400-e29b-41d4-a716-446655440010', 'system'),
    ('860e8400-e29b-41d4-a716-446655440011', '550e8400-e29b-41d4-a716-446655440010', 'CustomerProfileUpdated', 1, 2,
     '{"customerId": "750e8400-e29b-41d4-a716-446655440001", "phone": "+1-555-123-4567", "updatedFields": ["phone"]}'::jsonb,
     '{"source": "web"}'::jsonb,
     NOW() - INTERVAL '5 days', '960e8400-e29b-41d4-a716-446655440011', 'user-001'),
    ('860e8400-e29b-41d4-a716-446655440012', '550e8400-e29b-41d4-a716-446655440011', 'CustomerRegistered', 1, 1,
     '{"customerId": "750e8400-e29b-41d4-a716-446655440002", "email": "jane.smith@example.com", "name": "Jane Smith", "registeredAt": "2024-01-13T15:30:00Z"}'::jsonb,
     '{"source": "mobile", "campaign": "app_install"}'::jsonb,
     NOW() - INTERVAL '7 days', '960e8400-e29b-41d4-a716-446655440012', 'system');

-- Product events
INSERT INTO domain_event (event_id, stream_id, event_type, event_version, aggregate_version, event_data, metadata, occurred_at, correlation_id, user_id) VALUES
    ('860e8400-e29b-41d4-a716-446655440020', '550e8400-e29b-41d4-a716-446655440020', 'ProductCreated', 1, 1,
     '{"productId": "850e8400-e29b-41d4-a716-446655440001", "name": "Widget Pro", "description": "Professional grade widget", "priceCents": 2999, "category": "widgets"}'::jsonb,
     '{"createdBy": "admin"}'::jsonb,
     NOW() - INTERVAL '30 days', '960e8400-e29b-41d4-a716-446655440020', 'admin'),
    ('860e8400-e29b-41d4-a716-446655440021', '550e8400-e29b-41d4-a716-446655440021', 'ProductCreated', 1, 1,
     '{"productId": "850e8400-e29b-41d4-a716-446655440002", "name": "Gadget Deluxe", "description": "Luxury gadget", "priceCents": 4999, "category": "gadgets"}'::jsonb,
     '{"createdBy": "admin"}'::jsonb,
     NOW() - INTERVAL '30 days', '960e8400-e29b-41d4-a716-446655440021', 'admin'),
    ('860e8400-e29b-41d4-a716-446655440022', '550e8400-e29b-41d4-a716-446655440021', 'ProductPriceUpdated', 1, 2,
     '{"productId": "850e8400-e29b-41d4-a716-446655440002", "oldPriceCents": 4999, "newPriceCents": 3999, "reason": "sale"}'::jsonb,
     '{"updatedBy": "admin"}'::jsonb,
     NOW() - INTERVAL '15 days', '960e8400-e29b-41d4-a716-446655440022', 'admin');

-- Verify seeded data
SELECT 'Event streams seeded: ' || COUNT(*) FROM event_stream;
SELECT 'Domain events seeded: ' || COUNT(*) FROM domain_event;
