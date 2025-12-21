-- Function to automatically update updated_at column
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create the customer table
CREATE TABLE public.customer (
    id UUID PRIMARY KEY,
    email TEXT NOT NULL,
    status TEXT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Create index for common queries
CREATE INDEX idx_customer_email ON public.customer(email);
CREATE INDEX idx_customer_status ON public.customer(status);

-- Trigger to update updated_at on customer
CREATE TRIGGER customer_updated_at
    BEFORE UPDATE ON public.customer
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Create the address table with foreign key to customer
CREATE TABLE public.address (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id UUID        NOT NULL REFERENCES public.customer(id) ON DELETE CASCADE,
    type        TEXT        NOT NULL CHECK (type IN ('billing', 'shipping', 'home', 'work')),
    street      TEXT        NOT NULL,
    city        TEXT        NOT NULL,
    state       TEXT,
    postal_code TEXT        NOT NULL,
    country     TEXT        NOT NULL DEFAULT 'USA',
    is_default  BOOLEAN     NOT NULL DEFAULT false,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Indexes for address query optimization
CREATE INDEX idx_address_customer_id ON public.address(customer_id);
CREATE INDEX idx_address_type ON public.address(type);
CREATE INDEX idx_address_postal_code ON public.address(postal_code);

-- Trigger to update updated_at on address
CREATE TRIGGER address_updated_at
    BEFORE UPDATE ON public.address
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Create publication for Debezium (includes both customer and address tables)
CREATE PUBLICATION cdc_publication FOR TABLE public.customer, public.address;

-- Materialized view of customer data (populated by CDC consumer)
CREATE TABLE public.customer_materialized
(
    id               UUID PRIMARY KEY,
    email            TEXT        NOT NULL,
    status           TEXT        NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL,
    source_timestamp BIGINT
);
