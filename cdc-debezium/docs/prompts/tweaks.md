# Tweaks

## Upgrade PostgreSQL Version

```
Currently using Postgres quay.io/debezium/postgres:16 image. 
Upgrade to version 18 to take advantage of performance improvements and new features.
Upgrade quay.io/debezium/postgres:18 as the new image.
Create a feature branch off of main for this work.
Commit small changes with clear messages.
Once complete, create a pull request for review.
```

## Upgrade Debezium Connect Version

```
Currently using Debezium Kafka Connect debezium/connect:2.5 image. 
Upgrade to version 3.4.0 to take advantage of performance improvements and new features.
Upgrade quay.io/debezium/connect:3.4.0 as the new image.
Create a feature branch off of main for this work.
Commit small changes with clear messages.
Once complete, create a pull request for review.
```

## Tidy Up Documentation

```
We have a lot of documentation in the merged PRs that needs to be copied over to the @README.md file.
Create a feature branch off of main for this work.
Commit small changes with clear messages.
Once complete, create a pull request for review.
```

## Fix Skipped Acceptance Tests

```
We have a lot of acceptance tests that are currently being skipped by the various runners:
- `./gradlew acceptanceTest`
- `./gradlew mongoDbTest`
- `./gradlew observabilityTest`
Unskip these tests and ensure they run successfully or remove them.
Create a fix branch off of main for this work.
Commit small changes with clear messages.
Once complete, create a pull request for review.
```

## Reimplement Acceptance Tests

```
I removed all the Cucumber acceptance tests due to number of issues.
I need to reimplement these tests using a more stable framework.
Let's use JUnit 5 to create reliable acceptance tests.
Keep them separate from the unit tests using tags.
Create a feature branch off of main for this work.
Commit small changes with clear messages.
Once complete, create a pull request for review.
```

## Upgrade to JUnit 6

```
Upgrade the testing framework from JUnit 5 to JUnit 6 to take advantage of the latest features and improvements.
Create a feature branch off of main for this work.
Commit small changes with clear messages.
Once complete, create a pull request for review.
```
