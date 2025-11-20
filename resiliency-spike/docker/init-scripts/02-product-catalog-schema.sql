-- Product Catalog Database Schema
-- This script creates tables for managing a product catalog

-- Create categories table
CREATE TABLE IF NOT EXISTS categories (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    parent_category_id UUID,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    FOREIGN KEY (parent_category_id) REFERENCES categories(id) ON DELETE SET NULL
);

-- Create index on category name
CREATE INDEX IF NOT EXISTS idx_categories_name
    ON categories(name);

-- Create index on parent category for hierarchical queries
CREATE INDEX IF NOT EXISTS idx_categories_parent
    ON categories(parent_category_id);

-- Create index on active status
CREATE INDEX IF NOT EXISTS idx_categories_active
    ON categories(is_active);

-- Create products table
CREATE TABLE IF NOT EXISTS products (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    sku VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    category_id UUID NOT NULL,
    price NUMERIC(10, 2) NOT NULL,
    stock_quantity INTEGER DEFAULT 0,
    is_active BOOLEAN DEFAULT TRUE,
    metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE RESTRICT
);

-- Create index on product SKU
CREATE INDEX IF NOT EXISTS idx_products_sku
    ON products(sku);

-- Create index on product name for searching
CREATE INDEX IF NOT EXISTS idx_products_name
    ON products(name);

-- Create index on category for filtering
CREATE INDEX IF NOT EXISTS idx_products_category
    ON products(category_id);

-- Create index on active status
CREATE INDEX IF NOT EXISTS idx_products_active
    ON products(is_active);

-- Create index on price for range queries
CREATE INDEX IF NOT EXISTS idx_products_price
    ON products(price);

-- Create index on stock quantity
CREATE INDEX IF NOT EXISTS idx_products_stock
    ON products(stock_quantity);

-- Create GIN index on metadata JSONB column for efficient JSON queries
CREATE INDEX IF NOT EXISTS idx_products_metadata
    ON products USING GIN (metadata);

-- Create triggers to automatically update updated_at
CREATE TRIGGER update_categories_updated_at
    BEFORE UPDATE ON categories
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_products_updated_at
    BEFORE UPDATE ON products
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Grant permissions
GRANT ALL PRIVILEGES ON TABLE categories TO resiliency_user;
GRANT ALL PRIVILEGES ON TABLE products TO resiliency_user;
