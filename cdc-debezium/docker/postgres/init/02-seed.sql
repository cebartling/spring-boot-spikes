INSERT INTO public.customer (id, email, status, updated_at) VALUES
    ('550e8400-e29b-41d4-a716-446655440001', 'alice@example.com', 'active', now()),
    ('550e8400-e29b-41d4-a716-446655440002', 'bob@example.com', 'active', now()),
    ('550e8400-e29b-41d4-a716-446655440003', 'charlie@example.com', 'inactive', now());

-- Address seed data
INSERT INTO public.address (id, customer_id, type, street, city, state, postal_code, country, is_default) VALUES
    ('660e8400-e29b-41d4-a716-446655440001', '550e8400-e29b-41d4-a716-446655440001', 'shipping', '123 Main Street', 'Minneapolis', 'MN', '55401', 'USA', true),
    ('660e8400-e29b-41d4-a716-446655440002', '550e8400-e29b-41d4-a716-446655440001', 'billing', '456 Commerce Ave', 'Minneapolis', 'MN', '55402', 'USA', true),
    ('660e8400-e29b-41d4-a716-446655440003', '550e8400-e29b-41d4-a716-446655440002', 'home', '789 Oak Lane', 'St. Paul', 'MN', '55101', 'USA', true);
