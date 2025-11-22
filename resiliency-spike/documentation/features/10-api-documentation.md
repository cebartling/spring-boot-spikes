# Feature: Interactive API Documentation

**Epic:** Developer Experience
**Status:** Implemented
**Priority:** Medium

## User Story

**As a** API consumer
**I want** interactive, up-to-date API documentation
**So that** I can understand available endpoints and test them without additional tools

## Description

Interactive API documentation using OpenAPI (Swagger) provides comprehensive, auto-generated documentation for all REST endpoints. The documentation includes endpoint descriptions, request/response schemas, parameter details, and an interactive UI for testing endpoints directly from the browser.

## Acceptance Criteria

### OpenAPI Specification
- [ ] Given REST endpoints, when documentation is generated, then OpenAPI 3.0 specification is produced
- [ ] Given the specification, when accessed, then it is available at `/api-docs` endpoint
- [ ] Given the specification, when reviewed, then it includes all controllers and endpoints

### Swagger UI
- [ ] Given Swagger UI, when accessed at `/swagger-ui.html`, then interactive documentation is displayed
- [ ] Given Swagger UI, when viewed, then endpoints are grouped by tags (controller categories)
- [ ] Given Swagger UI, when used, then I can execute API calls directly from the interface
- [ ] Given Swagger UI, when testing endpoints, then I can input parameters and see responses

### Endpoint Documentation
- [ ] Given an endpoint, when documented, then it includes operation summary and description
- [ ] Given an endpoint, when documented, then all HTTP methods are shown
- [ ] Given an endpoint, when documented, then path parameters are described
- [ ] Given an endpoint, when documented, then query parameters with defaults are shown
- [ ] Given an endpoint, when documented, then request body schemas are provided
- [ ] Given an endpoint, when documented, then response codes and schemas are listed

### Schema Documentation
- [ ] Given DTOs, when documented, then all fields are described
- [ ] Given request schemas, when shown, then required fields are indicated
- [ ] Given response schemas, when shown, then example values are provided
- [ ] Given enums, when documented, then all valid values are listed

### API Metadata
- [ ] Given API info, when displayed, then title, description, and version are shown
- [ ] Given API info, when displayed, then contact information is included
- [ ] Given operations, when sorted, then they are organized by HTTP method

## Business Rules

1. All public REST endpoints must be documented
2. Internal/admin endpoints may be excluded from public documentation
3. Sensitive data (secrets, tokens) must not appear in examples
4. Documentation must stay synchronized with code (auto-generated)
5. Actuator endpoints are excluded from API documentation
6. Documentation must be accessible without authentication (for ease of use)

## Documented Endpoints

### Product Catalog (18 endpoints)
- Product CRUD operations
- Product search and filtering
- Product lifecycle management
- Product counting and statistics

### Shopping Cart (24 endpoints)
- Cart lifecycle operations
- Cart queries and filtering
- Cart state transitions
- Cart statistics and processing

### Cart Items (15 endpoints)
- Item management operations
- Item queries and calculations
- Item validation
- Cart totals

### Cart History (7 endpoints)
- Event retrieval
- Event counting
- Activity summaries

### Cart Analytics (5 endpoints)
- Conversion and abandonment rates
- Event queries by date range

## Out of Scope

- Authentication/authorization in Swagger UI
- Code generation from OpenAPI spec
- API versioning in URLs
- Webhooks documentation
- Async API documentation (for messaging)
- Custom themes for Swagger UI
- Multi-language support

## Technical Notes

- SpringDoc OpenAPI generates documentation
- Annotations: @Tag, @Operation, @Parameter, @Schema, @ApiResponse
- WebFlux variant of SpringDoc used (not MVC)
- Configuration in application.properties
- UI customization via springdoc.swagger-ui.* properties
- Operations sorted by HTTP method, tags alphabetically
