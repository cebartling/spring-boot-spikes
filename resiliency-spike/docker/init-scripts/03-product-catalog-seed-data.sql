-- Product Catalog Seed Data
-- This script populates the database with comprehensive sample product data

-- First, insert all root categories
INSERT INTO categories (name, description, parent_category_id)
VALUES
    ('Electronics', 'Electronic devices and accessories', NULL),
    ('Books', 'Books and publications', NULL),
    ('Home & Kitchen', 'Home appliances and kitchen essentials', NULL),
    ('Sports & Outdoors', 'Sports equipment and outdoor gear', NULL),
    ('Clothing', 'Apparel and fashion items', NULL),
    ('Toys & Games', 'Toys, games, and entertainment', NULL)
ON CONFLICT (name) DO NOTHING;

-- Electronics subcategories
INSERT INTO categories (name, description, parent_category_id)
VALUES
    ('Computers', 'Desktop and laptop computers', (SELECT id FROM categories WHERE name = 'Electronics')),
    ('Smartphones', 'Mobile phones and accessories', (SELECT id FROM categories WHERE name = 'Electronics')),
    ('Tablets', 'Tablet devices and accessories', (SELECT id FROM categories WHERE name = 'Electronics')),
    ('Headphones', 'Headphones and audio accessories', (SELECT id FROM categories WHERE name = 'Electronics')),
    ('Cameras', 'Cameras and photography equipment', (SELECT id FROM categories WHERE name = 'Electronics')),
    ('Wearables', 'Smart watches and fitness trackers', (SELECT id FROM categories WHERE name = 'Electronics'))
ON CONFLICT (name) DO NOTHING;

-- Home & Kitchen subcategories
INSERT INTO categories (name, description, parent_category_id)
VALUES
    ('Kitchen Appliances', 'Small kitchen appliances', (SELECT id FROM categories WHERE name = 'Home & Kitchen')),
    ('Cookware', 'Pots, pans, and cooking utensils', (SELECT id FROM categories WHERE name = 'Home & Kitchen')),
    ('Home Decor', 'Decorative items for home', (SELECT id FROM categories WHERE name = 'Home & Kitchen'))
ON CONFLICT (name) DO NOTHING;

-- Sports subcategories
INSERT INTO categories (name, description, parent_category_id)
VALUES
    ('Fitness Equipment', 'Exercise and fitness gear', (SELECT id FROM categories WHERE name = 'Sports & Outdoors')),
    ('Camping', 'Camping and hiking equipment', (SELECT id FROM categories WHERE name = 'Sports & Outdoors')),
    ('Team Sports', 'Equipment for team sports', (SELECT id FROM categories WHERE name = 'Sports & Outdoors'))
ON CONFLICT (name) DO NOTHING;

-- Clothing subcategories
INSERT INTO categories (name, description, parent_category_id)
VALUES
    ('Men''s Clothing', 'Men''s apparel', (SELECT id FROM categories WHERE name = 'Clothing')),
    ('Women''s Clothing', 'Women''s apparel', (SELECT id FROM categories WHERE name = 'Clothing')),
    ('Shoes', 'Footwear for all occasions', (SELECT id FROM categories WHERE name = 'Clothing'))
ON CONFLICT (name) DO NOTHING;

-- Books subcategories
INSERT INTO categories (name, description, parent_category_id)
VALUES
    ('Fiction', 'Fiction books and novels', (SELECT id FROM categories WHERE name = 'Books')),
    ('Non-Fiction', 'Non-fiction books', (SELECT id FROM categories WHERE name = 'Books')),
    ('Science & Technology', 'Technical and scientific books', (SELECT id FROM categories WHERE name = 'Books')),
    ('Children''s Books', 'Books for children and young adults', (SELECT id FROM categories WHERE name = 'Books'))
ON CONFLICT (name) DO NOTHING;

-- Now let's insert a comprehensive set of products

-- Electronics Products
INSERT INTO products (sku, name, description, category_id, price, stock_quantity, metadata)
VALUES
    -- Computers
    (
        'COMP-LAPTOP-001',
        'UltraBook Pro 15',
        'Lightweight 15-inch laptop with all-day battery life',
        (SELECT id FROM categories WHERE name = 'Computers'),
        1499.99,
        30,
        '{"brand": "TechMaster", "warranty": "3 years", "specs": {"ram": "16GB", "storage": "1TB SSD", "processor": "Intel i7", "screen": "15.6 inch 4K"}}'::jsonb
    ),
    (
        'COMP-LAPTOP-002',
        'Gaming Beast X1',
        'High-performance gaming laptop with RTX graphics',
        (SELECT id FROM categories WHERE name = 'Computers'),
        2299.99,
        15,
        '{"brand": "GameForce", "warranty": "2 years", "specs": {"ram": "32GB", "storage": "2TB SSD", "gpu": "RTX 4070", "processor": "AMD Ryzen 9"}}'::jsonb
    ),
    (
        'COMP-DESKTOP-001',
        'Office Pro Desktop',
        'Reliable desktop computer for business use',
        (SELECT id FROM categories WHERE name = 'Computers'),
        899.99,
        20,
        '{"brand": "BizTech", "warranty": "3 years", "specs": {"ram": "16GB", "storage": "512GB SSD", "processor": "Intel i5"}}'::jsonb
    ),

    -- Smartphones
    (
        'PHONE-FLAG-001',
        'SuperPhone 14 Pro',
        'Latest flagship smartphone with advanced AI features',
        (SELECT id FROM categories WHERE name = 'Smartphones'),
        1199.99,
        50,
        '{"brand": "SuperPhone", "warranty": "1 year", "specs": {"storage": "256GB", "screen": "6.7 inch OLED", "camera": "48MP triple camera", "5G": true}}'::jsonb
    ),
    (
        'PHONE-MID-001',
        'ValuePhone A50',
        'Affordable smartphone with great features',
        (SELECT id FROM categories WHERE name = 'Smartphones'),
        399.99,
        75,
        '{"brand": "ValueTech", "warranty": "1 year", "specs": {"storage": "128GB", "screen": "6.4 inch", "camera": "12MP dual camera", "5G": false}}'::jsonb
    ),
    (
        'PHONE-ACC-001',
        'Wireless Charging Pad',
        'Fast wireless charging for compatible devices',
        (SELECT id FROM categories WHERE name = 'Smartphones'),
        29.99,
        100,
        '{"brand": "PowerUp", "warranty": "1 year", "specs": {"wattage": "15W", "fast_charging": true}}'::jsonb
    ),

    -- Tablets
    (
        'TAB-PRO-001',
        'ProTab 12 inch',
        'Professional tablet for creative work',
        (SELECT id FROM categories WHERE name = 'Tablets'),
        799.99,
        40,
        '{"brand": "ProTech", "warranty": "2 years", "specs": {"storage": "256GB", "screen": "12.9 inch", "stylus_included": true}}'::jsonb
    ),
    (
        'TAB-BASIC-001',
        'Family Tablet 10',
        'Perfect tablet for entertainment and browsing',
        (SELECT id FROM categories WHERE name = 'Tablets'),
        299.99,
        60,
        '{"brand": "HomeTab", "warranty": "1 year", "specs": {"storage": "64GB", "screen": "10.1 inch"}}'::jsonb
    ),

    -- Headphones
    (
        'AUDIO-HP-001',
        'NoiseCancel Pro Wireless',
        'Premium noise-cancelling wireless headphones',
        (SELECT id FROM categories WHERE name = 'Headphones'),
        349.99,
        45,
        '{"brand": "AudioElite", "warranty": "2 years", "specs": {"battery_life": "30 hours", "noise_cancelling": true, "bluetooth": "5.0"}}'::jsonb
    ),
    (
        'AUDIO-EB-001',
        'SportBuds Wireless',
        'Water-resistant earbuds for active lifestyles',
        (SELECT id FROM categories WHERE name = 'Headphones'),
        129.99,
        80,
        '{"brand": "FitSound", "warranty": "1 year", "specs": {"battery_life": "8 hours", "water_resistant": "IPX7", "bluetooth": "5.2"}}'::jsonb
    ),

    -- Cameras
    (
        'CAM-DSLR-001',
        'ProShot DSLR D850',
        'Professional DSLR camera with 4K video',
        (SELECT id FROM categories WHERE name = 'Cameras'),
        1899.99,
        12,
        '{"brand": "ProPhoto", "warranty": "2 years", "specs": {"megapixels": "45.7MP", "video": "4K 60fps", "lens_mount": "F-mount"}}'::jsonb
    ),
    (
        'CAM-MIRR-001',
        'MirrorLess Alpha',
        'Compact mirrorless camera for enthusiasts',
        (SELECT id FROM categories WHERE name = 'Cameras'),
        1299.99,
        18,
        '{"brand": "AlphaVision", "warranty": "2 years", "specs": {"megapixels": "24.2MP", "video": "4K 30fps", "autofocus_points": 425}}'::jsonb
    ),

    -- Wearables
    (
        'WEAR-WATCH-001',
        'SmartWatch Series 8',
        'Advanced smartwatch with health monitoring',
        (SELECT id FROM categories WHERE name = 'Wearables'),
        449.99,
        55,
        '{"brand": "TimeTrack", "warranty": "1 year", "specs": {"battery_life": "18 hours", "water_resistant": "50m", "sensors": ["heart rate", "ECG", "SpO2"]}}'::jsonb
    ),
    (
        'WEAR-FIT-001',
        'FitBand Pro',
        'Fitness tracker with sleep monitoring',
        (SELECT id FROM categories WHERE name = 'Wearables'),
        99.99,
        90,
        '{"brand": "FitLife", "warranty": "1 year", "specs": {"battery_life": "7 days", "water_resistant": true, "sensors": ["heart rate", "sleep", "steps"]}}'::jsonb
    )
ON CONFLICT (sku) DO NOTHING;

-- Books
INSERT INTO products (sku, name, description, category_id, price, stock_quantity, metadata)
VALUES
    -- Fiction
    (
        'BOOK-FIC-001',
        'The Midnight Library',
        'A novel about life, death, and everything in between',
        (SELECT id FROM categories WHERE name = 'Fiction'),
        16.99,
        120,
        '{"author": "Matt Haig", "publisher": "Viking", "isbn": "978-0525559474", "pages": 304, "year": 2020, "format": "Hardcover"}'::jsonb
    ),
    (
        'BOOK-FIC-002',
        'Project Hail Mary',
        'A lone astronaut must save the earth',
        (SELECT id FROM categories WHERE name = 'Fiction'),
        18.99,
        95,
        '{"author": "Andy Weir", "publisher": "Ballantine Books", "isbn": "978-0593135204", "pages": 496, "year": 2021, "format": "Hardcover"}'::jsonb
    ),
    (
        'BOOK-FIC-003',
        'The Seven Husbands of Evelyn Hugo',
        'A Hollywood icon tells her story',
        (SELECT id FROM categories WHERE name = 'Fiction'),
        15.99,
        85,
        '{"author": "Taylor Jenkins Reid", "publisher": "Atria Books", "isbn": "978-1501161933", "pages": 400, "year": 2017, "format": "Paperback"}'::jsonb
    ),

    -- Non-Fiction
    (
        'BOOK-NF-001',
        'Atomic Habits',
        'Tiny changes, remarkable results',
        (SELECT id FROM categories WHERE name = 'Non-Fiction'),
        19.99,
        150,
        '{"author": "James Clear", "publisher": "Avery", "isbn": "978-0735211292", "pages": 320, "year": 2018, "format": "Hardcover"}'::jsonb
    ),
    (
        'BOOK-NF-002',
        'Educated: A Memoir',
        'A memoir about a young woman who leaves her survivalist family',
        (SELECT id FROM categories WHERE name = 'Non-Fiction'),
        17.99,
        110,
        '{"author": "Tara Westover", "publisher": "Random House", "isbn": "978-0399590504", "pages": 352, "year": 2018, "format": "Hardcover"}'::jsonb
    ),

    -- Science & Technology
    (
        'BOOK-TECH-001',
        'Clean Code',
        'A handbook of agile software craftsmanship',
        (SELECT id FROM categories WHERE name = 'Science & Technology'),
        44.99,
        65,
        '{"author": "Robert C. Martin", "publisher": "Prentice Hall", "isbn": "978-0132350884", "pages": 464, "year": 2008, "format": "Paperback"}'::jsonb
    ),
    (
        'BOOK-TECH-002',
        'Design Patterns',
        'Elements of reusable object-oriented software',
        (SELECT id FROM categories WHERE name = 'Science & Technology'),
        54.99,
        45,
        '{"author": "Gang of Four", "publisher": "Addison-Wesley", "isbn": "978-0201633610", "pages": 416, "year": 1994, "format": "Hardcover"}'::jsonb
    ),
    (
        'BOOK-TECH-003',
        'The Pragmatic Programmer',
        'Your journey to mastery, 20th Anniversary Edition',
        (SELECT id FROM categories WHERE name = 'Science & Technology'),
        49.99,
        55,
        '{"author": "David Thomas, Andrew Hunt", "publisher": "Addison-Wesley", "isbn": "978-0135957059", "pages": 352, "year": 2019, "format": "Paperback"}'::jsonb
    ),

    -- Children's Books
    (
        'BOOK-CHILD-001',
        'Where the Wild Things Are',
        'A classic children''s picture book',
        (SELECT id FROM categories WHERE name = 'Children''s Books'),
        12.99,
        80,
        '{"author": "Maurice Sendak", "publisher": "HarperCollins", "isbn": "978-0064431781", "pages": 48, "year": 1963, "format": "Hardcover", "age_range": "4-8"}'::jsonb
    ),
    (
        'BOOK-CHILD-002',
        'Harry Potter and the Sorcerer''s Stone',
        'The first book in the magical series',
        (SELECT id FROM categories WHERE name = 'Children''s Books'),
        14.99,
        125,
        '{"author": "J.K. Rowling", "publisher": "Scholastic", "isbn": "978-0439708180", "pages": 309, "year": 1998, "format": "Paperback", "age_range": "8-12"}'::jsonb
    )
ON CONFLICT (sku) DO NOTHING;

-- Home & Kitchen Products
INSERT INTO products (sku, name, description, category_id, price, stock_quantity, metadata)
VALUES
    -- Kitchen Appliances
    (
        'HOME-COFFEE-001',
        'Espresso Master Pro',
        'Professional-grade espresso machine',
        (SELECT id FROM categories WHERE name = 'Kitchen Appliances'),
        599.99,
        25,
        '{"brand": "BrewMaster", "warranty": "2 years", "specs": {"pressure": "15 bar", "capacity": "1.8L", "programmable": true}}'::jsonb
    ),
    (
        'HOME-BLEND-001',
        'PowerBlend 3000',
        'High-speed blender for smoothies and more',
        (SELECT id FROM categories WHERE name = 'Kitchen Appliances'),
        129.99,
        40,
        '{"brand": "BlendTech", "warranty": "5 years", "specs": {"power": "1500W", "capacity": "64oz", "speeds": 10}}'::jsonb
    ),
    (
        'HOME-AIR-001',
        'AirFry Pro XL',
        'Large capacity air fryer with digital controls',
        (SELECT id FROM categories WHERE name = 'Kitchen Appliances'),
        149.99,
        35,
        '{"brand": "HealthyCook", "warranty": "1 year", "specs": {"capacity": "6 quarts", "temperature_range": "180-400F", "preset_programs": 8}}'::jsonb
    ),

    -- Cookware
    (
        'HOME-PAN-001',
        'Non-Stick Pan Set 10pc',
        'Complete cookware set with non-stick coating',
        (SELECT id FROM categories WHERE name = 'Cookware'),
        199.99,
        30,
        '{"brand": "ChefPro", "warranty": "lifetime", "specs": {"pieces": 10, "material": "aluminum", "coating": "ceramic non-stick", "oven_safe": "450F"}}'::jsonb
    ),
    (
        'HOME-KNIFE-001',
        'Chef''s Knife Professional',
        '8-inch professional chef knife',
        (SELECT id FROM categories WHERE name = 'Cookware'),
        79.99,
        50,
        '{"brand": "SharpEdge", "warranty": "lifetime", "specs": {"blade_length": "8 inch", "material": "high-carbon stainless steel", "handle": "ergonomic"}}'::jsonb
    ),

    -- Home Decor
    (
        'HOME-LAMP-001',
        'Modern LED Floor Lamp',
        'Adjustable LED floor lamp with smart features',
        (SELECT id FROM categories WHERE name = 'Home Decor'),
        89.99,
        45,
        '{"brand": "LightStyle", "warranty": "2 years", "specs": {"lumens": 2000, "color_temperature": "2700-6500K", "smart_compatible": true}}'::jsonb
    ),
    (
        'HOME-RUG-001',
        'Bohemian Area Rug 5x7',
        'Soft woven area rug with geometric pattern',
        (SELECT id FROM categories WHERE name = 'Home Decor'),
        129.99,
        20,
        '{"brand": "ComfortHome", "specs": {"size": "5x7 feet", "material": "100% polypropylene", "pile_height": "0.5 inch"}}'::jsonb
    )
ON CONFLICT (sku) DO NOTHING;

-- Sports & Outdoors Products
INSERT INTO products (sku, name, description, category_id, price, stock_quantity, metadata)
VALUES
    -- Fitness Equipment
    (
        'SPORT-YOGA-001',
        'Premium Yoga Mat',
        'Extra thick yoga mat with carrying strap',
        (SELECT id FROM categories WHERE name = 'Fitness Equipment'),
        39.99,
        70,
        '{"brand": "ZenFit", "specs": {"thickness": "6mm", "material": "TPE", "size": "72x24 inch", "eco_friendly": true}}'::jsonb
    ),
    (
        'SPORT-DUMB-001',
        'Adjustable Dumbbell Set',
        'Space-saving adjustable dumbbells 5-52.5 lbs',
        (SELECT id FROM categories WHERE name = 'Fitness Equipment'),
        299.99,
        15,
        '{"brand": "IronFit", "warranty": "2 years", "specs": {"weight_range": "5-52.5 lbs", "adjustments": 15, "space_saving": true}}'::jsonb
    ),
    (
        'SPORT-TREAD-001',
        'Smart Treadmill Pro',
        'Foldable treadmill with interactive training',
        (SELECT id FROM categories WHERE name = 'Fitness Equipment'),
        1299.99,
        8,
        '{"brand": "RunTech", "warranty": "5 years", "specs": {"speed_range": "0.5-12 mph", "incline": "0-15%", "touchscreen": "10 inch", "foldable": true}}'::jsonb
    ),

    -- Camping
    (
        'SPORT-TENT-001',
        'Family Camping Tent 6-Person',
        'Spacious waterproof tent for family camping',
        (SELECT id FROM categories WHERE name = 'Camping'),
        249.99,
        22,
        '{"brand": "OutdoorLife", "specs": {"capacity": "6 person", "waterproof": true, "setup_time": "10 minutes", "weight": "18 lbs"}}'::jsonb
    ),
    (
        'SPORT-SLEEP-001',
        'Ultra-Light Sleeping Bag',
        'Compact sleeping bag rated to 20°F',
        (SELECT id FROM categories WHERE name = 'Camping'),
        89.99,
        35,
        '{"brand": "CampComfort", "specs": {"temperature_rating": "20F", "weight": "2.5 lbs", "fill": "synthetic", "packed_size": "8x14 inch"}}'::jsonb
    ),

    -- Team Sports
    (
        'SPORT-BALL-001',
        'Premium Basketball',
        'Official size indoor/outdoor basketball',
        (SELECT id FROM categories WHERE name = 'Team Sports'),
        29.99,
        60,
        '{"brand": "CourtKing", "specs": {"size": "official size 7", "material": "composite leather", "indoor_outdoor": true}}'::jsonb
    ),
    (
        'SPORT-SOCCER-001',
        'Match Soccer Ball',
        'FIFA quality pro soccer ball',
        (SELECT id FROM categories WHERE name = 'Team Sports'),
        39.99,
        50,
        '{"brand": "GoalMaster", "specs": {"size": "size 5", "material": "synthetic leather", "FIFA_approved": true}}'::jsonb
    )
ON CONFLICT (sku) DO NOTHING;

-- Clothing Products
INSERT INTO products (sku, name, description, category_id, price, stock_quantity, metadata)
VALUES
    -- Men's Clothing
    (
        'CLOTH-MEN-001',
        'Classic Cotton T-Shirt',
        'Comfortable everyday cotton tee',
        (SELECT id FROM categories WHERE name = 'Men''s Clothing'),
        19.99,
        100,
        '{"brand": "ComfortWear", "specs": {"material": "100% cotton", "sizes": ["S", "M", "L", "XL", "XXL"], "colors": ["black", "white", "navy", "gray"]}}'::jsonb
    ),
    (
        'CLOTH-MEN-002',
        'Slim Fit Jeans',
        'Modern slim fit denim jeans',
        (SELECT id FROM categories WHERE name = 'Men''s Clothing'),
        59.99,
        75,
        '{"brand": "DenimCo", "specs": {"material": "98% cotton, 2% elastane", "fit": "slim", "sizes": ["28-38"], "wash": "dark indigo"}}'::jsonb
    ),

    -- Women's Clothing
    (
        'CLOTH-WOM-001',
        'Yoga Leggings',
        'High-waisted moisture-wicking leggings',
        (SELECT id FROM categories WHERE name = 'Women''s Clothing'),
        49.99,
        85,
        '{"brand": "ActiveFit", "specs": {"material": "87% polyester, 13% spandex", "features": ["moisture-wicking", "4-way stretch"], "sizes": ["XS", "S", "M", "L", "XL"]}}'::jsonb
    ),
    (
        'CLOTH-WOM-002',
        'Summer Maxi Dress',
        'Flowy bohemian maxi dress',
        (SELECT id FROM categories WHERE name = 'Women''s Clothing'),
        69.99,
        40,
        '{"brand": "StyleFlow", "specs": {"material": "rayon", "length": "maxi", "sizes": ["XS", "S", "M", "L", "XL"], "pattern": "floral"}}'::jsonb
    ),

    -- Shoes
    (
        'CLOTH-SHOE-001',
        'Running Shoes CloudRun',
        'Lightweight running shoes with cloud cushioning',
        (SELECT id FROM categories WHERE name = 'Shoes'),
        129.99,
        55,
        '{"brand": "RunCloud", "specs": {"type": "running", "cushioning": "cloud foam", "weight": "8.5 oz", "sizes": ["6-13"], "width": "regular"}}'::jsonb
    ),
    (
        'CLOTH-SHOE-002',
        'Classic Sneakers',
        'Timeless canvas sneakers',
        (SELECT id FROM categories WHERE name = 'Shoes'),
        54.99,
        90,
        '{"brand": "ClassicKicks", "specs": {"material": "canvas", "sole": "rubber", "sizes": ["5-12"], "colors": ["white", "black", "navy", "red"]}}'::jsonb
    )
ON CONFLICT (sku) DO NOTHING;

-- Toys & Games
INSERT INTO products (sku, name, description, category_id, price, stock_quantity, metadata)
VALUES
    (
        'TOY-LEGO-001',
        'Building Blocks Master Set',
        '1000-piece creative building set',
        (SELECT id FROM categories WHERE name = 'Toys & Games'),
        79.99,
        45,
        '{"brand": "BlockMaster", "specs": {"pieces": 1000, "age_range": "6+", "themes": "creative", "compatible": "standard blocks"}}'::jsonb
    ),
    (
        'TOY-BOARD-001',
        'Strategy Board Game',
        'Award-winning family strategy game',
        (SELECT id FROM categories WHERE name = 'Toys & Games'),
        44.99,
        50,
        '{"brand": "GameNight", "specs": {"players": "2-4", "age_range": "10+", "play_time": "60-90 minutes", "category": "strategy"}}'::jsonb
    ),
    (
        'TOY-PUZZLE-001',
        '1000 Piece Jigsaw Puzzle',
        'Beautiful landscape jigsaw puzzle',
        (SELECT id FROM categories WHERE name = 'Toys & Games'),
        24.99,
        60,
        '{"brand": "PuzzlePro", "specs": {"pieces": 1000, "size": "27x20 inch", "age_range": "12+", "theme": "landscape"}}'::jsonb
    ),
    (
        'TOY-DRONE-001',
        'Beginner Drone with Camera',
        'Easy-to-fly drone with HD camera',
        (SELECT id FROM categories WHERE name = 'Toys & Games'),
        149.99,
        25,
        '{"brand": "SkyFlyer", "specs": {"camera": "720p HD", "flight_time": "15 minutes", "range": "100m", "age_range": "14+", "features": ["altitude hold", "headless mode"]}}'::jsonb
    )
ON CONFLICT (sku) DO NOTHING;

-- Add some products with low stock to test low stock queries
INSERT INTO products (sku, name, description, category_id, price, stock_quantity, metadata)
VALUES
    (
        'LOWSTOCK-001',
        'Limited Edition Gaming Headset',
        'Premium gaming headset - limited quantities',
        (SELECT id FROM categories WHERE name = 'Headphones'),
        199.99,
        3,
        '{"brand": "GameAudio", "warranty": "2 years", "specs": {"surround_sound": "7.1", "microphone": "noise-cancelling", "limited_edition": true}}'::jsonb
    ),
    (
        'LOWSTOCK-002',
        'Rare Collectible Book',
        'First edition collector''s item',
        (SELECT id FROM categories WHERE name = 'Fiction'),
        499.99,
        2,
        '{"author": "Classic Author", "publisher": "Heritage Press", "isbn": "978-0000000001", "pages": 500, "year": 1950, "format": "First Edition Hardcover", "condition": "excellent"}'::jsonb
    ),
    (
        'LOWSTOCK-003',
        'Professional Camera Lens',
        'Ultra-wide angle lens - high demand',
        (SELECT id FROM categories WHERE name = 'Cameras'),
        1599.99,
        5,
        '{"brand": "LensMaster", "warranty": "3 years", "specs": {"focal_length": "14-24mm", "aperture": "f/2.8", "mount": "universal"}}'::jsonb
    )
ON CONFLICT (sku) DO NOTHING;
