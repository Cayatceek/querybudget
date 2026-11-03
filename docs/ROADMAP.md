# Development plan

## 0.1.0 (in progress)
- [x] Explicit query recording scopes
- [x] Instrument JDBC Statement, PreparedStatement and CallableStatement execution
- [x] Query-count assertions
- [x] JDBC integration tests backed by H2
- [x] GitHub Actions CI
- [ ] Confirm Maven verify on CI

## 0.2.0
- [ ] SQL fingerprinting and repeated-query threshold alerts
- [ ] JUnit 5 extension and test annotations
- [ ] Query latency measurements and diagnostic reporting
- [ ] Explicit context propagation for asynchronous tests

## 0.3.0
- [ ] Hibernate/Spring Boot integration example
- [ ] Baseline snapshots and regression comparisons
- [ ] Check against real PostgreSQL via Testcontainers

## Design principles
- Test-only instrumentation: never recommend it as a production observability solution.
- API compatibility and correctness over broad features.
- Every public feature needs an integration test and documentation.
- Avoid claiming reliable N+1 detection until a measured and tested strategy exists.
