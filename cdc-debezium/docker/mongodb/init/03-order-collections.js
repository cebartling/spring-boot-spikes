db = db.getSiblingDB('cdc_materialized');

// Orders collection - stores order with embedded items
db.createCollection('orders', {
  validator: {
    $jsonSchema: {
      bsonType: 'object',
      required: ['_id', 'customerId', 'status', 'totalAmount', 'createdAt'],
      properties: {
        _id: { bsonType: 'string' },
        customerId: { bsonType: 'string' },
        status: {
          bsonType: 'string',
          enum: ['PENDING', 'CONFIRMED', 'PROCESSING', 'SHIPPED', 'DELIVERED', 'CANCELLED']
        },
        totalAmount: { bsonType: ['decimal', 'double', 'int', 'long'] },
        items: {
          bsonType: 'array',
          items: {
            bsonType: 'object',
            required: ['id', 'productSku', 'productName', 'quantity', 'unitPrice'],
            properties: {
              id: { bsonType: 'string' },
              productSku: { bsonType: 'string' },
              productName: { bsonType: 'string' },
              quantity: { bsonType: 'int' },
              unitPrice: { bsonType: ['decimal', 'double', 'int', 'long'] },
              lineTotal: { bsonType: ['decimal', 'double', 'int', 'long'] },
              cdcMetadata: { bsonType: 'object' }
            }
          }
        },
        createdAt: { bsonType: 'date' },
        updatedAt: { bsonType: 'date' },
        cdcMetadata: { bsonType: 'object' }
      }
    }
  }
});

// Indexes for orders collection
db.orders.createIndex({ 'customerId': 1 });
db.orders.createIndex({ 'status': 1 });
db.orders.createIndex({ 'createdAt': -1 });
db.orders.createIndex({ 'customerId': 1, 'status': 1 });
db.orders.createIndex({ 'items.productSku': 1 });

print('Order collections initialized');
