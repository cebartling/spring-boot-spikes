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

print('MongoDB initialization complete');
