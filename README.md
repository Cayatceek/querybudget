# QueryBudget

A small, explicit SQL query budget library for Java 21. Detect surprising changes in the number of JDBC operations in integration tests.

> **Status:** experimental 0.1.0-SNAPSHOT. Not published on Maven Central.

## Why

A test may pass while a new code path introduces dozens of additional SQL calls. QueryBudget wraps your existing JDBC `DataSource` and counts `execute*` invocations only within explicitly opened scopes.

## Install locally

```bash
mvn verify
mvn install
```

Add `dev.cayatceek:querybudget:0.1.0-SNAPSHOT` as a test dependency to your Maven project.

## Example

```java
DataSource monitored = new MonitoringDataSource(originalDataSource);
try (QueryBudget.Scope budget = QueryBudget.begin()) {
    // Execute repository/service operations through monitored.
    orderService.findAll();
    budget.assertMax(5);
}
```

**Important:** Inject the monitored DataSource into the application's persistence layer; simply creating it without using it will measure nothing. Prefer wrapping the DataSource once in test configuration.

## What is counted

- One count per JDBC `execute`, `executeQuery`, `executeUpdate`, `executeLargeUpdate`, `executeBatch` or `executeLargeBatch` invocation.
- Prepared and callable statements count the SQL provided at preparation; regular statements count the SQL passed to execution.
- Failed invocations count, too; a batch counts as **one JDBC execution**, not the number of batched statements.
- Work outside the calling thread or active scope is **not** counted.
- This is a JDBC instrumentation tool, not yet a Hibernate N+1 detector or query profiler.
- Standard JDBC `unwrap(Connection.class)`, `unwrap(Statement.class)`, and statement `getConnection()` return monitored wrappers. Vendor-specific unwrapping and `ResultSet.getStatement()` can still bypass instrumentation and must not be used in measured code.
- Assertion messages omit raw SQL to avoid exposing literal values in CI logs. Explicit `countsBySql()` diagnostics can still contain sensitive SQL literals; do not log them indiscriminately.

## Scope and limitations

The initial release deliberately avoids global state shared across tests: the active measurement is a `ThreadLocal`. Asynchronous tasks, connection pools and tests using worker threads need explicit support planned for later versions. A new scope may start only after the previous scope is closed; nested scopes are rejected.

## Modules

Single Maven artifact for 0.1.0. JUnit 5 integration, SQL fingerprinting and baseline comparison are planned but not yet implemented.

## Development

- Java 21 and Maven 3.9+.
- `mvn verify` runs the JUnit 5 + H2 integration tests.
- Pull requests welcome; please add tests for every behavior change.
- Licensed under GPL-3.0 (see LICENSE).
