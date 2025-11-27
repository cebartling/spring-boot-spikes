-- Idempotency tracking table for command handlers
-- This table stores processed commands to prevent duplicate processing

CREATE TABLE IF NOT EXISTS command_model.processed_command (
    idempotency_key VARCHAR(255) PRIMARY KEY,
    command_type VARCHAR(100) NOT NULL,
    product_id UUID NOT NULL,
    result_data TEXT,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL
);

-- Index for cleanup queries (finding expired entries)
CREATE INDEX idx_processed_command_expires_at
    ON command_model.processed_command(expires_at);

-- Index for product-specific queries
CREATE INDEX idx_processed_command_product_id
    ON command_model.processed_command(product_id);

-- Index for command type queries (useful for monitoring)
CREATE INDEX idx_processed_command_type
    ON command_model.processed_command(command_type);

-- Comment for documentation
COMMENT ON TABLE command_model.processed_command IS
    'Tracks processed commands for idempotency. Entries expire after configured TTL (default 24 hours).';

COMMENT ON COLUMN command_model.processed_command.idempotency_key IS
    'Unique key provided by client to identify duplicate requests';

COMMENT ON COLUMN command_model.processed_command.command_type IS
    'The type of command that was processed (e.g., CreateProductCommand)';

COMMENT ON COLUMN command_model.processed_command.product_id IS
    'The product ID affected by the command';

COMMENT ON COLUMN command_model.processed_command.result_data IS
    'JSON serialized CommandSuccess result for returning to duplicate requests';

COMMENT ON COLUMN command_model.processed_command.processed_at IS
    'Timestamp when the command was originally processed';

COMMENT ON COLUMN command_model.processed_command.expires_at IS
    'Timestamp after which this record can be deleted';
