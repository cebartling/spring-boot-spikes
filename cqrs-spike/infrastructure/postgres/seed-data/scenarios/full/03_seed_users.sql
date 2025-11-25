-- infrastructure/postgres/seed-data/scenarios/full/03_seed_users.sql
-- Full scenario - Command Model Users

SET search_path TO command_model;

-- Create users table if not exists
CREATE TABLE IF NOT EXISTS users (
    user_id VARCHAR(50) PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    role VARCHAR(50) DEFAULT 'USER',
    active BOOLEAN DEFAULT true,
    last_login_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Clean existing data
TRUNCATE TABLE users CASCADE;

-- Seed users
INSERT INTO users (user_id, username, email, role, active, last_login_at, created_at, updated_at)
VALUES
    ('user-001', 'johndoe', 'john.doe@example.com', 'USER', true, NOW() - INTERVAL '1 day', NOW() - INTERVAL '30 days', NOW() - INTERVAL '1 day'),
    ('user-002', 'janesmith', 'jane.smith@example.com', 'USER', true, NOW() - INTERVAL '3 days', NOW() - INTERVAL '20 days', NOW() - INTERVAL '3 days'),
    ('user-003', 'bobwilson', 'bob.wilson@example.com', 'USER', true, NOW() - INTERVAL '3 days', NOW() - INTERVAL '10 days', NOW() - INTERVAL '3 days'),
    ('support-001', 'support_agent', 'support@example.com', 'SUPPORT', true, NOW() - INTERVAL '7 days', NOW() - INTERVAL '60 days', NOW() - INTERVAL '7 days'),
    ('admin', 'admin', 'admin@example.com', 'ADMIN', true, NOW() - INTERVAL '1 hour', NOW() - INTERVAL '90 days', NOW() - INTERVAL '1 hour'),
    ('system', 'system', 'system@example.com', 'SYSTEM', true, NULL, NOW() - INTERVAL '90 days', NOW() - INTERVAL '90 days');

-- Create api_keys table if not exists
CREATE TABLE IF NOT EXISTS api_keys (
    key_id VARCHAR(50) PRIMARY KEY,
    user_id VARCHAR(50) NOT NULL REFERENCES users(user_id),
    key_name VARCHAR(100) NOT NULL,
    key_hash VARCHAR(255) NOT NULL,
    scopes TEXT[] DEFAULT '{}',
    active BOOLEAN DEFAULT true,
    expires_at TIMESTAMP WITH TIME ZONE,
    last_used_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Clean existing data
TRUNCATE TABLE api_keys CASCADE;

-- Seed API keys
INSERT INTO api_keys (key_id, user_id, key_name, key_hash, scopes, active, expires_at, last_used_at, created_at)
VALUES
    ('key-001', 'admin', 'Admin API Key', 'sha256:fake-hash-admin', ARRAY['read', 'write', 'admin'], true, NOW() + INTERVAL '365 days', NOW() - INTERVAL '1 hour', NOW() - INTERVAL '60 days'),
    ('key-002', 'system', 'System Integration Key', 'sha256:fake-hash-system', ARRAY['read', 'write'], true, NULL, NOW() - INTERVAL '1 day', NOW() - INTERVAL '90 days'),
    ('key-003', 'user-001', 'John Personal Key', 'sha256:fake-hash-user-001', ARRAY['read'], true, NOW() + INTERVAL '30 days', NOW() - INTERVAL '5 days', NOW() - INTERVAL '15 days');

-- Verify
SELECT 'Users seeded: ' || COUNT(*) FROM users;
SELECT 'API keys seeded: ' || COUNT(*) FROM api_keys;
