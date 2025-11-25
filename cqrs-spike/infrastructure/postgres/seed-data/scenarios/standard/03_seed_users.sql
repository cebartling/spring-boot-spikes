-- infrastructure/postgres/seed-data/scenarios/standard/03_seed_users.sql
-- Standard scenario - Command Model Users

SET search_path TO command_model;

-- Create users table if not exists
CREATE TABLE IF NOT EXISTS users (
    user_id VARCHAR(50) PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    role VARCHAR(50) DEFAULT 'USER',
    active BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Clean existing data
TRUNCATE TABLE users CASCADE;

-- Seed users
INSERT INTO users (user_id, username, email, role, active, created_at, updated_at)
VALUES
    ('user-001', 'johndoe', 'john.doe@example.com', 'USER', true, NOW() - INTERVAL '10 days', NOW() - INTERVAL '5 days'),
    ('user-002', 'janesmith', 'jane.smith@example.com', 'USER', true, NOW() - INTERVAL '7 days', NOW() - INTERVAL '7 days'),
    ('admin', 'admin', 'admin@example.com', 'ADMIN', true, NOW() - INTERVAL '30 days', NOW() - INTERVAL '30 days'),
    ('system', 'system', 'system@example.com', 'SYSTEM', true, NOW() - INTERVAL '30 days', NOW() - INTERVAL '30 days');

-- Verify
SELECT 'Users seeded: ' || COUNT(*) FROM users;
