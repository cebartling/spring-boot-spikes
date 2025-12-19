# PLAN-002: PostgreSQL Schema and Seed Data

## Objective

Create the `customer` table with proper schema for CDC, configure logical replication publication, and provide seed data for testing.

## Dependencies

- PLAN-001: Docker Compose infrastructure must be running

## Changes

### Files to Create

| File | Purpose |
|------|---------|
| `docker/postgres/init/01-schema.sql` | Table schema and publication |
| `docker/postgres/init/02-seed.sql` | Initial test data |

### Schema Definition (01-schema.sql)

```sql
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
```

### Seed Data (02-seed.sql)

```sql
INSERT INTO public.customer (id, email, status, updated_at) VALUES
    ('550e8400-e29b-41d4-a716-446655440001', 'alice@example.com', 'active', now()),
    ('550e8400-e29b-41d4-a716-446655440002', 'bob@example.com', 'active', now()),
    ('550e8400-e29b-41d4-a716-446655440003', 'charlie@example.com', 'inactive', now());
```

### Docker Compose Update

Add init script mounting to postgres service:

```yaml
postgres:
  volumes:
    - ./docker/postgres/init:/docker-entrypoint-initdb.d:ro
```

## Commands to Run

```bash
# Recreate postgres to run init scripts
docker compose down postgres
docker compose up -d postgres

# Verify table exists
docker compose exec postgres psql -U postgres -d postgres -c "\d public.customer"

# Verify publication exists
docker compose exec postgres psql -U postgres -d postgres -c "SELECT * FROM pg_publication;"

# Verify publication includes customer table
docker compose exec postgres psql -U postgres -d postgres -c \
  "SELECT * FROM pg_publication_tables WHERE pubname = 'cdc_publication';"

# Verify seed data
docker compose exec postgres psql -U postgres -d postgres -c "SELECT * FROM public.customer;"

# Test INSERT (will be captured by CDC later)
docker compose exec postgres psql -U postgres -d postgres -c \
  "INSERT INTO public.customer (id, email, status) VALUES (gen_random_uuid(), 'test@example.com', 'active');"

# Test UPDATE
docker compose exec postgres psql -U postgres -d postgres -c \
  "UPDATE public.customer SET status = 'inactive' WHERE email = 'alice@example.com';"

# Test DELETE
docker compose exec postgres psql -U postgres -d postgres -c \
  "DELETE FROM public.customer WHERE email = 'test@example.com';"
```

## Acceptance Criteria

1. [ ] `public.customer` table exists with correct schema (id UUID PK, email TEXT, status TEXT, updated_at TIMESTAMPTZ)
2. [ ] Publication `cdc_publication` exists and includes `public.customer`
3. [ ] Seed data is present (3 initial customers)
4. [ ] INSERT, UPDATE, DELETE operations succeed
5. [ ] `updated_at` defaults to `now()` on insert
6. [ ] Table survives `docker compose restart postgres`

## Estimated Complexity

Low - Standard SQL schema creation.

## Notes

- PostgreSQL init scripts run alphabetically, hence the `01-` and `02-` prefixes
- Init scripts only run on first container start (when data directory is empty)
- Use `gen_random_uuid()` for UUID generation in PostgreSQL 13+
