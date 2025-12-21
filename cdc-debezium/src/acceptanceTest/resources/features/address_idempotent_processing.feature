Feature: Address Idempotent Upsert/Delete Processing
  As a CDC consumer
  I want to process address CDC events idempotently
  So that duplicate or out-of-order events do not corrupt the materialized address data

  Background:
    Given PostgreSQL is running and accessible
    And the address materialized table is empty

  Scenario: INSERT event creates new address in materialized table
    Given an address does not exist with id "660e8400-e29b-41d4-a716-446655440001"
    When a CDC insert event is processed for address:
      | id                                   | customerId                           | type     | street       | city        | state | postalCode | country | isDefault | sourceTimestamp |
      | 660e8400-e29b-41d4-a716-446655440001 | 550e8400-e29b-41d4-a716-446655440001 | shipping | 123 Main St  | Springfield | IL    | 62701      | USA     | true      | 1000            |
    Then an address should exist in the materialized table with id "660e8400-e29b-41d4-a716-446655440001"
    And the address should have street "123 Main St"
    And the address should have city "Springfield"
    And the address should have type "SHIPPING"

  Scenario: UPDATE event modifies existing address in materialized table
    Given an address exists in the materialized table:
      | id                                   | customerId                           | type     | street         | city        | state | postalCode | country | isDefault | sourceTimestamp |
      | 660e8400-e29b-41d4-a716-446655440002 | 550e8400-e29b-41d4-a716-446655440001 | shipping | 100 Old Street | OldCity     | CA    | 90210      | USA     | false     | 1000            |
    When a CDC update event is processed for address:
      | id                                   | customerId                           | type     | street         | city        | state | postalCode | country | isDefault | sourceTimestamp |
      | 660e8400-e29b-41d4-a716-446655440002 | 550e8400-e29b-41d4-a716-446655440001 | billing  | 200 New Avenue | NewCity     | NY    | 10001      | USA     | true      | 2000            |
    Then the address should have street "200 New Avenue"
    And the address should have city "NewCity"
    And the address should have type "BILLING"

  Scenario: DELETE event removes address from materialized table
    Given an address exists in the materialized table:
      | id                                   | customerId                           | type     | street       | city     | state | postalCode | country | isDefault | sourceTimestamp |
      | 660e8400-e29b-41d4-a716-446655440003 | 550e8400-e29b-41d4-a716-446655440001 | shipping | 456 Delete Ln | DeleteMe | TX    | 75001      | USA     | false     | 1000            |
    When a CDC delete event is processed for address id "660e8400-e29b-41d4-a716-446655440003"
    Then an address should not exist in the materialized table with id "660e8400-e29b-41d4-a716-446655440003"

  Scenario: Duplicate INSERT event updates existing row instead of creating duplicate
    Given an address exists in the materialized table:
      | id                                   | customerId                           | type     | street        | city      | state | postalCode | country | isDefault | sourceTimestamp |
      | 660e8400-e29b-41d4-a716-446655440004 | 550e8400-e29b-41d4-a716-446655440001 | shipping | First Address | FirstCity | WA    | 98001      | USA     | false     | 1000            |
    When a CDC insert event is processed for address:
      | id                                   | customerId                           | type    | street         | city       | state | postalCode | country | isDefault | sourceTimestamp |
      | 660e8400-e29b-41d4-a716-446655440004 | 550e8400-e29b-41d4-a716-446655440001 | billing | Second Address | SecondCity | OR    | 97001      | USA     | true      | 2000            |
    Then the address count in the materialized table should be 1
    And the address should have street "Second Address"

  Scenario: DELETE event on non-existent address succeeds without error
    Given an address does not exist with id "660e8400-e29b-41d4-a716-446655440005"
    When a CDC delete event is processed for address id "660e8400-e29b-41d4-a716-446655440005"
    Then no error should occur
    And an address should not exist in the materialized table with id "660e8400-e29b-41d4-a716-446655440005"

  Scenario: Out-of-order event with older timestamp is skipped
    Given an address exists in the materialized table:
      | id                                   | customerId                           | type     | street         | city      | state | postalCode | country | isDefault | sourceTimestamp |
      | 660e8400-e29b-41d4-a716-446655440006 | 550e8400-e29b-41d4-a716-446655440001 | shipping | Newer Address  | NewerCity | AZ    | 85001      | USA     | true      | 2000            |
    When a CDC update event is processed for address:
      | id                                   | customerId                           | type     | street        | city      | state | postalCode | country | isDefault | sourceTimestamp |
      | 660e8400-e29b-41d4-a716-446655440006 | 550e8400-e29b-41d4-a716-446655440001 | billing  | Older Address | OlderCity | NV    | 89001      | USA     | false     | 1000            |
    Then the address should have street "Newer Address"
    And the address should have city "NewerCity"

  Scenario: Event with same timestamp as existing is skipped
    Given an address exists in the materialized table:
      | id                                   | customerId                           | type     | street           | city         | state | postalCode | country | isDefault | sourceTimestamp |
      | 660e8400-e29b-41d4-a716-446655440007 | 550e8400-e29b-41d4-a716-446655440001 | shipping | Existing Address | ExistingCity | CO    | 80001      | USA     | true      | 1000            |
    When a CDC update event is processed for address:
      | id                                   | customerId                           | type    | street       | city     | state | postalCode | country | isDefault | sourceTimestamp |
      | 660e8400-e29b-41d4-a716-446655440007 | 550e8400-e29b-41d4-a716-446655440001 | billing | Same Address | SameCity | UT    | 84001      | USA     | false     | 1000            |
    Then the address should have street "Existing Address"
    And the address should have city "ExistingCity"

  Scenario: Event with newer timestamp updates the record
    Given an address exists in the materialized table:
      | id                                   | customerId                           | type     | street        | city      | state | postalCode | country | isDefault | sourceTimestamp |
      | 660e8400-e29b-41d4-a716-446655440008 | 550e8400-e29b-41d4-a716-446655440001 | shipping | Older Address | OlderCity | NM    | 87001      | USA     | false     | 1000            |
    When a CDC update event is processed for address:
      | id                                   | customerId                           | type    | street        | city      | state | postalCode | country | isDefault | sourceTimestamp |
      | 660e8400-e29b-41d4-a716-446655440008 | 550e8400-e29b-41d4-a716-446655440001 | billing | Newer Address | NewerCity | OK    | 73001      | USA     | true      | 2000            |
    Then the address should have street "Newer Address"
    And the address should have city "NewerCity"
    And the address should have type "BILLING"

  Scenario: Address with customer relationship is correctly stored
    Given an address does not exist with id "660e8400-e29b-41d4-a716-446655440009"
    When a CDC insert event is processed for address:
      | id                                   | customerId                           | type | street      | city       | state | postalCode | country | isDefault | sourceTimestamp |
      | 660e8400-e29b-41d4-a716-446655440009 | 550e8400-e29b-41d4-a716-446655440099 | home | 999 Home St | HomeCity   | FL    | 33001      | USA     | true      | 1000            |
    Then an address should exist in the materialized table with id "660e8400-e29b-41d4-a716-446655440009"
    And the address should belong to customer "550e8400-e29b-41d4-a716-446655440099"
