// Switch to the application database
db = db.getSiblingDB('cdc_materialized');

// Create application user with readWrite access
db.createUser({
  user: 'cdc_app',
  pwd: 'cdc_app_password',
  roles: [
    { role: 'readWrite', db: 'cdc_materialized' }
  ]
});

// Create collections with schema validation
db.createCollection('customers', {
  validator: {
    $jsonSchema: {
      bsonType: 'object',
      required: ['_id', 'email', 'status', 'updatedAt', 'cdcMetadata'],
      properties: {
        _id: { bsonType: 'string', description: 'UUID as string' },
        email: { bsonType: 'string' },
        status: { bsonType: 'string' },
        updatedAt: { bsonType: 'date' },
        cdcMetadata: {
          bsonType: 'object',
          required: ['sourceTimestamp', 'operation', 'processedAt'],
          properties: {
            sourceTimestamp: { bsonType: 'long' },
            operation: { bsonType: 'string', enum: ['INSERT', 'UPDATE', 'DELETE'] },
            kafkaOffset: { bsonType: 'long' },
            kafkaPartition: { bsonType: 'int' },
            processedAt: { bsonType: 'date' }
          }
        }
      }
    }
  }
});

// Create indexes for query optimization
db.customers.createIndex({ 'email': 1 }, { unique: true });
db.customers.createIndex({ 'cdcMetadata.sourceTimestamp': -1 });
db.customers.createIndex({ 'cdcMetadata.processedAt': -1 });
db.customers.createIndex({ 'status': 1 });

// Create addresses collection with schema validation
db.createCollection('addresses', {
  validator: {
    $jsonSchema: {
      bsonType: 'object',
      required: ['_id', 'customerId', 'type', 'street', 'city', 'postalCode', 'country', 'cdcMetadata'],
      properties: {
        _id: { bsonType: 'string', description: 'UUID as string' },
        customerId: { bsonType: 'string', description: 'Customer UUID as string' },
        type: { bsonType: 'string', enum: ['BILLING', 'SHIPPING', 'HOME', 'WORK'] },
        street: { bsonType: 'string' },
        city: { bsonType: 'string' },
        state: { bsonType: ['string', 'null'] },
        postalCode: { bsonType: 'string' },
        country: { bsonType: 'string' },
        isDefault: { bsonType: 'bool' },
        updatedAt: { bsonType: 'date' },
        cdcMetadata: {
          bsonType: 'object',
          required: ['sourceTimestamp', 'operation', 'processedAt'],
          properties: {
            sourceTimestamp: { bsonType: 'long' },
            operation: { bsonType: 'string', enum: ['INSERT', 'UPDATE', 'DELETE'] },
            kafkaOffset: { bsonType: 'long' },
            kafkaPartition: { bsonType: 'int' },
            processedAt: { bsonType: 'date' }
          }
        }
      }
    }
  }
});

// Create indexes for addresses
db.addresses.createIndex({ 'customerId': 1 });
db.addresses.createIndex({ 'customerId': 1, 'type': 1 });
db.addresses.createIndex({ 'postalCode': 1 });
db.addresses.createIndex({ 'cdcMetadata.sourceTimestamp': -1 });

print('MongoDB initialization complete');
