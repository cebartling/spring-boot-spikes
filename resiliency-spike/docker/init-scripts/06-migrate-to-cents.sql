-- Migration: Convert shopping_carts monetary columns from numeric(12,2) to bigint (cents)
-- This script converts all monetary values from decimal dollars to integer cents

-- Step 1: Add new columns with _cents suffix
ALTER TABLE shopping_carts
    ADD COLUMN subtotal_cents BIGINT,
    ADD COLUMN tax_amount_cents BIGINT,
    ADD COLUMN discount_amount_cents BIGINT,
    ADD COLUMN total_amount_cents BIGINT;

-- Step 2: Migrate data from decimal to cents (multiply by 100 and round)
UPDATE shopping_carts
SET subtotal_cents = ROUND(subtotal * 100)::BIGINT,
    tax_amount_cents = ROUND(tax_amount * 100)::BIGINT,
    discount_amount_cents = ROUND(discount_amount * 100)::BIGINT,
    total_amount_cents = ROUND(total_amount * 100)::BIGINT;

-- Step 3: Set NOT NULL constraints and defaults
ALTER TABLE shopping_carts
    ALTER COLUMN subtotal_cents SET NOT NULL,
    ALTER COLUMN subtotal_cents SET DEFAULT 0,
    ALTER COLUMN tax_amount_cents SET NOT NULL,
    ALTER COLUMN tax_amount_cents SET DEFAULT 0,
    ALTER COLUMN discount_amount_cents SET NOT NULL,
    ALTER COLUMN discount_amount_cents SET DEFAULT 0,
    ALTER COLUMN total_amount_cents SET NOT NULL,
    ALTER COLUMN total_amount_cents SET DEFAULT 0;

-- Step 4: Drop old decimal columns
ALTER TABLE shopping_carts
    DROP COLUMN subtotal,
    DROP COLUMN tax_amount,
    DROP COLUMN discount_amount,
    DROP COLUMN total_amount;

-- Step 5: Drop old check constraint
ALTER TABLE shopping_carts
    DROP CONSTRAINT valid_amounts;

-- Step 6: Add new check constraint for cents columns
ALTER TABLE shopping_carts
    ADD CONSTRAINT valid_amounts CHECK (
        subtotal_cents >= 0 AND
        tax_amount_cents >= 0 AND
        discount_amount_cents >= 0 AND
        total_amount_cents >= 0
    );

-- Step 7: Update the trigger function to work with cents
CREATE OR REPLACE FUNCTION update_cart_totals()
RETURNS TRIGGER AS $$
BEGIN
    UPDATE shopping_carts
    SET subtotal_cents = (
            SELECT COALESCE(SUM(line_total_cents - discount_amount_cents), 0)
            FROM cart_items
            WHERE cart_id = NEW.cart_id
        ),
        item_count = (
            SELECT COALESCE(SUM(quantity), 0)
            FROM cart_items
            WHERE cart_id = NEW.cart_id
        ),
        total_amount_cents = (
            SELECT COALESCE(SUM(line_total_cents - discount_amount_cents), 0)
            FROM cart_items
            WHERE cart_id = NEW.cart_id
        ) + COALESCE((SELECT tax_amount_cents FROM shopping_carts WHERE id = NEW.cart_id), 0)
          - COALESCE((SELECT discount_amount_cents FROM shopping_carts WHERE id = NEW.cart_id), 0),
        updated_at = CURRENT_TIMESTAMP
    WHERE id = NEW.cart_id;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Update the trigger function for deletes
CREATE OR REPLACE FUNCTION update_cart_totals_on_delete()
RETURNS TRIGGER AS $$
BEGIN
    UPDATE shopping_carts
    SET subtotal_cents = (
            SELECT COALESCE(SUM(line_total_cents - discount_amount_cents), 0)
            FROM cart_items
            WHERE cart_id = OLD.cart_id
        ),
        item_count = (
            SELECT COALESCE(SUM(quantity), 0)
            FROM cart_items
            WHERE cart_id = OLD.cart_id
        ),
        total_amount_cents = (
            SELECT COALESCE(SUM(line_total_cents - discount_amount_cents), 0)
            FROM cart_items
            WHERE cart_id = OLD.cart_id
        ) + COALESCE((SELECT tax_amount_cents FROM shopping_carts WHERE id = OLD.cart_id), 0)
          - COALESCE((SELECT discount_amount_cents FROM shopping_carts WHERE id = OLD.cart_id), 0),
        updated_at = CURRENT_TIMESTAMP
    WHERE id = OLD.cart_id;
    RETURN OLD;
END;
$$ LANGUAGE plpgsql;
