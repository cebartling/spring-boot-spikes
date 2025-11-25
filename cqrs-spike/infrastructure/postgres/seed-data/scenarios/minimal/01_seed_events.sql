-- infrastructure/postgres/seed-data/scenarios/minimal/01_seed_events.sql
-- Minimal scenario - Event Store Data

SET search_path TO event_store;

-- Clean existing data (in reverse dependency order)
TRUNCATE TABLE domain_event CASCADE;
TRUNCATE TABLE event_stream CASCADE;

-- Seed event streams
INSERT INTO event_stream (stream_id, aggregate_type, aggregate_id, version, created_at, updated_at)
VALUES
    ('550e8400-e29b-41d4-a716-446655440001', 'Order', '650e8400-e29b-41d4-a716-446655440001', 2, NOW() - INTERVAL '2 days', NOW() - INTERVAL '1 day'),
    ('550e8400-e29b-41d4-a716-446655440002', 'Order', '650e8400-e29b-41d4-a716-446655440002', 1, NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day'),
    ('550e8400-e29b-41d4-a716-446655440003', 'Customer', '750e8400-e29b-41d4-a716-446655440001', 1, NOW() - INTERVAL '3 days', NOW() - INTERVAL '3 days');

-- Seed domain events for Order 1
INSERT INTO domain_event (
    event_id,
    stream_id,
    event_type,
    event_version,
    aggregate_version,
    event_data,
    metadata,
    occurred_at,
    correlation_id,
    user_id
) VALUES
    (
        '850e8400-e29b-41d4-a716-446655440001',
        '550e8400-e29b-41d4-a716-446655440001',
        'OrderCreated',
        1,
        1,
        '{"orderId": "650e8400-e29b-41d4-a716-446655440001", "customerId": "750e8400-e29b-41d4-a716-446655440001", "items": [{"productId": "P001", "quantity": 2, "price": 29.99}], "totalAmount": 59.98}'::jsonb,
        '{"ipAddress": "192.168.1.100", "userAgent": "Mozilla/5.0"}'::jsonb,
        NOW() - INTERVAL '2 days',
        '950e8400-e29b-41d4-a716-446655440001',
        'user-001'
    ),
    (
        '850e8400-e29b-41d4-a716-446655440002',
        '550e8400-e29b-41d4-a716-446655440001',
        'OrderShipped',
        1,
        2,
        '{"orderId": "650e8400-e29b-41d4-a716-446655440001", "shippingAddress": {"street": "123 Main St", "city": "Springfield", "zip": "12345"}, "trackingNumber": "TRACK001"}'::jsonb,
        '{"carrier": "FedEx"}'::jsonb,
        NOW() - INTERVAL '1 day',
        '950e8400-e29b-41d4-a716-446655440001',
        'system'
    );

-- Seed domain events for Order 2
INSERT INTO domain_event (
    event_id,
    stream_id,
    event_type,
    event_version,
    aggregate_version,
    event_data,
    metadata,
    occurred_at,
    correlation_id,
    user_id
) VALUES
    (
        '850e8400-e29b-41d4-a716-446655440003',
        '550e8400-e29b-41d4-a716-446655440002',
        'OrderCreated',
        1,
        1,
        '{"orderId": "650e8400-e29b-41d4-a716-446655440002", "customerId": "750e8400-e29b-41d4-a716-446655440001", "items": [{"productId": "P002", "quantity": 1, "price": 149.99}], "totalAmount": 149.99}'::jsonb,
        '{"ipAddress": "192.168.1.101"}'::jsonb,
        NOW() - INTERVAL '1 day',
        '950e8400-e29b-41d4-a716-446655440002',
        'user-001'
    );

-- Seed domain events for Customer
INSERT INTO domain_event (
    event_id,
    stream_id,
    event_type,
    event_version,
    aggregate_version,
    event_data,
    metadata,
    occurred_at,
    correlation_id,
    user_id
) VALUES
    (
        '850e8400-e29b-41d4-a716-446655440004',
        '550e8400-e29b-41d4-a716-446655440003',
        'CustomerRegistered',
        1,
        1,
        '{"customerId": "750e8400-e29b-41d4-a716-446655440001", "email": "customer@example.com", "name": "John Doe", "registeredAt": "2024-01-15T10:00:00Z"}'::jsonb,
        '{"source": "web"}'::jsonb,
        NOW() - INTERVAL '3 days',
        '950e8400-e29b-41d4-a716-446655440003',
        'system'
    );

-- Verify seeded data
SELECT 'Event streams seeded: ' || COUNT(*) FROM event_stream;
SELECT 'Domain events seeded: ' || COUNT(*) FROM domain_event;
