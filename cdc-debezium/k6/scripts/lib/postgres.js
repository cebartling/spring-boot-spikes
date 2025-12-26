// k6/scripts/lib/postgres.js
// PostgreSQL helper module for k6 load testing
import sql from 'k6/x/sql';
import { config } from './config.js';
import { Trend, Counter } from 'k6/metrics';

// Custom metrics
const pgWriteDuration = new Trend('pg_write_duration', true);
const pgWriteErrors = new Counter('pg_write_errors');
const pgRecordsInserted = new Counter('pg_records_inserted');

// Open connection at init time (module load) - this is shared across all VUs
const db = sql.open('postgres', config.postgres.connectionString);

export function openConnection() {
  // Connection is already open at init time, return it
  return db;
}

export function closeConnection() {
  // Connection is managed by k6 runtime, no-op here
  // db.close() would break other VUs
}

export function insertCustomer(customer) {
  const start = Date.now();
  try {
    db.exec(`
      INSERT INTO customer (id, email, status, updated_at)
      VALUES ($1, $2, $3, NOW())
      ON CONFLICT (id) DO UPDATE SET
        email = EXCLUDED.email,
        status = EXCLUDED.status,
        updated_at = NOW()
    `, customer.id, customer.email, customer.status);

    pgWriteDuration.add(Date.now() - start);
    pgRecordsInserted.add(1);
    return { success: true, id: customer.id, timestamp: Date.now() };
  } catch (error) {
    pgWriteDuration.add(Date.now() - start);
    pgWriteErrors.add(1);
    return { success: false, error: error.message };
  }
}

export function insertAddress(address) {
  const start = Date.now();
  try {
    db.exec(`
      INSERT INTO address (id, customer_id, type, street, city, state, postal_code, country, is_default, updated_at)
      VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, NOW())
      ON CONFLICT (id) DO UPDATE SET
        street = EXCLUDED.street,
        city = EXCLUDED.city,
        updated_at = NOW()
    `, address.id, address.customerId, address.type, address.street,
       address.city, address.state, address.postalCode, address.country, address.isDefault);

    pgWriteDuration.add(Date.now() - start);
    pgRecordsInserted.add(1);
    return { success: true, id: address.id };
  } catch (error) {
    pgWriteDuration.add(Date.now() - start);
    pgWriteErrors.add(1);
    return { success: false, error: error.message };
  }
}

export function insertOrder(order) {
  const start = Date.now();
  try {
    db.exec(`
      INSERT INTO orders (id, customer_id, status, total_amount, created_at, updated_at)
      VALUES ($1, $2, $3, $4, NOW(), NOW())
    `, order.id, order.customerId, order.status, order.totalAmount);

    pgWriteDuration.add(Date.now() - start);
    pgRecordsInserted.add(1);
    return { success: true, id: order.id };
  } catch (error) {
    pgWriteDuration.add(Date.now() - start);
    pgWriteErrors.add(1);
    return { success: false, error: error.message };
  }
}

export function updateCustomerStatus(customerId, newStatus) {
  const start = Date.now();
  try {
    db.exec(`
      UPDATE customer SET status = $1, updated_at = NOW()
      WHERE id = $2
    `, newStatus, customerId);

    pgWriteDuration.add(Date.now() - start);
    return { success: true };
  } catch (error) {
    pgWriteDuration.add(Date.now() - start);
    pgWriteErrors.add(1);
    return { success: false, error: error.message };
  }
}

export function deleteCustomer(customerId) {
  const start = Date.now();
  try {
    db.exec(`DELETE FROM customer WHERE id = $1`, customerId);
    pgWriteDuration.add(Date.now() - start);
    return { success: true };
  } catch (error) {
    pgWriteDuration.add(Date.now() - start);
    pgWriteErrors.add(1);
    return { success: false, error: error.message };
  }
}
