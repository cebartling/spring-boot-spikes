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

-- Create publication for Debezium (filtered to customer table only)
CREATE PUBLICATION cdc_publication FOR TABLE public.customer;
