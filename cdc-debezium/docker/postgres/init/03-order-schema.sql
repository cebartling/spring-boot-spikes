-- Order status enum
CREATE TYPE order_status AS ENUM (
    'pending', 'confirmed', 'processing', 'shipped', 'delivered', 'cancelled'
);

-- Order entity
CREATE TABLE public.orders
(
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id  UUID         NOT NULL REFERENCES public.customer (id),
    status       order_status NOT NULL DEFAULT 'pending',
    total_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_orders_customer_id ON public.orders (customer_id);
CREATE INDEX idx_orders_status ON public.orders (status);
CREATE INDEX idx_orders_created_at ON public.orders (created_at DESC);

-- Order item entity
CREATE TABLE public.order_item
(
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id     UUID           NOT NULL REFERENCES public.orders (id) ON DELETE CASCADE,
    product_sku  TEXT           NOT NULL,
    product_name TEXT           NOT NULL,
    quantity     INTEGER        NOT NULL CHECK (quantity > 0),
    unit_price   DECIMAL(10, 2) NOT NULL CHECK (unit_price >= 0),
    line_total   DECIMAL(12, 2) NOT NULL GENERATED ALWAYS AS (quantity * unit_price) STORED
);

CREATE INDEX idx_order_item_order_id ON public.order_item (order_id);
CREATE INDEX idx_order_item_sku ON public.order_item (product_sku);

-- Trigger to update order updated_at
CREATE TRIGGER orders_updated_at
    BEFORE UPDATE ON public.orders
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Function to recalculate order total
CREATE OR REPLACE FUNCTION recalculate_order_total()
RETURNS TRIGGER AS $$
BEGIN
    UPDATE public.orders
    SET total_amount = (
        SELECT COALESCE(SUM(line_total), 0)
        FROM public.order_item
        WHERE order_id = COALESCE(NEW.order_id, OLD.order_id)
    )
    WHERE id = COALESCE(NEW.order_id, OLD.order_id);
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER order_item_total_trigger
    AFTER INSERT OR UPDATE OR DELETE ON public.order_item
    FOR EACH ROW
    EXECUTE FUNCTION recalculate_order_total();

-- Update publication for Debezium to include order tables
ALTER PUBLICATION cdc_publication ADD TABLE public.orders, public.order_item;

-- Seed data: Create order for Alice
INSERT INTO public.orders (id, customer_id, status)
SELECT
    '11111111-1111-1111-1111-111111111111'::uuid,
    id,
    'confirmed'
FROM public.customer
WHERE email = 'alice@example.com';

-- Add items to Alice's order
INSERT INTO public.order_item (order_id, product_sku, product_name, quantity, unit_price)
VALUES
    ('11111111-1111-1111-1111-111111111111', 'PROD-001', 'Widget Pro', 2, 29.99),
    ('11111111-1111-1111-1111-111111111111', 'PROD-002', 'Gadget Plus', 1, 49.99);

-- Create a pending order for Bob
INSERT INTO public.orders (id, customer_id, status)
SELECT
    '22222222-2222-2222-2222-222222222222'::uuid,
    id,
    'pending'
FROM public.customer
WHERE email = 'bob@example.com';

INSERT INTO public.order_item (order_id, product_sku, product_name, quantity, unit_price)
VALUES
    ('22222222-2222-2222-2222-222222222222', 'PROD-003', 'Super Widget', 1, 99.99);
