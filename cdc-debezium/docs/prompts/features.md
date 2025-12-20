# Feature Prompts

## Initial CDC Features

```
Think hard about how to implement the feature specification found at @docs/features/FEATURE-001.md.
Break this feature specification into small, independently testable implementation plans.
For each implementation plan: describe changes, file names, commands to run, and acceptance criteria.
Optimize for fast feedback and local validation.
Write in markdown format.
Write files to @docs/implementation-plans directory.
Create a feature branch off of main for this work.
Commit changes with clear messages.
Once complete, create a pull request for review.
```

## Enhancements to the Initial CDC Features

```
Think hard about the collection of features listed below:
- Migrate the materialized tables to use MongoDB instead of PostgreSQL.
- Implement data validation checks after each CDC operation to ensure data integrity.
- Build up the source schema to include other database entities (tables) linked to the customer.
- Add support for handling schema changes in the source PostgreSQL database.    
- Add Grafana dashboards for monitoring CDC performance and data flow metrics.
- Add Grafana alerts for critical issues detected in the CDC process.
Create a comprehensive feature specification at @docs/features/FEATURE-002.md.
Feel free to break down each feature into smaller sub-features if necessary.
Feel free to use Mermaid diagrams to illustrate complex workflows or architectures. I like Mermaid diagrams. I don't like ASCII art.
Create a feature branch off of main for this work.
Commit changes with clear messages.
Once complete, create a pull request for review.
```
