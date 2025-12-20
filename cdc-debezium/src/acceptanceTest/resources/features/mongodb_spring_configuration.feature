Feature: MongoDB Spring Data Configuration
  As a developer
  I want Spring Boot configured with reactive MongoDB
  So that I can persist CDC events to MongoDB

  @requires-mongodb
  Scenario: MongoDB is accessible
    Given MongoDB is running and accessible
    Then MongoDB should be healthy

  @requires-mongodb
  Scenario: ReactiveMongoRepository is available
    Given the application context is loaded
    When I check for CustomerMongoRepository bean
    Then the repository bean should be available
    And it should be a reactive repository

  @requires-mongodb
  Scenario: Document can be saved and retrieved
    Given MongoDB is running and accessible
    And the application is started
    When I save a CustomerDocument with id "test-acceptance-123"
    Then the document should be persisted in MongoDB
    And I should be able to retrieve it by id "test-acceptance-123"

  @requires-mongodb
  Scenario: CdcMetadata is properly embedded
    Given MongoDB is running and accessible
    When I save a CustomerDocument with CDC metadata
    Then the document should contain cdcMetadata field
    And cdcMetadata should have sourceTimestamp
    And cdcMetadata should have operation
    And cdcMetadata should have processedAt

  @requires-mongodb
  Scenario: Documents can be queried by status
    Given MongoDB is running and accessible
    And I have saved the following CustomerDocuments:
      | id     | email                | status   |
      | doc-1  | active1@test.com     | active   |
      | doc-2  | active2@test.com     | active   |
      | doc-3  | inactive@test.com    | inactive |
    When I query for documents with status "active"
    Then only documents with status "active" should be returned
    And the result count should be 2
