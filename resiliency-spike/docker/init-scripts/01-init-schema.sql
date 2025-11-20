-- Resiliency Spike Database Schema Initialization
-- This script runs once during container initialization

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Create a sample table for tracking resilience events
CREATE TABLE IF NOT EXISTS resilience_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    event_type VARCHAR(50) NOT NULL,
    event_name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL,
    error_message TEXT,
    metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Create index on event_type and created_at for efficient querying
CREATE INDEX IF NOT EXISTS idx_resilience_events_type_created
    ON resilience_events(event_type, created_at DESC);

-- Create index on status
CREATE INDEX IF NOT EXISTS idx_resilience_events_status
    ON resilience_events(status);

-- Create a table for circuit breaker state tracking
CREATE TABLE IF NOT EXISTS circuit_breaker_state (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    circuit_breaker_name VARCHAR(100) NOT NULL UNIQUE,
    state VARCHAR(20) NOT NULL,
    failure_count INTEGER DEFAULT 0,
    success_count INTEGER DEFAULT 0,
    last_failure_time TIMESTAMP WITH TIME ZONE,
    last_success_time TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Create index on circuit breaker name
CREATE INDEX IF NOT EXISTS idx_circuit_breaker_name
    ON circuit_breaker_state(circuit_breaker_name);

-- Create a table for rate limiter metrics
CREATE TABLE IF NOT EXISTS rate_limiter_metrics (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    rate_limiter_name VARCHAR(100) NOT NULL,
    permitted_calls INTEGER DEFAULT 0,
    rejected_calls INTEGER DEFAULT 0,
    window_start TIMESTAMP WITH TIME ZONE NOT NULL,
    window_end TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Create index on rate limiter name and window
CREATE INDEX IF NOT EXISTS idx_rate_limiter_window
    ON rate_limiter_metrics(rate_limiter_name, window_start DESC);

-- Create a function to update the updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create triggers to automatically update updated_at
CREATE TRIGGER update_resilience_events_updated_at
    BEFORE UPDATE ON resilience_events
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_circuit_breaker_state_updated_at
    BEFORE UPDATE ON circuit_breaker_state
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Grant permissions (if needed for specific roles)
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO resiliency_user;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO resiliency_user;
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO resiliency_user;

-- Insert some initial data for testing (optional)
-- Uncomment if you want seed data
-- INSERT INTO circuit_breaker_state (circuit_breaker_name, state, failure_count, success_count)
-- VALUES ('example-service', 'CLOSED', 0, 0)
-- ON CONFLICT (circuit_breaker_name) DO NOTHING;
