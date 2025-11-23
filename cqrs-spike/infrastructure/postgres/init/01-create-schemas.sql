-- Create schemas for CQRS pattern
-- This script runs during PostgreSQL initialization

-- Create schemas
CREATE SCHEMA IF NOT EXISTS event_store;
CREATE SCHEMA IF NOT EXISTS read_model;
CREATE SCHEMA IF NOT EXISTS command_model;

-- Create extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Set search path
ALTER DATABASE cqrs_db SET search_path TO event_store, read_model, command_model, public;

-- Grant permissions to application user
GRANT USAGE ON SCHEMA event_store TO cqrs_user;
GRANT USAGE ON SCHEMA read_model TO cqrs_user;
GRANT USAGE ON SCHEMA command_model TO cqrs_user;

GRANT CREATE ON SCHEMA event_store TO cqrs_user;
GRANT CREATE ON SCHEMA read_model TO cqrs_user;
GRANT CREATE ON SCHEMA command_model TO cqrs_user;

-- Comment on schemas
COMMENT ON SCHEMA event_store IS 'Event Sourcing - immutable event log';
COMMENT ON SCHEMA read_model IS 'CQRS Read Side - denormalized projections';
COMMENT ON SCHEMA command_model IS 'CQRS Write Side - normalized domain model';

-- Log completion
DO $$
BEGIN
    RAISE NOTICE 'Schemas created successfully: event_store, read_model, command_model';
END $$;
