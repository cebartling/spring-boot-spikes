-- Migration: Create base schemas for CQRS architecture
-- Version: 1
-- Description: Create event_store, read_model, and command_model schemas with extensions and permissions

-- Create schemas if they don't exist
CREATE SCHEMA IF NOT EXISTS event_store;
CREATE SCHEMA IF NOT EXISTS read_model;
CREATE SCHEMA IF NOT EXISTS command_model;

-- Create extensions in public schema so they're available to all schemas
SET search_path TO public;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Reset search_path to command_model for the rest of the script
SET search_path TO command_model;

-- Grant schema usage permissions
GRANT USAGE ON SCHEMA event_store TO ${application_user};
GRANT USAGE ON SCHEMA read_model TO ${application_user};
GRANT USAGE ON SCHEMA command_model TO ${application_user};

-- Grant schema creation permissions
GRANT CREATE ON SCHEMA event_store TO ${application_user};
GRANT CREATE ON SCHEMA read_model TO ${application_user};
GRANT CREATE ON SCHEMA command_model TO ${application_user};

-- Grant permissions on existing tables
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA event_store TO ${application_user};
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA read_model TO ${application_user};
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA command_model TO ${application_user};

-- Grant permissions on existing sequences
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA event_store TO ${application_user};
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA read_model TO ${application_user};
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA command_model TO ${application_user};

-- Set default privileges for future objects
ALTER DEFAULT PRIVILEGES IN SCHEMA event_store GRANT ALL ON TABLES TO ${application_user};
ALTER DEFAULT PRIVILEGES IN SCHEMA read_model GRANT ALL ON TABLES TO ${application_user};
ALTER DEFAULT PRIVILEGES IN SCHEMA command_model GRANT ALL ON TABLES TO ${application_user};

ALTER DEFAULT PRIVILEGES IN SCHEMA event_store GRANT ALL ON SEQUENCES TO ${application_user};
ALTER DEFAULT PRIVILEGES IN SCHEMA read_model GRANT ALL ON SEQUENCES TO ${application_user};
ALTER DEFAULT PRIVILEGES IN SCHEMA command_model GRANT ALL ON SEQUENCES TO ${application_user};

-- Add comments for documentation
COMMENT ON SCHEMA event_store IS 'Event sourcing schema - stores immutable domain events';
COMMENT ON SCHEMA read_model IS 'Query/read model schema - optimized for queries';
COMMENT ON SCHEMA command_model IS 'Command model schema - handles write operations';
