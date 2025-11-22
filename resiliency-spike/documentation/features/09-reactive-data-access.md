# Feature: Reactive Database Access with R2DBC

**Epic:** Platform Architecture
**Status:** Implemented
**Priority:** Critical

## User Story

**As a** platform architect
**I want** fully non-blocking reactive database access
**So that** the application can handle high concurrency efficiently with limited resources

## Description

Reactive database access using R2DBC provides non-blocking, asynchronous access to PostgreSQL. All database operations return reactive types (Mono/Flux), enabling backpressure-aware data streaming and efficient resource utilization. The reactive approach integrates seamlessly with Spring WebFlux for end-to-end reactive flows.

## Acceptance Criteria

### Reactive Repositories
- [ ] Given an entity, when I create a repository, then it extends ReactiveCrudRepository
- [ ] Given repository methods, when they execute, then they return Mono<T> for single results
- [ ] Given repository methods, when they return collections, then they return Flux<T>
- [ ] Given custom queries, when defined, then they use @Query annotation with reactive return types

### Connection Management
- [ ] Given database connections, when acquired, then they use R2DBC connection pool
- [ ] Given pool configuration, when connections are needed, then pool provides them reactively
- [ ] Given pool limits, when exceeded, then backpressure is applied to callers
- [ ] Given idle connections, when timeout occurs, then they are released back to pool

### Entity Mapping
- [ ] Given entities, when they are defined, then they use @Table and @Column annotations
- [ ] Given primary keys, when defined, then they use @Id annotation
- [ ] Given relationships, when not directly persisted, then they use @Transient annotation
- [ ] Given entity operations, when they execute, then mapping is handled by R2DBC

### Query Execution
- [ ] Given a query, when it executes, then it runs asynchronously without blocking
- [ ] Given query results, when they are streamed, then backpressure is respected
- [ ] Given parameterized queries, when they execute, then parameters are bound safely
- [ ] Given transactions, when needed, then they are managed reactively

### Data Types
- [ ] Given UUID fields, when persisted, then they are stored as PostgreSQL UUID type
- [ ] Given timestamps, when persisted, then they use OffsetDateTime with timezone
- [ ] Given JSONB fields, when persisted, then they are stored as String and parsed when retrieved
- [ ] Given monetary values, when persisted, then they are stored as integers (cents)

## Business Rules

1. All database operations must be non-blocking
2. No JDBC or JPA dependencies allowed (they are blocking)
3. Entities must be immutable data classes with copy() method
4. Connection pool must be configured with appropriate min/max sizes
5. All queries must use prepared statements for security
6. Transient relationships must be loaded via separate queries when needed

## Supported Operations

### CRUD Operations
- Create (save/insert)
- Read (findById, findAll, custom queries)
- Update (save with existing ID)
- Delete (deleteById, delete)

### Custom Queries
- Named queries via @Query
- Dynamic queries via Criteria API
- Native SQL when needed
- Aggregation queries (count, sum, etc.)

### Batch Operations
- Save all (saveAll)
- Delete all (deleteAll)
- Reactive streaming of results

## Out of Scope

- JPA/Hibernate compatibility
- Lazy loading (explicit loading required)
- Complex bidirectional relationships
- Automatic schema generation (handled by SQL scripts)
- Change tracking/dirty checking
- Caching (separate concern)

## Technical Notes

- R2DBC PostgreSQL driver provides reactive database access
- Connection pool configuration in application.properties
- Repositories use Spring Data R2DBC
- Custom repository extensions via interface/implementation pattern
- JSONB mapping handled manually via ObjectMapper
- Database schema managed via init scripts
