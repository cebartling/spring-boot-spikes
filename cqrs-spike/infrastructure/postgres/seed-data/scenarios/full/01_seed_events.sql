-- infrastructure/postgres/seed-data/scenarios/full/01_seed_events.sql
-- Full scenario - Comprehensive Event Store Data

SET search_path TO event_store;

-- Clean existing data (in reverse dependency order)
TRUNCATE TABLE domain_event CASCADE;
TRUNCATE TABLE event_stream CASCADE;

-- Seed event streams (comprehensive data)
INSERT INTO event_stream (stream_id, aggregate_type, aggregate_id, version, created_at, updated_at)
VALUES
    -- Orders (various states)
    ('550e8400-e29b-41d4-a716-446655440001', 'Order', '650e8400-e29b-41d4-a716-446655440001', 4, NOW() - INTERVAL '14 days', NOW() - INTERVAL '7 days'),
    ('550e8400-e29b-41d4-a716-446655440002', 'Order', '650e8400-e29b-41d4-a716-446655440002', 3, NOW() - INTERVAL '10 days', NOW() - INTERVAL '5 days'),
    ('550e8400-e29b-41d4-a716-446655440003', 'Order', '650e8400-e29b-41d4-a716-446655440003', 2, NOW() - INTERVAL '7 days', NOW() - INTERVAL '3 days'),
    ('550e8400-e29b-41d4-a716-446655440004', 'Order', '650e8400-e29b-41d4-a716-446655440004', 1, NOW() - INTERVAL '3 days', NOW() - INTERVAL '3 days'),
    ('550e8400-e29b-41d4-a716-446655440005', 'Order', '650e8400-e29b-41d4-a716-446655440005', 2, NOW() - INTERVAL '5 days', NOW() - INTERVAL '4 days'),
    -- Customers (multiple profiles)
    ('550e8400-e29b-41d4-a716-446655440010', 'Customer', '750e8400-e29b-41d4-a716-446655440001', 3, NOW() - INTERVAL '30 days', NOW() - INTERVAL '5 days'),
    ('550e8400-e29b-41d4-a716-446655440011', 'Customer', '750e8400-e29b-41d4-a716-446655440002', 2, NOW() - INTERVAL '20 days', NOW() - INTERVAL '10 days'),
    ('550e8400-e29b-41d4-a716-446655440012', 'Customer', '750e8400-e29b-41d4-a716-446655440003', 1, NOW() - INTERVAL '10 days', NOW() - INTERVAL '10 days'),
    -- Products (catalog items)
    ('550e8400-e29b-41d4-a716-446655440020', 'Product', '850e8400-e29b-41d4-a716-446655440001', 2, NOW() - INTERVAL '60 days', NOW() - INTERVAL '15 days'),
    ('550e8400-e29b-41d4-a716-446655440021', 'Product', '850e8400-e29b-41d4-a716-446655440002', 3, NOW() - INTERVAL '60 days', NOW() - INTERVAL '7 days'),
    ('550e8400-e29b-41d4-a716-446655440022', 'Product', '850e8400-e29b-41d4-a716-446655440003', 1, NOW() - INTERVAL '45 days', NOW() - INTERVAL '45 days'),
    ('550e8400-e29b-41d4-a716-446655440023', 'Product', '850e8400-e29b-41d4-a716-446655440004', 2, NOW() - INTERVAL '30 days', NOW() - INTERVAL '10 days'),
    -- Inventory
    ('550e8400-e29b-41d4-a716-446655440030', 'Inventory', '950e8400-e29b-41d4-a716-446655440001', 5, NOW() - INTERVAL '60 days', NOW() - INTERVAL '1 day');

-- Order 1 events (completed with refund)
INSERT INTO domain_event (event_id, stream_id, event_type, event_version, aggregate_version, event_data, metadata, occurred_at, correlation_id, user_id) VALUES
    ('860e8400-e29b-41d4-a716-446655440001', '550e8400-e29b-41d4-a716-446655440001', 'OrderCreated', 1, 1,
     '{"orderId": "650e8400-e29b-41d4-a716-446655440001", "customerId": "750e8400-e29b-41d4-a716-446655440001", "items": [{"productId": "850e8400-e29b-41d4-a716-446655440001", "quantity": 2, "price": 29.99}, {"productId": "850e8400-e29b-41d4-a716-446655440002", "quantity": 1, "price": 79.99}], "totalAmount": 139.97}'::jsonb,
     '{"ipAddress": "192.168.1.100", "userAgent": "Mozilla/5.0", "sessionId": "sess-001"}'::jsonb,
     NOW() - INTERVAL '14 days', '960e8400-e29b-41d4-a716-446655440001', 'user-001'),
    ('860e8400-e29b-41d4-a716-446655440002', '550e8400-e29b-41d4-a716-446655440001', 'OrderShipped', 1, 2,
     '{"orderId": "650e8400-e29b-41d4-a716-446655440001", "shippingAddress": {"street": "123 Main St", "city": "Springfield", "state": "IL", "zip": "62701"}, "trackingNumber": "FDX-12345678"}'::jsonb,
     '{"carrier": "FedEx", "shippingClass": "ground"}'::jsonb,
     NOW() - INTERVAL '12 days', '960e8400-e29b-41d4-a716-446655440001', 'system'),
    ('860e8400-e29b-41d4-a716-446655440003', '550e8400-e29b-41d4-a716-446655440001', 'OrderDelivered', 1, 3,
     '{"orderId": "650e8400-e29b-41d4-a716-446655440001", "deliveredAt": "2024-01-08T14:30:00Z", "signedBy": "John Doe"}'::jsonb,
     '{"deliveryAgent": "FedEx Driver"}'::jsonb,
     NOW() - INTERVAL '10 days', '960e8400-e29b-41d4-a716-446655440001', 'system'),
    ('860e8400-e29b-41d4-a716-446655440004', '550e8400-e29b-41d4-a716-446655440001', 'OrderPartialRefund', 1, 4,
     '{"orderId": "650e8400-e29b-41d4-a716-446655440001", "refundAmount": 29.99, "reason": "Damaged item", "itemId": "850e8400-e29b-41d4-a716-446655440001", "quantity": 1}'::jsonb,
     '{"processedBy": "support-001", "refundId": "REF-001"}'::jsonb,
     NOW() - INTERVAL '7 days', '960e8400-e29b-41d4-a716-446655440002', 'support-001');

-- Order 2 events (completed)
INSERT INTO domain_event (event_id, stream_id, event_type, event_version, aggregate_version, event_data, metadata, occurred_at, correlation_id, user_id) VALUES
    ('860e8400-e29b-41d4-a716-446655440005', '550e8400-e29b-41d4-a716-446655440002', 'OrderCreated', 1, 1,
     '{"orderId": "650e8400-e29b-41d4-a716-446655440002", "customerId": "750e8400-e29b-41d4-a716-446655440001", "items": [{"productId": "850e8400-e29b-41d4-a716-446655440003", "quantity": 1, "price": 199.99}], "totalAmount": 199.99}'::jsonb,
     '{"ipAddress": "192.168.1.100"}'::jsonb,
     NOW() - INTERVAL '10 days', '960e8400-e29b-41d4-a716-446655440003', 'user-001'),
    ('860e8400-e29b-41d4-a716-446655440006', '550e8400-e29b-41d4-a716-446655440002', 'OrderShipped', 1, 2,
     '{"orderId": "650e8400-e29b-41d4-a716-446655440002", "shippingAddress": {"street": "123 Main St", "city": "Springfield", "state": "IL", "zip": "62701"}, "trackingNumber": "UPS-87654321"}'::jsonb,
     '{"carrier": "UPS", "shippingClass": "express"}'::jsonb,
     NOW() - INTERVAL '8 days', '960e8400-e29b-41d4-a716-446655440003', 'system'),
    ('860e8400-e29b-41d4-a716-446655440007', '550e8400-e29b-41d4-a716-446655440002', 'OrderDelivered', 1, 3,
     '{"orderId": "650e8400-e29b-41d4-a716-446655440002", "deliveredAt": "2024-01-12T11:00:00Z"}'::jsonb,
     '{}'::jsonb,
     NOW() - INTERVAL '5 days', '960e8400-e29b-41d4-a716-446655440003', 'system');

-- Order 3 events (shipped)
INSERT INTO domain_event (event_id, stream_id, event_type, event_version, aggregate_version, event_data, metadata, occurred_at, correlation_id, user_id) VALUES
    ('860e8400-e29b-41d4-a716-446655440008', '550e8400-e29b-41d4-a716-446655440003', 'OrderCreated', 1, 1,
     '{"orderId": "650e8400-e29b-41d4-a716-446655440003", "customerId": "750e8400-e29b-41d4-a716-446655440002", "items": [{"productId": "850e8400-e29b-41d4-a716-446655440001", "quantity": 5, "price": 29.99}], "totalAmount": 149.95}'::jsonb,
     '{"ipAddress": "10.0.0.50", "userAgent": "iOS App/2.1"}'::jsonb,
     NOW() - INTERVAL '7 days', '960e8400-e29b-41d4-a716-446655440004', 'user-002'),
    ('860e8400-e29b-41d4-a716-446655440009', '550e8400-e29b-41d4-a716-446655440003', 'OrderShipped', 1, 2,
     '{"orderId": "650e8400-e29b-41d4-a716-446655440003", "shippingAddress": {"street": "456 Oak Ave", "city": "Chicago", "state": "IL", "zip": "60601"}, "trackingNumber": "USPS-11111111"}'::jsonb,
     '{"carrier": "USPS", "shippingClass": "priority"}'::jsonb,
     NOW() - INTERVAL '3 days', '960e8400-e29b-41d4-a716-446655440004', 'system');

-- Order 4 events (pending)
INSERT INTO domain_event (event_id, stream_id, event_type, event_version, aggregate_version, event_data, metadata, occurred_at, correlation_id, user_id) VALUES
    ('860e8400-e29b-41d4-a716-446655440010', '550e8400-e29b-41d4-a716-446655440004', 'OrderCreated', 1, 1,
     '{"orderId": "650e8400-e29b-41d4-a716-446655440004", "customerId": "750e8400-e29b-41d4-a716-446655440003", "items": [{"productId": "850e8400-e29b-41d4-a716-446655440002", "quantity": 2, "price": 79.99}, {"productId": "850e8400-e29b-41d4-a716-446655440004", "quantity": 1, "price": 49.99}], "totalAmount": 209.97}'::jsonb,
     '{"ipAddress": "172.16.0.100"}'::jsonb,
     NOW() - INTERVAL '3 days', '960e8400-e29b-41d4-a716-446655440005', 'user-003');

-- Order 5 events (cancelled)
INSERT INTO domain_event (event_id, stream_id, event_type, event_version, aggregate_version, event_data, metadata, occurred_at, correlation_id, user_id) VALUES
    ('860e8400-e29b-41d4-a716-446655440011', '550e8400-e29b-41d4-a716-446655440005', 'OrderCreated', 1, 1,
     '{"orderId": "650e8400-e29b-41d4-a716-446655440005", "customerId": "750e8400-e29b-41d4-a716-446655440002", "items": [{"productId": "850e8400-e29b-41d4-a716-446655440003", "quantity": 1, "price": 199.99}], "totalAmount": 199.99}'::jsonb,
     '{"ipAddress": "10.0.0.50"}'::jsonb,
     NOW() - INTERVAL '5 days', '960e8400-e29b-41d4-a716-446655440006', 'user-002'),
    ('860e8400-e29b-41d4-a716-446655440012', '550e8400-e29b-41d4-a716-446655440005', 'OrderCancelled', 1, 2,
     '{"orderId": "650e8400-e29b-41d4-a716-446655440005", "reason": "Customer request", "cancelledAt": "2024-01-16T09:00:00Z"}'::jsonb,
     '{"cancelledBy": "user-002"}'::jsonb,
     NOW() - INTERVAL '4 days', '960e8400-e29b-41d4-a716-446655440006', 'user-002');

-- Customer events
INSERT INTO domain_event (event_id, stream_id, event_type, event_version, aggregate_version, event_data, metadata, occurred_at, correlation_id, user_id) VALUES
    ('860e8400-e29b-41d4-a716-446655440020', '550e8400-e29b-41d4-a716-446655440010', 'CustomerRegistered', 1, 1,
     '{"customerId": "750e8400-e29b-41d4-a716-446655440001", "email": "john.doe@example.com", "name": "John Doe"}'::jsonb,
     '{"source": "web", "campaign": "winter2024"}'::jsonb,
     NOW() - INTERVAL '30 days', '960e8400-e29b-41d4-a716-446655440020', 'system'),
    ('860e8400-e29b-41d4-a716-446655440021', '550e8400-e29b-41d4-a716-446655440010', 'CustomerProfileUpdated', 1, 2,
     '{"customerId": "750e8400-e29b-41d4-a716-446655440001", "phone": "+1-555-123-4567"}'::jsonb,
     '{}'::jsonb,
     NOW() - INTERVAL '25 days', '960e8400-e29b-41d4-a716-446655440021', 'user-001'),
    ('860e8400-e29b-41d4-a716-446655440022', '550e8400-e29b-41d4-a716-446655440010', 'CustomerAddressAdded', 1, 3,
     '{"customerId": "750e8400-e29b-41d4-a716-446655440001", "addressId": "addr-001", "address": {"street": "123 Main St", "city": "Springfield", "state": "IL", "zip": "62701"}, "isDefault": true}'::jsonb,
     '{}'::jsonb,
     NOW() - INTERVAL '5 days', '960e8400-e29b-41d4-a716-446655440022', 'user-001'),
    ('860e8400-e29b-41d4-a716-446655440023', '550e8400-e29b-41d4-a716-446655440011', 'CustomerRegistered', 1, 1,
     '{"customerId": "750e8400-e29b-41d4-a716-446655440002", "email": "jane.smith@example.com", "name": "Jane Smith"}'::jsonb,
     '{"source": "mobile"}'::jsonb,
     NOW() - INTERVAL '20 days', '960e8400-e29b-41d4-a716-446655440023', 'system'),
    ('860e8400-e29b-41d4-a716-446655440024', '550e8400-e29b-41d4-a716-446655440011', 'CustomerAddressAdded', 1, 2,
     '{"customerId": "750e8400-e29b-41d4-a716-446655440002", "addressId": "addr-002", "address": {"street": "456 Oak Ave", "city": "Chicago", "state": "IL", "zip": "60601"}, "isDefault": true}'::jsonb,
     '{}'::jsonb,
     NOW() - INTERVAL '10 days', '960e8400-e29b-41d4-a716-446655440024', 'user-002'),
    ('860e8400-e29b-41d4-a716-446655440025', '550e8400-e29b-41d4-a716-446655440012', 'CustomerRegistered', 1, 1,
     '{"customerId": "750e8400-e29b-41d4-a716-446655440003", "email": "bob.wilson@example.com", "name": "Bob Wilson"}'::jsonb,
     '{"source": "referral", "referredBy": "750e8400-e29b-41d4-a716-446655440001"}'::jsonb,
     NOW() - INTERVAL '10 days', '960e8400-e29b-41d4-a716-446655440025', 'system');

-- Product events
INSERT INTO domain_event (event_id, stream_id, event_type, event_version, aggregate_version, event_data, metadata, occurred_at, correlation_id, user_id) VALUES
    ('860e8400-e29b-41d4-a716-446655440030', '550e8400-e29b-41d4-a716-446655440020', 'ProductCreated', 1, 1,
     '{"productId": "850e8400-e29b-41d4-a716-446655440001", "name": "Widget Pro", "description": "Professional grade widget for all your needs", "priceCents": 2999, "category": "widgets", "sku": "WGT-PRO-001"}'::jsonb,
     '{"createdBy": "admin"}'::jsonb,
     NOW() - INTERVAL '60 days', '960e8400-e29b-41d4-a716-446655440030', 'admin'),
    ('860e8400-e29b-41d4-a716-446655440031', '550e8400-e29b-41d4-a716-446655440020', 'ProductPriceUpdated', 1, 2,
     '{"productId": "850e8400-e29b-41d4-a716-446655440001", "oldPriceCents": 2999, "newPriceCents": 2499, "reason": "Black Friday Sale"}'::jsonb,
     '{}'::jsonb,
     NOW() - INTERVAL '15 days', '960e8400-e29b-41d4-a716-446655440031', 'admin'),
    ('860e8400-e29b-41d4-a716-446655440032', '550e8400-e29b-41d4-a716-446655440021', 'ProductCreated', 1, 1,
     '{"productId": "850e8400-e29b-41d4-a716-446655440002", "name": "Gadget Deluxe", "description": "Premium gadget with advanced features", "priceCents": 7999, "category": "gadgets", "sku": "GDG-DLX-001"}'::jsonb,
     '{"createdBy": "admin"}'::jsonb,
     NOW() - INTERVAL '60 days', '960e8400-e29b-41d4-a716-446655440032', 'admin'),
    ('860e8400-e29b-41d4-a716-446655440033', '550e8400-e29b-41d4-a716-446655440021', 'ProductDescriptionUpdated', 1, 2,
     '{"productId": "850e8400-e29b-41d4-a716-446655440002", "oldDescription": "Premium gadget with advanced features", "newDescription": "Premium gadget with advanced features and extended warranty"}'::jsonb,
     '{}'::jsonb,
     NOW() - INTERVAL '30 days', '960e8400-e29b-41d4-a716-446655440033', 'admin'),
    ('860e8400-e29b-41d4-a716-446655440034', '550e8400-e29b-41d4-a716-446655440021', 'ProductPriceUpdated', 1, 3,
     '{"productId": "850e8400-e29b-41d4-a716-446655440002", "oldPriceCents": 7999, "newPriceCents": 6999}'::jsonb,
     '{}'::jsonb,
     NOW() - INTERVAL '7 days', '960e8400-e29b-41d4-a716-446655440034', 'admin'),
    ('860e8400-e29b-41d4-a716-446655440035', '550e8400-e29b-41d4-a716-446655440022', 'ProductCreated', 1, 1,
     '{"productId": "850e8400-e29b-41d4-a716-446655440003", "name": "Super Tool", "description": "Multi-purpose tool for professionals", "priceCents": 19999, "category": "tools", "sku": "TL-SPR-001"}'::jsonb,
     '{"createdBy": "admin"}'::jsonb,
     NOW() - INTERVAL '45 days', '960e8400-e29b-41d4-a716-446655440035', 'admin'),
    ('860e8400-e29b-41d4-a716-446655440036', '550e8400-e29b-41d4-a716-446655440023', 'ProductCreated', 1, 1,
     '{"productId": "850e8400-e29b-41d4-a716-446655440004", "name": "Basic Kit", "description": "Starter kit for beginners", "priceCents": 4999, "category": "kits", "sku": "KIT-BSC-001"}'::jsonb,
     '{"createdBy": "admin"}'::jsonb,
     NOW() - INTERVAL '30 days', '960e8400-e29b-41d4-a716-446655440036', 'admin'),
    ('860e8400-e29b-41d4-a716-446655440037', '550e8400-e29b-41d4-a716-446655440023', 'ProductDiscontinued', 1, 2,
     '{"productId": "850e8400-e29b-41d4-a716-446655440004", "reason": "Replaced by new model", "discontinuedAt": "2024-01-10T00:00:00Z"}'::jsonb,
     '{}'::jsonb,
     NOW() - INTERVAL '10 days', '960e8400-e29b-41d4-a716-446655440037', 'admin');

-- Inventory events
INSERT INTO domain_event (event_id, stream_id, event_type, event_version, aggregate_version, event_data, metadata, occurred_at, correlation_id, user_id) VALUES
    ('860e8400-e29b-41d4-a716-446655440040', '550e8400-e29b-41d4-a716-446655440030', 'InventoryInitialized', 1, 1,
     '{"warehouseId": "950e8400-e29b-41d4-a716-446655440001", "location": "Warehouse A"}'::jsonb,
     '{}'::jsonb,
     NOW() - INTERVAL '60 days', '960e8400-e29b-41d4-a716-446655440040', 'admin'),
    ('860e8400-e29b-41d4-a716-446655440041', '550e8400-e29b-41d4-a716-446655440030', 'StockAdded', 1, 2,
     '{"productId": "850e8400-e29b-41d4-a716-446655440001", "quantity": 100, "reason": "Initial stock"}'::jsonb,
     '{}'::jsonb,
     NOW() - INTERVAL '60 days', '960e8400-e29b-41d4-a716-446655440041', 'admin'),
    ('860e8400-e29b-41d4-a716-446655440042', '550e8400-e29b-41d4-a716-446655440030', 'StockAdded', 1, 3,
     '{"productId": "850e8400-e29b-41d4-a716-446655440002", "quantity": 50, "reason": "Initial stock"}'::jsonb,
     '{}'::jsonb,
     NOW() - INTERVAL '60 days', '960e8400-e29b-41d4-a716-446655440042', 'admin'),
    ('860e8400-e29b-41d4-a716-446655440043', '550e8400-e29b-41d4-a716-446655440030', 'StockReserved', 1, 4,
     '{"productId": "850e8400-e29b-41d4-a716-446655440001", "quantity": 7, "orderId": "650e8400-e29b-41d4-a716-446655440001"}'::jsonb,
     '{}'::jsonb,
     NOW() - INTERVAL '14 days', '960e8400-e29b-41d4-a716-446655440001', 'system'),
    ('860e8400-e29b-41d4-a716-446655440044', '550e8400-e29b-41d4-a716-446655440030', 'StockReplenished', 1, 5,
     '{"productId": "850e8400-e29b-41d4-a716-446655440001", "quantity": 50, "purchaseOrderId": "PO-001"}'::jsonb,
     '{}'::jsonb,
     NOW() - INTERVAL '1 day', '960e8400-e29b-41d4-a716-446655440043', 'admin');

-- Verify seeded data
SELECT 'Event streams seeded: ' || COUNT(*) FROM event_stream;
SELECT 'Domain events seeded: ' || COUNT(*) FROM domain_event;
