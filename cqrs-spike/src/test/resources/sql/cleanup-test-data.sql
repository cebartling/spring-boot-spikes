-- Test Data Cleanup Script
-- Run this script before test suites to ensure a clean database state.
--
-- Usage:
--   psql -h localhost -U cqrs_user -d cqrs_db -f cleanup-test-data.sql
--
-- Or via Docker:
--   docker exec -i cqrs-spike-postgres psql -U cqrs_user -d cqrs_db < cleanup-test-data.sql

-- ============================================================================
-- IMPORTANT: This script deletes ALL test data. Use with caution!
-- ============================================================================

BEGIN;

-- 1. Clean idempotency records (command_model schema)
DELETE FROM command_model.processed_command;
RAISE NOTICE 'Cleaned command_model.processed_command';

-- 2. Clean read model (read_model schema)
DELETE FROM read_model.product;
DELETE FROM read_model.projection_position;
RAISE NOTICE 'Cleaned read_model tables';

-- 3. Clean event store (event_store schema)
-- Must delete events before streams due to foreign key
DELETE FROM event_store.domain_event;
DELETE FROM event_store.event_stream;
RAISE NOTICE 'Cleaned event_store tables';

COMMIT;

-- Verify cleanup
SELECT 'command_model.processed_command' as table_name, COUNT(*) as row_count FROM command_model.processed_command
UNION ALL
SELECT 'read_model.product', COUNT(*) FROM read_model.product
UNION ALL
SELECT 'read_model.projection_position', COUNT(*) FROM read_model.projection_position
UNION ALL
SELECT 'event_store.domain_event', COUNT(*) FROM event_store.domain_event
UNION ALL
SELECT 'event_store.event_stream', COUNT(*) FROM event_store.event_stream;
