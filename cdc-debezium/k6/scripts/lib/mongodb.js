// k6/scripts/lib/mongodb.js
// MongoDB helper module for k6 load testing
import mongo from 'k6/x/mongo';
import { sleep } from 'k6';
import { config } from './config.js';
import { Trend, Counter } from 'k6/metrics';

// Custom metrics
const mongoReadDuration = new Trend('mongo_read_duration', true);
const mongoReadErrors = new Counter('mongo_read_errors');
const mongoDocumentsFound = new Counter('mongo_documents_found');
const mongoDocumentsNotFound = new Counter('mongo_documents_not_found');

let client = null;

export function openConnection() {
  if (!client) {
    client = mongo.newClient(config.mongodb.uri);
  }
  return client;
}

export function closeConnection() {
  if (client) {
    client.close();
    client = null;
  }
}

export function findCustomer(customerId, maxRetries = 10, retryDelayMs = 500) {
  const start = Date.now();

  for (let attempt = 0; attempt < maxRetries; attempt++) {
    try {
      const collection = client.database(config.mongodb.database).collection(config.mongodb.collections.customers);
      const result = collection.findOne({ _id: customerId });

      if (result) {
        mongoReadDuration.add(Date.now() - start);
        mongoDocumentsFound.add(1);
        return {
          found: true,
          document: result,
          attempts: attempt + 1,
          latency: Date.now() - start
        };
      }

      // Not found yet, wait and retry
      if (attempt < maxRetries - 1) {
        sleep(retryDelayMs / 1000);
      }
    } catch (error) {
      mongoReadDuration.add(Date.now() - start);
      mongoReadErrors.add(1);
      return { found: false, error: error.message };
    }
  }

  mongoReadDuration.add(Date.now() - start);
  mongoDocumentsNotFound.add(1);
  return {
    found: false,
    attempts: maxRetries,
    latency: Date.now() - start
  };
}

export function findAddress(addressId) {
  const start = Date.now();
  try {
    const collection = client.database(config.mongodb.database).collection(config.mongodb.collections.addresses);
    const result = collection.findOne({ _id: addressId });

    mongoReadDuration.add(Date.now() - start);
    if (result) {
      mongoDocumentsFound.add(1);
    } else {
      mongoDocumentsNotFound.add(1);
    }
    return { found: !!result, document: result };
  } catch (error) {
    mongoReadDuration.add(Date.now() - start);
    mongoReadErrors.add(1);
    return { found: false, error: error.message };
  }
}

export function findOrder(orderId) {
  const start = Date.now();
  try {
    const collection = client.database(config.mongodb.database).collection(config.mongodb.collections.orders);
    const result = collection.findOne({ _id: orderId });

    mongoReadDuration.add(Date.now() - start);
    if (result) {
      mongoDocumentsFound.add(1);
    } else {
      mongoDocumentsNotFound.add(1);
    }
    return { found: !!result, document: result };
  } catch (error) {
    mongoReadDuration.add(Date.now() - start);
    mongoReadErrors.add(1);
    return { found: false, error: error.message };
  }
}

export function countCustomers() {
  try {
    const collection = client.database(config.mongodb.database).collection(config.mongodb.collections.customers);
    return collection.count({});
  } catch (error) {
    mongoReadErrors.add(1);
    return -1;
  }
}
