/**
 * Test data generators for k6 load tests.
 *
 * This module provides functions to generate realistic test data
 * for order creation and other API operations.
 */

/**
 * Generate a random UUID v4.
 * Note: This is a simple implementation for testing purposes.
 *
 * @returns {string} UUID string
 */
export function generateUUID() {
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (c) {
        const r = Math.random() * 16 | 0;
        const v = c === 'x' ? r : (r & 0x3 | 0x8);
        return v.toString(16);
    });
}

/**
 * Product catalog for test data.
 * Products with various price points for realistic order values.
 */
const PRODUCT_CATALOG = [
    { id: 'prod-001', name: 'Wireless Mouse', priceInCents: 2999 },
    { id: 'prod-002', name: 'Mechanical Keyboard', priceInCents: 12999 },
    { id: 'prod-003', name: 'USB-C Hub', priceInCents: 4999 },
    { id: 'prod-004', name: '27" Monitor', priceInCents: 34999 },
    { id: 'prod-005', name: 'Webcam HD', priceInCents: 7999 },
    { id: 'prod-006', name: 'Headphones', priceInCents: 19999 },
    { id: 'prod-007', name: 'Mouse Pad XL', priceInCents: 1999 },
    { id: 'prod-008', name: 'Laptop Stand', priceInCents: 5999 },
    { id: 'prod-009', name: 'Cable Management Kit', priceInCents: 2499 },
    { id: 'prod-010', name: 'Desk Lamp LED', priceInCents: 3999 },
];

/**
 * Sample street names for address generation.
 */
const STREET_NAMES = [
    'Main Street',
    'Oak Avenue',
    'Pine Road',
    'Maple Drive',
    'Cedar Lane',
    'Elm Boulevard',
    'Birch Court',
    'Willow Way',
    'Spruce Circle',
    'Ash Terrace',
];

/**
 * Sample cities for address generation.
 */
const CITIES = [
    { city: 'New York', state: 'NY', postalCode: '10001' },
    { city: 'Los Angeles', state: 'CA', postalCode: '90001' },
    { city: 'Chicago', state: 'IL', postalCode: '60601' },
    { city: 'Houston', state: 'TX', postalCode: '77001' },
    { city: 'Phoenix', state: 'AZ', postalCode: '85001' },
    { city: 'Philadelphia', state: 'PA', postalCode: '19101' },
    { city: 'San Antonio', state: 'TX', postalCode: '78201' },
    { city: 'San Diego', state: 'CA', postalCode: '92101' },
    { city: 'Dallas', state: 'TX', postalCode: '75201' },
    { city: 'San Jose', state: 'CA', postalCode: '95101' },
];

/**
 * Payment method IDs for test data.
 */
const PAYMENT_METHODS = [
    'pm_card_visa',
    'pm_card_mastercard',
    'pm_card_amex',
    'pm_card_discover',
    'pm_card_jcb',
];

/**
 * Get a random element from an array.
 *
 * @param {Array} array - Array to pick from
 * @returns {any} Random element
 */
function randomElement(array) {
    return array[Math.floor(Math.random() * array.length)];
}

/**
 * Get a random integer between min and max (inclusive).
 *
 * @param {number} min - Minimum value
 * @param {number} max - Maximum value
 * @returns {number} Random integer
 */
function randomInt(min, max) {
    return Math.floor(Math.random() * (max - min + 1)) + min;
}

/**
 * Generate a random customer ID.
 *
 * @returns {string} Customer UUID
 */
export function generateCustomerId() {
    return generateUUID();
}

/**
 * Generate a random product ID from the catalog.
 *
 * @returns {string} Product UUID
 */
export function generateProductId() {
    return generateUUID();
}

/**
 * Generate a random order item.
 *
 * @returns {object} Order item object
 */
export function generateOrderItem() {
    const product = randomElement(PRODUCT_CATALOG);
    return {
        productId: generateProductId(),
        productName: product.name,
        quantity: randomInt(1, 5),
        unitPriceInCents: product.priceInCents,
    };
}

/**
 * Generate multiple random order items.
 *
 * @param {number} count - Number of items to generate (default: random 1-5)
 * @returns {Array} Array of order item objects
 */
export function generateOrderItems(count = null) {
    const itemCount = count || randomInt(1, 5);
    const items = [];

    for (let i = 0; i < itemCount; i++) {
        items.push(generateOrderItem());
    }

    return items;
}

/**
 * Generate a random shipping address.
 *
 * @returns {object} Shipping address object
 */
export function generateShippingAddress() {
    const streetNumber = randomInt(100, 9999);
    const street = randomElement(STREET_NAMES);
    const location = randomElement(CITIES);

    return {
        street: `${streetNumber} ${street}`,
        city: location.city,
        state: location.state,
        postalCode: location.postalCode,
        country: 'USA',
    };
}

/**
 * Generate a random payment method ID.
 *
 * @returns {string} Payment method ID
 */
export function generatePaymentMethodId() {
    return randomElement(PAYMENT_METHODS);
}

/**
 * Generate a complete order creation request.
 *
 * @param {object} options - Optional overrides
 * @param {string} options.customerId - Override customer ID
 * @param {number} options.itemCount - Number of items
 * @param {string} options.paymentMethodId - Override payment method
 * @returns {object} CreateOrderRequest object
 */
export function generateCreateOrderRequest(options = {}) {
    return {
        customerId: options.customerId || generateCustomerId(),
        items: options.items || generateOrderItems(options.itemCount),
        paymentMethodId: options.paymentMethodId || generatePaymentMethodId(),
        shippingAddress: options.shippingAddress || generateShippingAddress(),
    };
}

/**
 * Generate a batch of order requests for parallel execution.
 *
 * @param {number} count - Number of requests to generate
 * @returns {Array} Array of CreateOrderRequest objects
 */
export function generateOrderBatch(count) {
    const batch = [];
    for (let i = 0; i < count; i++) {
        batch.push(generateCreateOrderRequest());
    }
    return batch;
}

/**
 * Generate a consistent customer ID for a given VU.
 * Useful for scenarios where you want the same VU to use the same customer.
 *
 * @param {number} vuId - Virtual user ID
 * @returns {string} Deterministic customer UUID based on VU ID
 */
export function getVUCustomerId(vuId) {
    // Create a consistent UUID based on VU ID
    const hex = vuId.toString(16).padStart(12, '0');
    return `00000000-0000-4000-8000-${hex}`;
}
