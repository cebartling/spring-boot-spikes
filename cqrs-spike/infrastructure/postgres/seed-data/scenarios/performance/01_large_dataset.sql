-- infrastructure/postgres/seed-data/scenarios/performance/01_large_dataset.sql
-- Performance scenario - Large Dataset for Load Testing

SET search_path TO event_store;

-- Enable uuid-ossp extension if not exists
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Clean existing test data (preserve non-test data if any)
DELETE FROM domain_event WHERE user_id = 'perf-test-user';
DELETE FROM event_stream WHERE aggregate_type = 'TestAggregate';

-- Generate 1000 event streams with 10 events each (10,000 total events)
DO $$
DECLARE
    i INTEGER;
    j INTEGER;
    stream_uuid UUID;
    aggregate_uuid UUID;
    event_uuid UUID;
    correlation_uuid UUID;
    batch_start TIMESTAMP;
BEGIN
    batch_start := NOW();
    RAISE NOTICE 'Starting performance data generation at %', batch_start;

    FOR i IN 1..1000 LOOP
        stream_uuid := uuid_generate_v4();
        aggregate_uuid := uuid_generate_v4();
        correlation_uuid := uuid_generate_v4();

        -- Create stream
        INSERT INTO event_stream (stream_id, aggregate_type, aggregate_id, version, created_at, updated_at)
        VALUES (stream_uuid, 'TestAggregate', aggregate_uuid, 10, NOW() - (i || ' hours')::INTERVAL, NOW());

        -- Create 10 events per stream
        FOR j IN 1..10 LOOP
            event_uuid := uuid_generate_v4();

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
            ) VALUES (
                event_uuid,
                stream_uuid,
                'TestEvent' || j,
                1,
                j,
                jsonb_build_object(
                    'data', 'test-' || i || '-' || j,
                    'timestamp', NOW(),
                    'iteration', i,
                    'sequence', j,
                    'payload', jsonb_build_object(
                        'field1', 'value-' || i,
                        'field2', random() * 1000,
                        'field3', array[i, j, i*j],
                        'field4', md5(random()::text)
                    )
                ),
                jsonb_build_object(
                    'meta', 'value-' || i,
                    'generatedAt', NOW(),
                    'batchId', batch_start
                ),
                NOW() - (i || ' hours')::INTERVAL + (j || ' minutes')::INTERVAL,
                correlation_uuid,
                'perf-test-user'
            );
        END LOOP;

        IF i % 100 = 0 THEN
            RAISE NOTICE 'Progress: % streams created (% events)', i, i * 10;
        END IF;
    END LOOP;

    RAISE NOTICE 'Performance data generation completed in % seconds',
        EXTRACT(EPOCH FROM (NOW() - batch_start));
END $$;

-- Generate additional read model data
SET search_path TO read_model;

-- Create test order summary table if not exists
CREATE TABLE IF NOT EXISTS order_summary (
    order_id UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    total_amount DECIMAL(10, 2) NOT NULL,
    status VARCHAR(50) NOT NULL,
    item_count INTEGER DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Generate 500 test order summaries
DO $$
DECLARE
    i INTEGER;
    order_uuid UUID;
    customer_uuid UUID;
    statuses TEXT[] := ARRAY['PENDING', 'CONFIRMED', 'SHIPPED', 'DELIVERED', 'CANCELLED'];
    batch_start TIMESTAMP;
BEGIN
    batch_start := NOW();
    RAISE NOTICE 'Starting order summary generation at %', batch_start;

    FOR i IN 1..500 LOOP
        order_uuid := uuid_generate_v4();
        customer_uuid := uuid_generate_v4();

        INSERT INTO order_summary (order_id, customer_id, total_amount, status, item_count, created_at, updated_at)
        VALUES (
            order_uuid,
            customer_uuid,
            (random() * 500 + 10)::DECIMAL(10, 2),
            statuses[1 + floor(random() * 5)::INT],
            floor(random() * 10 + 1)::INT,
            NOW() - (random() * 30 || ' days')::INTERVAL,
            NOW() - (random() * 7 || ' days')::INTERVAL
        );

        IF i % 100 = 0 THEN
            RAISE NOTICE 'Order summaries progress: % created', i;
        END IF;
    END LOOP;

    RAISE NOTICE 'Order summary generation completed in % seconds',
        EXTRACT(EPOCH FROM (NOW() - batch_start));
END $$;

-- Create test customer summary table if not exists
CREATE TABLE IF NOT EXISTS customer_summary (
    customer_id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    total_orders INTEGER DEFAULT 0,
    total_spent DECIMAL(12, 2) DEFAULT 0.00,
    registered_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Generate 200 test customer summaries
DO $$
DECLARE
    i INTEGER;
    customer_uuid UUID;
    batch_start TIMESTAMP;
BEGIN
    batch_start := NOW();
    RAISE NOTICE 'Starting customer summary generation at %', batch_start;

    FOR i IN 1..200 LOOP
        customer_uuid := uuid_generate_v4();

        INSERT INTO customer_summary (customer_id, name, email, total_orders, total_spent, registered_at, updated_at)
        VALUES (
            customer_uuid,
            'Test Customer ' || i,
            'test.customer.' || i || '@perftest.example.com',
            floor(random() * 20)::INT,
            (random() * 5000)::DECIMAL(12, 2),
            NOW() - (random() * 365 || ' days')::INTERVAL,
            NOW() - (random() * 30 || ' days')::INTERVAL
        );

        IF i % 50 = 0 THEN
            RAISE NOTICE 'Customer summaries progress: % created', i;
        END IF;
    END LOOP;

    RAISE NOTICE 'Customer summary generation completed in % seconds',
        EXTRACT(EPOCH FROM (NOW() - batch_start));
END $$;

-- Verify seeded data
SET search_path TO event_store;
SELECT 'Performance test streams seeded: ' || COUNT(*) FROM event_stream WHERE aggregate_type = 'TestAggregate';
SELECT 'Performance test events seeded: ' || COUNT(*) FROM domain_event WHERE user_id = 'perf-test-user';

SET search_path TO read_model;
SELECT 'Total order summaries: ' || COUNT(*) FROM order_summary;
SELECT 'Total customer summaries: ' || COUNT(*) FROM customer_summary;

-- Performance metrics
SET search_path TO event_store;
SELECT
    'Event distribution by type:' as metric;
SELECT
    event_type,
    COUNT(*) as count
FROM domain_event
WHERE user_id = 'perf-test-user'
GROUP BY event_type
ORDER BY event_type;
